package com.meteorpatch.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class RotationUtil {
    private RotationUtil() {}
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static float dynamicGcd() {
        try {
            Double sens = mc.options.getMouseSensitivity().getValue();
            return (float) (sens * 0.6 + 0.2);
        } catch (Exception e) {
            return 0.15f;
        }
    }

    public static float yawTo(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    public static float pitchTo(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        return (float) -Math.toDegrees(Math.atan2(dy, horiz));
    }

    public static void smoothLookAt(Vec3d target, float maxStep) {
        if (mc.player == null) return;
        Vec3d eyes = mc.player.getEyePos();
        apply(yawTo(eyes, target), pitchTo(eyes, target), maxStep);
    }

    public static void lookAtEntity(Entity e, double predictTicks, float maxStep) {
        if (mc.player == null || e == null) return;
        Vec3d pos = e.getPos().add(e.getVelocity().multiply(predictTicks));
        smoothLookAt(pos.add(0, e.getHeight() * 0.85, 0), maxStep);
    }

    public static void apply(float targetYaw, float targetPitch, float maxStep) {
        if (mc.player == null) return;
        float curYaw = mc.player.getYaw();
        float curPitch = mc.player.getPitch();
        float dYaw = MathHelper.wrapDegrees(targetYaw - curYaw);
        float dPitch = MathHelper.wrapDegrees(targetPitch - curPitch);
        dYaw = MathUtil.clamp(dYaw, -maxStep, maxStep);
        dPitch = MathUtil.clamp(dPitch, -maxStep, maxStep);
        float gcd = dynamicGcd();
        float newYaw = MathUtil.gcdAlign(curYaw + dYaw, gcd);
        float newPitch = MathUtil.gcdAlign(MathUtil.clamp(curPitch + dPitch, -90f, 90f), gcd);
        mc.player.setYaw(newYaw);
        mc.player.setPitch(newPitch);
    }

    public static void instantLookAt(Vec3d target) {
        if (mc.player == null) return;
        Vec3d eyes = mc.player.getEyePos();
        mc.player.setYaw(yawTo(eyes, target));
        mc.player.setPitch(pitchTo(eyes, target));
    }
}
