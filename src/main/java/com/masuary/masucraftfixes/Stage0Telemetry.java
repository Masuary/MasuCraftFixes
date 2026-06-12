package com.masuary.masucraftfixes;

import iskallia.vault.core.vault.Vault;
import iskallia.vault.core.world.storage.VirtualWorld;
import iskallia.vault.mixin.AccessorMinecraftServer;
import iskallia.vault.world.data.ServerVaults;
import iskallia.vault.world.data.VirtualWorlds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

public final class Stage0Telemetry {
    private static final ThreadLocal<Long> WOLDS_STACK_MATCH_START_NANOS = new ThreadLocal<>();
    private static final ThreadLocal<Long> WOLDS_GET_INVENTORY_START_NANOS = new ThreadLocal<>();
    private static final ThreadLocal<WoldsHandlerStart> WOLDS_FILTER_HANDLER_START = new ThreadLocal<>();

    private static final LongAdder PICKUP_EVENTS = new LongAdder();
    private static final LongAdder VAULT_PICKUP_EVENTS = new LongAdder();
    private static final LongAdder CANCELED_PICKUP_EVENTS = new LongAdder();
    private static final LongAdder REMOVED_PICKUP_EVENTS = new LongAdder();
    private static final LongAdder BLOCK_BREAK_EVENTS = new LongAdder();
    private static final LongAdder VAULT_BLOCK_BREAK_EVENTS = new LongAdder();
    private static final LongAdder CANCELED_BLOCK_BREAK_EVENTS = new LongAdder();
    private static final AtomicLong MAX_BLOCK_BREAKS_PER_PLAYER_TICK = new AtomicLong();
    private static final ConcurrentHashMap<UUID, BurstCounter> BLOCK_BREAK_BURSTS = new ConcurrentHashMap<>();

    private static final TimingStats WOLDS_FILTER_HANDLER = new TimingStats();
    private static final TimingStats WOLDS_STACK_MATCHES = new TimingStats();
    private static final TimingStats WOLDS_GET_INVENTORY = new TimingStats();
    private static final LongAdder WOLDS_STACK_MATCH_TRUE = new LongAdder();
    private static final LongAdder WOLDS_FILTER_VOID_TRANSITIONS = new LongAdder();

    private static final ConcurrentHashMap<String, LongAdder> PICKUP_ITEM_TYPES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> PICKUP_PLAYER_VAULTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> BLOCK_BREAK_BLOCK_TYPES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> BLOCK_BREAK_PLAYER_VAULTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> WOLDS_MATCH_ITEM_TYPES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> WOLDS_NECKLACE_INVENTORY_HASHES = new ConcurrentHashMap<>();

    private static final LongAdder VIRTUAL_WORLD_SAMPLES = new LongAdder();
    private static final AtomicLong MAX_VIRTUAL_WORLD_TICK_NANOS = new AtomicLong();
    private static final AtomicLong MAX_VIRTUAL_WORLD_ITEM_ENTITIES = new AtomicLong();
    private static final AtomicLong MAX_VIRTUAL_WORLD_HOSTILE_MOBS = new AtomicLong();
    private static final AtomicLong MAX_VIRTUAL_WORLD_LOADED_CHUNKS = new AtomicLong();
    private static final AtomicReference<String> LAST_VIRTUAL_WORLD_SAMPLE = new AtomicReference<>("none");

    private static final AtomicInteger LAST_SUMMARY_TICK = new AtomicInteger();
    private static final AtomicBoolean LOG_WRITE_FAILURE_REPORTED = new AtomicBoolean();

    private Stage0Telemetry() {
    }

