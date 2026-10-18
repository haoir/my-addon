import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
package com.meteorpatch.modules;

import com.meteorpatch.utils.DamageCalc;
import com.meteorpatch.utils.RotationUtil;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

public class CrystalAura extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> placeRange = sg.add(new DoubleSetting.Builder()
        .name("place-range").description("水晶放置距离").defaultValue(4.5).min(1).sliderRange(1, 6).build());

    private final Setting<Double> breakRange = sg.add(new DoubleSetting.Builder()
        .name("break-range").description("引爆距离").defaultValue(4.5).min(1).sliderRange(1, 6).build());

    private final Setting<Double> targetRange = sg.add(new DoubleSetting.Builder()
        .name("target-range").description("目标搜索距离").defaultValue(8.0).min(1).sliderRange(1, 12).build());

    private final Setting<Double> minTargetDamage = sg.add(new DoubleSetting.Builder()
        .name("min-target-damage").description("目标伤害低于此值不操作").defaultValue(4.0).min(0).sliderRange(0, 20).build());

    private final Setting<Double> maxSelfDamage = sg.add(new DoubleSetting.Builder()
        .name("max-self-damage").description("自伤超过此值跳过").defaultValue(6.0).min(0).sliderRange(0, 20).build());

    private final Setting<Double> minHealth = sg.add(new DoubleSetting.Builder()
        .name("min-health").description("自身血量低于此值不操作").defaultValue(6.0).min(0).sliderRange(0, 20).build());

    private final Setting<Double> predictTicks = sg.add(new DoubleSetting.Builder()
        .name("predict-ticks").description("目标位置预测 tick 数").defaultValue(2.0).min(0).sliderRange(0, 4).build());

    private final Setting<Integer> placeCooldownTicks = sg.add(new IntSetting.Builder()
        .name("place-cooldown").description("两次放置最小间隔 tick").defaultValue(2).min(1).sliderRange(1, 10).build());

    private final Setting<Integer> breakDelayTicks = sg.add(new IntSetting.Builder()
        .name("break-delay").description("水晶生成后到引爆的最短 tick").defaultValue(1).min(0).sliderRange(0, 5).build());

    private final Setting<Boolean> autoSwitch = sg.add(new BoolSetting.Builder()
        .name("auto-switch").description("自动切到水晶").defaultValue(true).build());

    private final Map<EndCrystalEntity, Integer> crystalSpawnTicks = new HashMap<>();
    private int placeCooldown = 0;
    private int tickCounter = 0;

    public CrystalAura() {
        super(Categories.Combat, "crystal-aura-plus", "伤害枚举自动放水晶 + 延迟引爆 + 防自杀");
    }

    @Override
    public void onActivate() {
        crystalSpawnTicks.clear();
        placeCooldown = 0;
        tickCounter = 0;
    }

    @Override
    public void onDeactivate() {
        crystalSpawnTicks.clear();
    }

    @EventHandler private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        tickCounter++;
        if (placeCooldown > 0) placeCooldown--;

        // 定期清理失效的水晶记录
        if (tickCounter % 40 == 0) {
            crystalSpawnTicks.entrySet().removeIf(en ->
                !en.getKey().isAlive() || mc.world.getEntityById(en.getKey().getId()) == null);
        }

        LivingEntity target = findBestTarget();
        if (target == null) return;

        // 血量保护：自己血量太低直接不动
        if (mc.player.getHealth() < minHealth.get()) return;

        // 1. 先尝试引爆已存在的水晶
        if (tryExplode(target)) return;

        // 2. 再尝试放置新水晶
        if (placeCooldown > 0) return;
        placeCrystal(target);
    }

    private LivingEntity findBestTarget() {
        LivingEntity best = null;
        double bestDist = targetRange.get();
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof PlayerEntity p) || p == mc.player || !p.isAlive()) continue;
            if (p.isCreative() || p.isSpectator()) continue;
            double d = mc.player.distanceTo(p);
            if (d < bestDist) { bestDist = d; best = p; }
        }
        return best;
    }

    private Vec3d predictTargetPos(LivingEntity target) {
        double t = predictTicks.get();
        if (t <= 0.01) return target.getPos();
        return target.getPos().add(target.getVelocity().multiply(t));
    }

    private float estimateAtDistance(double distance, float armor) {
        if (distance > 12.0) return 0f;
        double exposure = 1.0 - (distance / 12.0);
        float baseDamage = (float) (exposure * exposure * 12.0 + exposure * 12.0);
        float reduction = Math.min(20.0f, Math.max(armor / 5.0f, armor - baseDamage / 2.0f));
        return Math.max(0f, baseDamage * (1.0f - reduction / 25.0f));
    }

    private boolean tryExplode(LivingEntity target) {
        double breakR = breakRange.get();
        double minDmg = minTargetDamage.get();
        double maxSelf = maxSelfDamage.get();
        int delay = breakDelayTicks.get();
        Vec3d predicted = predictTargetPos(target);

        EndCrystalEntity bestCrystal = null;
        float bestDamage = 0f;

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof EndCrystalEntity crystal) || !crystal.isAlive()) continue;
            Vec3d cPos = crystal.getPos();
            if (mc.player.getEyePos().distanceTo(cPos) > breakR) continue;

            float tgtDmg = estimateAtDistance(predicted.distanceTo(cPos), target.getArmor());
            if (tgtDmg < minDmg) continue;

            float selfDmg = estimateAtDistance(mc.player.getPos().distanceTo(cPos), mc.player.getArmor());
            // 防自杀四重检查
            if (selfDmg >= maxSelf) continue;
            if (selfDmg >= mc.player.getHealth() - 2.0f) continue;
            if (selfDmg >= tgtDmg) continue;

            // 延迟引爆
            Integer spawnTick = crystalSpawnTicks.get(crystal);
            if (spawnTick == null) {
                crystalSpawnTicks.put(crystal, tickCounter);
                continue;
            }
            if (tickCounter - spawnTick < delay) continue;

            if (tgtDmg > bestDamage) {
                bestDamage = tgtDmg;
                bestCrystal = crystal;
            }
        }

        if (bestCrystal == null) return false;

        RotationUtil.instantLookAt(bestCrystal.getPos().add(0, 0.5, 0));
        mc.interactionManager.attackEntity(mc.player, bestCrystal);
        mc.player.swingHand(Hand.MAIN_HAND);
        crystalSpawnTicks.remove(bestCrystal);
        return true;
    }

    private void placeCrystal(LivingEntity target) {
        Vec3d predicted = predictTargetPos(target);
        BlockPos base = BlockPos.ofFloored(predicted);
        int r = 5;
        double bestDamage = 0f;
        BlockPos best = null;
        Vec3d eye = mc.player.getEyePos();
        double maxRange = placeRange.get();
        double minDmg = minTargetDamage.get();
        double maxSelf = maxSelfDamage.get();

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = base.add(dx, dy, dz);
                    if (!canPlaceCrystalAt(pos)) continue;
                    Vec3d cPos = Vec3d.ofCenter(pos.up());
                    if (eye.distanceTo(cPos) > maxRange) continue;

                    float tgtDmg = estimateAtDistance(predicted.distanceTo(cPos), target.getArmor());
                    if (tgtDmg < minDmg) continue;

                    float selfDmg = estimateAtDistance(mc.player.getPos().distanceTo(cPos), mc.player.getArmor());
                    if (selfDmg >= maxSelf) continue;
                    if (selfDmg >= mc.player.getHealth() - 2.0f) continue;
                    if (selfDmg >= tgtDmg) continue;

                    if (tgtDmg > bestDamage) {
                        bestDamage = tgtDmg;
                        best = pos;
                    }
                }
            }
        }

        if (best == null) return;
        if (!ensureCrystalInHand()) return;

        Vec3d hitVec = new Vec3d(best.getX() + 0.5, best.getY() + 1.0, best.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, best, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
        placeCooldown = placeCooldownTicks.get();
    }

    private boolean canPlaceCrystalAt(BlockPos pos) {
        var state = mc.world.getBlockState(pos);
        var block = state.getBlock();
        if (block != Blocks.OBSIDIAN && block != Blocks.BEDROCK) return false;
        if (!mc.world.getBlockState(pos.up()).isAir()) return false;
        if (!mc.world.getBlockState(pos.up(2)).isAir()) return false;
        return true;
    }

    private boolean ensureCrystalInHand() {
        if (mc.player.getMainHandStack().isOf(Items.END_CRYSTAL)) return true;
        if (mc.player.getOffHandStack().isOf(Items.END_CRYSTAL)) return true;
        if (!autoSwitch.get()) return false;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.END_CRYSTAL)) {
                mc.player.getInventory().setSelectedSlot(i);
                return true;
            }
        }
        return false;
    }
}
