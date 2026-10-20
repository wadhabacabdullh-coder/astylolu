package com.jarvis.assistant;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Runs Jarvis in the background with the floating circle over other apps. */
public class JarvisService extends Service {

    static final String ACTION_STOP = "com.jarvis.assistant.STOP";
    static volatile boolean running;

    private static final String CHANNEL = "jarvis";
    // After the first sentence (sent alone so Jarvis starts talking fast),
    // sentences are grouped so Fish Audio gets fewer, larger requests.
    private static final int VOICE_CHUNK_CHARS = 120;
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?…]+(?=\\s)|\\n");
    private static final Pattern WAKE = Pattern.compile("\\b(?:jarvis|jervis|jarvas)\\b[\\s,.!?]*(.*)$", Pattern.CASE_INSENSITIVE);

    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private BubbleView bubble;
    private WindowManager.LayoutParams lp;
    private SpeechRecognizer recognizer;
    private Brain brain;
    private Voice voice;

    private boolean listening;
    private boolean paused;      // user tapped the circle or said "stop listening"
    private boolean followUp;    // next request doesn't need the wake word
    private boolean netWarned;
    private boolean thinking;
    private final StringBuilder reply = new StringBuilder();
    private int spoken;
    private int chunks;
    private int autoScrollSeconds;

    private final Runnable autoScroll = new Runnable() {
        @Override public void run() {
            PhoneControl pc = PhoneControl.instance;
            if (autoScrollSeconds <= 0 || pc == null) return;
            pc.swipe(true);
            main.postDelayed(this, autoScrollSeconds * 1000L);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        startInForeground();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        brain = new Brain();
        voice = new Voice(this, new Voice.Listener() {
            @Override public void onAllDone() { onSpeechFinished(); }
            @Override public void onVoiceError(String message) { toast(message); }
        });
        addBubble();
        if (alwaysOn()) main.postDelayed(this::autoListen, 600);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        voice.resetWarning();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        main.removeCallbacksAndMessages(null);
        brain.cancel();
        voice.shutdown();
        if (recognizer != null) recognizer.destroy();
        if (bubble != null) {
            try { wm.removeView(bubble); } catch (IllegalArgumentException ignored) {}
        }
        super.onDestroy();
    }

    // ---- Foreground notification -------------------------------------------

    private void startInForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Jarvis", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, JarvisService.class).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Jarvis is online")
                .setContentText("Tap the circle to talk")
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, "Stop", stop).build())
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        else startForeground(1, n);
    }

    // ---- Floating circle ---------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    private void addBubble() {
        float d = getResources().getDisplayMetrics().density;
        int size = (int) (68 * d);
        SharedPreferences p = Prefs.get(this);
        lp = new WindowManager.LayoutParams(size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.START;
        lp.x = p.getInt(Prefs.BUBBLE_X, (int) (14 * d));
        lp.y = p.getInt(Prefs.BUBBLE_Y, (int) (130 * d));

        bubble = new BubbleView(this);
        int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        final long longPress = ViewConfiguration.getLongPressTimeout();
        bubble.setOnTouchListener(new android.view.View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            boolean dragged;
            final Runnable openApp = () -> {
                dragged = true;
                startActivity(new Intent(JarvisService.this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            };

            @Override public boolean onTouch(android.view.View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX(); downY = e.getRawY();
                        startX = lp.x; startY = lp.y;
                        dragged = false;
                        main.postDelayed(openApp, longPress * 2);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                        if (!dragged && Math.hypot(dx, dy) > slop) { dragged = true; main.removeCallbacks(openApp); }
                        if (dragged) {
                            lp.x = startX + (int) dx;
                            lp.y = startY - (int) dy;   // gravity is bottom, so y grows upward
                            wm.updateViewLayout(bubble, lp);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        main.removeCallbacks(openApp);
                        if (dragged) Prefs.get(JarvisService.this).edit().putInt(Prefs.BUBBLE_X, lp.x).putInt(Prefs.BUBBLE_Y, lp.y).apply();
                        else onBubbleTap();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        main.removeCallbacks(openApp);
                        return true;
                }
                return false;
            }
        });
        wm.addView(bubble, lp);
    }

    private boolean alwaysOn() {
        return Prefs.bool(this, Prefs.HANDS_FREE, true);
    }

    private boolean needWakeWord() {
        return Prefs.bool(this, Prefs.WAKE_WORD, true);
    }

    /** Tap: talk now (no wake word needed), interrupt Jarvis, or pause listening. */
    private void onBubbleTap() {
        if (thinking || voice.isSpeaking()) {
            brain.cancel();
            voice.stop();
            thinking = false;
        } else if (listening && (!alwaysOn() || followUp)) {
            stopListening();
            paused = true;
            bubble.setState(BubbleView.State.IDLE);
            if (alwaysOn()) toast("Jarvis paused. Tap the circle to wake it.");
            return;
        }
        paused = false;
        followUp = true;
        if (listening) stopListening();
        startListening();
    }

    // ---- Listening ---------------------------------------------------------

    private void startListening() {
        if (listening || thinking) return;
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("Speech recognition isn't available. Install or update the Google app.");
            return;
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new Listener());
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        listening = true;
        bubble.setState(BubbleView.State.LISTENING);
        recognizer.startListening(i);
    }

    private void stopListening() {
        listening = false;
        if (recognizer != null) recognizer.cancel();
    }

    private void listenAgainLater(long delayMs) {
        main.removeCallbacks(autoListenTask);
        if (alwaysOn() && !paused) {
            if (!listening) bubble.setState(BubbleView.State.IDLE);
            main.postDelayed(autoListenTask, delayMs);
        } else {
            bubble.setState(BubbleView.State.IDLE);
        }
    }

    private final Runnable autoListenTask = this::autoListen;

    private void autoListen() {
        if (!paused && !thinking && !voice.isSpeaking()) startListening();
    }

    private final class Listener implements RecognitionListener {
        @Override public void onReadyForSpeech(Bundle params) {}
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) { bubble.setRms(rmsdB); }
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() {}
        @Override public void onPartialResults(Bundle partialResults) {}
        @Override public void onEvent(int eventType, Bundle params) {}

        @Override public void onError(int error) {
            listening = false;
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                bubble.setState(BubbleView.State.IDLE);
                toast("Jarvis needs microphone permission. Open the Jarvis app to allow it.");
            } else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                recognizer.destroy();
                recognizer = null;
                listenAgainLater(500);
            } else if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
                    || error == SpeechRecognizer.ERROR_SERVER) {
                if (!netWarned) toast("No internet for speech recognition. Retrying…");
                netWarned = true;
                listenAgainLater(5000);
            } else {
                followUp = false;        // nothing said: the follow-up window ends
                listenAgainLater(300);
            }
        }

        @Override public void onResults(Bundle results) {
            listening = false;
            netWarned = false;
            ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            String said = list != null && !list.isEmpty() ? list.get(0).trim() : "";
            handleUtterance(said);
        }
    }

    // ---- Understanding -----------------------------------------------------

    private void handleUtterance(String said) {
        if (said.isEmpty()) { listenAgainLater(300); return; }
        boolean free = followUp;
        followUp = false;
        if (needWakeWord() && alwaysOn() && !free) {
            Matcher m = WAKE.matcher(said);
            if (!m.find()) { listenAgainLater(200); return; }
            said = m.group(1).trim();
            if (said.isEmpty()) { followUp = true; voice.speakLocal("Yes?"); return; }
        }

        Commands.Action action = Commands.parse(said);
        if (action != null) {
            runCommand(action);
            return;
        }
        askBrain(said);
    }

    private void runCommand(Commands.Action a) {
        PhoneControl pc = PhoneControl.instance;
        boolean ok = true;
        switch (a.type) {
            case OPEN_APP: {
                Map<String, String> apps = AppFinder.installedApps(this);
                String label = AppFinder.bestMatch(a.arg, apps);
                if (label == null || !AppFinder.open(this, apps.get(label))) {
                    voice.speakLocal("I couldn't find an app called " + a.arg + ".");
                    return;
                }
                break;
            }
            case STOP_LISTENING:
                stopListening();
                paused = true;
                toast("Jarvis paused. Tap the circle to wake it.");
                bubble.setState(BubbleView.State.IDLE);
                bubble.flash();
                return;
            case STOP_SCROLL:
                autoScrollSeconds = 0;
                main.removeCallbacks(autoScroll);
                break;
            default:
                if (pc == null) {
                    voice.speakLocal("Turn on Jarvis phone control in Accessibility settings first.");
                    return;
                }
                switch (a.type) {
                    case SWIPE_UP: ok = pc.swipe(true); break;
                    case SWIPE_DOWN: ok = pc.swipe(false); break;
                    case BACK: ok = pc.global(AccessibilityService.GLOBAL_ACTION_BACK); break;
                    case HOME: ok = pc.global(AccessibilityService.GLOBAL_ACTION_HOME); break;
                    case RECENTS: ok = pc.global(AccessibilityService.GLOBAL_ACTION_RECENTS); break;
                    case NOTIFICATIONS: ok = pc.global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS); break;
                    case PLAY_PAUSE: ok = pc.tapCenter(1); break;
                    case LIKE: ok = pc.tapCenter(2); break;
                    case AUTO_SCROLL:
                        autoScrollSeconds = a.seconds;
                        main.removeCallbacks(autoScroll);
                        main.postDelayed(autoScroll, a.seconds * 1000L);
                        toast("Auto scrolling every " + a.seconds + " seconds. Say \"stop scrolling\" to stop.");
                        break;
                    default: break;
                }
        }
        if (!ok) toast("That didn't work. Check Jarvis phone control in Accessibility settings.");
        bubble.flash();
        // Give the swipe or app launch a moment before listening again.
        listenAgainLater(900);
    }

    // ---- Talking to the AI -------------------------------------------------

    private void askBrain(String text) {
        thinking = true;
        reply.setLength(0);
        spoken = 0;
        chunks = 0;
        voice.stop();
        bubble.setState(BubbleView.State.THINKING);
        brain.ask(this, text, new Brain.Listener() {
            @Override public void onText(String delta) {
                main.post(() -> {
                    if (!thinking) return;
                    reply.append(delta);
                    flushSpeech(false);
                });
            }

            @Override public void onDone(double cost) {
                main.post(() -> {
                    if (!thinking) return;
                    thinking = false;
                    followUp = true;
                    flushSpeech(true);
                    if (Prefs.str(JarvisService.this, Prefs.ENGINE, "fish").equals("off") && reply.length() > 0) {
                        toast(reply.toString());
                    }
                    if (!voice.isSpeaking()) onSpeechFinished();
                });
            }

            @Override public void onError(String message) {
                main.post(() -> {
                    thinking = false;
                    toast(message);
                    voice.speakLocal(message);
                });
            }
        });
    }

    /** Sends finished sentences to the voice while the reply is still streaming. */
    private void flushSpeech(boolean last) {
        String rest = reply.substring(spoken);
        if (last) {
            if (!rest.trim().isEmpty()) speakChunk(rest);
            spoken = reply.length();
            return;
        }
        int cut = -1;
        Matcher m = SENTENCE_END.matcher(rest);
        while (m.find()) {
            if (chunks == 0) { cut = m.end(); break; }
            if (m.end() >= VOICE_CHUNK_CHARS) cut = m.end();
        }
        if (cut > 0) {
            speakChunk(rest.substring(0, cut));
            spoken += cut;
            chunks++;
        }
    }

    private void speakChunk(String text) {
        bubble.setState(BubbleView.State.SPEAKING);
        voice.speak(text);
    }

    private void onSpeechFinished() {
        if (thinking || listening) return;
        bubble.setState(BubbleView.State.IDLE);
        listenAgainLater(250);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