    public static void recordPickup(ServerPlayer player, ItemEntity itemEntity, String vaultId, boolean canceled, boolean removed) {
        if (!Stage0TelemetryConfig.isTelemetryEnabled()) {
            return;
        }

        PICKUP_EVENTS.increment();
        if (vaultId != null) {
            VAULT_PICKUP_EVENTS.increment();
            incrementTop(PICKUP_PLAYER_VAULTS, playerVaultKey(player, vaultId));
        }
        if (canceled) {
            CANCELED_PICKUP_EVENTS.increment();
        }
        if (removed) {
            REMOVED_PICKUP_EVENTS.increment();
        }
        if (itemEntity != null) {
            incrementTop(PICKUP_ITEM_TYPES, itemKey(itemEntity.getItem()));
        }
    }

    public static void recordBlockBreak(ServerPlayer player, Level level, String vaultId, boolean canceled, BlockState state) {
        if (!Stage0TelemetryConfig.isTelemetryEnabled()) {
            return;
        }

        BLOCK_BREAK_EVENTS.increment();
        if (vaultId != null) {
            VAULT_BLOCK_BREAK_EVENTS.increment();
            incrementTop(BLOCK_BREAK_PLAYER_VAULTS, playerVaultKey(player, vaultId));
        }
        if (canceled) {
            CANCELED_BLOCK_BREAK_EVENTS.increment();
        }
        if (state != null && state.getBlock() != null) {
            ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
            incrementTop(BLOCK_BREAK_BLOCK_TYPES, key == null ? state.getBlock().getDescriptionId() : key.toString());
        }
        if (player != null && player.server != null) {
            recordBlockBreakBurst(player.getUUID(), player.server.getTickCount());
        }
    }

    public static void startWoldsFilterHandler(EntityItemPickupEvent event) {
        if (!Stage0TelemetryConfig.isTelemetryEnabled()) {
            WOLDS_FILTER_HANDLER_START.remove();
            return;
        }

        ItemEntity itemEntity = event == null ? null : event.getItem();
        WOLDS_FILTER_HANDLER_START.set(new WoldsHandlerStart(
                System.nanoTime(),
                event != null && event.isCanceled(),
                itemEntity != null && itemEntity.isRemoved()
        ));
    }

    public static void finishWoldsFilterHandler(EntityItemPickupEvent event) {
        WoldsHandlerStart start = WOLDS_FILTER_HANDLER_START.get();
        WOLDS_FILTER_HANDLER_START.remove();
        if (start == null || !Stage0TelemetryConfig.isTelemetryEnabled()) {
            return;
        }

        long nanos = System.nanoTime() - start.startNanos;
        WOLDS_FILTER_HANDLER.record(nanos);

        ItemEntity itemEntity = event == null ? null : event.getItem();
        boolean nowCanceled = event != null && event.isCanceled();
        boolean nowRemoved = itemEntity != null && itemEntity.isRemoved();
        if (!start.canceledAtStart && nowCanceled && !start.removedAtStart && nowRemoved) {
            WOLDS_FILTER_VOID_TRANSITIONS.increment();
        }
        maybeLogSlow("wolds_filter_handler", nanos);
    }

    public static void startWoldsStackMatchesFilter() {
        startTimer(WOLDS_STACK_MATCH_START_NANOS);
    }

    public static void finishWoldsStackMatchesFilter(ItemStack stack, ItemStack necklace, boolean matched) {
        long nanos = finishTimer(WOLDS_STACK_MATCH_START_NANOS);
        if (nanos < 0L || !Stage0TelemetryConfig.isTelemetryEnabled()) {
            return;
        }

        WOLDS_STACK_MATCHES.record(nanos);
        if (matched) {
            WOLDS_STACK_MATCH_TRUE.increment();
        }
        incrementTop(WOLDS_MATCH_ITEM_TYPES, itemKey(stack));
        incrementTop(WOLDS_NECKLACE_INVENTORY_HASHES, necklaceInventoryHash(necklace));
        maybeLogSlow("wolds_stack_matches_filter", nanos);
    }

    public static void startWoldsGetInventory() {
        startTimer(WOLDS_GET_INVENTORY_START_NANOS);
    }

