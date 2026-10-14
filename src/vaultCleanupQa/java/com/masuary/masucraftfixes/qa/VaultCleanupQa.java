package com.masuary.masucraftfixes.qa;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.netty.channel.embedded.EmbeddedChannel;
import iskallia.vault.world.data.ServerVaults;
import iskallia.vault.world.data.VirtualWorlds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Mod("masucraftfixes_vaultqa")
public final class VaultCleanupQa {
    private static final Logger LOGGER = LogManager.getLogger();

    public VaultCleanupQa() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("mcfqa").requires(source -> source.hasPermission(4))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("login").then(Commands.argument("uuid", StringArgumentType.word())
                        .executes(context -> login(context.getSource(), UUID.fromString(StringArgumentType.getString(context, "uuid")))))));
    }

    private int status(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        long registered = VirtualWorlds.getAll().stream().filter(world -> world.dimension().location().getPath().startsWith("vault_")).count();
        long markedForDeletion = VirtualWorlds.getAll().stream().filter(world -> world.isMarkedForDeletion()).count();
        long folders;
        Path vaultFolders = server.getWorldPath(LevelResource.ROOT).resolve("dimensions").resolve("the_vault");
        try (var entries = Files.list(vaultFolders)) {
            folders = entries.filter(path -> path.getFileName().toString().startsWith("vault_")).count();
        } catch (IOException exception) {
            folders = -1;
        }
        report(source, "MCF-QA status: registered vault worlds=" + registered + ", marked for deletion=" + markedForDeletion
                + ", vault_ folders=" + folders + ", running vaults=" + ServerVaults.getAll().size());
        return 1;
    }

    private int login(CommandSourceStack source, UUID playerUuid) {
        MinecraftServer server = source.getServer();
        String name = "McfQa" + playerUuid.toString().substring(0, 6);
        try {
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
            Class<?> permissions = Class.forName("net.luckperms.api.LuckPerms");
            Class<?> users = Class.forName("net.luckperms.api.model.user.UserManager");
            Object manager = permissions.getMethod("getUserManager").invoke(provider.getMethod("get").invoke(null));
            var loaded = (CompletableFuture<?>) users.getMethod("loadUser", UUID.class, String.class).invoke(manager, playerUuid, name);
            loaded.orTimeout(10, TimeUnit.SECONDS).whenComplete((user, failure) -> server.execute(() -> {
                if (failure != null) {
                    report(source, "MCF-QA LuckPerms preload failed for " + playerUuid + ": " + failure);
                    return;
                }
                place(source, playerUuid, name);
            }));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("MCF-QA requires the copied pack's LuckPerms API", exception);
        }
        return 1;
    }

    private void place(CommandSourceStack source, UUID playerUuid, String name) {
        MinecraftServer server = source.getServer();
        ServerPlayer player = new ServerPlayer(server, server.overworld(), new GameProfile(playerUuid, name));
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        connection.setProtocol(ConnectionProtocol.PLAY);
        try {
            server.getPlayerList().placeNewPlayer(connection, player);
        } catch (RuntimeException exception) {
            LOGGER.error("MCF-QA placement threw", exception);
        }
        report(source, "MCF-QA login " + playerUuid + ": placed in " + player.level.dimension().location()
                + " at " + player.blockPosition() + " (world spawn " + server.overworld().getSharedSpawnPos() + ")");
        if (server.getPlayerList().getPlayer(playerUuid) != null) {
            server.getPlayerList().remove(player);
        }
    }

    private static void report(CommandSourceStack source, String message) {
        LOGGER.info(message);
        source.sendSuccess(new TextComponent(message), false);
    }
}
