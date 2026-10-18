package com.meteorpatch;

import com.meteorpatch.modules.*;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MeteorPatchAddon extends MeteorAddon {
    public static final Logger LOG = LoggerFactory.getLogger("MeteorPatch");

    @Override
    public void onInitialize() {
        LOG.info("[MeteorPatch] 初始化中...");
        Modules.get().add(new CrystalAura());
        Modules.get().add(new AutoTotemPlus());
        Modules.get().add(new ElytraAssist());
        Modules.get().add(new ScaffoldAssist());
        Modules.get().add(new AntiExpose());
        Modules.get().add(new PresetManager());
        LOG.info("[MeteorPatch] 全部 6 个模块注册完毕");
    }

    @Override public void onRegisterCategories() {}
    @Override public String getPackage() { return "com.meteorpatch"; }
}