    public static void finishWoldsGetInventory() {
        long nanos = finishTimer(WOLDS_GET_INVENTORY_START_NANOS);
        if (nanos < 0L || !Stage0TelemetryConfig.isTelemetryEnabled()) {
            return;
        }

        WOLDS_GET_INVENTORY.record(nanos);
        maybeLogSlow("wolds_filter_get_inventory", nanos);
    }

    public static void onServerTick(MinecraftServer server) {
        if (!Stage0TelemetryConfig.isTelemetryEnabled() || server == null) {
            return;
        }

        int tick = server.getTickCount();
        if (tick % Stage0TelemetryConfig.worldSampleIntervalTicks() == 0) {
            sampleVirtualWorlds(server);
        }

        int last = LAST_SUMMARY_TICK.get();
        if (tick - last < Stage0TelemetryConfig.summaryIntervalTicks()) {
            return;
        }
        if (!LAST_SUMMARY_TICK.compareAndSet(last, tick)) {
            return;
        }

        writeTelemetry("stage0_summary tick=" + tick + " " + currentSummary());
    }

    public static String currentSummary() {
        return "pickups={total=" + PICKUP_EVENTS.sum()
                + ", vault=" + VAULT_PICKUP_EVENTS.sum()
                + ", canceled=" + CANCELED_PICKUP_EVENTS.sum()
                + ", removed=" + REMOVED_PICKUP_EVENTS.sum()
                + ", itemTypes=" + topSummary(PICKUP_ITEM_TYPES)
                + ", playerVaults=" + topSummary(PICKUP_PLAYER_VAULTS)
                + "} blockBreaks={total=" + BLOCK_BREAK_EVENTS.sum()
                + ", vault=" + VAULT_BLOCK_BREAK_EVENTS.sum()
                + ", canceled=" + CANCELED_BLOCK_BREAK_EVENTS.sum()
                + ", maxPerPlayerTick=" + MAX_BLOCK_BREAKS_PER_PLAYER_TICK.get()
                + ", blockTypes=" + topSummary(BLOCK_BREAK_BLOCK_TYPES)
                + ", playerVaults=" + topSummary(BLOCK_BREAK_PLAYER_VAULTS)
                + "} woldsFilter={handler=" + WOLDS_FILTER_HANDLER.summary()
                + ", voidTransitions=" + WOLDS_FILTER_VOID_TRANSITIONS.sum()
                + ", stackMatches=" + WOLDS_STACK_MATCHES.summary()
                + ", matched=" + WOLDS_STACK_MATCH_TRUE.sum()
                + ", getInventory=" + WOLDS_GET_INVENTORY.summary()
                + ", matchItemTypes=" + topSummary(WOLDS_MATCH_ITEM_TYPES)
                + ", necklaceInventoryHashes=" + topSummary(WOLDS_NECKLACE_INVENTORY_HASHES)
                + "} virtualWorlds={samples=" + VIRTUAL_WORLD_SAMPLES.sum()
                + ", maxWorldTickMs=" + formatMillis(MAX_VIRTUAL_WORLD_TICK_NANOS.get())
                + ", maxItemEntities=" + MAX_VIRTUAL_WORLD_ITEM_ENTITIES.get()
                + ", maxHostileMobs=" + MAX_VIRTUAL_WORLD_HOSTILE_MOBS.get()
                + ", maxLoadedChunks=" + MAX_VIRTUAL_WORLD_LOADED_CHUNKS.get()
                + ", last=" + LAST_VIRTUAL_WORLD_SAMPLE.get()
                + "}";
    }

