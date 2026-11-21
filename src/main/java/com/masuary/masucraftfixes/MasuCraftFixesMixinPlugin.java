package com.masuary.masucraftfixes;

import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Applies the mixins for an optional mod only when that mod is installed. */
public class MasuCraftFixesMixinPlugin implements IMixinConfigPlugin {

    private static final String ORECHID_IGNEM_MIXIN = "com.masuary.masucraftfixes.mixin.OrechidIgnemMixin";
    private static final String REFINED_STORAGE_MIXIN_PACKAGE = "com.masuary.masucraftfixes.mixin.refinedstorage.";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (ORECHID_IGNEM_MIXIN.equals(mixinClassName)) {
            return isModLoaded("botania");
        }
        if (mixinClassName.startsWith(REFINED_STORAGE_MIXIN_PACKAGE)) {
            return isModLoaded("refinedstorage");
        }
        return true;
    }

    private static boolean isModLoaded(String modId) {
        LoadingModList loadingModList = LoadingModList.get();
        return loadingModList != null && loadingModList.getModFileById(modId) != null;
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
}
