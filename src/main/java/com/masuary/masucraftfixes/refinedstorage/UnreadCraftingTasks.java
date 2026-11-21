package com.masuary.masucraftfixes.refinedstorage;

/** Implemented on Refined Storage's {@code CraftingManager} by {@code CraftingManagerMixin}. */
public interface UnreadCraftingTasks {

    /** Saved crafting tasks RS has not read back yet because the network has not run since the server started. */
    int masucraftfixes$unreadSavedTaskCount();
}