    public static void reset() {
        PICKUP_EVENTS.reset();
        VAULT_PICKUP_EVENTS.reset();
        CANCELED_PICKUP_EVENTS.reset();
        REMOVED_PICKUP_EVENTS.reset();
        BLOCK_BREAK_EVENTS.reset();
        VAULT_BLOCK_BREAK_EVENTS.reset();
        CANCELED_BLOCK_BREAK_EVENTS.reset();
        MAX_BLOCK_BREAKS_PER_PLAYER_TICK.set(0L);
        BLOCK_BREAK_BURSTS.clear();
        WOLDS_FILTER_HANDLER.reset();
        WOLDS_STACK_MATCHES.reset();
        WOLDS_GET_INVENTORY.reset();
        WOLDS_STACK_MATCH_TRUE.reset();
        WOLDS_FILTER_VOID_TRANSITIONS.reset();
        PICKUP_ITEM_TYPES.clear();
        PICKUP_PLAYER_VAULTS.clear();
        BLOCK_BREAK_BLOCK_TYPES.clear();
        BLOCK_BREAK_PLAYER_VAULTS.clear();
        WOLDS_MATCH_ITEM_TYPES.clear();
        WOLDS_NECKLACE_INVENTORY_HASHES.clear();
        VIRTUAL_WORLD_SAMPLES.reset();
        MAX_VIRTUAL_WORLD_TICK_NANOS.set(0L);
        MAX_VIRTUAL_WORLD_ITEM_ENTITIES.set(0L);
        MAX_VIRTUAL_WORLD_HOSTILE_MOBS.set(0L);
        MAX_VIRTUAL_WORLD_LOADED_CHUNKS.set(0L);
        LAST_VIRTUAL_WORLD_SAMPLE.set("none");
        LAST_SUMMARY_TICK.set(0);
    }

    private static void startTimer(ThreadLocal<Long> timer) {
        if (!Stage0TelemetryConfig.isTelemetryEnabled()) {
            timer.remove();
            return;
        }
        timer.set(System.nanoTime());
    }

    private static long finishTimer(ThreadLocal<Long> timer) {
        Long start = timer.get();
        timer.remove();
        return start == null ? -1L : System.nanoTime() - start;
    }

    private static void recordBlockBreakBurst(UUID playerId, int tick) {
        BurstCounter counter = BLOCK_BREAK_BURSTS.computeIfAbsent(playerId, ignored -> new BurstCounter());
        int burst;
        synchronized (counter) {
            if (counter.tick != tick) {
                updateMax(MAX_BLOCK_BREAKS_PER_PLAYER_TICK, counter.count);
                counter.tick = tick;
                counter.count = 0;
            }
            counter.count++;
            burst = counter.count;
        }
        updateMax(MAX_BLOCK_BREAKS_PER_PLAYER_TICK, burst);
    }

    private static void sampleVirtualWorlds(MinecraftServer server) {
        int worlds = 0;
        int totalEntities = 0;
        int totalItems = 0;
        int totalHostile = 0;
        int totalChunks = 0;
        int totalPlayers = 0;
        long maxTickNanos = 0L;
        StringBuilder worldDetails = new StringBuilder("[");

        try {
            for (VirtualWorld world : VirtualWorlds.getAll()) {
                worlds++;
                WorldCounts counts = countWorld(world);
                totalEntities += counts.entities;
                totalItems += counts.itemEntities;
                totalHostile += counts.hostileMobs;
                int chunks = world.getChunkSource().getLoadedChunksCount();
                int players = world.players().size();
                totalChunks += chunks;
                totalPlayers += players;

                TickTimeSummary tickTimes = tickTimes(server, world.dimension());
                maxTickNanos = Math.max(maxTickNanos, tickTimes.maxNanos);

                if (worlds <= Stage0TelemetryConfig.topEntryLimit()) {
                    if (worlds > 1) {
                        worldDetails.append("; ");
                    }
                    worldDetails.append(world.dimension().location())
                            .append(" vault=").append(vaultId(world))
                            .append(" entities=").append(counts.entities)
                            .append(" items=").append(counts.itemEntities)
                            .append(" hostile=").append(counts.hostileMobs)
                            .append(" chunks=").append(chunks)
                            .append(" players=").append(players)
                            .append(" avgTickMs=").append(formatMillis(tickTimes.avgNanos))
                            .append(" maxTickMs=").append(formatMillis(tickTimes.maxNanos));
                }
            }
        } catch (RuntimeException exception) {
            LAST_VIRTUAL_WORLD_SAMPLE.set("error=" + exception.getClass().getSimpleName());
            return;
        }

        if (worlds > Stage0TelemetryConfig.topEntryLimit()) {
            worldDetails.append("; ...");
        }
        worldDetails.append(']');

        VIRTUAL_WORLD_SAMPLES.increment();
        updateMax(MAX_VIRTUAL_WORLD_TICK_NANOS, maxTickNanos);
        updateMax(MAX_VIRTUAL_WORLD_ITEM_ENTITIES, totalItems);
        updateMax(MAX_VIRTUAL_WORLD_HOSTILE_MOBS, totalHostile);
        updateMax(MAX_VIRTUAL_WORLD_LOADED_CHUNKS, totalChunks);
        LAST_VIRTUAL_WORLD_SAMPLE.set("worlds=" + worlds
                + " entities=" + totalEntities
                + " itemEntities=" + totalItems
                + " hostileMobs=" + totalHostile
                + " loadedChunks=" + totalChunks
                + " players=" + totalPlayers
                + " maxTickMs=" + formatMillis(maxTickNanos)
                + " details=" + worldDetails);
    }

