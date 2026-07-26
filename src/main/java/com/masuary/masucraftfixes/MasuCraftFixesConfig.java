package com.masuary.masucraftfixes;

import net.minecraftforge.common.ForgeConfigSpec;

public final class MasuCraftFixesConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLE_LEGACY_V166_VAULT_DATA_COMPATIBILITY;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("vaultDataCompatibility");
        ENABLE_LEGACY_V166_VAULT_DATA_COMPATIBILITY = builder
                .comment(
                        "Migrates Vault 3.21.5 build 6574 v1_66 data to the standard v1_67",
                        "format before The Vault 20.0.3 build 6872 loads the world. The migration",
                        "keeps verified backups and is ignored on every other Vault build."
                )
                .define("enableLegacy6574V166Compatibility", true);
        builder.pop();

        SPEC = builder.build();
    }

    private MasuCraftFixesConfig() {
    }
}
