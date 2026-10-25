package com.rasha.omni;

/** Pure spring integration (semi-implicit Euler, unit mass), shared by Spring and its tests. */
final class SpringMath {
    private SpringMath() { }

    static float damping(float stiffness, float dampingRatio) { return 2f * dampingRatio * (float) Math.sqrt(stiffness); }

    /** Advances (x, v) toward target by dt seconds using sub-steps of at most 4 ms; returns {x, v}. */
    static float[] advance(float x, float v, float target, float stiffness, float damping, float dt) {
        int steps = Math.max(1, (int) Math.ceil(dt / 0.004f));
        float h = dt / steps;
        for (int i = 0; i < steps; i++) {
            float a = -stiffness * (x - target) - damping * v;
            v += a * h;
            x += v * h;
        }
        return new float[]{x, v};
    }
}
