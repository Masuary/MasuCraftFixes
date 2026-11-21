package com.masuary.masucraftfixes.refinedstorage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CraftingTaskCarryOverTest {

    @Test
    void runningTasksAloneAreWrittenUnchanged() {
        ListTag running = tasks("running-a", "running-b");

        ListTag written = CraftingTaskCarryOver.withUnsavedTasks(running, null, List.of());

        assertEquals(running, written);
    }

    @Test
    void unreadSavedTasksAreKeptWhenNothingRuns() {
        ListTag unread = tasks("saved-before-restart");

        ListTag written = CraftingTaskCarryOver.withUnsavedTasks(new ListTag(), unread, List.of());

        assertEquals(tasks("saved-before-restart"), written);
    }

    @Test
    void runningThenUnreadThenStartedTasksAreAllWritten() {
        ListTag written = CraftingTaskCarryOver.withUnsavedTasks(
                tasks("running"), tasks("unread"), List.of(task("started-this-tick")));

        assertEquals(tasks("running", "unread", "started-this-tick"), written);
    }

    @Test
    void inputsAreNotModified() {
        ListTag running = tasks("running");
        ListTag unread = tasks("unread");

        CraftingTaskCarryOver.withUnsavedTasks(running, unread, List.of(task("started")));

        assertEquals(tasks("running"), running);
        assertEquals(tasks("unread"), unread);
    }

    private static ListTag tasks(String... ids) {
        ListTag list = new ListTag();
        for (String id : ids) {
            list.add(task(id));
        }
        return list;
    }

    private static CompoundTag task(String id) {
        CompoundTag data = new CompoundTag();
        data.putString("Id", id);
        CompoundTag task = new CompoundTag();
        task.putString("Type", "refinedstorage:v6");
        task.put("Task", data);
        return task;
    }
}
