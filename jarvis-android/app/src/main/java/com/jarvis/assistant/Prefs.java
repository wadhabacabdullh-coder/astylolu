package com.jarvis.assistant;

import android.content.Context;
import android.content.SharedPreferences;

/** Settings, stored privately on the phone. */
final class Prefs {
    static final String OR_KEY = "or_key";
    static final String MODEL = "model";
    static final String LENGTH = "length";
    static final String ENGINE = "engine";          // fish | phone | off
    static final String FISH_KEY = "fish_key";
    static final String FISH_VOICE = "fish_voice";
    static final String FISH_MODEL = "fish_model";
    static final String HANDS_FREE = "hands_free";
    static final String WAKE_WORD = "wake_word";
    static final String BUBBLE_X = "bubble_x";
    static final String BUBBLE_Y = "bubble_y";

    static final String DEFAULT_MODEL = "openrouter/free";

    private Prefs() {}

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences("jarvis", Context.MODE_PRIVATE);
    }

    static String str(Context c, String key, String def) {
        String v = get(c).getString(key, def);
        return v == null || v.trim().isEmpty() ? def : v.trim();
    }

    static boolean bool(Context c, String key) {
        return get(c).getBoolean(key, false);
    }
}
