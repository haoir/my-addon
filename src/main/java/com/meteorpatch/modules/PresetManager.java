package com.meteorpatch.modules;

import java.util.HashMap;
import java.util.Map;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;

public class PresetManager extends Module {
    public enum Preset {
        NONE,
        SURVIVAL,
        PVP,
        SCAFFOLD,
        ELYTRA
    }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Preset> preset = sg.add(new EnumSetting.Builder<Preset>()
        .name("preset")
        .description("选择预设，切换时自动应用")
        .defaultValue(Preset.PVP)
        .build());

    private final Setting<Boolean> disableOthers = sg.add(new BoolSetting.Builder()
        .name("disable-others")
        .description("应用预设时关闭其他本插件模块")
        .defaultValue(true)
        .build());

    // 预设 → 要启用的模块名（Meteor 注册名）
    private static final Map<Preset, String[]> PRESETS = new HashMap<>();
    static {
        PRESETS.put(Preset.SURVIVAL, new String[]{"auto-totem-plus", "anti-expose"});
        PRESETS.put(Preset.PVP,      new String[]{"auto-totem-plus", "crystal-aura-plus", "anti-expose"});
        PRESETS.put(Preset.SCAFFOLD, new String[]{"auto-totem-plus", "scaffold-assist"});
        PRESETS.put(Preset.ELYTRA,   new String[]{"elytra-assist", "anti-expose"});
    }

    private static final String[] ALL_OWN_MODULES = {
        "crystal-aura-plus", "auto-totem-plus", "elytra-assist",
        "scaffold-assist", "anti-expose"
    };

    private Preset lastApplied = Preset.NONE;

    public PresetManager() {
        super(Categories.Misc, "preset-manager", "一键加载无政府生存/PvP/搭路/鞘翅预设");
    }

    @Override
    public void onActivate() {
        lastApplied = Preset.NONE;
        applyPreset(preset.get());
    }

    @Override
    public void onDeactivate() {
        lastApplied = Preset.NONE;
    }

    @EventHandler private void onTick(TickEvent.Post event) {
        if (preset.get() != lastApplied) {
            applyPreset(preset.get());
        }
    }

    private void applyPreset(Preset p) {
        lastApplied = p;
        if (p == Preset.NONE) return;

        Modules modules = Modules.get();

        // 先关闭其他本插件模块
        if (disableOthers.get()) {
            for (String name : ALL_OWN_MODULES) {
                Module m = modules.get(name);
                if (m != null && m != this) m.toggle();
            }
        }

        // 再启用目标预设
        String[] target = PRESETS.get(p);
        if (target == null) return;
        for (String name : target) {
            Module m = modules.get(name);
            if (m != null && !m.isActive()) m.toggle();
        }

        info("已应用预设：" + p.name());
    }
}
