package com.masuary.masucraftfixes.mixin.refinedstorage;

import com.masuary.masucraftfixes.refinedstorage.CraftingTaskCarryOver;
import com.masuary.masucraftfixes.refinedstorage.UnreadCraftingTasks;
import com.refinedmods.refinedstorage.api.autocrafting.task.ICraftingTask;
import com.refinedmods.refinedstorage.apiimpl.autocrafting.CraftingManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = CraftingManager.class, remap = false)
public abstract class CraftingManagerMixin implements UnreadCraftingTasks {

    @Unique
    private static final String TASKS_TAG = "Tasks";
    @Unique
    private static final String TASK_TYPE_TAG = "Type";
    @Unique
    private static final String TASK_DATA_TAG = "Task";

    @Shadow
    private ListTag tasksToRead;

    @Shadow
    @Final
    private List<ICraftingTask> tasksToAdd;

    @Inject(method = "writeToNbt", at = @At("RETURN"))
    private void keepUnreadAndStartedTasks(CompoundTag tag, CallbackInfoReturnable<CompoundTag> callbackInfo) {
        if (tasksToRead == null && tasksToAdd.isEmpty()) {
            return;
        }
        List<CompoundTag> startedTasks = new ArrayList<>(tasksToAdd.size());
        for (ICraftingTask task : tasksToAdd) {
            CompoundTag taskTag = new CompoundTag();
            taskTag.putString(TASK_TYPE_TAG, task.getPattern().getCraftingTaskFactoryId().toString());
            taskTag.put(TASK_DATA_TAG, task.writeToNbt(new CompoundTag()));
            startedTasks.add(taskTag);
        }
        ListTag runningTasks = tag.getList(TASKS_TAG, Tag.TAG_COMPOUND);
        tag.put(TASKS_TAG, CraftingTaskCarryOver.withUnsavedTasks(runningTasks, tasksToRead, startedTasks));
    }

    @Override
    public int masucraftfixes$unreadSavedTaskCount() {
        return tasksToRead == null ? 0 : tasksToRead.size();
    }
}
