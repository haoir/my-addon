package com.meteorpatch.utils;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

public final class DamageCalc {
    private DamageCalc() {}

    public static float crystalDamage(LivingEntity target, Vec3d crystalPos) {
        double distance = target.getPos().distanceTo(crystalPos);
        if (distance > 12.0) return 0f;
        double exposure = 1.0 - (distance / 12.0);
        float baseDamage = (float) (exposure * exposure * 12.0 + exposure * 12.0);
        float armor = target.getArmor();
        float reduction = Math.min(20.0f, Math.max(armor / 5.0f, armor - baseDamage / 2.0f));
        return Math.max(0f, baseDamage * (1.0f - reduction / 25.0f));
    }

    public static float crystalSelfDamage(LivingEntity self, Vec3d crystalPos) {
        return crystalDamage(self, crystalPos);
    }

    public static boolean willKill(LivingEntity target, Vec3d crystalPos) {
        return crystalDamage(target, crystalPos) >= target.getHealth();
    }
}
