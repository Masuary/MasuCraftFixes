package com.masuary.masucraftfixes;

import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class MasuCraftFixesMixinPlugin implements IMixinConfigPlugin {

    private static final String ORECHID_IGNEM_MIXIN =
            "com.masuary.masucraftfixes.mixin.OrechidIgnemMixin";
    private static final Set<String> VAULT_V166_COMPATIBILITY_MIXINS = Set.of(
            "com.masuary.masucraftfixes.mixin.VaultSnapshotV166CompatibilityMixin"
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
        if (ORECHID_IGNEM_MIXIN.equals(mixinClassName)) {
            return isModLoaded("botania");
        }

        if (!VAULT_V166_COMPATIBILITY_MIXINS.contains(mixinClassName)) {
            return true;
        }

        LoadingModList loadingModList = LoadingModList.get();
        if (loadingModList == null) {
            return false;
        }

        ModFileInfo vaultModFile = loadingModList.getModFileById("the_vault");
        if (vaultModFile == null) {
            return false;
        }

        return vaultModFile.getMods().stream()
                .anyMatch(modInfo -> VaultV166Compatibility.TARGET_VAULT_VERSION.equals(modInfo.getVersion().toString()));
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
