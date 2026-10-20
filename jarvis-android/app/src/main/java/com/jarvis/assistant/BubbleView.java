package com.jarvis.assistant;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/** The floating voice circle. Animates differently for each state. */
final class BubbleView extends View {

    enum State { IDLE, LISTENING, THINKING, SPEAKING }

    private static final int CYAN = Color.rgb(47, 210, 255);
    private static final int DEEP = Color.rgb(10, 126, 168);

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();

    private State state = State.IDLE;
    private float level;          // smoothed mic level 0..1
    private float targetLevel;
    private long flashUntil;

    BubbleView(Context c) {
        super(c);
        bg.setColor(Color.argb(215, 5, 8, 15));
        ring.setStyle(Paint.Style.STROKE);
        fill.setColor(CYAN);
    }

    void setState(State s) {
        state = s;
        if (s != State.LISTENING) targetLevel = 0;
        invalidate();
    }

    State getState() {
        return state;
    }

    /** Mic loudness from SpeechRecognizer.onRmsChanged (roughly -2..10 dB). */
    void setRms(float rmsDb) {
        targetLevel = Math.max(0f, Math.min(1f, (rmsDb + 2f) / 12f));
    }

    /** Quick bright flash to confirm a phone command. */
    void flash() {
        flashUntil = SystemClock.uptimeMillis() + 350;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f, r = Math.min(w, h) / 2f;
        long now = SystemClock.uptimeMillis();
        float t = now / 1000f;
        level += (targetLevel - level) * 0.35f;

        canvas.drawCircle(cx, cy, r * 0.98f, bg);
        ring.setStrokeWidth(r * 0.08f);
        boolean flashing = now < flashUntil;

        switch (state) {
            case IDLE: {
                float breathe = 0.5f + 0.5f * (float) Math.sin(t * 2.0);
                ring.setColor(flashing ? CYAN : DEEP);
                ring.setAlpha(flashing ? 255 : (int) (140 + 80 * breathe));
                canvas.drawCircle(cx, cy, r * 0.78f, ring);
                fill.setAlpha(flashing ? 255 : 170);
                canvas.drawCircle(cx, cy, r * (0.28f + 0.03f * breathe), fill);
                break;
            }
            case LISTENING: {
                // Ripples move outward; the core grows with your voice.
                for (int i = 0; i < 2; i++) {
                    float p = ((t * 0.9f) + i * 0.5f) % 1f;
                    ring.setColor(CYAN);
                    ring.setAlpha((int) (200 * (1 - p)));
                    canvas.drawCircle(cx, cy, r * (0.45f + 0.5f * p), ring);
                }
                fill.setAlpha(255);
                canvas.drawCircle(cx, cy, r * (0.32f + 0.3f * level), fill);
                break;
            }
            case THINKING: {
                ring.setColor(CYAN);
                ring.setAlpha(255);
                float pad = r * 0.22f;
                arc.set(pad, pad, w - pad, h - pad);
                float start = (t * 360f) % 360f;
                canvas.drawArc(arc, start, 100, false, ring);
                canvas.drawArc(arc, start + 180, 100, false, ring);
                fill.setAlpha(150);
                canvas.drawCircle(cx, cy, r * 0.22f, fill);
                break;
            }
            case SPEAKING: {
                // Five bars bouncing like a voice meter.
                fill.setAlpha(255);
                float barW = r * 0.16f, gap = r * 0.08f;
                float total = 5 * barW + 4 * gap;
                for (int i = 0; i < 5; i++) {
                    double wave = Math.abs(Math.sin(t * 7.0 + i * 1.3)) * 0.6 + Math.abs(Math.sin(t * 3.1 + i)) * 0.4;
                    float bh = r * (0.25f + 0.85f * (float) wave);
                    float x = cx - total / 2 + i * (barW + gap);
                    canvas.drawRoundRect(x, cy - bh / 2, x + barW, cy + bh / 2, barW / 2, barW / 2, fill);
                }
                ring.setColor(DEEP);
                ring.setAlpha(200);
                canvas.drawCircle(cx, cy, r * 0.86f, ring);
                break;
            }
        }

        // Smooth animation while active; a slow refresh when idle saves battery.
        if (state == State.IDLE && !flashing) postInvalidateDelayed(80);
        else postInvalidateOnAnimation();
    }
}
