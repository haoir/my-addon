package com.meteorpatch.modules;

import com.meteorpatch.utils.MathUtil;
import com.meteorpatch.utils.RotationUtil;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class ScaffoldAssist extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTower = settings.createGroup("Tower");
    private final SettingGroup sgSafety = settings.createGroup("Safety");

    // === 通用设置（Round 7 保留） ===
    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-switch").defaultValue(true).build());

    private final Setting<Integer> minDelay = sgGeneral.add(new IntSetting.Builder()
        .name("min-delay").defaultValue(2).min(1).sliderRange(1, 6).build());

    private final Setting<Integer> maxDelay = sgGeneral.add(new IntSetting.Builder()
        .name("max-delay").defaultValue(4).min(2).sliderRange(2, 8).build());

    private final Setting<Double> aimSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("aim-speed").defaultValue(12.0).min(2).sliderRange(2, 30).build());

    private final Setting<Boolean> smoothRotation = sgGeneral.add(new BoolSetting.Builder()
        .name("smooth-rotation").defaultValue(true).build());

    private final Setting<Integer> rotationDelay = sgGeneral.add(new IntSetting.Builder()
        .name("rotation-delay").defaultValue(1).min(0).sliderRange(0, 4).build());

    // === Round 8 新增：Tower 模式 ===
    private final Setting<Boolean> towerMode = sgTower.add(new BoolSetting.Builder()
        .name("tower-mode").description("垂直向上搭路（参考 FDPClient TowerMode: Jump）").defaultValue(false).build());

    private final Setting<Double> towerJumpMotion = sgTower.add(new DoubleSetting.Builder()
        .name("tower-jump-motion").description("Tower 跳跃的 Y 速度").defaultValue(0.42).min(0.3).sliderRange(0.3, 0.6).build());

    private final Setting<Integer> towerJumpDelay = sgTower.add(new IntSetting.Builder()
        .name("tower-jump-delay").description("Tower 模式跳跃后等待多少 tick 再放置").defaultValue(0).min(0).sliderRange(0, 4).build());

    private final Setting<Boolean> towerStopWhenBlockAbove = sgTower.add(new BoolSetting.Builder()
        .name("stop-when-block-above").description("头顶有方块时停止 Tower").defaultValue(true).build());

    private final Setting<Boolean> towerOnlyWhenStill = sgTower.add(new BoolSetting.Builder()
        .name("only-when-still").description("只有不移动时才触发 Tower（NoMove）").defaultValue(true).build());

    // === Round 8 新增：危险方块强化检测 ===
    private final Setting<Boolean> safeBlockFilter = sgSafety.add(new BoolSetting.Builder()
        .name("safe-block-filter").description("过滤下落方块和无碰撞方块").defaultValue(true).build());

    private final Setting<Boolean> extraBlacklist = sgSafety.add(new BoolSetting.Builder()
        .name("extra-blacklist").description("额外过滤脚手架/雪/草等").defaultValue(true).build());

    // === 运行时状态 ===
    private int placeCooldown = 0;
    private int pendingPlaceTicks = 0;
    private BlockPos pendingPos = null;
    private Direction pendingFace = null;
    private int towerJumpCooldown = 0;
    private int towerPlaceDelay = 0;

    public ScaffoldAssist() {
        super(Categories.Movement, "scaffold-assist", "平滑旋转 + Tower 模式 + 危险方块过滤");
    }

    @Override
    public void onActivate() {
        placeCooldown = 0;
        pendingPlaceTicks = 0;
        pendingPos = null;
        pendingFace = null;
        towerJumpCooldown = 0;
        towerPlaceDelay = 0;
    }

    @EventHandler private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        if (placeCooldown > 0) placeCooldown--;
        if (towerJumpCooldown > 0) towerJumpCooldown--;
        if (towerPlaceDelay > 0) towerPlaceDelay--;

        // 1. 处理延迟放置队列
        if (pendingPos != null) {
            pendingPlaceTicks--;
            if (pendingPlaceTicks <= 0) executePendingPlace();
            return;
        }
        if (placeCooldown > 0) return;

        // 2. Tower 模式优先
        if (towerMode.get() && tryTower()) return;

        // 3. 普通模式：检查脚下
        BlockPos below = mc.player.getBlockPos().down();
        if (!mc.world.getBlockState(below).isAir()) return;

        int slot = findBlockSlot();
        if (slot == -1) return;

        Direction face = Direction.UP;
        Vec3d hitVec = new Vec3d(below.getX() + 0.5, below.getY() + 1.0, below.getZ() + 0.5);
        rotateTo(hitVec);

        pendingPos = below;
        pendingFace = face;
        pendingPlaceTicks = rotationDelay.get();
        if (pendingPlaceTicks <= 0) executePendingPlace();

        if (autoSwitch.get()) mc.player.getInventory().setSelectedSlot(slot);
    }

    // === Round 8：Tower 模式核心 ===
    private boolean tryTower() {
        // 必须在地面，且按住跳跃键
        if (!mc.player.isOnGround() || !mc.options.jumpKey.isPressed()) return false;

        // 只有不移动时才触发（NoMove）
        if (towerOnlyWhenStill.get()) {
            double horizSpeed = mc.player.getVelocity().horizontalLength();
            if (horizSpeed > 0.02) return false;
        }

        // 头顶有方块则停止
        if (towerStopWhenBlockAbove.get()) {
            BlockPos above = mc.player.getBlockPos().up(2);
            if (!mc.world.getBlockState(above).isAir()) return false;
        }

        // 跳跃冷却（防止连跳过快）
        if (towerJumpCooldown > 0) return false;

        BlockPos below = mc.player.getBlockPos().down();
        if (!mc.world.getBlockState(below).isAir()) return false;

        int slot = findBlockSlot();
        if (slot == -1) return false;

        // 在跳跃的同时放置方块
        // 参考 FDPClient: TowerMode = Jump, TowerJumpMotion = 0.42
        mc.player.setVelocity(mc.player.getVelocity().x, towerJumpMotion.get(), mc.player.getVelocity().z);
        towerJumpCooldown = 4;

        // 旋转看向脚下
        Vec3d hitVec = new Vec3d(below.getX() + 0.5, below.getY() + 1.0, below.getZ() + 0.5);
        if (smoothRotation.get()) {
            RotationUtil.smoothLookAt(hitVec, aimSpeed.get().floatValue());
        } else {
            RotationUtil.instantLookAt(hitVec);
        }

        if (autoSwitch.get()) mc.player.getInventory().setSelectedSlot(slot);

        // 延迟放置
        pendingPos = below;
        pendingFace = Direction.UP;
        pendingPlaceTicks = towerJumpDelay.get();
        if (pendingPlaceTicks <= 0) executePendingPlace();
        return true;
    }

    private void rotateTo(Vec3d hitVec) {
        if (smoothRotation.get()) {
            RotationUtil.smoothLookAt(hitVec, aimSpeed.get().floatValue());
        } else {
            RotationUtil.instantLookAt(hitVec);
        }
    }

    private void executePendingPlace() {
        if (mc.player == null || pendingPos == null || pendingFace == null) {
            pendingPos = null; pendingFace = null; return;
        }

        Vec3d hitVec = new Vec3d(
            pendingPos.getX() + 0.5,
            pendingPos.getY() + 1.0,
            pendingPos.getZ() + 0.5
        );
        BlockHitResult hit = new BlockHitResult(hitVec, pendingFace, pendingPos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);

        int minD = minDelay.get();
        int maxD = maxDelay.get();
        if (maxD <= minD) maxD = minD + 1;
        placeCooldown = ThreadLocalRandom.current().nextInt(minD, maxD + 1);

        pendingPos = null;
        pendingFace = null;
        pendingPlaceTicks = 0;
    }

    // === Round 8：危险方块强化过滤 ===
    private boolean isDangerousBlock(Block block) {
        if (!safeBlockFilter.get()) return false;
        // 下落方块：沙砾、沙、混凝土粉末等，搭路途中会掉落
        if (block instanceof FallingBlock) return true;
        if (extraBlacklist.get()) {
            // 脚手架、雪层、草等：可能触发 Grim 的 ScaffoldAngle 或没有碰撞
            if (block == Blocks.SCAFFOLDING) return true;
            if (block == Blocks.SNOW) return true;
            if (block == Blocks.GRASS_BLOCK) return true;
            if (block == Blocks.TALL_GRASS) return true;
        }
        return false;
    }

    private int findBlockSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty() || !(s.getItem() instanceof BlockItem bi)) continue;
            if (isDangerousBlock(bi.getBlock())) continue;
            return i;
        }
        return -1;
    }
}