    private static WorldCounts countWorld(ServerLevel world) {
        int entities = 0;
        int itemEntities = 0;
        int hostileMobs = 0;
        for (Entity entity : world.getAllEntities()) {
            entities++;
            if (entity instanceof ItemEntity) {
                itemEntities++;
            }
            if (entity instanceof Monster || entity.getType().getCategory() == MobCategory.MONSTER) {
                hostileMobs++;
            }
        }
        return new WorldCounts(entities, itemEntities, hostileMobs);
    }

    private static TickTimeSummary tickTimes(MinecraftServer server, ResourceKey<Level> dimension) {
        try {
            long[] tickTimes = ((AccessorMinecraftServer) server).getPerWorldTickTimes().get(dimension);
            if (tickTimes == null || tickTimes.length == 0) {
                return TickTimeSummary.EMPTY;
            }

            long total = 0L;
            long max = 0L;
            int count = 0;
            for (long tickTime : tickTimes) {
                if (tickTime <= 0L) {
                    continue;
                }
                total += tickTime;
                max = Math.max(max, tickTime);
                count++;
            }
            return count == 0 ? TickTimeSummary.EMPTY : new TickTimeSummary(total / count, max);
        } catch (RuntimeException exception) {
            return TickTimeSummary.EMPTY;
        }
    }

    private static String vaultId(Level level) {
        Optional<Vault> vault = ServerVaults.get(level);
        if (vault.isPresent() && vault.get().has(Vault.ID)) {
            return vault.get().get(Vault.ID).toString();
        }
        return "unknown";
    }

