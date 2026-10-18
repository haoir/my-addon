package com.meteorpatch.modules;

import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;

public class AntiExpose extends Module {
    public AntiExpose() { super(Categories.Misc, "anti-expose", "击退削减"); }
    
    @Override 
    public void onTick() {
        if (mc.player == null || mc.player.hurtTime <= 0) return;
        // 受伤时直接削减自身物理速度
        mc.player.setVelocity(mc.player.getVelocity().x * 0.55, mc.player.getVelocity().y * 0.65, mc.player.getVelocity().z * 0.55);
    }
}
