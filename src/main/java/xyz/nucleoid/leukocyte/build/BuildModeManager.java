package xyz.nucleoid.leukocyte.build;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import xyz.nucleoid.leukocyte.Leukocyte;

import java.util.*;

public final class BuildModeManager {
    private static final Map<UUID, ActiveBuilder> activeBuilders = new HashMap<>();
    private static int tickCounter = 0;

    public record ActiveBuilder(
        UUID uuid,
        BuildArea buildArea,
        BlockPos returnPos,
        RegistryKey<World> returnWorld,
        GameMode savedGameMode,
        List<String> excludedAuthorityKeys
    ) {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(BuildModeManager::onServerTick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            BuildModeManager.onPlayerJoin(handler.player);
        });
    }

    private static void onServerTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter % 60 != 0) return;

        for (var entry : activeBuilders.entrySet()) {
            var player = server.getPlayerManager().getPlayer(entry.getKey());
            var builder = entry.getValue();

            if (player == null) continue;

            var currentWorld = player.getWorld().getRegistryKey();
            var expectedWorld = builder.buildArea().dimension();

            if (!currentWorld.equals(expectedWorld) || !builder.buildArea().contains(player.getBlockPos())) {
                var targetWorld = server.getWorld(expectedWorld);
                if (targetWorld != null) {
                    var center = builder.buildArea().center();
                    player.teleport(targetWorld, center.getX() + 0.5, center.getY(), center.getZ() + 0.5,
                        player.getYaw(), player.getPitch());
                    player.sendMessage(Text.literal("§cYou left the build area! Teleported back."), true);
                }
            }
        }
    }

    public static boolean enterBuildMode(ServerPlayerEntity player, BuildArea area) {
        if (activeBuilders.containsKey(player.getUuid())) {
            player.sendMessage(Text.literal("§cYou are already in build mode!"), false);
            return false;
        }

        var build = LeukocyteBuild.get(player.getServer().getOverworld());
        String playerName = player.getName().getString();

        if (build.getGlobalBlockedPlayers().contains(playerName)) {
            player.sendMessage(Text.literal("§cYou are blocked from using build mode!"), false);
            return false;
        }

        if (area.whitelistEnabled() && !area.whitelist().contains(playerName)) {
            player.sendMessage(Text.literal("§cYou are not in the whitelist for this build area!"), false);
            return false;
        }

        var returnPos = player.getBlockPos().mutableCopy();
        var returnWorld = player.getWorld().getRegistryKey();
        var savedGameMode = player.interactionManager.getGameMode();

        var excludedKeys = new ArrayList<String>();
        var leukocyte = Leukocyte.get(player.getServer());
        for (var authority : leukocyte.getAuthorities()) {
            if (authority.getExclusions().addPlayerByUUID(player.getUuid())) {
                excludedKeys.add(authority.getKey());
            }
        }

        var builder = new ActiveBuilder(player.getUuid(), area, returnPos, returnWorld, savedGameMode, excludedKeys);
        activeBuilders.put(player.getUuid(), builder);

        saveBuilder(player.getServer(), builder);

        player.changeGameMode(GameMode.CREATIVE);

        xyz.nucleoid.leukocyte.network.ServerBuildPacketHandler.sendBuildData(player);
        xyz.nucleoid.leukocyte.network.ServerBuildPacketHandler.sendBuildModeStatus(player, true);

        var center = area.center();
        var targetWorld = player.getServer().getWorld(area.dimension());
        if (targetWorld != null) {
            player.teleport(targetWorld, center.getX() + 0.5, center.getY(), center.getZ() + 0.5,
                player.getYaw(), player.getPitch());
        }

        player.sendMessage(Text.literal("§aBuild mode enabled!"), false);
        player.sendMessage(Text.literal("§7Area: §f" + area.name()), false);
        player.sendMessage(Text.literal("§7Type §f/exit §7to leave."), false);

        return true;
    }

    public static boolean exitBuildMode(ServerPlayerEntity player) {
        return exitBuildModeByUUID(player.getServer(), player.getUuid());
    }

    private static boolean exitBuildModeByUUID(MinecraftServer server, UUID uuid) {
        var builder = activeBuilders.remove(uuid);
        if (builder == null) return false;

        removeBuilder(server, uuid);

        var player = server.getPlayerManager().getPlayer(uuid);
        if (player != null) {
            var leukocyte = Leukocyte.get(server);
            for (String key : builder.excludedAuthorityKeys()) {
                var authority = leukocyte.getAuthorityByKey(key);
                if (authority != null) {
                    authority.getExclusions().removePlayerByUUID(uuid);
                }
            }

            player.getInventory().clear();
            player.changeGameMode(builder.savedGameMode());
            xyz.nucleoid.leukocyte.network.ServerBuildPacketHandler.sendBuildModeStatus(player, false);

            var returnWorld = server.getWorld(builder.returnWorld());
            if (returnWorld != null) {
                player.teleport(returnWorld,
                    builder.returnPos().getX() + 0.5,
                    builder.returnPos().getY(),
                    builder.returnPos().getZ() + 0.5,
                    player.getYaw(), player.getPitch());
            }

            player.sendMessage(Text.literal("§aBuild mode disabled. Inventory cleared."), false);
        }

        return true;
    }

    public static void onPlayerJoin(ServerPlayerEntity player) {
        var overworld = player.getServer().getOverworld();
        var state = BuildModeState.get(overworld);
        var saved = state.getBuilder(player.getUuid());
        if (saved == null) return;

        var builder = new ActiveBuilder(
            saved.uuid(),
            saved.buildArea(),
            saved.returnPos(),
            saved.returnWorld(),
            saved.savedGameMode(),
            saved.excludedAuthorityKeys()
        );
        activeBuilders.put(player.getUuid(), builder);

        player.changeGameMode(GameMode.CREATIVE);
        xyz.nucleoid.leukocyte.network.ServerBuildPacketHandler.sendBuildData(player);
        xyz.nucleoid.leukocyte.network.ServerBuildPacketHandler.sendBuildModeStatus(player, true);

        var center = builder.buildArea().center();
        var targetWorld = player.getServer().getWorld(builder.buildArea().dimension());
        if (targetWorld != null) {
            player.teleport(targetWorld, center.getX() + 0.5, center.getY(), center.getZ() + 0.5,
                player.getYaw(), player.getPitch());
        }

        player.sendMessage(Text.literal("§aBuild mode restored!"), false);
        player.sendMessage(Text.literal("§7Area: §f" + builder.buildArea().name()), false);
        player.sendMessage(Text.literal("§7Type §f/exit §7to leave."), false);
    }

    private static void saveBuilder(MinecraftServer server, ActiveBuilder builder) {
        var overworld = server.getOverworld();
        var state = BuildModeState.get(overworld);
        state.addBuilder(builder);
    }

    private static void removeBuilder(MinecraftServer server, UUID uuid) {
        var overworld = server.getOverworld();
        var state = BuildModeState.get(overworld);
        state.removeBuilder(uuid);
    }

    public static boolean isInBuildMode(UUID uuid) {
        return activeBuilders.containsKey(uuid);
    }

    public static ActiveBuilder getBuilder(UUID uuid) {
        return activeBuilders.get(uuid);
    }

    public static Collection<ActiveBuilder> getActiveBuilders() {
        return activeBuilders.values();
    }

    public static boolean isInsideBuildArea(PlayerEntity player) {
        var builder = activeBuilders.get(player.getUuid());
        if (builder == null) return false;
        return builder.buildArea().dimension().equals(player.getWorld().getRegistryKey())
            && builder.buildArea().contains(player.getBlockPos());
    }

    public static boolean isBlockInBuildArea(PlayerEntity player, BlockPos blockPos) {
        var builder = activeBuilders.get(player.getUuid());
        if (builder == null) return false;
        return builder.buildArea().dimension().equals(player.getWorld().getRegistryKey())
            && builder.buildArea().contains(blockPos);
    }
}
