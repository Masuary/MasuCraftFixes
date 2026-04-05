package com.masuary.masucraftfixes;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import iskallia.vault.world.data.DiscoveredModelsData;
import iskallia.vault.world.data.PlayerPatreonDisplayData;
import iskallia.vault.www.patreon.PatreonManager;
import iskallia.vault.www.patreon.PatreonTier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PatreonPerksHandler {

    private static final Map<UUID, PatreonTier> lpGrantedTiers = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<ResourceLocation>> lpGrantedModels = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> pendingUpdates = new ConcurrentHashMap<>();
    private static final int UPDATE_DELAY_TICKS = 40;

    private static final String[] PERMISSION_NODES = {
            "masucraftfixes.patron.legend",
            "masucraftfixes.patron.champion",
            "masucraftfixes.patron.goblin",
            "masucraftfixes.patron.cheeser",
            "masucraftfixes.patron.dweller"
    };

    private static final PatreonTier[] PERMISSION_TIERS = {
            PatreonTier.LEGEND,
            PatreonTier.CHAMPION,
            PatreonTier.GOBLIN,
            PatreonTier.CHEESER,
            PatreonTier.DWELLER
    };

    private static Field tierCacheField;
    private static boolean fieldInitialized;

    public static void scheduleUpdate(UUID playerUuid) {
        pendingUpdates.put(playerUuid, UPDATE_DELAY_TICKS);
    }

    public static void tick() {
        if (pendingUpdates.isEmpty()) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (var iterator = pendingUpdates.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            int remaining = entry.getValue() - 1;

            if (remaining <= 0) {
                iterator.remove();
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player != null) {
                    updatePlayerPatronStatus(player);
                }
            } else {
                entry.setValue(remaining);
            }
        }
    }

    public static void updatePlayerPatronStatus(ServerPlayer player) {
        UUID uuid = player.getUUID();

        PatreonTier effectiveTier = getEffectiveLpTier(player);

        if (effectiveTier != null) {
            grantPatronPerks(player, effectiveTier);
        } else if (lpGrantedTiers.containsKey(uuid)) {
            revokePatronPerks(player);
        }
    }

    private static PatreonTier getEffectiveLpTier(ServerPlayer player) {
        for (int i = 0; i < PERMISSION_NODES.length; i++) {
            if (LuckPermsInt.hasPermission(player, PERMISSION_NODES[i])) {
                return PERMISSION_TIERS[i];
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static void grantPatronPerks(ServerPlayer player, PatreonTier lpTier) {
        UUID uuid = player.getUUID();

        Map<UUID, List<PatreonTier>> cache = getTierCache();
        if (cache == null) return;

        PatreonManager manager = PatreonManager.getInstance();

        synchronized (manager) {
            List<PatreonTier> existingTiers = cache.get(uuid);
            boolean isRealPatron = existingTiers != null && !existingTiers.isEmpty() && !lpGrantedTiers.containsKey(uuid);

            if (isRealPatron) {
                PatreonTier existingHighest = PatreonTier.getHighestTier(existingTiers);
                if (existingHighest != null && existingHighest.isThisEqualOrHigherThan(lpTier)) {
                    return;
                }
            }

            List<PatreonTier> newTiers = lpTier.getCurrentAndPreviousTiers();
            cache.put(uuid, newTiers);
            lpGrantedTiers.put(uuid, lpTier);

            MasuCraftFixes.LOGGER.info("Granted patron tier {} to {} via LuckPerms", lpTier.getName(), player.getName().getString());

            var server = player.getServer();
            if (server != null) {
                PlayerPatreonDisplayData displayData = PlayerPatreonDisplayData.get(server);
                PlayerPatreonDisplayData.PatreonDisplay existing = displayData.getDisplaySettings(uuid);

                if (existing == null || existing.getDisplayTier() == null) {
                    displayData.setDisplaySettings(uuid, new PlayerPatreonDisplayData.PatreonDisplay(lpTier, true, true));
                } else {
                    if (!existing.getDisplayTier().isThisEqualOrHigherThan(lpTier)) {
                        existing.setDisplayTier(lpTier);
                        displayData.setDisplaySettings(uuid, existing);
                    }
                }

                discoverTierModels(server, uuid, newTiers);
            }

        }
    }

    @SuppressWarnings("unchecked")
    private static void revokePatronPerks(ServerPlayer player) {
        UUID uuid = player.getUUID();

        Map<UUID, List<PatreonTier>> cache = getTierCache();
        if (cache == null) return;

        PatreonManager manager = PatreonManager.getInstance();

        synchronized (manager) {
            cache.remove(uuid);
            lpGrantedTiers.remove(uuid);

            MasuCraftFixes.LOGGER.info("Revoked patron perks from {} (LuckPerms permission removed)", player.getName().getString());

            var server = player.getServer();
            if (server != null) {
                PlayerPatreonDisplayData displayData = PlayerPatreonDisplayData.get(server);
                displayData.setDisplaySettings(uuid, new PlayerPatreonDisplayData.PatreonDisplay(null, false, false));

                revokeTierModels(server, uuid);
            }
        }
    }

    public static void onPlayerLeave(UUID uuid) {
        pendingUpdates.remove(uuid);
        lpGrantedModels.remove(uuid);

        if (lpGrantedTiers.remove(uuid) != null) {
            Map<UUID, List<PatreonTier>> cache = getTierCache();
            if (cache != null) {
                synchronized (PatreonManager.getInstance()) {
                    cache.remove(uuid);
                }
            }
        }
    }

    private static void discoverTierModels(net.minecraft.server.MinecraftServer server, UUID uuid, List<PatreonTier> tiers) {
        DiscoveredModelsData modelsData = DiscoveredModelsData.get(server);
        Set<ResourceLocation> granted = new HashSet<>();

        for (PatreonTier tier : tiers) {
            for (ResourceLocation modelId : tier.getModelRewards()) {
                if (modelsData.discoverModel(uuid, modelId)) {
                    granted.add(modelId);
                }
            }
        }

        if (!granted.isEmpty()) {
            Set<ResourceLocation> existing = lpGrantedModels.computeIfAbsent(uuid, k -> new HashSet<>());
            existing.addAll(granted);
            MasuCraftFixes.LOGGER.info("Discovered {} patron models for {}", granted.size(), uuid);
        }
    }

    @SuppressWarnings("unchecked")
    private static void revokeTierModels(net.minecraft.server.MinecraftServer server, UUID uuid) {
        Set<ResourceLocation> granted = lpGrantedModels.remove(uuid);
        if (granted == null || granted.isEmpty()) return;

        try {
            DiscoveredModelsData modelsData = DiscoveredModelsData.get(server);
            Field modelsField = DiscoveredModelsData.class.getDeclaredField("discoveredModels");
            modelsField.setAccessible(true);
            Map<UUID, Map<ResourceLocation, Integer>> discoveredModels =
                    (Map<UUID, Map<ResourceLocation, Integer>>) modelsField.get(modelsData);

            Map<ResourceLocation, Integer> playerModels = discoveredModels.get(uuid);
            if (playerModels != null) {
                for (ResourceLocation modelId : granted) {
                    playerModels.remove(modelId);
                }
                modelsData.setDirty();
                MasuCraftFixes.LOGGER.info("Revoked {} patron models from {}", granted.size(), uuid);
            }
        } catch (Exception e) {
            MasuCraftFixes.LOGGER.error("Failed to revoke patron models", e);
        }
    }

    public static PatreonTier getLpGrantedTier(UUID uuid) {
        return lpGrantedTiers.get(uuid);
    }

    public static void onPermissionChange(UUID uuid) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                updatePlayerPatronStatus(player);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, List<PatreonTier>> getTierCache() {
        try {
            if (!fieldInitialized) {
                tierCacheField = PatreonManager.class.getDeclaredField("loadedTierCache");
                tierCacheField.setAccessible(true);
                fieldInitialized = true;
            }
            return (Map<UUID, List<PatreonTier>>) tierCacheField.get(PatreonManager.getInstance());
        } catch (Exception e) {
            MasuCraftFixes.LOGGER.error("Failed to access PatreonManager tier cache", e);
            return null;
        }
    }

    public static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("donatordisplay")
                        .then(Commands.literal("emblem")
                                .then(Commands.argument("toggle", StringArgumentType.word())
                                        .suggests((ctx, builder) -> builder.suggest("on").suggest("off").buildFuture())
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            String toggle = StringArgumentType.getString(ctx, "toggle");
                                            return handleDisplayToggle(player, "emblem", toggle.equalsIgnoreCase("on"));
                                        })))
                        .then(Commands.literal("colour")
                                .then(Commands.argument("toggle", StringArgumentType.word())
                                        .suggests((ctx, builder) -> builder.suggest("on").suggest("off").buildFuture())
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            String toggle = StringArgumentType.getString(ctx, "toggle");
                                            return handleDisplayToggle(player, "colour", toggle.equalsIgnoreCase("on"));
                                        })))
                        .then(Commands.literal("tier")
                                .then(Commands.argument("tier", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (PatreonTier t : PatreonTier.values()) {
                                                builder.suggest(t.name().toLowerCase());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            String tierName = StringArgumentType.getString(ctx, "tier");
                                            return handleTierChange(player, tierName);
                                        })))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            return showCurrentSettings(player);
                        })
        );
    }

    private static int showCurrentSettings(ServerPlayer player) {
        PatreonTier grantedTier = getLpGrantedTier(player.getUUID());
        if (grantedTier == null) {
            player.sendMessage(new TextComponent("You don't have donator perks.").withStyle(ChatFormatting.RED), Util.NIL_UUID);
            return 0;
        }

        PlayerPatreonDisplayData displayData = PlayerPatreonDisplayData.get(player.getServer());
        PlayerPatreonDisplayData.PatreonDisplay display = displayData.getDisplaySettings(player.getUUID());

        player.sendMessage(new TextComponent("Donator Display Settings").withStyle(ChatFormatting.GOLD), Util.NIL_UUID);
        player.sendMessage(new TextComponent("  Granted Tier: ").withStyle(ChatFormatting.GRAY)
                .append(new TextComponent(grantedTier.getName()).withStyle(ChatFormatting.WHITE)), Util.NIL_UUID);

        if (display != null) {
            String displayTierName = display.getDisplayTier() != null ? display.getDisplayTier().getName() : "None";
            player.sendMessage(new TextComponent("  Display Tier: ").withStyle(ChatFormatting.GRAY)
                    .append(new TextComponent(displayTierName).withStyle(ChatFormatting.WHITE)), Util.NIL_UUID);
            player.sendMessage(new TextComponent("  Emblem: ").withStyle(ChatFormatting.GRAY)
                    .append(new TextComponent(display.isEmblemEnabled() ? "On" : "Off").withStyle(ChatFormatting.WHITE)), Util.NIL_UUID);
            player.sendMessage(new TextComponent("  Colour: ").withStyle(ChatFormatting.GRAY)
                    .append(new TextComponent(display.isColourEnabled() ? "On" : "Off").withStyle(ChatFormatting.WHITE)), Util.NIL_UUID);
        }
        return 1;
    }

    private static int handleDisplayToggle(ServerPlayer player, String setting, boolean enabled) {
        PatreonTier grantedTier = getLpGrantedTier(player.getUUID());
        if (grantedTier == null) {
            player.sendMessage(new TextComponent("You don't have donator perks.").withStyle(ChatFormatting.RED), Util.NIL_UUID);
            return 0;
        }

        PlayerPatreonDisplayData displayData = PlayerPatreonDisplayData.get(player.getServer());
        PlayerPatreonDisplayData.PatreonDisplay display = displayData.getDisplaySettings(player.getUUID());
        if (display == null) {
            display = new PlayerPatreonDisplayData.PatreonDisplay(grantedTier, true, true);
        }

        if (setting.equals("emblem")) {
            display.enableEmblem(enabled);
        } else {
            display.enableColour(enabled);
        }

        displayData.setDisplaySettings(player.getUUID(), display);

        player.sendMessage(new TextComponent("Donator " + setting + " " + (enabled ? "enabled" : "disabled") + ".").withStyle(ChatFormatting.GREEN), Util.NIL_UUID);
        return 1;
    }

    private static int handleTierChange(ServerPlayer player, String tierName) {
        PatreonTier grantedTier = getLpGrantedTier(player.getUUID());
        if (grantedTier == null) {
            player.sendMessage(new TextComponent("You don't have donator perks.").withStyle(ChatFormatting.RED), Util.NIL_UUID);
            return 0;
        }

        PatreonTier requestedTier = PatreonTier.fromName(tierName);
        if (requestedTier == null) {
            for (PatreonTier t : PatreonTier.values()) {
                if (t.name().equalsIgnoreCase(tierName)) {
                    requestedTier = t;
                    break;
                }
            }
        }

        if (requestedTier == null) {
            player.sendMessage(new TextComponent("Unknown tier: " + tierName).withStyle(ChatFormatting.RED), Util.NIL_UUID);
            return 0;
        }

        if (!grantedTier.isThisEqualOrHigherThan(requestedTier)) {
            player.sendMessage(new TextComponent("You can only display tiers up to " + grantedTier.getName() + ".").withStyle(ChatFormatting.RED), Util.NIL_UUID);
            return 0;
        }

        PlayerPatreonDisplayData displayData = PlayerPatreonDisplayData.get(player.getServer());
        PlayerPatreonDisplayData.PatreonDisplay display = displayData.getDisplaySettings(player.getUUID());
        if (display == null) {
            display = new PlayerPatreonDisplayData.PatreonDisplay(requestedTier, true, true);
        } else {
            display.setDisplayTier(requestedTier);
        }

        displayData.setDisplaySettings(player.getUUID(), display);

        player.sendMessage(new TextComponent("Display tier set to " + requestedTier.getName() + ".").withStyle(ChatFormatting.GREEN), Util.NIL_UUID);
        return 1;
    }
}
