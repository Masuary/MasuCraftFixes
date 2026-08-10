package com.masuary.masucraftfixes.mixin;

import com.masuary.masucraftfixes.VaultV166Compatibility;
import iskallia.vault.core.data.adapter.Adapters;
import iskallia.vault.core.data.adapter.IBitAdapter;
import iskallia.vault.core.data.adapter.array.LongArrayAdapter;
import iskallia.vault.core.net.BitBuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Optional;

@Mixin(value = LongArrayAdapter.class, remap = false)
public abstract class LongArrayAdapterMigrationSafetyMixin {

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
    public final Optional<long[]> readBits(BitBuffer buffer) {
        if (!(elementAdapter instanceof IBitAdapter adapter)) {
            throw new UnsupportedOperationException();
        }
        if (nullable && buffer.readBoolean()) {
            return Optional.empty();
        }
        int length = Adapters.INT_SEGMENTED_7.readBits(buffer).orElseThrow();
        length = VaultV166Compatibility.requireSafeSequenceLength(
                "long array",
                length,
                buffer.getRemainingBits(),
                1
        );
        long[] values = new long[length];
        for (int index = 0; index < values.length; index++) {
            values[index] = (long) adapter.readBits(buffer, null).orElse(0L);
        }
        return Optional.of(values);
    }
}
