package com.masuary.masucraftfixes;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public class RewardPermissions {

    private static final String PREFIX = "masucraftfixes.rewards.";
    private static final Map<String, String> MODEL_TO_PERMISSION = new HashMap<>();

    static {
        // Forsaken set
        mapModels("forsaken",
                "gear/armor/forsaken",
                "gear/axe/forsaken",
                "gear/sword/forsaken"
        );

        // Spring set
        mapModels("spring",
                "gear/armor/flowery_madness",
                "gear/axe/spring_axe",
                "gear/axe/petal_splitter",
                "gear/sword/spring_sword",
                "gear/shield/spring_shield",
                "gear/focus/spring_focus",
                "gear/wand/spring_wand",
                "gear/magnets/spring_magnet"
        );

        // Christmas set
        mapModels("christmas",
                "gear/armor/green_christmas_hat",
                "gear/armor/red_christmas_hat",
                "gear/armor/snowman",
                "gear/armor/gingerbread",
                "gear/shield/fireplace",
                "gear/sword/lost_christmas",
                "gear/axe/lost_christmas_axe",
                "gear/wand/nutcracker_wand"
        );

        // Red Dragon set
        mapModels("reddragon",
                "gear/axe/reddragon_axe",
                "gear/sword/reddragon_sword"
        );

        // Dylan's set
        mapModels("dylans",
                "gear/armor/dylans_suit",
                "gear/axe/dylans_cleaver",
                "gear/sword/dylans_blade",
                "gear/sword/haunted_dagger",
                "gear/shield/dylans_protector",
                "gear/focus/dylans_book",
                "gear/wand/dylans_magic_stick"
        );

        // Competition rewards
        mapModels("competition",
                "gear/armor/royale_crown",
                "gear/armor/five_in_a_row_guard",
                "gear/armor/combat",
                "gear/armor/companion10",
                "gear/armor/falcon"
        );

        // Special/misc rewards
        mapModels("special",
                "gear/armor/sculk",
                "gear/armor/retirement",
                "gear/shield/golden_kappa",
                "gear/shield/molten",
                "gear/shield/mattress",
                "gear/shield/never_ever",
                "gear/shield/nocturn",
                "gear/shield/lamenting_mirror",
                "gear/sword/twin_blade",
                "gear/sword/buster_sword",
                "gear/sword/jubilant_blade",
                "gear/sword/centennial_blade",
                "gear/sword/doomslasher",
                "gear/axe/pick_axe",
                "gear/axe/arry",
                "gear/focus/ancient_scroll",
                "gear/focus/tiny_treasure_train",
                "gear/focus/essence_of_iskallium",
                "gear/wand/stormcallers_wand",
                "gear/wand/hypefuse_scepter",
                "gear/wand/hytale",
                "gear/wand/midnight_thorn"
        );
    }

    private static void mapModels(String category, String... modelPaths) {
        String permission = PREFIX + category;
        for (String path : modelPaths) {
            MODEL_TO_PERMISSION.put(path, permission);
        }
    }

    public static String getPermissionForModel(ResourceLocation modelId) {
        return MODEL_TO_PERMISSION.get(modelId.getPath());
    }
}
