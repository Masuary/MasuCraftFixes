package com.masuary.masucraftfixes;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class MasuCraftFixesMixinPlugin implements IMixinConfigPlugin {
    private static final Set<String> CASINOCRAFT_MIXINS = Set.of(
            "com.masuary.masucraftfixes.mixin.CasinoCraftBlockEntityMachineMixin",
            "com.masuary.masucraftfixes.mixin.CasinoCraftMenuProviderMixin",
            "com.masuary.masucraftfixes.mixin.CasinoCraftMessageInventoryServerHandlerMixin",
            "com.masuary.masucraftfixes.mixin.CasinoCraftMessagePlayerServerHandlerMixin",
            "com.masuary.masucraftfixes.mixin.CasinoCraftMessageScoreServerHandlerMixin",
            "com.masuary.masucraftfixes.mixin.CasinoCraftMessageSettingServerHandlerMixin",
            "com.masuary.masucraftfixes.mixin.CasinoCraftMessageStartServerHandlerMixin",
            "com.masuary.masucraftfixes.mixin.CasinoCraftMessageStateServerHandlerMixin"
    );
    private static final String WOLDS_PLAYER_EVENTS_TELEMETRY_MIXIN = "com.masuary.masucraftfixes.mixin.WoldsPlayerEventsTelemetryMixin";
    private static final String WOLDS_FILTER_NECKLACE_ITEM_TELEMETRY_MIXIN = "com.masuary.masucraftfixes.mixin.WoldsFilterNecklaceItemTelemetryMixin";
    private static final Set<String> WOLDS_STAGE0_MIXINS = Set.of(
            WOLDS_PLAYER_EVENTS_TELEMETRY_MIXIN,
            WOLDS_FILTER_NECKLACE_ITEM_TELEMETRY_MIXIN
    );

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (CASINOCRAFT_MIXINS.contains(mixinClassName)) {
            return isModLoaded("casinocraft");
        }

        if (WOLDS_STAGE0_MIXINS.contains(mixinClassName)) {
            return isModLoaded("woldsvaults") && isWoldsStage0MixinEnabled(mixinClassName);
        }

        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private static boolean isModLoaded(String modId) {
        try {
            Class<?> loadingModListClass = Class.forName("net.minecraftforge.fml.loading.LoadingModList");
            Object loadingModList = loadingModListClass.getMethod("get").invoke(null);
            Object modFile = loadingModListClass.getMethod("getModFileById", String.class).invoke(loadingModList, modId);
            return modFile != null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean isWoldsStage0MixinEnabled(String mixinClassName) {
        if (!boolProperty("masucraftfixes.stage0.woldsMixins", true)) {
            return false;
        }

        if (WOLDS_PLAYER_EVENTS_TELEMETRY_MIXIN.equals(mixinClassName)) {
            return boolProperty("masucraftfixes.stage0.woldsPlayerEventsMixin", true);
        }
        if (WOLDS_FILTER_NECKLACE_ITEM_TELEMETRY_MIXIN.equals(mixinClassName)) {
            return boolProperty("masucraftfixes.stage0.woldsFilterNecklaceItemMixin", true);
        }
        return true;
    }

    private static boolean boolProperty(String key, boolean fallback) {
        String value = System.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }
}
