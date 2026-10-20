package com.jarvis.assistant;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Setup screen: permissions, keys and settings. Built in code, no layouts. */
public class MainActivity extends Activity {

    private static final int BG = Color.rgb(5, 8, 15);
    private static final int PANEL = Color.rgb(13, 20, 34);
    private static final int TEXT = Color.rgb(220, 231, 247);
    private static final int MUTED = Color.rgb(125, 143, 171);
    private static final int CYAN = Color.rgb(47, 210, 255);

    private static final String[] LENGTH_VALUES = {"200", "500", "1200"};
    private static final String[] LENGTH_LABELS = {"Short (saves credits)", "Medium", "Long"};
    private static final String[] ENGINE_VALUES = {"fish", "phone", "off"};
    private static final String[] ENGINE_LABELS = {"Fish Audio (realistic)", "Phone voice (free)", "Silent (text pop-ups)"};
    private static final String[] FISH_MODELS = {"s2-pro", "s1"};

    private LinearLayout root;
    private float dp;
    private Button micBtn, overlayBtn, accessBtn, startBtn;
    private EditText orKey, model, fishKey, fishVoice;
    private Spinner length, engine, fishModel;
    private CheckBox handsFree, wakeWord;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        dp = getResources().getDisplayMetrics().density;
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFitsSystemWindows(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = px(18);
        root.setPadding(pad, pad, pad, px(40));
        scroll.addView(root);
        setContentView(scroll);

        TextView title = text("J A R V I S", 28, CYAN);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView sub = text("Your voice assistant. Just say \"Jarvis\" and what you want. The glowing circle in the corner shows when it's listening. Tap it to pause, drag it to move it, and hold it to open this screen.", 14, MUTED);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub);

