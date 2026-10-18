package com.meteorpatch.utils;

import net.minecraft.util.math.Vec3d;

public final class MathUtil {
    private MathUtil() {}

    public static double horizSq(Vec3d a, Vec3d b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    public static float wrapDeg(float deg) {
        deg = deg % 360.0f;
        if (deg >= 180.0f) deg -= 360.0f;
        if (deg < -180.0f) deg += 360.0f;
        return deg;
    }

    public static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }

    public static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static boolean isClose(double a, double b, double eps) {
        return Math.abs(a - b) <= eps;
    }

    public static float gcdAlign(float deg, float gcd) {
        if (gcd <= 0.001f) return deg;
        return Math.round(deg / gcd) * gcd;
    }
}
