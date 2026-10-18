package com.meteorpatch.modules;

import com.meteorpatch.utils.MathUtil;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;

import java.util.concurrent.ThreadLocalRandom;

public class AntiExpose extends Module {
    private final SettingGroup sgVelocity = settings.getDefaultGroup();
    private final SettingGroup sgRotation = settings.createGroup("Rotation");
    private final SettingGroup sgTiming = settings.createGroup("Timing");

    // === 击退修改：收到包时直接改客户端 velocity（Meteor Velocity 官方思路） ===
    private final Setting<Boolean> velocityModify = sgVelocity.add(new BoolSetting.Builder()
        .name("velocity-modify").defaultValue(true).build());

    private final Setting<Double> knockbackHorizontal = sgVelocity.add(new DoubleSetting.Builder()
        .name("knockback-horizontal").defaultValue(0.55).min(0).sliderRange(0, 1)
        .visible(velocityModify::get).build());

    private final Setting<Double> knockbackVertical = sgVelocity.add(new DoubleSetting.Builder()
        .name("knockback-vertical").defaultValue(0.65).min(0).sliderRange(0, 1)
        .visible(velocityModify::get).build());

    private final Setting<Double> velocityJitter = sgVelocity.add(new DoubleSetting.Builder()
        .name("velocity-jitter").defaultValue(0.08).min(0).sliderRange(0, 0.3)
        .visible(velocityModify::get).build());

    // === 旋转锯齿抖动 ===
    private final Setting<Boolean> rotationJitter = sgRotation.add(new BoolSetting.Builder()
        .name("rotation-jitter").defaultValue(true).build());

    private final Setting<Double> jitterAmplitude = sgRotation.add(new DoubleSetting.Builder()
        .name("jitter-amplitude").defaultValue(0.08).min(0.01).sliderRange(0.01, 0.2)
        .visible(rotationJitter::get).build());

    private final Setting<Integer> jitterHoldTicks = sgRotation.add(new IntSetting.Builder()
        .name("jitter-hold").defaultValue(6).min(1).sliderRange(1, 20)
        .visible(rotationJitter::get).build());

    // === 包间隔随机化 ===
    private final Setting<Boolean> packetJitter = sgTiming.add(new BoolSetting.Builder()
        .name("packet-jitter").defaultValue(true).build());

    private final Setting<Double> skipProbability = sgTiming.add(new DoubleSetting.Builder()
        .name("skip-probability").defaultValue(0.15).min(0).sliderRange(0, 0.5)
        .visible(packetJitter::get).build());

    private int hurtTicksLeft = 0;
    private boolean jitterUp = true;

    public AntiExpose() {
        super(Categories.Misc, "anti-expose", "击退平滑化 + 旋转锯齿抖动 + 包间隔随机化");
    }

    @Override public void onActivate() { hurtTicksLeft = 0; jitterUp = true; }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (mc.player == null) return;
        if (!velocityModify.get()) return;
        if (!(event.packet instanceof EntityVelocityUpdateS2CPacket packet)) return;
        if (packet.getId() != mc.player.getId()) return;

        double velX = packet.getVelocityX() / 8000.0;
        double velY = packet.getVelocityY() / 8000.0;
        double velZ = packet.getVelocityZ() / 8000.0;

        double jitter = velocityJitter.get() > 0
                ? ThreadLocalRandom.current().nextDouble(-velocityJitter.get(), velocityJitter.get())
                : 0;

        double hFactor = MathUtil.clamp(knockbackHorizontal.get() + jitter, 0, 1.5);
        double vFactor = MathUtil.clamp(knockbackVertical.get() + jitter, 0, 1.5);

        // 直接改客户端玩家速度，影响当前 tick 的物理预测
        mc.player.setVelocity(velX * hFactor, velY * vFactor, velZ * hFactor);

        if (rotationJitter.get()) hurtTicksLeft = jitterHoldTicks.get();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;
        if (!rotationJitter.get() || hurtTicksLeft <= 0) return;

        hurtTicksLeft--;
        float amp = jitterAmplitude.get().floatValue();
        float gcd = com.meteorpatch.utils.RotationUtil.dynamicGcd();

        float swing = jitterUp ? amp : -amp;
        jitterUp = !jitterUp;
        swing *= (float) ThreadLocalRandom.current().nextDouble(0.6, 1.0);

        float curYaw = mc.player.getYaw();
        float curPitch = mc.player.getPitch();
        float alignedYaw = MathUtil.gcdAlign(curYaw, gcd);
        float alignedPitch = MathUtil.gcdAlign(curPitch, gcd);
        mc.player.setYaw(alignedYaw + swing);
        mc.player.setPitch(MathUtil.clamp(alignedPitch - swing * 0.5f, -90f, 90f));
    }

    public boolean allowPacketThisTick() {
        if (!packetJitter.get()) return true;
        return ThreadLocalRandom.current().nextDouble() >= skipProbability.get();
    }
}
