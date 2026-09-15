package com.masuary.masucraftfixes.mixin;

import com.masuary.masucraftfixes.MasuCraftFixes;
import com.masuary.masucraftfixes.VaultChallengeGuardMonitor;
import com.masuary.masucraftfixes.VaultChallengeTickGuard;
import iskallia.vault.core.vault.challenge.base.ChallengeManager;
import iskallia.vault.core.world.storage.VirtualWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(value = ChallengeManager.class, remap = false)
public abstract class VaultChallengeTickGuardMixin {
    @Shadow
    protected ServerLevel attachedWorld;

    @Shadow
    public boolean deleted;

    @Shadow
    public UUID uuid;

    @Shadow
    public BlockPos pos;

    @Unique
    private boolean masucraftfixes$loggedInvalidWorld;

    @Unique
    private String masucraftfixes$blockedDimension;

    // Guard the callback before virtual dispatch, so every challenge subtype is covered.
    @Dynamic("Synthetic server-tick lambda in VH 3.21.6.6884; verified by VaultChallengeTickTargetTest")
    @SuppressWarnings("target") // Javac hides synthetic methods; the bytecode test validates this target.
    @Inject(
            method = "lambda$registerEvents$3(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraftforge/event/TickEvent$ServerTickEvent;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private void masucraftfixes$skipInvalidWorldTick(ServerLevel world, TickEvent.ServerTickEvent event,
                                                   CallbackInfo callbackInfo) {
        ServerLevel registeredWorld = world.getServer().getLevel(world.dimension());
        boolean worldMarkedForDeletion = world instanceof VirtualWorld virtualWorld
                && virtualWorld.isMarkedForDeletion();
        boolean allowed = VaultChallengeTickGuard.canTick(
                attachedWorld, world, registeredWorld, deleted, worldMarkedForDeletion);
        if (allowed) {
            VaultChallengeGuardMonitor.recordCallback(true);
            return;
        }

        callbackInfo.cancel();
        VaultChallengeGuardMonitor.recordCallback(false);
        // Native ChallengeData cleanup still detaches curses and removes expired records.
        if (!masucraftfixes$loggedInvalidWorld) {
            masucraftfixes$loggedInvalidWorld = true;
            masucraftfixes$blockedDimension = world.dimension().location().toString();
            VaultChallengeGuardMonitor.recordBlockedChallenge("challenge=" + uuid + " type=" + getClass().getName()
                    + " dimension=" + masucraftfixes$blockedDimension + " position=" + pos
                    + " challengeDeleted=" + deleted + " attachedWorldMatches=" + (attachedWorld == world)
                    + " registeredWorldMatches=" + (registeredWorld == world)
                    + " markedForDeletion=" + worldMarkedForDeletion);
            if (!deleted && attachedWorld == world) {
                MasuCraftFixes.LOGGER.warn(
                        "Skipped stale Vault challenge {} at {} in {}: registeredWorldMatches={}, markedForDeletion={}. "
                                + "Leaving cleanup to Vault Hunters.",
                        uuid, pos, world.dimension().location(), registeredWorld == world, worldMarkedForDeletion);
            }
        }
    }

    @Inject(method = "onDetach()V", at = @At("TAIL"), require = 1)
    private void masucraftfixes$recordNativeDetach(CallbackInfo callbackInfo) {
        if (masucraftfixes$loggedInvalidWorld) {
            VaultChallengeGuardMonitor.recordDetachedChallenge("challenge=" + uuid
                    + " dimension=" + masucraftfixes$blockedDimension
                    + " attachedWorldCleared=" + (attachedWorld == null));
            masucraftfixes$loggedInvalidWorld = false;
            masucraftfixes$blockedDimension = null;
        }
    }
}
