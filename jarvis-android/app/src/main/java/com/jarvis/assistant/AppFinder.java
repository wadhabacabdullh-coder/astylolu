package com.jarvis.assistant;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Finds an installed app from a spoken name ("tick tock" → TikTok) and opens it. */
final class AppFinder {

    // Common ways speech recognition hears app names.
    private static final String[][] ALIASES = {
            {"tick tock", "tiktok"}, {"tic toc", "tiktok"}, {"tik tok", "tiktok"}, {"tic tac", "tiktok"},
            {"insta", "instagram"}, {"ig", "instagram"}, {"yt", "youtube"}, {"you tube", "youtube"},
            {"snap", "snapchat"}, {"whats app", "whatsapp"}, {"what's app", "whatsapp"},
            {"face book", "facebook"}, {"fb", "facebook"}, {"messenger", "messenger"},
            {"twitter", "x"}, {"google maps", "maps"}, {"play store", "play store"},
            {"camera app", "camera"}, {"settings app", "settings"}, {"spotify music", "spotify"},
    };

    // Package names of popular apps, used when the spoken name doesn't match the icon label.
    private static final String[][] KNOWN = {
            {"tiktok", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill"},
            {"youtube", "com.google.android.youtube"},
            {"youtube music", "com.google.android.apps.youtube.music"},
            {"instagram", "com.instagram.android"},
            {"snapchat", "com.snapchat.android"},
            {"whatsapp", "com.whatsapp"},
            {"spotify", "com.spotify.music"},
            {"netflix", "com.netflix.mediaclient"},
            {"facebook", "com.facebook.katana"},
            {"messenger", "com.facebook.orca"},
            {"x", "com.twitter.android"},
            {"discord", "com.discord"},
            {"telegram", "org.telegram.messenger"},
            {"roblox", "com.roblox.client"},
            {"chrome", "com.android.chrome"},
            {"gmail", "com.google.android.gm"},
            {"maps", "com.google.android.apps.maps"},
            {"play store", "com.android.vending"},
            {"camera", "com.sec.android.app.camera", "com.google.android.GoogleCamera"},
            {"gallery", "com.sec.android.gallery3d", "com.google.android.apps.photos"},
            {"settings", "com.android.settings"},
            {"pinterest", "com.pinterest"},
            {"twitch", "tv.twitch.android.app"},
            {"capcut", "com.lemon.lvoverseas"},
    };

    private AppFinder() {}

    /** Label (lowercase) → package name for every app with a launcher icon. */
    static Map<String, String> installedApps(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(i, 0);
        Map<String, String> apps = new LinkedHashMap<>();
        for (ResolveInfo ri : list) {
            String label = String.valueOf(ri.loadLabel(pm)).toLowerCase(Locale.ROOT).trim();
            if (!label.isEmpty() && !apps.containsKey(label)) apps.put(label, ri.activityInfo.packageName);
        }
        return apps;
    }

    /** Returns the label of the best match, or null. */
    static String bestMatch(String spoken, Map<String, String> apps) {
        String q = spoken.toLowerCase(Locale.ROOT).trim();
        for (String[] a : ALIASES) {
            if (q.equals(a[0])) { q = a[1]; break; }
        }
        String qc = compact(q);
        if (qc.isEmpty()) return null;

        String best = null;
        int bestScore = Integer.MAX_VALUE;
        for (String label : apps.keySet()) {
            String lc = compact(label);
            if (lc.isEmpty()) continue;
            int score;
            boolean partialOk = Math.min(lc.length(), qc.length()) >= 3;
            if (lc.equals(qc)) score = 0;
            else if (partialOk && (lc.startsWith(qc) || qc.startsWith(lc))) score = 10 + Math.abs(lc.length() - qc.length());
            else if (partialOk && lc.contains(qc)) score = 30 + lc.length() - qc.length();
            else {
                int d = distance(lc, qc);
                // Allow small mis-hearings: about one wrong letter in four.
                if (d > Math.max(1, Math.min(lc.length(), qc.length()) / 4)) continue;
                score = 60 + d * 10;
            }
            if (score < bestScore) { bestScore = score; best = label; }
        }
        return best;
    }

    /** Finds the package for a spoken app name: icon labels first, then well-known apps. */
    static String findPackage(Context c, String spoken) {
        Map<String, String> apps = installedApps(c);
        String label = bestMatch(spoken, apps);
        if (label != null) return apps.get(label);

        Map<String, String> known = new LinkedHashMap<>();
        for (String[] k : KNOWN) {
            for (int i = 1; i < k.length; i++) {
                if (c.getPackageManager().getLaunchIntentForPackage(k[i]) != null) { known.put(k[0], k[i]); break; }
            }
        }
        label = bestMatch(spoken, known);
        return label != null ? known.get(label) : null;
    }

    static boolean open(Context c, String pkg) {
        Intent launch = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) return false;
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        // Android blocks most background apps from opening other apps. Starting it from
        // the accessibility service is allowed, so use that when phone control is on.
        PhoneControl pc = PhoneControl.instance;
        try {
            if (pc != null) {
                pc.startActivity(launch);
                return true;
            }
            if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions opts = ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                PendingIntent.getActivity(c, pkg.hashCode(), launch,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)
                        .send(c, 0, null, null, null, null, opts.toBundle());
            } else {
                c.startActivity(launch);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String compact(String s) {
        return s.replaceAll("[^a-z0-9]", "");
    }

    private static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }
}
