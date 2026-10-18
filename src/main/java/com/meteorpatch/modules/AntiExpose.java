package com.meteorpatch.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class AntiExpose extends Module {
    public AntiExpose() { super(Categories.Misc, "anti-expose", "击退削减"); }
    
    @EventHandler private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.player.hurtTime <= 0) return;
        // 受伤时直接削减自身物理速度
        mc.player.setVelocity(mc.player.getVelocity().x * 0.55, mc.player.getVelocity().y * 0.65, mc.player.getVelocity().z * 0.55);
    }
}
