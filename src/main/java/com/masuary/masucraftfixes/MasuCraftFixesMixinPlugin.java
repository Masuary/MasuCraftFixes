package com.masuary.masucraftfixes;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class MasuCraftFixesMixinPlugin implements IMixinConfigPlugin {
    private static final Set<String> FTB_ESSENTIALS_MIXINS = Set.of(
            "com.masuary.masucraftfixes.mixin.AngelExpertiseMixin",
            "com.masuary.masucraftfixes.mixin.FTBCheatCommandsMixin",
            "com.masuary.masucraftfixes.mixin.ServerPlayerMixin"
    );
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
    private static final String VAULT_CHALLENGE_TICK_GUARD_MIXIN =
            "com.masuary.masucraftfixes.mixin.VaultChallengeTickGuardMixin";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (FTB_ESSENTIALS_MIXINS.contains(mixinClassName)) {
            return isModLoaded("ftbessentials");
        }

        if (CASINOCRAFT_MIXINS.contains(mixinClassName)) {
            return isModLoaded("casinocraft");
        }

        if (VAULT_CHALLENGE_TICK_GUARD_MIXIN.equals(mixinClassName)) {
            return isModVersion("the_vault", VaultChallengeTickGuard.TARGET_VAULT_VERSION);
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

    private static boolean isModVersion(String modId, String expectedVersion) {
        try {
            Class<?> loadingModListClass = Class.forName("net.minecraftforge.fml.loading.LoadingModList");
            Object loadingModList = loadingModListClass.getMethod("get").invoke(null);
            Object modFile = loadingModListClass
                    .getMethod("getModFileById", String.class)
                    .invoke(loadingModList, modId);
            if (modFile == null) {
                return false;
            }
            Object mods = modFile.getClass().getMethod("getMods").invoke(modFile);
            if (!(mods instanceof Iterable<?> modInfos)) {
                return false;
            }
            for (Object modInfo : modInfos) {
                Object foundModId = modInfo.getClass().getMethod("getModId").invoke(modInfo);
                if (!modId.equals(foundModId)) {
                    continue;
                }
                Object version = modInfo.getClass().getMethod("getVersion").invoke(modInfo);
                return expectedVersion.equals(String.valueOf(version));
            }
            return false;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }
}
