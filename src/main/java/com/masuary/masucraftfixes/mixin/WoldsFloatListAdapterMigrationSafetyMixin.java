package com.masuary.masucraftfixes.mixin;

import com.masuary.masucraftfixes.VaultV166Compatibility;
import iskallia.vault.core.data.sync.context.SyncContext;
import iskallia.vault.core.net.BitBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Pseudo
@Mixin(
        targets = "xyz.iwolfking.woldsvaults.objectives.enchanted_elixir.ElixirBreakpointMap$FloatListAdapter",
        remap = false
)
public abstract class WoldsFloatListAdapterMigrationSafetyMixin {

    /**
     * @author Masuary
     * @reason Bound malformed Wolds elixir lists only while the one-time disk migration is decoding them.
     */
    @Overwrite
    public Optional<List<Float>> readBits(BitBuffer buffer, SyncContext context) {
        int length = buffer.readIntSegmented(8);
        length = VaultV166Compatibility.requireSafeSequenceLength(
                "Wolds elixir float list",
                length,
                buffer.getRemainingBits(),
                Float.SIZE
        );
        List<Float> values = new ArrayList<>(length);
        for (int index = 0; index < length; index++) {
            values.add(buffer.readFloat());
        }
        return Optional.of(values);
    }
}
