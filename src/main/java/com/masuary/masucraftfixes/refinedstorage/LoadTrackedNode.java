package com.masuary.masucraftfixes.refinedstorage;

/** Implemented on Refined Storage's {@code NetworkNode} by {@code NetworkNodeMixin}. */
public interface LoadTrackedNode {

    /** Records whether the node's chunk is loaded this tick and returns what was recorded on the previous tick. */
    boolean masucraftfixes$recordLoaded(boolean loaded);
}
