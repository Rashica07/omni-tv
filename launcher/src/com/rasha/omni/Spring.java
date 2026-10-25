package com.rasha.omni;

import android.view.Choreographer;

/**
 * Damped spring (unit mass) stepped on the display's vsync. Retargeting mid-flight keeps the
 * current velocity, so rapid D-pad input glides instead of restarting. Main thread only.
 */
final class Spring implements Choreographer.FrameCallback {
    interface Listener { void onValue(float v); }

    private final float stiffness;
    private final float damping;
    private final float eps;
    private final Listener listener;
    private float value;
    private float target;
    private float velocity;
    private boolean running;
    private long last;

    /** @param eps settle threshold in the animated unit (0.002 for scales, 0.5 for pixels) */
    Spring(float stiffness, float dampingRatio, float start, float eps, Listener l) {
        this.stiffness = stiffness;
        this.damping = SpringMath.damping(stiffness, dampingRatio);
        this.eps = eps;
        this.value = start;
        this.target = start;
        this.listener = l;
    }

    boolean isRunning() { return running; }

    float value() { return value; }

    void to(float t) {
        if (Theme.reduceMotion) {
            snap(t);
            return;
        }
        target = t;
        if (!running) {
            running = true;
            last = 0;
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    /** Jumps to v immediately and stops. */
    void snap(float v) {
        value = v;
        target = v;
        velocity = 0;
        listener.onValue(v);
    }

    @Override public void doFrame(long now) {
        if (last == 0) last = now;
        float dt = Math.min((now - last) / 1e9f, 0.032f);
        last = now;
        float[] xv = SpringMath.advance(value, velocity, target, stiffness, damping, dt);
        value = xv[0];
        velocity = xv[1];
        if (Math.abs(value - target) < eps && Math.abs(velocity) < eps * 10) {
            value = target;
            velocity = 0;
            running = false;
            listener.onValue(value);
            return;
        }
        listener.onValue(value);
        Choreographer.getInstance().postFrameCallback(this);
    }
}
