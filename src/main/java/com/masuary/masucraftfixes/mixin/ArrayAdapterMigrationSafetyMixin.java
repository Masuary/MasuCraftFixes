package com.masuary.masucraftfixes.mixin;

import com.masuary.masucraftfixes.VaultV166Compatibility;
import iskallia.vault.core.data.adapter.Adapters;
import iskallia.vault.core.data.adapter.IBitAdapter;
import iskallia.vault.core.data.adapter.array.ArrayAdapter;
import iskallia.vault.core.net.BitBuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.Supplier;

@Mixin(value = ArrayAdapter.class, remap = false)
public abstract class ArrayAdapterMigrationSafetyMixin<T> {

    @Shadow
    @Final
    private IntFunction<T[]> constructor;

    @Shadow
    @Final
    private Object elementAdapter;

    @Shadow
    @Final
    private Supplier<T> defaultValue;

    @Shadow
    @Final
    private boolean nullable;

    /**
     * @author Masuary
     * @reason Bound malformed legacy snapshot arrays only while the one-time disk migration is decoding them.
     */
    @Overwrite
    @SuppressWarnings("unchecked")
    public final Optional<T[]> readBits(BitBuffer buffer, Object context) {
        if (!(elementAdapter instanceof IBitAdapter adapter)) {
            throw new UnsupportedOperationException();
        }
        if (nullable && buffer.readBoolean()) {
            return Optional.empty();
        }
        int length = Adapters.INT_SEGMENTED_3.readBits(buffer).orElseThrow();
        length = VaultV166Compatibility.requireSafeSequenceLength(
                "object array",
                length,
                buffer.getRemainingBits(),
                1
        );
        T[] values = constructor.apply(length);
        for (int index = 0; index < values.length; index++) {
            values[index] = (T) adapter.readBits(buffer, context).orElseGet(defaultValue);
        }
        return Optional.of(values);
    }
}
