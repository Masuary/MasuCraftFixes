package com.masuary.masucraftfixes.refinedstorage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Refined Storage only saves the crafting tasks it is running. Tasks it loaded from disk but has not read back yet
 * (the network has not run since the server started) and tasks started this tick (moved in on the next network
 * update) would be dropped by a save, with the ingredients they already took out of storage.
 */
public final class CraftingTaskCarryOver {

    private CraftingTaskCarryOver() {
    }

    public static ListTag withUnsavedTasks(ListTag runningTasks, @Nullable ListTag unreadSavedTasks, List<CompoundTag> startedTasks) {
        ListTag allTasks = runningTasks.copy();
        if (unreadSavedTasks != null) {
            allTasks.addAll(unreadSavedTasks.copy());
        }
        allTasks.addAll(startedTasks);
        return allTasks;
    }
}
