package com.masuary.masucraftfixes;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class CasinoCraftAuditLogger {
    private static final long ACTOR_TTL_MS = 30_000L;
    private static final Path AUDIT_LOG = Path.of("logs", "casinocraft-audit.log");
    private static final Map<String, Actor> LAST_ACTORS = new ConcurrentHashMap<>();
    private static final Map<String, TickSnapshot> TICK_SNAPSHOTS = new ConcurrentHashMap<>();

    private CasinoCraftAuditLogger() {
    }

    public static void logAdminAccess(Player player, Object menu, Object menuProvider) {
        if (player == null || player.level.isClientSide || menu == null) {
            return;
        }

        String menuClass = menu.getClass().getName();
        if (!isAdminMenu(menuClass)) {
            return;
        }

        Object board = getField(menuProvider, "board");
        BlockPos pos = blockPos(board);
        if (pos == null) {
            return;
        }

        String key = tableKey(player.level, pos);
        Actor actor = actor(player, "admin_menu_open");
        LAST_ACTORS.put(key, actor);

        writeAudit("admin_access",
                "player=" + quoted(actor.name)
                        + " uuid=" + actor.uuid
                        + " menu=" + simpleName(menuClass)
                        + " table=" + quoted(formatTable(player.level, pos))
                        + " storageToken=" + readIntField(board, "storageToken", -1)
                        + " storageReward=" + readIntField(board, "storageReward", -1)
                        + " ioSlot=" + quoted(stackName(slot(board, 2)))
                        + " keySlot=" + quoted(stackName(slot(board, 0)))
        );
    }

    public static void logSettingPacket(Supplier<NetworkEvent.Context> contextSupplier) {
        ServerPlayer player = sender(contextSupplier);
        if (player == null) {
            return;
        }

        int[] packetData = (int[]) getStaticField("mod.casinocraft.network.MessageSettingServer", "packetData");
        BlockPos pos = (BlockPos) getStaticField("mod.casinocraft.network.MessageSettingServer", "pos");
        if (packetData == null || packetData.length < 15 || pos == null) {
            return;
        }

        boolean tokenOut = packetData[12] == 1;
        boolean rewardOut = packetData[14] == 1;
        if (!tokenOut && !rewardOut) {
            return;
        }

        Object blockEntity = player.level.getBlockEntity(pos);
        Actor actor = actor(player, tokenOut && rewardOut ? "transfer_token_reward_out_request" : tokenOut ? "transfer_token_out_request" : "transfer_reward_out_request");
        LAST_ACTORS.put(tableKey(player.level, pos), actor);

        writeAudit("transfer_out_request",
                "player=" + quoted(actor.name)
                        + " uuid=" + actor.uuid
                        + " tokenOut=" + tokenOut
                        + " rewardOut=" + rewardOut
                        + " table=" + quoted(formatTable(player.level, pos))
                        + " storageToken=" + readIntField(blockEntity, "storageToken", -1)
                        + " storageReward=" + readIntField(blockEntity, "storageReward", -1)
                        + " ioSlot=" + quoted(stackName(slot(blockEntity, 2)))
        );
    }

    public static void logInventoryPacket(Supplier<NetworkEvent.Context> contextSupplier) {
        ServerPlayer player = sender(contextSupplier);
        if (player == null) {
            return;
        }

        BlockPos pos = (BlockPos) getStaticField("mod.casinocraft.network.MessageInventoryServer", "pos");
        if (pos == null) {
            return;
        }

        Object blockEntity = player.level.getBlockEntity(pos);
        int oldStorageToken = readIntField(blockEntity, "storageToken", -1);
        int oldStorageReward = readIntField(blockEntity, "storageReward", -1);
        int newStorageToken = readStaticInt("mod.casinocraft.network.MessageInventoryServer", "storageToken", -1);
        int newStorageReward = readStaticInt("mod.casinocraft.network.MessageInventoryServer", "storagePrize", -1);

        if (newStorageToken < oldStorageToken || newStorageReward < oldStorageReward) {
            Actor actor = actor(player, "inventory_storage_decrease_packet");
            LAST_ACTORS.put(tableKey(player.level, pos), actor);

            writeAudit("inventory_storage_decrease",
                    "player=" + quoted(actor.name)
                            + " uuid=" + actor.uuid
                            + " table=" + quoted(formatTable(player.level, pos))
                            + " storageToken=" + oldStorageToken + "->" + newStorageToken
                            + " storageReward=" + oldStorageReward + "->" + newStorageReward
                            + " ioSlotBefore=" + quoted(stackName(slot(blockEntity, 2)))
            );
        }
    }

    public static void logPlayerPacket(Supplier<NetworkEvent.Context> contextSupplier) {
        ServerPlayer player = sender(contextSupplier);
        if (player == null) {
            return;
        }

        ItemStack stack = copy((ItemStack) getStaticField("mod.casinocraft.network.MessagePlayerServer", "stack"));
        int amount = readStaticInt("mod.casinocraft.network.MessagePlayerServer", "amount", 0);
        BlockPos pos = currentMenuPos(player);
        Object blockEntity = pos == null ? null : player.level.getBlockEntity(pos);
        String event = amount < 0 ? "player_token_removed" : "player_token_added";

        if (pos != null) {
            LAST_ACTORS.put(tableKey(player.level, pos), actor(player, event));
        }

        writeAudit(event,
                "player=" + quoted(player.getGameProfile().getName())
                        + " uuid=" + player.getUUID()
                        + " amount=" + amount
                        + " item=" + quoted(stackName(stack))
                        + " menu=" + quoted(currentMenuName(player))
                        + " table=" + quoted(formatNullableTable(player.level, pos))
                        + " storageToken=" + readIntField(blockEntity, "storageToken", -1)
                        + " storageReward=" + readIntField(blockEntity, "storageReward", -1)
        );
    }

    public static void logStartPacket(Supplier<NetworkEvent.Context> contextSupplier) {
        ServerPlayer player = sender(contextSupplier);
        if (player == null) {
            return;
        }

        String name = String.valueOf(getStaticField("mod.casinocraft.network.MessageStartServer", "name"));
        int seed = readStaticInt("mod.casinocraft.network.MessageStartServer", "seed", -2);
        BlockPos pos = (BlockPos) getStaticField("mod.casinocraft.network.MessageStartServer", "pos");
        Object blockEntity = pos == null ? null : player.level.getBlockEntity(pos);
        Actor actor = actor(player, seed > -1 ? "game_start" : "game_join");
        if (pos != null) {
            LAST_ACTORS.put(tableKey(player.level, pos), actor);
        }

        writeAudit(seed > -1 ? "game_start" : "game_join",
                "player=" + quoted(actor.name)
                        + " uuid=" + actor.uuid
                        + " packetName=" + quoted(name)
                        + " seed=" + seed
                        + " table=" + quoted(formatNullableTable(player.level, pos))
                        + " storageToken=" + readIntField(blockEntity, "storageToken", -1)
                        + " storageReward=" + readIntField(blockEntity, "storageReward", -1)
                        + " turnstate=" + readLogicInt(blockEntity, "turnstate", -1)
                        + " currentPlayers=" + quoted(currentPlayers(blockEntity))
                        + " rewards=" + quoted(rewards(blockEntity))
        );
    }

    public static void logStatePacket(Supplier<NetworkEvent.Context> contextSupplier) {
        ServerPlayer player = sender(contextSupplier);
        if (player == null) {
            return;
        }

        boolean system = readStaticBoolean("mod.casinocraft.network.MessageStateServer", "system", false);
        int state = readStaticInt("mod.casinocraft.network.MessageStateServer", "state", Integer.MIN_VALUE);
        BlockPos pos = (BlockPos) getStaticField("mod.casinocraft.network.MessageStateServer", "pos");
        Object blockEntity = pos == null ? null : player.level.getBlockEntity(pos);
        Actor actor = actor(player, system ? "system_state" : "game_action");
        if (pos != null) {
            LAST_ACTORS.put(tableKey(player.level, pos), actor);
        }

        writeAudit(system ? "system_state" : "game_action",
                "player=" + quoted(actor.name)
                        + " uuid=" + actor.uuid
                        + " system=" + system
                        + " state=" + state
                        + " action=" + quoted(actionName(system, state))
                        + " table=" + quoted(formatNullableTable(player.level, pos))
                        + " storageToken=" + readIntField(blockEntity, "storageToken", -1)
                        + " storageReward=" + readIntField(blockEntity, "storageReward", -1)
                        + " turnstate=" + readLogicInt(blockEntity, "turnstate", -1)
                        + " currentPlayers=" + quoted(currentPlayers(blockEntity))
                        + " rewards=" + quoted(rewards(blockEntity))
        );
    }

    public static void logScorePacket(Supplier<NetworkEvent.Context> contextSupplier) {
        ServerPlayer player = sender(contextSupplier);
        if (player == null) {
            return;
        }

        String names = String.valueOf(getStaticField("mod.casinocraft.network.MessageScoreServer", "names"));
        int points = readStaticInt("mod.casinocraft.network.MessageScoreServer", "points", 0);
        BlockPos pos = (BlockPos) getStaticField("mod.casinocraft.network.MessageScoreServer", "pos");
        Object blockEntity = pos == null ? null : player.level.getBlockEntity(pos);
        Actor actor = actor(player, "score_submit");
        if (pos != null) {
            LAST_ACTORS.put(tableKey(player.level, pos), actor);
        }

        writeAudit("score_submit",
                "player=" + quoted(actor.name)
                        + " uuid=" + actor.uuid
                        + " packetName=" + quoted(names)
                        + " points=" + points
                        + " table=" + quoted(formatNullableTable(player.level, pos))
                        + " storageToken=" + readIntField(blockEntity, "storageToken", -1)
                        + " storageReward=" + readIntField(blockEntity, "storageReward", -1)
                        + " turnstate=" + readLogicInt(blockEntity, "turnstate", -1)
                        + " currentPlayers=" + quoted(currentPlayers(blockEntity))
                        + " rewards=" + quoted(rewards(blockEntity))
        );
    }

    public static void beforeMachineTick(Level level, BlockPos pos, Object blockEntity) {
        if (level == null || level.isClientSide || pos == null) {
            return;
        }

        TICK_SNAPSHOTS.put(tableKey(level, pos), new TickSnapshot(
                readIntField(blockEntity, "storageToken", -1),
                readIntField(blockEntity, "storageReward", -1),
                copy(slot(blockEntity, 2))
        ));
    }

    public static void afterMachineTick(Level level, BlockPos pos, Object blockEntity) {
        if (level == null || level.isClientSide || pos == null) {
            return;
        }

        String key = tableKey(level, pos);
        TickSnapshot before = TICK_SNAPSHOTS.remove(key);
        if (before == null) {
            return;
        }

        int afterStorageToken = readIntField(blockEntity, "storageToken", -1);
        int afterStorageReward = readIntField(blockEntity, "storageReward", -1);
        ItemStack afterIoSlot = slot(blockEntity, 2);
        int tokenDelta = before.storageToken - afterStorageToken;
        int rewardDelta = before.storageReward - afterStorageReward;

        if (tokenDelta <= 0 && rewardDelta <= 0) {
            return;
        }

        Actor actor = recentActor(key);
        writeAudit("withdrawal_executed",
                "actor=" + quoted(actor.name)
                        + " uuid=" + actor.uuid
                        + " action=" + actor.action
                        + " table=" + quoted(formatTable(level, pos))
                        + " tokenDelta=" + Math.max(tokenDelta, 0)
                        + " rewardDelta=" + Math.max(rewardDelta, 0)
                        + " storageToken=" + before.storageToken + "->" + afterStorageToken
                        + " storageReward=" + before.storageReward + "->" + afterStorageReward
                        + " ioSlot=" + quoted(stackName(before.ioSlot)) + "->" + quoted(stackName(afterIoSlot))
        );
    }

    private static boolean isAdminMenu(String menuClass) {
        return menuClass.equals("mod.casinocraft.menu.block.MenuCardTable")
                || menuClass.equals("mod.casinocraft.menu.block.MenuArcade")
                || menuClass.equals("mod.casinocraft.menu.block.MenuSlotMachine");
    }

    private static ServerPlayer sender(Supplier<NetworkEvent.Context> contextSupplier) {
        if (contextSupplier == null) {
            return null;
        }

        NetworkEvent.Context context = contextSupplier.get();
        if (context == null) {
            return null;
        }

        return context.getSender();
    }

    private static Actor actor(Player player, String action) {
        String name = player.getGameProfile().getName();
        UUID uuid = player.getUUID();
        return new Actor(name, uuid, action, System.currentTimeMillis());
    }

    private static Actor recentActor(String key) {
        Actor actor = LAST_ACTORS.get(key);
        if (actor == null || System.currentTimeMillis() - actor.timeMs > ACTOR_TTL_MS) {
            return new Actor("unknown", null, "none_recent", 0L);
        }

        return actor;
    }

    private static String tableKey(Level level, BlockPos pos) {
        return level.dimension().location() + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String formatTable(Level level, BlockPos pos) {
        return level.dimension().location() + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static String formatNullableTable(Level level, BlockPos pos) {
        return pos == null ? "unknown" : formatTable(level, pos);
    }

    private static BlockPos blockPos(Object object) {
        if (object instanceof BlockEntity blockEntity) {
            return blockEntity.getBlockPos();
        }

        return null;
    }

    private static ItemStack slot(Object object, int slot) {
        if (object instanceof Container container && slot >= 0 && slot < container.getContainerSize()) {
            return container.getItem(slot);
        }

        return ItemStack.EMPTY;
    }

    private static ItemStack copy(ItemStack stack) {
        return stack == null ? ItemStack.EMPTY : stack.copy();
    }

    private static String stackName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String name = stack.getHoverName().getString();
        return stack.getCount() + "x " + itemId + " \"" + name + "\"";
    }

    private static String simpleName(String className) {
        int index = className.lastIndexOf('.');
        return index == -1 ? className : className.substring(index + 1);
    }

    private static String currentMenuName(ServerPlayer player) {
        AbstractContainerMenu menu = player.containerMenu;
        return menu == null ? "none" : menu.getClass().getName();
    }

    private static BlockPos currentMenuPos(ServerPlayer player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || !menu.getClass().getName().startsWith("mod.casinocraft.menu.")) {
            return null;
        }

        Object value = invoke(menu, "pos");
        return value instanceof BlockPos blockPos ? blockPos : null;
    }

    private static String actionName(boolean system, int state) {
        if (!system) {
            return switch (state) {
                case 0 -> "hit";
                case 1 -> "stand";
                case 2 -> "split";
                case 3 -> "double";
                default -> "game_action_" + state;
            };
        }

        if (state == -1) {
            return "toggle_pause";
        }
        if (state == -2) {
            return "reset_game";
        }
        if (state == -3) {
            return "reset_players";
        }
        if (state >= 10) {
            return "clear_reward_" + (state - 10);
        }
        return "set_turnstate_" + state;
    }

    private static int readLogicInt(Object blockEntity, String fieldName, int fallback) {
        Object logic = getField(blockEntity, "logic");
        return readIntField(logic, fieldName, fallback);
    }

    private static String currentPlayers(Object blockEntity) {
        Object logic = getField(blockEntity, "logic");
        Object value = getField(logic, "currentPlayer");
        if (!(value instanceof String[] players)) {
            return "unknown";
        }

        return String.join(",", players);
    }

    private static String rewards(Object blockEntity) {
        Object logic = getField(blockEntity, "logic");
        Object value = getField(logic, "reward");
        if (!(value instanceof int[] rewards)) {
            return "unknown";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < rewards.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(rewards[i]);
        }
        return builder.toString();
    }

    private static synchronized void writeAudit(String event, String details) {
        String line = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now())
                + " event=" + event
                + " " + details
                + System.lineSeparator();

        try {
            Files.createDirectories(AUDIT_LOG.getParent());
            Files.writeString(
                    AUDIT_LOG,
                    line,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            MasuCraftFixes.LOGGER.warn("[CasinoCraftAudit] Could not write audit log {}", AUDIT_LOG, exception);
        }
    }

    private static String quoted(String value) {
        if (value == null) {
            return "\"null\"";
        }

        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n") + "\"";
    }

    private static int readStaticInt(String className, String fieldName, int fallback) {
        Object value = getStaticField(className, fieldName);
        return value instanceof Integer integer ? integer : fallback;
    }

    private static boolean readStaticBoolean(String className, String fieldName, boolean fallback) {
        Object value = getStaticField(className, fieldName);
        return value instanceof Boolean bool ? bool : fallback;
    }

    private static int readIntField(Object target, String fieldName, int fallback) {
        Object value = getField(target, fieldName);
        return value instanceof Integer integer ? integer : fallback;
    }

    private static Object getField(Object target, String fieldName) {
        if (target == null) {
            return null;
        }

        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException exception) {
                MasuCraftFixes.LOGGER.warn("[CasinoCraftAudit] Could not read field {} from {}", fieldName, target.getClass().getName(), exception);
                return null;
            }
        }

        return null;
    }

    private static Object invoke(Object target, String methodName) {
        if (target == null) {
            return null;
        }

        Class<?> type = target.getClass();
        while (type != null) {
            try {
                var method = type.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException exception) {
                MasuCraftFixes.LOGGER.warn("[CasinoCraftAudit] Could not invoke method {} on {}", methodName, target.getClass().getName(), exception);
                return null;
            }
        }

        return null;
    }

    private static Object getStaticField(String className, String fieldName) {
        try {
            Class<?> type = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            Field field = type.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(null);
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException exception) {
            MasuCraftFixes.LOGGER.warn("[CasinoCraftAudit] Could not read static field {}.{}", className, fieldName, exception);
            return null;
        }
    }

    private record Actor(String name, UUID uuid, String action, long timeMs) {
    }

    private record TickSnapshot(int storageToken, int storageReward, ItemStack ioSlot) {
    }
}
