package com.jarvis.assistant;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Speaks text with Fish Audio, falling back to the phone's free voice.
 * Chunks are fetched in parallel but always played in order.
 */
final class Voice {

    interface Listener {
        void onAllDone();                 // main thread
        void onVoiceError(String message); // main thread, at most once per session
    }

    private static final AudioAttributes SPEECH = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();

    private final Context ctx;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService fetchers = Executors.newFixedThreadPool(2);
    private final ExecutorService playback = Executors.newSingleThreadExecutor();
    private final AtomicInteger pending = new AtomicInteger();
    private final AudioManager audio;
    private final AudioFocusRequest focus;
    private TextToSpeech tts;   // not final: the init callback refers to it
    private volatile boolean ttsReady;
    private volatile int generation;
    private volatile boolean warned;

    private final Object lock = new Object();
    private MediaPlayer current;
    private CountDownLatch currentDone;

    Voice(Context ctx, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.listener = listener;
        audio = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        // Ask other apps (e.g. TikTok) to lower their volume while Jarvis talks.
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(SPEECH)
                .build();
        tts = new TextToSpeech(this.ctx, status -> {
            if (status != TextToSpeech.SUCCESS) return;
            ttsReady = true;
            this.tts.setAudioAttributes(SPEECH);
            if (this.tts.isLanguageAvailable(Locale.UK) >= TextToSpeech.LANG_AVAILABLE) this.tts.setLanguage(Locale.UK);
            this.tts.setSpeechRate(1.1f);
        });
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {}
            @Override public void onDone(String id) { releaseCurrent(); }
            @Override public void onError(String id) { releaseCurrent(); }
            @Override public void onStop(String id, boolean interrupted) { releaseCurrent(); }
        });
    }

    boolean isSpeaking() {
        return pending.get() > 0;
    }

    void speak(String text) {
        final String clean = text.replaceAll("[*_#`>~]", "").replaceAll("\\s+", " ").trim();
        String engine = Prefs.str(ctx, Prefs.ENGINE, "fish");
        if (clean.isEmpty() || engine.equals("off")) return;
        final String fishKey = Prefs.str(ctx, Prefs.FISH_KEY, "");
        final Future<File> file = engine.equals("fish") && !fishKey.isEmpty()
                ? fetchers.submit(() -> fetchFish(clean, fishKey))
                : null;
        final int gen = generation;
        if (pending.getAndIncrement() == 0) audio.requestAudioFocus(focus);
        playback.execute(() -> {
            try {
                if (gen != generation) return;
                File f = null;
                if (file != null) {
                    try {
                        f = file.get(30, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        String why = cause.getMessage() != null ? cause.getMessage() : "Fish Audio took too long.";
                        warnOnce(why + " Using the phone voice instead.");
                    }
                }
                if (gen != generation) { if (f != null) f.delete(); return; }
                if (f != null) playFile(f); else playPhone(clean);
            } finally {
                if (pending.decrementAndGet() == 0) {
                    audio.abandonAudioFocusRequest(focus);
                    main.post(listener::onAllDone);
                }
            }
        });
    }

    /** Speaks with the phone voice only (for short error messages). */
    void speakLocal(String text) {
        if (pending.getAndIncrement() == 0) audio.requestAudioFocus(focus);
        playback.execute(() -> {
            try { playPhone(text); } finally {
                if (pending.decrementAndGet() == 0) {
                    audio.abandonAudioFocusRequest(focus);
                    main.post(listener::onAllDone);
                }
            }
        });
    }

    void stop() {
        generation++;
        synchronized (lock) {
            if (current != null) {
                try { current.stop(); } catch (IllegalStateException ignored) {}
            }
            if (currentDone != null) currentDone.countDown();
        }
        if (ttsReady) tts.stop();
    }

    void shutdown() {
        stop();
        fetchers.shutdownNow();
        playback.shutdownNow();
        tts.shutdown();
        audio.abandonAudioFocusRequest(focus);
    }

    void resetWarning() {
        warned = false;
    }

    private void warnOnce(String msg) {
        if (warned) return;
        warned = true;
        main.post(() -> listener.onVoiceError(msg));
    }

    private File fetchFish(String text, String key) throws Exception {
        JSONObject body = new JSONObject()
                .put("text", text)
                .put("format", "mp3")
                .put("mp3_bitrate", 64)
                .put("latency", "balanced")
                .put("normalize", true);
        String voice = Prefs.str(ctx, Prefs.FISH_VOICE, "");
        if (!voice.isEmpty()) body.put("reference_id", voice);

        HttpURLConnection c = (HttpURLConnection) new URL("https://api.fish.audio/v1/tts").openConnection();
        try {
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("Authorization", "Bearer " + key);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("model", Prefs.str(ctx, Prefs.FISH_MODEL, "s2-pro"));
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = c.getResponseCode();
            if (code == 401 || code == 403) throw new IOException("Fish Audio rejected your key.");
            if (code == 402) throw new IOException("Fish Audio is out of credits.");
            if (code >= 400) throw new IOException("Fish Audio error " + code + ".");
            File f = File.createTempFile("jarvis", ".mp3", ctx.getCacheDir());
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(f)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            return f;
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("Fish Audio")) throw e;
            throw new IOException("I can't reach Fish Audio.");
        } finally {
            c.disconnect();
        }
    }

    private void playFile(File f) {
        CountDownLatch done = new CountDownLatch(1);
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setAudioAttributes(SPEECH);
            mp.setDataSource(f.getAbsolutePath());
            mp.setOnCompletionListener(m -> done.countDown());
            mp.setOnErrorListener((m, what, extra) -> { done.countDown(); return true; });
            mp.prepare();
            synchronized (lock) { current = mp; currentDone = done; }
            mp.start();
            done.await(120, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        } finally {
            synchronized (lock) { current = null; currentDone = null; }
            mp.release();
            f.delete();
        }
    }

    private void playPhone(String text) {
        if (!ttsReady) return;
        CountDownLatch done = new CountDownLatch(1);
        synchronized (lock) { currentDone = done; }
        tts.speak(text, TextToSpeech.QUEUE_ADD, new Bundle(), "jarvis-" + System.nanoTime());
        try { done.await(60, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        synchronized (lock) { currentDone = null; }
    }

    private void releaseCurrent() {
        synchronized (lock) {
            if (currentDone != null) currentDone.countDown();
        }
    }
}
