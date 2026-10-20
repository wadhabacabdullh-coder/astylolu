package com.jarvis.assistant;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;

/**
 * Accessibility service that performs swipes, taps and back/home for voice
 * commands. It never reads screen content.
 */
public class PhoneControl extends AccessibilityService {
    static volatile PhoneControl instance;

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
        Path path = new Path();
        path.moveTo(dm.widthPixels * 0.5f, dm.heightPixels * 0.5f);
        GestureDescription.Builder b = new GestureDescription.Builder();
        for (int i = 0; i < times; i++) {
            b.addStroke(new GestureDescription.StrokeDescription(path, i * 120L, 40));
        }
        return dispatch(b.build());
    }

    boolean global(int action) {
        return performGlobalAction(action);
    }

    private boolean dispatch(GestureDescription g) {
        return dispatchGesture(g, null, null);
    }
}
