package com.masuary.masucraftfixes;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.*;

public class LpGrantedModelsData extends SavedData {

    private static final String DATA_NAME = "masucraftfixes_lp_granted_models";
    private final Map<UUID, Set<ResourceLocation>> grantedModels = new HashMap<>();

    private LpGrantedModelsData() {
    }

    private LpGrantedModelsData(CompoundTag tag) {
        for (String uuidKey : tag.getAllKeys()) {
            UUID uuid = UUID.fromString(uuidKey);
            Set<ResourceLocation> models = new HashSet<>();
            ListTag list = tag.getList(uuidKey, Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
                if (id != null) {
                    models.add(id);
                }
            }
            grantedModels.put(uuid, models);
        }
    }

    public Set<ResourceLocation> getModels(UUID uuid) {
        return grantedModels.getOrDefault(uuid, Collections.emptySet());
    }

    public void setModels(UUID uuid, Set<ResourceLocation> models) {
        if (models.isEmpty()) {
            grantedModels.remove(uuid);
        } else {
            grantedModels.put(uuid, new HashSet<>(models));
        }
        setDirty();
    }

    public void clearModels(UUID uuid) {
        if (grantedModels.remove(uuid) != null) {
            setDirty();
        }
    }

    @Nonnull
    @Override
    public CompoundTag save(@Nonnull CompoundTag compound) {
        grantedModels.forEach((uuid, models) -> {
            ListTag list = new ListTag();
            models.forEach(id -> list.add(StringTag.valueOf(id.toString())));
            compound.put(uuid.toString(), list);
        });
        return compound;
    }

    public static LpGrantedModelsData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(LpGrantedModelsData::new, LpGrantedModelsData::new, DATA_NAME);
    }
}
