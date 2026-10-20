package com.jarvis.assistant;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;

/**
 * Accessibility service that performs swipes, taps and back/home for voice
 * commands, and presses on-screen buttons (Comments, Share, Repost) by their
 * labels. Labels are only matched on the phone, never stored or sent anywhere.
 */
public class PhoneControl extends AccessibilityService {
    static volatile PhoneControl instance;

    private static final int MAX_NODES = 4000;

    @Override
    protected void onServiceConnected() {
        instance = this;
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    /** Swipe up = next video on TikTok/Reels/Shorts; swipe down = previous. */
    boolean swipe(boolean up) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float x = dm.widthPixels * 0.5f;
        float from = dm.heightPixels * (up ? 0.75f : 0.25f);
        float to = dm.heightPixels * (up ? 0.25f : 0.75f);
        Path path = new Path();
        path.moveTo(x, from);
        path.lineTo(x, to);
        return dispatch(new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 220))
                .build());
    }

    /** Taps the middle of the screen: once pauses/plays, twice likes on TikTok. */
    boolean tapCenter(int times) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        return tapAt(dm.widthPixels * 0.5f, dm.heightPixels * 0.5f, times);
    }

    boolean global(int action) {
        return performGlobalAction(action);
    }

    /**
     * Finds a visible button whose label contains one of the keywords and presses it.
     *
     * @param preferRight favour buttons on the right edge (TikTok's side bar)
     * @param mustStart   only accept labels that start with a keyword (avoids
     *                    matching captions such as "please repost this")
     */
    boolean clickLabel(String[] keywords, boolean preferRight, boolean mustStart) {
        int width = getResources().getDisplayMetrics().widthPixels;
        AccessibilityNodeInfo best = null;
        Rect bestBounds = null;
        int bestScore = Integer.MIN_VALUE;

        for (AccessibilityNodeInfo root : roots()) {
            ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
            queue.add(root);
            int seen = 0;
            while (!queue.isEmpty() && seen++ < MAX_NODES) {
                AccessibilityNodeInfo n = queue.poll();
                for (int i = 0; i < n.getChildCount(); i++) {
                    AccessibilityNodeInfo c = n.getChild(i);
                    if (c != null) queue.add(c);
                }
                if (!n.isVisibleToUser()) continue;
                String desc = lower(n.getContentDescription());
                String text = lower(n.getText());
                boolean inDesc = matches(desc, keywords, mustStart);
                if (!inDesc && !matches(text, keywords, mustStart)) continue;

                AccessibilityNodeInfo target = clickableSelfOrParent(n);
                Rect r = new Rect();
                (target != null ? target : n).getBoundsInScreen(r);
                if (r.isEmpty()) continue;

                int score = 0;
                if (target != null) score += 4;
                if (inDesc) score += 2;
                if (preferRight && r.centerX() > width * 0.7f) score += 3;
                score -= Math.min(4, (desc.length() + text.length()) / 40);  // short labels are buttons
                if (score > bestScore) {
                    bestScore = score;
                    best = target != null ? target : n;
                    bestBounds = r;
                }
            }
        }
        if (best == null) return false;
        if (best.isClickable() && best.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
        return tapAt(bestBounds.exactCenterX(), bestBounds.exactCenterY(), 1);
    }

    private List<AccessibilityNodeInfo> roots() {
        List<AccessibilityNodeInfo> out = new java.util.ArrayList<>();
        // Every app window on screen, so sheets and pop-ups (like TikTok's share sheet) are included.
        for (AccessibilityWindowInfo w : getWindows()) {
            if (w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
            AccessibilityNodeInfo r = w.getRoot();
            if (r != null && !getPackageName().contentEquals(r.getPackageName() == null ? "" : r.getPackageName())) out.add(r);
        }
        if (out.isEmpty()) {
            AccessibilityNodeInfo r = getRootInActiveWindow();
            if (r != null) out.add(r);
        }
        return out;
    }

    private static AccessibilityNodeInfo clickableSelfOrParent(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo cur = n;
        for (int i = 0; i < 5 && cur != null; i++) {
            if (cur.isClickable()) return cur;
            cur = cur.getParent();
        }
        return null;
    }

    private static boolean matches(String label, String[] keywords, boolean mustStart) {
        if (label.isEmpty()) return false;
        for (String k : keywords) {
            if (mustStart ? label.startsWith(k) : label.contains(k)) return true;
        }
        return false;
    }

    private static String lower(CharSequence s) {
        return s == null ? "" : s.toString().toLowerCase(Locale.ROOT).trim();
    }

    private boolean tapAt(float x, float y, int times) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.Builder b = new GestureDescription.Builder();
        for (int i = 0; i < times; i++) {
            b.addStroke(new GestureDescription.StrokeDescription(path, i * 120L, 40));
        }
        return dispatch(b.build());
    }

    private boolean dispatch(GestureDescription g) {
        return dispatchGesture(g, null, null);
    }
}
