package com.masuary.masucraftfixes;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.EntityLeaveWorldEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class VesselAntiAfk {

    private static volatile boolean debugEnabled = false;

    public static void setDebug(boolean enabled) {
        debugEnabled = enabled;
    }

    public static boolean isDebug() {
        return debugEnabled;
    }

    private static final String VESSEL_CLASS_NAME = "iskallia.vault.entity.boss.TheVesselEntity";
    private static final String NBT_KEY_SEEN_TICK = "MasuCraftFixes_VesselSeenTick";
    private static final String NBT_KEY_TARGET_UUID = "MasuCraftFixes_VesselTargetUuid";
    private static final String NBT_KEY_ANCHOR_X = "MasuCraftFixes_VesselAnchorX";
    private static final String NBT_KEY_ANCHOR_Y = "MasuCraftFixes_VesselAnchorY";
    private static final String NBT_KEY_ANCHOR_Z = "MasuCraftFixes_VesselAnchorZ";
    private static final long TIMEOUT_TICKS = 6000L;
    private static final long LOST_TARGET_TIMEOUT_TICKS = 2400L;
    private static final long DEBUG_SNAPSHOT_INTERVAL_TICKS = 100L;
    private static final double MOVE_THRESHOLD_SQ = 9.0;
    private static final double MAX_LEGIT_MOVE_SQ = 100.0;
    private static final double MIN_TELEPORT_DISTANCE_SQ = 144.0;

    private static final Map<UUID, Long> lastEngagementTick = new HashMap<>();
    private static final Map<UUID, Long> lastTargetSeenTick = new HashMap<>();
    private static final Map<UUID, UUID> lastTargetUuid = new HashMap<>();
    private static final Map<UUID, Vec3> lastTargetPosition = new HashMap<>();
    private static final Map<UUID, Vec3> lastVesselPosition = new HashMap<>();
    private static final Map<UUID, Long> lastDebugSnapshotTick = new HashMap<>();
    /**
     * Position the Vessel first appeared at (captured on EntityJoinWorldEvent). Used to
     * rescue a Vessel that has glitched out of its arena and fallen into the void - if it
     * starts taking {@code outOfWorld} damage we teleport it back here and cancel the
     * damage, instead of letting the player AFK while the Vessel slowly dies below the
     * world. Cleared on EntityLeaveWorldEvent.
     */
    private static final Map<UUID, Vec3> vesselSpawnPosition = new HashMap<>();

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.getWorld().isClientSide || !(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        if (!isVessel(entity)) {
            return;
        }
        UUID vesselId = entity.getUUID();
        // Restore persisted state from the entity NBT (auto-loaded by Forge before
        // EntityJoinWorldEvent fires). This survives full server restarts - the in-memory
        // maps would otherwise be empty after a JVM restart even though the entity NBT
        // still has the tracking data.
        loadFromNbt(entity, vesselId);

        // Only capture on the very first join. In-memory map hit means chunk-reload-only;
        // if NBT had the anchor we already restored it above and it's now in the map.
        if (vesselSpawnPosition.containsKey(vesselId)) {
            debug("Vessel {} re-joined world (kept anchor {})",
                    shortId(vesselId), fmt(vesselSpawnPosition.get(vesselId)));
            return;
        }
        vesselSpawnPosition.put(vesselId, entity.position());
        persistToNbt(entity, vesselId);
        debug("Vessel {} joined world at {} (arena rescue anchor)",
                shortId(vesselId), fmt(entity.position()));
    }

    /**
     * Cancel {@code outOfWorld} damage on a Vessel and teleport it back to its arena
     * spawn position. Fires before damage is calculated, so a single intercept here is
     * cheaper and cleaner than letting LivingDamageEvent fire and trying to undo damage
     * after the fact. Cancelling stops the player exploit where a Vessel glitches off
     * the arena and the player AFKs while it slowly dies below Y=-64.
     */
    @SubscribeEvent
    public void onLivingAttack(LivingAttackEvent event) {
        LivingEntity victim = event.getEntityLiving();
        if (victim.level.isClientSide || !isVessel(victim)) {
            return;
        }
        if (event.getSource() != DamageSource.OUT_OF_WORLD) {
            return;
        }
        UUID vesselId = victim.getUUID();
        Vec3 anchor = vesselSpawnPosition.get(vesselId);
        if (anchor == null) {
            // No spawn captured (vessel was already in world when mod loaded). Cancel
            // the damage anyway - better to leave the vessel stranded than let it die
            // and complete the AFK exploit.
            event.setCanceled(true);
            debug("Vessel {} took outOfWorld damage but no spawn anchor captured - damage cancelled, no teleport",
                    shortId(vesselId));
            return;
        }
        victim.teleportTo(anchor.x, anchor.y, anchor.z);
        victim.setDeltaMovement(Vec3.ZERO);
        victim.fallDistance = 0.0f;
        event.setCanceled(true);
        MasuCraftFixes.LOGGER.info(
                "[VesselAntiAfk] Vessel {} fell out of arena (Y={}), rescued back to {} and damage cancelled.",
                vesselId, String.format("%.1f", victim.getY()), fmt(anchor));
    }

    @SubscribeEvent
    public void onTargetChanged(LivingChangeTargetEvent event) {
        LivingEntity entity = event.getEntityLiving();
        if (entity.level.isClientSide || !isVessel(entity)) {
            return;
        }
        LivingEntity newTarget = event.getNewTarget();
        UUID vesselId = entity.getUUID();
        long now = entity.level.getGameTime();
        if (newTarget == null) {
            // The event fires before setTarget is applied, so entity.getTarget() still
            // returns the PREVIOUS target. Only log a real null transition (was != null,
            // now == null) - the vessel's AI re-fires this event every tick with null
            // when no valid target is found, which otherwise spams the log.
            LivingEntity previousTarget = entity instanceof Mob mob ? mob.getTarget() : null;
            if (previousTarget != null) {
                debug("Vessel {} target cleared (was {}, last tracked UUID={})",
                        shortId(vesselId),
                        previousTarget.getName().getString(),
                        lastTargetUuid.get(vesselId));
            }
            return;
        }
        lastEngagementTick.put(vesselId, now);
        lastTargetSeenTick.put(vesselId, now);
        lastTargetUuid.put(vesselId, newTarget.getUUID());
        lastTargetPosition.put(vesselId, newTarget.position());
        lastVesselPosition.put(vesselId, entity.position());
        persistToNbt(entity, vesselId);
        debug("Vessel {} acquired target {} at {} (vessel at {}, dist={})",
                shortId(vesselId),
                newTarget.getName().getString(),
                fmt(newTarget.position()),
                fmt(entity.position()),
                String.format("%.1f", Math.sqrt(entity.distanceToSqr(newTarget))));
    }

    @SubscribeEvent
    public void onLivingDamage(LivingDamageEvent event) {
        LivingEntity victim = event.getEntityLiving();
        if (isVessel(victim)) {
            stampEngagement(victim);
            debug("Vessel {} took {} damage from {}",
                    shortId(victim.getUUID()),
                    String.format("%.2f", event.getAmount()),
                    event.getSource().msgId);
            return;
        }
        if (event.getSource().getEntity() instanceof LivingEntity attacker && isVessel(attacker)) {
            stampEngagement(attacker);
            debug("Vessel {} dealt {} damage to {} via {}",
                    shortId(attacker.getUUID()),
                    String.format("%.2f", event.getAmount()),
                    victim.getName().getString(),
                    event.getSource().msgId);
        }
    }

    @SubscribeEvent
    public void onLivingTick(LivingEvent.LivingUpdateEvent event) {
        LivingEntity entity = event.getEntityLiving();
        if (!isVessel(entity) || !(entity instanceof Mob vessel)) {
            return;
        }
        if (vessel.level.isClientSide) {
            return;
        }

        UUID vesselId = vessel.getUUID();
        long now = vessel.level.getGameTime();
        LivingEntity target = vessel.getTarget();

        if (target == null || !target.isAlive() || target.level != vessel.level) {
            Long lastSeen = lastTargetSeenTick.get(vesselId);
            // Seed presumptive tracking if we have none yet. This matters after a server
            // restart where the JVM maps are empty but the Vessel was already in a fight,
            // or any case where the Vessel exists in a dim with a player but never
            // acquired aggro (player AFK out of range). Single-player dim only - if
            // multiple players are in the dim we can't safely pick one to pull.
            if (lastSeen == null && vessel.level instanceof ServerLevel serverLevel) {
                List<ServerPlayer> playersInDim = serverLevel.players();
                if (playersInDim.size() == 1) {
                    ServerPlayer presumptive = playersInDim.get(0);
                    lastTargetSeenTick.put(vesselId, now);
                    lastTargetUuid.put(vesselId, presumptive.getUUID());
                    persistToNbt(vessel, vesselId);
                    debug("Vessel {} seeded presumptive target {} (no prior tracking, single player in dim)",
                            shortId(vesselId), presumptive.getName().getString());
                    lastSeen = now;
                } else if (debugEnabled && !playersInDim.isEmpty() && (now % DEBUG_SNAPSHOT_INTERVAL_TICKS == 0)) {
                    debug("Vessel {} no tracking and {} players in dim - cannot seed presumptive target",
                            shortId(vesselId), playersInDim.size());
                }
            }
            if (lastSeen != null) {
                long elapsed = now - lastSeen;
                if (elapsed > LOST_TARGET_TIMEOUT_TICKS) {
                    pullLostTarget(vessel, vesselId, lastSeen, now);
                } else if (debugEnabled && elapsed > 0 && elapsed % DEBUG_SNAPSHOT_INTERVAL_TICKS == 0) {
                    debug("Vessel {} no target ({}s elapsed, pull at {}s)",
                            shortId(vesselId), elapsed / 20, LOST_TARGET_TIMEOUT_TICKS / 20);
                }
            }
            return;
        }

        lastTargetSeenTick.put(vesselId, now);
        // Refresh target UUID in case AI re-targeted to a different player. Persist so a
        // server restart picks up the live target rather than a stale one.
        UUID currentTargetUuid = target.getUUID();
        UUID storedTargetUuid = lastTargetUuid.get(vesselId);
        if (!currentTargetUuid.equals(storedTargetUuid)) {
            lastTargetUuid.put(vesselId, currentTargetUuid);
        }
        persistToNbt(vessel, vesselId);

        Vec3 previousTargetPosition = lastTargetPosition.get(vesselId);
        Vec3 currentTargetPosition = target.position();
        double targetMoveSq = previousTargetPosition == null ? 0.0 : currentTargetPosition.distanceToSqr(previousTargetPosition);
        boolean targetTeleported = targetMoveSq > MAX_LEGIT_MOVE_SQ;
        boolean targetMovedLegit = !targetTeleported && targetMoveSq > MOVE_THRESHOLD_SQ;

        Vec3 previousVesselPosition = lastVesselPosition.get(vesselId);
        Vec3 currentVesselPosition = vessel.position();
        double vesselMoveSq = previousVesselPosition == null ? 0.0 : currentVesselPosition.distanceToSqr(previousVesselPosition);
        boolean vesselTeleported = vesselMoveSq > MAX_LEGIT_MOVE_SQ;
        boolean vesselMovedLegit = !vesselTeleported && vesselMoveSq > MOVE_THRESHOLD_SQ;

        boolean targetTookDamage = target.hurtTime > 0;

        if (targetTeleported) {
            lastTargetPosition.put(vesselId, currentTargetPosition);
            debug("Vessel {} target teleport filtered ({} blocks, no stamp)",
                    shortId(vesselId), String.format("%.1f", Math.sqrt(targetMoveSq)));
        }
        if (vesselTeleported) {
            lastVesselPosition.put(vesselId, currentVesselPosition);
            debug("Vessel {} self-teleport filtered ({} blocks, no stamp)",
                    shortId(vesselId), String.format("%.1f", Math.sqrt(vesselMoveSq)));
        }

        if (targetMovedLegit || vesselMovedLegit || targetTookDamage) {
            lastTargetPosition.put(vesselId, currentTargetPosition);
            lastVesselPosition.put(vesselId, currentVesselPosition);
            lastEngagementTick.put(vesselId, now);
            if (debugEnabled){
                StringBuilder reasons = new StringBuilder();
                if (targetMovedLegit) reasons.append("targetMoved(").append(String.format("%.1f", Math.sqrt(targetMoveSq))).append("b) ");
                if (vesselMovedLegit) reasons.append("vesselMoved(").append(String.format("%.1f", Math.sqrt(vesselMoveSq))).append("b) ");
                if (targetTookDamage) reasons.append("hurtTime(").append(target.hurtTime).append(") ");
                logSnapshot(vesselId, now, "stamp: " + reasons.toString().trim());
            }
        }

        double distSq = vessel.distanceToSqr(target);
        if (distSq < MIN_TELEPORT_DISTANCE_SQ) {
            if (debugEnabled)maybeLogSnapshot(vesselId, now, vessel, target, distSq, "in-range, gated");
            return;
        }

        long lastTick = lastEngagementTick.getOrDefault(vesselId, now);
        long sinceEngagement = now - lastTick;
        if (sinceEngagement > TIMEOUT_TICKS) {
            debug("Vessel {} TIMEOUT - pulling {} (since engagement: {}s, dist: {}b)",
                    shortId(vesselId), target.getName().getString(), sinceEngagement / 20, String.format("%.1f", Math.sqrt(distSq)));
            forceEngagement(vessel, target);
            lastEngagementTick.put(vesselId, now);
            lastTargetPosition.put(vesselId, target.position());
            lastVesselPosition.put(vesselId, vessel.position());
        } else if (debugEnabled){
            maybeLogSnapshot(vesselId, now, vessel, target, distSq, "out-of-range, ticking");
        }
    }

    @SubscribeEvent
    public void onEntityLeaveWorld(EntityLeaveWorldEvent event) {
        if (event.getWorld().isClientSide || !(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        if (isVessel(entity)) {
            UUID vesselId = entity.getUUID();
            // Reason-aware cleanup: chunk-unload / player-unload / dimension-change all fire
            // EntityLeaveWorldEvent but the vessel will re-join the same world from NBT later.
            // Wiping target/timer state on those removals would mean a player can log out near
            // an active vessel, log back in far enough to drop aggro, and exploit the gap
            // because lastTargetSeenTick is gone -> lost-target pull never fires.
            net.minecraft.world.entity.Entity.RemovalReason removal = entity.getRemovalReason();
            boolean permanent = removal == net.minecraft.world.entity.Entity.RemovalReason.KILLED
                    || removal == net.minecraft.world.entity.Entity.RemovalReason.DISCARDED;
            if (permanent) {
                lastEngagementTick.remove(vesselId);
                lastTargetSeenTick.remove(vesselId);
                lastTargetUuid.remove(vesselId);
                lastTargetPosition.remove(vesselId);
                lastVesselPosition.remove(vesselId);
                lastDebugSnapshotTick.remove(vesselId);
                vesselSpawnPosition.remove(vesselId);
                debug("Vessel {} left world permanently (reason={}, all state dropped)",
                        shortId(vesselId), removal);
            } else {
                debug("Vessel {} left world (reason={}, all state kept for rejoin)",
                        shortId(vesselId), removal);
            }
        }
    }

    private boolean isVessel(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        for (Class<?> klass = entity.getClass(); klass != null; klass = klass.getSuperclass()) {
            if (klass.getName().equals(VESSEL_CLASS_NAME)) {
                return true;
            }
        }
        return false;
    }

    private void stampEngagement(LivingEntity vessel) {
        if (vessel.level.isClientSide) {
            return;
        }
        UUID vesselId = vessel.getUUID();
        long now = vessel.level.getGameTime();
        lastEngagementTick.put(vesselId, now);
        lastVesselPosition.put(vesselId, vessel.position());
        if (vessel instanceof Mob mob) {
            LivingEntity target = mob.getTarget();
            if (target != null) {
                lastTargetSeenTick.put(vesselId, now);
                lastTargetUuid.put(vesselId, target.getUUID());
                lastTargetPosition.put(vesselId, target.position());
            }
        }
        persistToNbt(vessel, vesselId);
    }

    private void forceEngagement(Mob vessel, LivingEntity target) {
        double targetX = vessel.getX();
        double targetY = vessel.getY();
        double targetZ = vessel.getZ();
        if (target instanceof ServerPlayer serverPlayerTarget) {
            serverPlayerTarget.connection.teleport(targetX, targetY, targetZ, serverPlayerTarget.getYRot(), serverPlayerTarget.getXRot());
        } else {
            target.teleportTo(targetX, targetY, targetZ);
        }
        MasuCraftFixes.LOGGER.info(
                "[VesselAntiAfk] Pulled {} to Vessel {} after {}s of no engagement",
                target.getName().getString(),
                vessel.getUUID(),
                TIMEOUT_TICKS / 20
        );
    }

    private void pullLostTarget(Mob vessel, UUID vesselId, long lastSeenTick, long now) {
        UUID targetUuid = lastTargetUuid.get(vesselId);
        if (targetUuid == null) {
            debug("Vessel {} lost-target timeout but no stored target UUID", shortId(vesselId));
            lastTargetSeenTick.put(vesselId, now);
            persistToNbt(vessel, vesselId);
            return;
        }
        if (!(vessel.level instanceof ServerLevel serverLevel)) {
            return;
        }
        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(targetUuid);
        if (player == null) {
            debug("Vessel {} lost-target timeout but player {} is offline (retry in {}s)",
                    shortId(vesselId), targetUuid, LOST_TARGET_TIMEOUT_TICKS / 20);
            lastTargetSeenTick.put(vesselId, now);
            persistToNbt(vessel, vesselId);
            return;
        }
        if (player.level != vessel.level) {
            debug("Vessel {} lost-target timeout but player {} is in another dimension (retry in {}s)",
                    shortId(vesselId), player.getName().getString(), LOST_TARGET_TIMEOUT_TICKS / 20);
            lastTargetSeenTick.put(vesselId, now);
            persistToNbt(vessel, vesselId);
            return;
        }
        player.connection.teleport(vessel.getX(), vessel.getY(), vessel.getZ(), player.getYRot(), player.getXRot());
        MasuCraftFixes.LOGGER.info(
                "[VesselAntiAfk] Pulled {} back to Vessel {} after {}s without target",
                player.getName().getString(),
                vessel.getUUID(),
                (now - lastSeenTick) / 20
        );
        lastTargetSeenTick.put(vesselId, now);
        lastTargetPosition.put(vesselId, player.position());
        lastVesselPosition.put(vesselId, vessel.position());
        lastEngagementTick.put(vesselId, now);
        persistToNbt(vessel, vesselId);
    }

    /**
     * Persist the tracking fields that matter across server restarts (target identity,
     * timer baseline, arena anchor) into the entity's auto-saved NBT. Cheap CompoundTag
     * write - safe to call after every state mutation. Position/engagement-tick stamps
     * are not persisted because they re-derive correctly from the next event tick.
     */
    private void persistToNbt(Entity vessel, UUID vesselId) {
        CompoundTag tag = vessel.getPersistentData();
        Long seen = lastTargetSeenTick.get(vesselId);
        UUID targetUuid = lastTargetUuid.get(vesselId);
        Vec3 anchor = vesselSpawnPosition.get(vesselId);
        if (seen != null) tag.putLong(NBT_KEY_SEEN_TICK, seen); else tag.remove(NBT_KEY_SEEN_TICK);
        if (targetUuid != null) tag.putUUID(NBT_KEY_TARGET_UUID, targetUuid); else tag.remove(NBT_KEY_TARGET_UUID);
        if (anchor != null) {
            tag.putDouble(NBT_KEY_ANCHOR_X, anchor.x);
            tag.putDouble(NBT_KEY_ANCHOR_Y, anchor.y);
            tag.putDouble(NBT_KEY_ANCHOR_Z, anchor.z);
        } else {
            tag.remove(NBT_KEY_ANCHOR_X);
            tag.remove(NBT_KEY_ANCHOR_Y);
            tag.remove(NBT_KEY_ANCHOR_Z);
        }
    }

    /**
     * Restore tracking state from entity NBT into the in-memory maps. Called on
     * EntityJoinWorldEvent so that chunk reload AND full server restart both restore
     * the same way - in-memory maps are pure cache, NBT is the source of truth.
     */
    private void loadFromNbt(Entity vessel, UUID vesselId) {
        CompoundTag tag = vessel.getPersistentData();
        if (tag.contains(NBT_KEY_SEEN_TICK)) {
            lastTargetSeenTick.put(vesselId, tag.getLong(NBT_KEY_SEEN_TICK));
        }
        if (tag.hasUUID(NBT_KEY_TARGET_UUID)) {
            lastTargetUuid.put(vesselId, tag.getUUID(NBT_KEY_TARGET_UUID));
        }
        if (tag.contains(NBT_KEY_ANCHOR_X) && tag.contains(NBT_KEY_ANCHOR_Y) && tag.contains(NBT_KEY_ANCHOR_Z)) {
            vesselSpawnPosition.put(vesselId, new Vec3(
                    tag.getDouble(NBT_KEY_ANCHOR_X),
                    tag.getDouble(NBT_KEY_ANCHOR_Y),
                    tag.getDouble(NBT_KEY_ANCHOR_Z)));
            debug("Vessel {} restored from NBT: anchor={}, seen={}, targetUuid={}",
                    shortId(vesselId),
                    fmt(vesselSpawnPosition.get(vesselId)),
                    lastTargetSeenTick.get(vesselId),
                    lastTargetUuid.get(vesselId));
        }
    }

    private void maybeLogSnapshot(UUID vesselId, long now, Mob vessel, LivingEntity target, double distSq, String label) {
        Long lastDbg = lastDebugSnapshotTick.get(vesselId);
        if (lastDbg != null && now - lastDbg < DEBUG_SNAPSHOT_INTERVAL_TICKS) {
            return;
        }
        long lastEng = lastEngagementTick.getOrDefault(vesselId, now);
        Long lastSeen = lastTargetSeenTick.get(vesselId);
        debug("Vessel {} {}: target={} at {}, vessel at {}, dist={}b, sinceEngagement={}s, sinceTargetSeen={}s",
                shortId(vesselId),
                label,
                target.getName().getString(),
                fmt(target.position()),
                fmt(vessel.position()),
                String.format("%.1f", Math.sqrt(distSq)),
                (now - lastEng) / 20,
                lastSeen == null ? "n/a" : ((now - lastSeen) / 20) + "");
        lastDebugSnapshotTick.put(vesselId, now);
    }

    private void logSnapshot(UUID vesselId, long now, String reason) {
        debug("Vessel {} {}", shortId(vesselId), reason);
    }

    private void debug(String fmt, Object... args) {
        if (debugEnabled){
            MasuCraftFixes.LOGGER.info("[VesselAntiAfk DEBUG] " + fmt, args);
        }
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private static String fmt(Vec3 v) {
        return String.format("(%.1f,%.1f,%.1f)", v.x, v.y, v.z);
    }
}
