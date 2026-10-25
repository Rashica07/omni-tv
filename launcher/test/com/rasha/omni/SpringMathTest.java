package com.rasha.omni;

final class SpringMathTest {
    static void run() {
        float k = 240f, c = SpringMath.damping(k, 0.82f);
        float x = 0, v = 0, maxX = 0;
        int frames = 0;
        while (frames < 120 && !(Math.abs(x - 1f) < 0.002f && Math.abs(v) < 0.02f)) {
            float[] r = SpringMath.advance(x, v, 1f, k, c, 1f / 60f);
            x = r[0]; v = r[1]; maxX = Math.max(maxX, x); frames++;
        }
        T.ok(frames <= 45, "focus spring settles within 0.75 s (took " + frames + " frames)");
        T.ok(maxX < 1.1f, "overshoot under 10% (" + maxX + ")");

        // retargeting mid-flight keeps velocity (no restart): velocity must change smoothly, not reset to zero
        x = 0; v = 0;
        for (int i = 0; i < 8; i++) { float[] r = SpringMath.advance(x, v, 1f, k, c, 1f / 60f); x = r[0]; v = r[1]; }
        float vBefore = v;
        float[] r = SpringMath.advance(x, v, 0f, k, c, 1f / 240f);
        T.ok(Math.abs(r[1] - vBefore) < Math.abs(vBefore) * 0.5f + 0.5f, "velocity is carried across a retarget");
        T.ok(vBefore > 0.5f, "the spring was actually moving");

        // critically damped never overshoots
        float cc = SpringMath.damping(k, 1f);
        x = 0; v = 0; float mx = 0;
        for (int i = 0; i < 120; i++) { float[] q = SpringMath.advance(x, v, 1f, k, cc, 1f / 60f); x = q[0]; v = q[1]; mx = Math.max(mx, x); }
        T.ok(mx <= 1.001f, "critical damping does not overshoot (" + mx + ")");

        // a long frame (dropped frames) must stay stable: dt is capped by callers at 32 ms, but 100 ms must not blow up
        float[] big = SpringMath.advance(0f, 0f, 1f, k, c, 0.1f);
        T.ok(!Float.isNaN(big[0]) && Math.abs(big[0]) < 2f, "stable under a long frame");
    }
}
