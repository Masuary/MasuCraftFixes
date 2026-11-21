package com.masuary.masucraftfixes.refinedstorage;

/** Implemented on Refined Storage's {@code Network} by {@code NetworkMixin}. */
public interface GraphRebuildDebt {

    /** The network rebuilds its node graph through RS's own inactive-to-active path once it can run again. */
    void masucraftfixes$oweGraphRebuild();
}