        heading("1 · Permissions");
        micBtn = button("Allow microphone", v -> {
            List<String> perms = new ArrayList<>();
            perms.add(Manifest.permission.RECORD_AUDIO);
            if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS);
            requestPermissions(perms.toArray(new String[0]), 1);
        });
        overlayBtn = button("Allow showing over other apps", v ->
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))));
        accessBtn = button("Turn on phone control (Accessibility)", v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            Toast.makeText(this, "Tap \"Installed apps\" or \"Downloaded apps\", then Jarvis phone control, then turn it on.", Toast.LENGTH_LONG).show();
        });
        root.addView(text("Phone control lets Jarvis swipe on TikTok, go back and go home. "
                + "If Android says it's a \"restricted setting\", tap the button below, then ⋮ (top right), then \"Allow restricted settings\", and try again.", 13, MUTED));
        button("Open Jarvis app info", v ->
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));

        SharedPreferences p = Prefs.get(this);

        heading("2 · AI (OpenRouter)");
        orKey = field("OpenRouter API key (sk-or-…)", p.getString(Prefs.OR_KEY, ""), true);
        model = field("Model", p.getString(Prefs.MODEL, Prefs.DEFAULT_MODEL), false);
        root.addView(text("Free: openrouter/free (slower, 50 a day).  Fast and cheap: google/gemini-3.5-flash", 13, MUTED));
        label("Reply length");
        length = spinner(LENGTH_LABELS, indexOf(LENGTH_VALUES, p.getString(Prefs.LENGTH, "200")));

        heading("3 · Voice (Fish Audio)");
        label("Voice engine");
        engine = spinner(ENGINE_LABELS, indexOf(ENGINE_VALUES, p.getString(Prefs.ENGINE, "fish")));
        fishKey = field("Fish Audio API key", p.getString(Prefs.FISH_KEY, ""), true);
        fishVoice = field("Fish voice ID (optional)", p.getString(Prefs.FISH_VOICE, ""), false);
        root.addView(text("On fish.audio, open a voice and copy the long ID from its link.", 13, MUTED));
        label("Fish model");
        fishModel = spinner(FISH_MODELS, indexOf(FISH_MODELS, p.getString(Prefs.FISH_MODEL, "s2-pro")));

        heading("4 · Listening");
        handsFree = check("Always listening: just say \"Jarvis …\", no tapping", p.getBoolean(Prefs.HANDS_FREE, true));
        wakeWord = check("Require \"Jarvis\" before each request (recommended: stops it from answering TikTok sounds and wasting credits)", p.getBoolean(Prefs.WAKE_WORD, true));

        startBtn = button("Save & start Jarvis", v -> saveAndStart());
        startBtn.setBackground(rounded(CYAN));
        startBtn.setTextColor(BG);
        button("Stop Jarvis", v -> {
            stopService(new Intent(this, JarvisService.class));
            refresh();
        });

        heading("Things you can say");
        root.addView(text(
                "Start with \"Jarvis\", for example \"Jarvis, open TikTok\" or \"Jarvis, next\". "
                + "After Jarvis answers a question, you can reply once without saying Jarvis.\n\n"
                + "• \"Open TikTok\" / \"Open YouTube\" / \"Open Spotify\"\n"
                + "• \"Scroll\" or \"Next\": next video\n"
                + "• \"Swipe down\" or \"Previous\": last video\n"
                + "• \"Like the post\" · \"Comments\" · \"Repost\" · \"Share\"\n"
                + "• \"Pause\" / \"Play\" · \"Close\" (closes comments)\n"
                + "• \"Auto scroll\" or \"Auto scroll every 10 seconds\" · \"Stop scrolling\"\n"
                + "• \"Go back\" · \"Go home\" · \"Recent apps\" · \"Notifications\"\n"
                + "• \"Stop listening\"\n"
                + "• Anything else goes to the AI.\n\n"
                + "Phone commands run instantly and cost nothing. Only questions use AI and voice credits.", 14, TEXT));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        refresh();
    }

    private void refresh() {
        mark(micBtn, "Allow microphone", checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);
        mark(overlayBtn, "Allow showing over other apps", Settings.canDrawOverlays(this));
        mark(accessBtn, "Turn on phone control (Accessibility)", accessibilityOn());
        startBtn.setText(JarvisService.running ? "Save & restart Jarvis" : "Save & start Jarvis");
    }

    private void saveAndStart() {
        Prefs.get(this).edit()
                .putString(Prefs.OR_KEY, orKey.getText().toString().trim())
                .putString(Prefs.MODEL, model.getText().toString().trim())
                .putString(Prefs.LENGTH, LENGTH_VALUES[length.getSelectedItemPosition()])
                .putString(Prefs.ENGINE, ENGINE_VALUES[engine.getSelectedItemPosition()])
                .putString(Prefs.FISH_KEY, fishKey.getText().toString().trim())
                .putString(Prefs.FISH_VOICE, fishVoice.getText().toString().trim())
                .putString(Prefs.FISH_MODEL, FISH_MODELS[fishModel.getSelectedItemPosition()])
                .putBoolean(Prefs.HANDS_FREE, handsFree.isChecked())
                .putBoolean(Prefs.WAKE_WORD, wakeWord.isChecked())
                .apply();

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Allow the microphone first (step 1).", Toast.LENGTH_LONG).show();
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow \"show over other apps\" first (step 1).", Toast.LENGTH_LONG).show();
            return;
        }
        if (orKey.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Add your OpenRouter key so Jarvis can answer questions.", Toast.LENGTH_LONG).show();
        }
        Intent svc = new Intent(this, JarvisService.class);
        if (JarvisService.running) stopService(svc);
        startForegroundService(svc);
        Toast.makeText(this, "Jarvis is online. Look for the circle in the bottom left.", Toast.LENGTH_SHORT).show();
        moveTaskToBack(true);
    }

    private boolean accessibilityOn() {
        if (PhoneControl.instance != null) return true;
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String me = new ComponentName(this, PhoneControl.class).flattenToString();
        return enabled != null && enabled.contains(me);
    }

    // ---- Tiny UI helpers ---------------------------------------------------

    private int px(int v) {
        return (int) (v * dp);
    }

    private static int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(v)) return i;
        return 0;
    }

    private GradientDrawable rounded(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(px(12));
        return g;
    }

    private LinearLayout.LayoutParams spaced(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(top);
        return lp;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.15f);
        t.setLayoutParams(spaced(6));
        return t;
    }

    private void heading(String s) {
        TextView t = text(s, 17, CYAN);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLayoutParams(spaced(26));
        root.addView(t);
    }

    private void label(String s) {
        TextView t = text(s, 13, MUTED);
        t.setLayoutParams(spaced(12));
        root.addView(t);
    }

    private Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(TEXT);
        b.setTextSize(15);
        b.setBackground(rounded(PANEL));
        b.setLayoutParams(spaced(10));
        b.setOnClickListener(l);
        root.addView(b);
        return b;
    }

    private void mark(Button b, String s, boolean ok) {
        b.setText((ok ? "✅  " : "⬜  ") + s);
    }

    private EditText field(String hint, String value, boolean secret) {
        label(hint);
        EditText e = new EditText(this);
        e.setText(value);
        e.setSingleLine(true);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setBackground(rounded(PANEL));
        e.setPadding(px(12), px(10), px(12), px(10));
        e.setInputType(secret
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setLayoutParams(spaced(4));
        root.addView(e);
        return e;
    }

    private Spinner spinner(String[] labels, int selected) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a);
        s.setSelection(selected);
        s.setBackground(rounded(PANEL));
        s.setLayoutParams(spaced(4));
        root.addView(s);
        return s;
    }

    private CheckBox check(String s, boolean on) {
        CheckBox c = new CheckBox(this);
        c.setText(s);
        c.setTextColor(TEXT);
        c.setChecked(on);
        c.setLayoutParams(spaced(8));
        root.addView(c);
        return c;
    }
}