    private static String itemKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key == null ? stack.getDescriptionId() : key.toString();
    }

    private static String playerVaultKey(ServerPlayer player, String vaultId) {
        String playerName = player == null ? "unknown" : player.getName().getString();
        return playerName + "@" + vaultId;
    }

    private static String necklaceInventoryHash(ItemStack necklace) {
        if (necklace == null || necklace.isEmpty() || !necklace.hasTag()) {
            return "missing";
        }
        CompoundTag tag = necklace.getTag();
        if (tag == null || !tag.contains("Inventory")) {
            return "missing";
        }
        return Integer.toHexString(tag.getCompound("Inventory").hashCode());
    }

    private static void incrementTop(ConcurrentHashMap<String, LongAdder> map, String key) {
        String normalized = key == null || key.isBlank() ? "unknown" : key;
        LongAdder existing = map.get(normalized);
        if (existing != null) {
            existing.increment();
            return;
        }

        if (map.size() >= Stage0TelemetryConfig.topEntryLimit()) {
            normalized = "other";
        }
        map.computeIfAbsent(normalized, ignored -> new LongAdder()).increment();
    }

    private static String topSummary(ConcurrentHashMap<String, LongAdder> map) {
        if (map.isEmpty()) {
            return "{}";
        }

        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        int emitted = 0;
        for (Map.Entry<String, LongAdder> entry : map.entrySet()) {
            if (emitted >= Stage0TelemetryConfig.topEntryLimit()) {
                break;
            }
            if (!first) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue().sum());
            first = false;
            emitted++;
        }
        return builder.append('}').toString();
    }

    private static void maybeLogSlow(String area, long nanos) {
        if (!Stage0TelemetryConfig.logSlowEvents()) {
            return;
        }
        long threshold = Stage0TelemetryConfig.slowWarnNanos();
        if (threshold > 0L && nanos >= threshold) {
            writeTelemetry("stage0_slow area=" + area + " elapsedMs=" + formatMillis(nanos));
        }
    }

    private static String formatMillis(long nanos) {
        if (nanos <= 0L) {
            return "0.000";
        }
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0D);
    }

    private static void updateMax(AtomicLong target, long value) {
        long previous;
        do {
            previous = target.get();
            if (value <= previous) {
                return;
            }
        } while (!target.compareAndSet(previous, value));
    }

    private static synchronized void writeTelemetry(String message) {
        String line = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now())
                + " " + message
                + System.lineSeparator();

        try {
            Files.createDirectories(Stage0TelemetryConfig.telemetryLogPath().getParent());
            Files.writeString(
                    Stage0TelemetryConfig.telemetryLogPath(),
                    line,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            if (LOG_WRITE_FAILURE_REPORTED.compareAndSet(false, true)) {
                MasuCraftFixes.LOGGER.warn("[Stage0] Could not write telemetry log {}", Stage0TelemetryConfig.telemetryLogPath(), exception);
            }
        }
    }

    private static final class BurstCounter {
        private int tick = -1;
        private int count;
    }

    private static final class WoldsHandlerStart {
        private final long startNanos;
        private final boolean canceledAtStart;
        private final boolean removedAtStart;

        private WoldsHandlerStart(long startNanos, boolean canceledAtStart, boolean removedAtStart) {
            this.startNanos = startNanos;
            this.canceledAtStart = canceledAtStart;
            this.removedAtStart = removedAtStart;
        }
    }

    private static final class WorldCounts {
        private final int entities;
        private final int itemEntities;
        private final int hostileMobs;

        private WorldCounts(int entities, int itemEntities, int hostileMobs) {
            this.entities = entities;
            this.itemEntities = itemEntities;
            this.hostileMobs = hostileMobs;
        }
    }

    private static final class TickTimeSummary {
        private static final TickTimeSummary EMPTY = new TickTimeSummary(0L, 0L);

        private final long avgNanos;
        private final long maxNanos;

        private TickTimeSummary(long avgNanos, long maxNanos) {
            this.avgNanos = avgNanos;
            this.maxNanos = maxNanos;
        }
    }

    private static final class TimingStats {
        private final LongAdder count = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();
        private final AtomicLong maxNanos = new AtomicLong();

        private void record(long nanos) {
            this.count.increment();
            this.totalNanos.add(Math.max(0L, nanos));
            updateMax(this.maxNanos, Math.max(0L, nanos));
        }

        private void reset() {
            this.count.reset();
            this.totalNanos.reset();
            this.maxNanos.set(0L);
        }

        private String summary() {
            long countValue = this.count.sum();
            double avgMs = countValue == 0L ? 0.0D : (this.totalNanos.sum() / 1_000_000.0D) / countValue;
            return "{count=" + countValue
                    + ", avgMs=" + String.format(Locale.ROOT, "%.3f", avgMs)
                    + ", maxMs=" + formatMillis(this.maxNanos.get())
                    + "}";
        }
    }
}
