package com.meteorpatch.modules;

import com.meteorpatch.utils.MathUtil;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public class ElytraAssist extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSafety = settings.createGroup("Safety");
    private final SettingGroup sgResource = settings.createGroup("Resource");

    // === Bounce 核心（Round 5 保留） ===
    private final Setting<Boolean> bounce = sgGeneral.add(new BoolSetting.Builder()
        .name("bounce").description("Bounce 模式：落地重发飞行包").defaultValue(true).build());

    private final Setting<Integer> recastInterval = sgGeneral.add(new IntSetting.Builder()
        .name("recast-interval").defaultValue(1).min(1).sliderRange(1, 5).build());

    private final Setting<Boolean> pitchDive = sgGeneral.add(new BoolSetting.Builder()
        .name("pitch-dive").description("俯冲增速").defaultValue(true).build());

    private final Setting<Double> divePitch = sgGeneral.add(new DoubleSetting.Builder()
        .name("dive-pitch").defaultValue(28.0).min(0).sliderRange(0, 45).build());

    // === 拉回检测 ===
    private final Setting<Integer> setbackCooldown = sgSafety.add(new IntSetting.Builder()
        .name("setback-cooldown").defaultValue(20).min(0).sliderRange(0, 60).build());

    // === Round 6 新增：高度保护 ===
    private final Setting<Boolean> heightProtection = sgSafety.add(new BoolSetting.Builder()
        .name("height-protection").description("低于 min-y 时自动抬头").defaultValue(true).build());

    private final Setting<Double> minY = sgSafety.add(new DoubleSetting.Builder()
        .name("min-y").defaultValue(64).min(-64).sliderRange(-64, 320).build());

    private final Setting<Double> climbRate = sgSafety.add(new DoubleSetting.Builder()
        .name("climb-rate").description("抬头时 pitch 每秒变化量").defaultValue(6.0).min(1).sliderRange(1, 20).build());

    // === Round 6 新增：烟花管理 ===
    private final Setting<Boolean> autoFirework = sgResource.add(new BoolSetting.Builder()
        .name("auto-firework").description("水平速度过低时自动放烟花").defaultValue(true).build());

    private final Setting<Double> fireworkSpeedThreshold = sgResource.add(new DoubleSetting.Builder()
        .name("firework-threshold").description("速度低于此值时放烟花").defaultValue(0.6).min(0).sliderRange(0, 2).build());

    private final Setting<Integer> fireworkCooldown = sgResource.add(new IntSetting.Builder()
        .name("firework-cooldown").defaultValue(15).min(5).sliderRange(5, 60).build());

    // === Round 6 新增：耐久切换 ===
    private final Setting<Boolean> durabilitySwap = sgResource.add(new BoolSetting.Builder()
        .name("durability-swap").description("鞘翅低耐久时自动换备用").defaultValue(true).build());

    private final Setting<Integer> duraThreshold = sgResource.add(new IntSetting.Builder()
        .name("dura-threshold").defaultValue(10).min(1).sliderRange(1, 100).build());

    // === Round 6 新增：末地安全 ===
    private final Setting<Boolean> endSafeMode = sgSafety.add(new BoolSetting.Builder()
        .name("end-safe-mode").description("末地自动降低俯冲 + 提高 min-y").defaultValue(true).build());

    private final Setting<Double> endExtraMinY = sgSafety.add(new DoubleSetting.Builder()
        .name("end-extra-min-y").description("末地额外抬高").defaultValue(20.0).min(0).sliderRange(0, 60).build());

    // === 运行时状态 ===
    private int tickCounter = 0;
    private int recastTimer = 0;
    private int setbackTimer = 0;
    private boolean setbackDetected = false;
    private int fireworkTimer = 0;
    private int swapCooldown = 0;

    public ElytraAssist() {
        super(Categories.Movement, "elytra-assist", "Bounce + 高度保护 + 烟花管理 + 耐久切换 + 末地安全");
    }

    @Override
    public void onActivate() {
        tickCounter = 0; recastTimer = 0; setbackTimer = 0;
        setbackDetected = false; fireworkTimer = 0; swapCooldown = 0;
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (mc.player == null) return;
        if (!(event.packet instanceof PlayerPositionLookS2CPacket)) return;
        if (!mc.player.isGliding()) return;
        setbackDetected = true;
        setbackTimer = setbackCooldown.get();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        tickCounter++;
        if (fireworkTimer > 0) fireworkTimer--;
        if (swapCooldown > 0) swapCooldown--;

        if (setbackTimer > 0) { setbackTimer--; return; }
        if (setbackDetected) { setbackDetected = false; recastTimer = 0; }

        var chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (!chest.isOf(Items.ELYTRA)) return;

        // === 耐久切换 ===
        if (durabilitySwap.get() && swapCooldown == 0) {
            int dura = chest.getMaxDamage() - chest.getDamage();
            if (dura <= duraThreshold.get()) {
                if (trySwapElytra()) { swapCooldown = 20; return; }
            }
        }

        // === Bounce 核心 ===
        if (bounce.get() && mc.player.isOnGround() && mc.player.networkHandler != null) {
            recastTimer++;
            if (recastTimer >= recastInterval.get()) {
                recastTimer = 0;
                mc.player.networkHandler.sendPacket(
                    new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }
        } else {
            recastTimer = 0;
        }

        if (!mc.player.isGliding()) return;

        // === 末地安全：先算有效参数 ===
        boolean inEnd = mc.world.getRegistryKey() == World.END;
        double effectiveMinY = minY.get() + (endSafeMode.get() && inEnd ? endExtraMinY.get() : 0);
        double effectiveDive = divePitch.get() * (endSafeMode.get() && inEnd ? 0.5 : 1.0);

        // === 高度保护：低于 minY 自动抬头 ===
        if (heightProtection.get() && mc.player.getY() < effectiveMinY) {
            float cur = mc.player.getPitch();
            float step = climbRate.get().floatValue();
            float newPitch = MathUtil.clamp(cur - step, -90f, 90f);
            mc.player.setPitch(newPitch);
        } else if (pitchDive.get()) {
            // 俯冲增速：速度低时压低 pitch
            double horizSpeed = mc.player.getVelocity().horizontalLength();
            if (horizSpeed < 0.5) {
                float cur = mc.player.getPitch();
                float newPitch = MathUtil.clamp(cur + 8f, -90f, (float) effectiveDive);
                mc.player.setPitch(newPitch);
            }
        }

        // === 烟花管理 ===
        if (autoFirework.get() && fireworkTimer == 0) {
            double speed = mc.player.getVelocity().horizontalLength();
            if (speed < fireworkSpeedThreshold.get()) {
                if (useFirework()) fireworkTimer = fireworkCooldown.get();
            }
        }

        // === 垂直速度抑制 ===
        double vy = mc.player.getVelocity().y;
        if (vy < -0.08) {
            mc.player.setVelocity(mc.player.getVelocity().x, vy * 0.5, mc.player.getVelocity().z);
        }
    }

    private boolean trySwapElytra() {
        if (mc.player == null) return false;
        for (int i = 9; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isOf(Items.ELYTRA)) {
                int dura = s.getMaxDamage() - s.getDamage();
                if (dura > duraThreshold.get()) {
                    mc.interactionManager.clickSlot(
                        mc.player.playerScreenHandler.syncId,
                        i, 6, SlotActionType.SWAP, mc.player);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean useFirework() {
        if (mc.player == null) return false;
        if (mc.player.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            return true;
        }
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) {
                int prev = mc.player.getInventory().selectedSlot;
                mc.player.getInventory().selectedSlot = i;
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.getInventory().selectedSlot = prev;
                return true;
            }
        }
        return false;
    }

    private int countItem(Item item) {
        if (mc.player == null) return 0;
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isOf(item)) n += s.getCount();
        }
        return n;
    }
}
