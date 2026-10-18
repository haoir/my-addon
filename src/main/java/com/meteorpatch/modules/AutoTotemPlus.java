import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
package com.meteorpatch.modules;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

public class AutoTotemPlus extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Boolean> offhandTotem = sg.add(new BoolSetting.Builder()
        .name("offhand-totem").description("副手自动补图腾").defaultValue(true).build());

    private final Setting<Boolean> mainhandTotem = sg.add(new BoolSetting.Builder()
        .name("mainhand-totem").description("低血量主手也切图腾").defaultValue(true).build());

    private final Setting<Double> mainhandHealthThreshold = sg.add(new DoubleSetting.Builder()
        .name("mainhand-health").description("主手切图腾的血量阈值").defaultValue(8.0).min(0).sliderRange(0, 20).build());

    private final Setting<Boolean> hotbarReplenish = sg.add(new BoolSetting.Builder()
        .name("hotbar-replenish").description("快捷栏图腾/水晶/黑曜石/金苹果不足时补货").defaultValue(true).build());

    private final Setting<Integer> replenishThreshold = sg.add(new IntSetting.Builder()
        .name("replenish-threshold").description("数量低于此值时补货").defaultValue(16).min(1).sliderRange(1, 64).build());

    private final Setting<Integer> actionDelay = sg.add(new IntSetting.Builder()
        .name("action-delay").description("两次操作的间隔 tick").defaultValue(3).min(1).sliderRange(1, 10).build());

    private int delay = 0;

    public AutoTotemPlus() {
        super(Categories.Combat, "auto-totem-plus", "副手补图腾 + 主手备胎 + 快捷栏补货");
    }

    @Override
    public void onActivate() { delay = 0; }

    @EventHandler private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        if (delay > 0) { delay--; return; }

        // 1. 副手补图腾
        if (offhandTotem.get() && !mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            int slot = findItemInInventory(Items.TOTEM_OF_UNDYING, -1);
            if (slot != -1) {
                doSwap(slot, 40); // 40 = PlayerInventory offhand slot
                delay = actionDelay.get();
                return;
            }
        }

        // 2. 低血量主手切图腾
        if (mainhandTotem.get() && mc.player.getHealth() < mainhandHealthThreshold.get()) {
            if (!mc.player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                for (int i = 0; i < 9; i++) {
                    if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                        mc.player.getInventory().setSelectedSlot(i);
                        delay = actionDelay.get();
                        return;
                    }
                }
            }
        }

        // 3. 快捷栏补货
        if (hotbarReplenish.get()) {
            if (replenishHotbar(Items.TOTEM_OF_UNDYING)) { delay = actionDelay.get(); return; }
            if (replenishHotbar(Items.END_CRYSTAL))      { delay = actionDelay.get(); return; }
            if (replenishHotbar(Items.OBSIDIAN))         { delay = actionDelay.get(); return; }
            if (replenishHotbar(Items.ENCHANTED_GOLDEN_APPLE)) { delay = actionDelay.get(); return; }
            if (replenishHotbar(Items.GOLDEN_APPLE))     { delay = actionDelay.get(); return; }
        }
    }

    /**
     * 在快捷栏找该物品，若数量低于阈值则从主背包补货。
     * @return 是否执行了补货
     */
    private boolean replenishHotbar(Item item) {
        int hotbarSlot = -1;
        int hotbarCount = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isOf(item)) {
                hotbarSlot = i;
                hotbarCount = s.getCount();
                break;
            }
        }
        // 快捷栏已经有该物品且数量够 → 不用补
        if (hotbarSlot != -1 && hotbarCount >= replenishThreshold.get()) return false;

        // 从主背包 9-35 找来源
        int srcSlot = -1;
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isOf(item)) {
                srcSlot = i;
                break;
            }
        }
        if (srcSlot == -1) return false;

        // 如果快捷栏没这个物品，找一个空槽或找图腾槽之外的槽位
        if (hotbarSlot == -1) {
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getStack(i).isEmpty()) {
                    hotbarSlot = i;
                    break;
                }
            }
            if (hotbarSlot == -1) return false;
        }

        doSwap(srcSlot, hotbarSlot);
        return true;
    }

    private int findItemInInventory(Item item, int excludeSlot) {
        for (int i = 0; i < 36; i++) {
            if (i == excludeSlot) continue;
            if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        }
        return -1;
    }

    /**
     * 用 SWAP 动作在背包槽位之间交换物品。
     * @param fromSlot  源槽位（0-35 = 主背包+快捷栏）
     * @param targetSlot 目标槽位（0-8=快捷栏，40=副手）
     */
    private void doSwap(int fromSlot, int targetSlot) {
        if (mc.interactionManager == null || mc.player == null) return;
        mc.interactionManager.clickSlot(
            mc.player.playerScreenHandler.syncId,
            fromSlot,
            targetSlot,
            SlotActionType.SWAP,
            mc.player
        );
    }
}
