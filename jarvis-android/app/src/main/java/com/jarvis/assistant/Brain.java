package com.jarvis.assistant;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Talks to OpenRouter, streaming the reply so Jarvis can start speaking early. */
final class Brain {

    interface Listener {
        void onText(String delta);          // called on a background thread
        void onDone(double cost);
        void onError(String message);
    }

    // Only the last few messages are re-sent each turn: every request pays for
    // the whole history again, so a short window saves the most credits.
    private static final int HISTORY_WINDOW = 8;

    private static final String SYSTEM_PROMPT =
            "You are JARVIS, a witty, loyal personal AI assistant living on the user's Android phone. "
            + "Your replies are read aloud, so answer in 1-3 short sentences unless asked for more. "
            + "No markdown, bullet points, emojis, code blocks or URLs; spell things the way they should be spoken. "
            + "The phone itself handles commands like \"open TikTok\", \"scroll\", \"like the post\", \"comments\", "
            + "\"repost\", \"go back\", \"go home\", \"pause\" and \"auto scroll\"; if the user asks for something like that, tell them the exact phrase to say.";

    private final List<JSONObject> history = new ArrayList<>();
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private volatile HttpURLConnection conn;
    private volatile int turn;

    void ask(Context ctx, String text, Listener l) {
        final int myTurn = ++turn;
        final String key = Prefs.str(ctx, Prefs.OR_KEY, "");
        final String model = Prefs.str(ctx, Prefs.MODEL, Prefs.DEFAULT_MODEL);
        final int maxTokens = Integer.parseInt(Prefs.str(ctx, Prefs.LENGTH, "200"));
        exec.execute(() -> run(myTurn, key, model, maxTokens, text, l));
    }

    void cancel() {
        turn++;
        HttpURLConnection c = conn;
        if (c != null) new Thread(c::disconnect).start();
    }

    void clear() {
        synchronized (history) { history.clear(); }
    }

    private void run(int myTurn, String key, String model, int maxTokens, String text, Listener l) {
        if (key.isEmpty()) { l.onError("Add your OpenRouter key in the Jarvis app first."); return; }
        StringBuilder reply = new StringBuilder();
        double cost = 0;
        try {
            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("stream", true);
            body.put("max_tokens", maxTokens);
            JSONArray msgs = new JSONArray();
            msgs.put(msg("system", SYSTEM_PROMPT));
            synchronized (history) { for (JSONObject m : history) msgs.put(m); }
            msgs.put(msg("user", text));
            body.put("messages", msgs);
            // Keep "thinking" models brief and don't send their reasoning back: faster, cheaper.
            body.put("reasoning", new JSONObject().put("effort", "low").put("exclude", true));
            body.put("usage", new JSONObject().put("include", true));
            // Route to whichever provider answers fastest (not for OpenRouter's own routers).
            if (!model.startsWith("openrouter/")) body.put("provider", new JSONObject().put("sort", "latency"));

            HttpURLConnection c = (HttpURLConnection) new URL("https://openrouter.ai/api/v1/chat/completions").openConnection();
            conn = c;
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(15000);
            c.setReadTimeout(60000);
            c.setRequestProperty("Authorization", "Bearer " + key);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("X-Title", "Jarvis");
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = c.getResponseCode();
            if (code >= 400) {
                String msg = errorMessage(c.getErrorStream());
                if (myTurn == turn) l.onError(friendly(code, msg, model));
                return;
            }

            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null && myTurn == turn) {
                    line = line.trim();
                    if (!line.startsWith("data:")) continue;   // skips ": OPENROUTER PROCESSING" keep-alives
                    String data = line.substring(5).trim();
                    if (data.equals("[DONE]")) break;
                    JSONObject ev;
                    try { ev = new JSONObject(data); } catch (JSONException e) { continue; }
                    JSONObject err = ev.optJSONObject("error");
                    if (err != null) {
                        if (myTurn == turn) l.onError(friendly(err.optInt("code"), err.optString("message"), model));
                        return;
                    }
                    JSONArray choices = ev.optJSONArray("choices");
                    if (choices != null && choices.length() > 0) {
                        JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
                        Object content = delta != null ? delta.opt("content") : null;
                        if (content instanceof String && !((String) content).isEmpty()) {
                            reply.append((String) content);
                            l.onText((String) content);
                        }
                    }
                    JSONObject usage = ev.optJSONObject("usage");
                    if (usage != null) cost += usage.optDouble("cost", 0);
                }
            }
            if (myTurn != turn) return;
            synchronized (history) {
                history.add(msg("user", text));
                history.add(msg("assistant", reply.length() > 0 ? reply.toString() : "(no reply)"));
                while (history.size() > HISTORY_WINDOW) { history.remove(0); history.remove(0); }
            }
            l.onDone(cost);
        } catch (Exception e) {
            if (myTurn == turn) l.onError("I can't reach OpenRouter. Check your internet connection.");
        } finally {
            conn = null;
        }
    }

    private static JSONObject msg(String role, String content) {
        try {
            return new JSONObject().put("role", role).put("content", content);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String errorMessage(InputStream es) {
        if (es == null) return "";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(es, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            JSONObject err = new JSONObject(sb.toString()).optJSONObject("error");
            return err != null ? err.optString("message", "") : "";
        } catch (IOException | JSONException e) {
            return "";
        }
    }

    private static String friendly(int code, String msg, String model) {
        switch (code) {
            case 401: return "Your OpenRouter key was rejected. Check it in the Jarvis app.";
            case 402: return "Your OpenRouter account is out of credits.";
            case 429: return "I'm being rate limited. Free models have a daily limit, so try a cheap paid model.";
            case 404: return "The model " + model + " isn't available. Pick another one in the Jarvis app.";
            case 502:
            case 503: return "The AI service is busy. Try again in a moment.";
            default: return "Something went wrong" + (msg == null || msg.isEmpty() ? "." : ": " + msg);
        }
    }
}
