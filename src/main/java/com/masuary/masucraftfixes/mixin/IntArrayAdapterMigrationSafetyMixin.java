package com.masuary.masucraftfixes.mixin;

import com.masuary.masucraftfixes.VaultV166Compatibility;
import iskallia.vault.core.data.adapter.Adapters;
import iskallia.vault.core.data.adapter.IBitAdapter;
import iskallia.vault.core.data.adapter.array.IntArrayAdapter;
import iskallia.vault.core.net.BitBuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Optional;

@Mixin(value = IntArrayAdapter.class, remap = false)
public abstract class IntArrayAdapterMigrationSafetyMixin {

    @Shadow
    @Final
    private Object elementAdapter;

    @Shadow
    @Final
    private boolean nullable;

    /**
     * @author Masuary
     * @reason Bound malformed legacy snapshot arrays only while the one-time disk migration is decoding them.
     */
    @Overwrite
    @SuppressWarnings("unchecked")
    public final Optional<int[]> readBits(BitBuffer buffer) {
        if (!(elementAdapter instanceof IBitAdapter adapter)) {
            throw new UnsupportedOperationException();
        }
        if (nullable && buffer.readBoolean()) {
            return Optional.empty();
        }
        int length = Adapters.INT_SEGMENTED_7.readBits(buffer).orElseThrow();
        length = VaultV166Compatibility.requireSafeSequenceLength(
                "int array",
                length,
                buffer.getRemainingBits(),
                1
        );
        int[] values = new int[length];
        for (int index = 0; index < values.length; index++) {
            values[index] = (int) adapter.readBits(buffer, null).orElse(0);
        }
        return Optional.of(values);
    }
}
