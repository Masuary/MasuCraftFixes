package com.masuary.masucraftfixes.mixin;

import com.masuary.masucraftfixes.MasuCraftFixes;
import com.masuary.masucraftfixes.quark.QuarkModuleOrder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import vazkii.quark.base.module.ModuleFinder;
import vazkii.quark.base.module.ModuleLoader;
import vazkii.quark.base.module.QuarkModule;

import java.util.Map;

@Mixin(value = ModuleLoader.class, remap = false)
public abstract class ModuleLoaderMixin {

    @Redirect(method = "findModules", at = @At(value = "INVOKE",
            target = "Lvazkii/quark/base/module/ModuleFinder;getFoundModules()Ljava/util/Map;"))
    private Map<Class<? extends QuarkModule>, QuarkModule> orderModulesByClassName(ModuleFinder finder) {
        Map<Class<? extends QuarkModule>, QuarkModule> sortedModules = QuarkModuleOrder.sortedByClassName(finder.getFoundModules());
        MasuCraftFixes.LOGGER.info("Quark modules ordered by class name ({} modules) so registry ids match on every backend",
                sortedModules.size());
        return sortedModules;
    }
}
