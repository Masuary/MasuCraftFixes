package com.masuary.masucraftfixes;

import com.masuary.masucraftfixes.refinedstorage.VaultIntegrationsChunkFixCheck;
import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Applies the mixins for an optional mod only when that mod is installed, and stops the boot when Vault Integrations'
 * own Refined Storage chunk fix would collide with ours.
 */
public class MasuCraftFixesMixinPlugin implements IMixinConfigPlugin {

    private static final String ORECHID_IGNEM_MIXIN = "com.masuary.masucraftfixes.mixin.OrechidIgnemMixin";
    private static final String REFINED_STORAGE_MIXIN_PACKAGE = "com.masuary.masucraftfixes.mixin.refinedstorage.";
    private static final String NETWORK_LISTENER_MIXIN = REFINED_STORAGE_MIXIN_PACKAGE + "NetworkListenerMixin";

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
            if (!isModLoaded("refinedstorage")) {
                return false;
            }
            if (NETWORK_LISTENER_MIXIN.equals(mixinClassName)) {
                VaultIntegrationsChunkFixCheck.requireConflictingFixInactive(MasuCraftFixesMixinPlugin.class.getClassLoader());
            }
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
