package xyz.nucleoid.leukocyte;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import xyz.nucleoid.leukocyte.build.BuildModeManager;
import xyz.nucleoid.leukocyte.build.BuildArea;
import xyz.nucleoid.leukocyte.build.LeukocyteBuild;
import xyz.nucleoid.leukocyte.command.BuildCommand;
import xyz.nucleoid.leukocyte.command.ProtectCommand;
import xyz.nucleoid.leukocyte.command.ShapeCommand;
import xyz.nucleoid.leukocyte.item.ToolItems;
import xyz.nucleoid.leukocyte.network.ServerBuildPacketHandler;
import xyz.nucleoid.leukocyte.network.ServerPacketHandler;
import xyz.nucleoid.leukocyte.rule.enforcer.LeukocyteRuleEnforcer;
import xyz.nucleoid.leukocyte.shape.*;
import xyz.nucleoid.stimuli.Stimuli;

public final class LeukocyteInitializer implements ModInitializer {
    private static MinecraftServer server;

    @Override
    public void onInitialize() {
        ProtectionShape.register("universal", UniversalShape.CODEC);
        ProtectionShape.register("dimension", DimensionShape.CODEC);
        ProtectionShape.register("box", BoxShape.CODEC);
        ProtectionShape.register("union", UnionShape.CODEC);

        Leukocyte.registerRuleEnforcer(LeukocyteRuleEnforcer.INSTANCE);

        Stimuli.registerSelector(new LeukocyteEventListenerSelector());

        ServerWorldEvents.LOAD.register((s, world) -> {
            server = s;
            Leukocyte.get(s).onWorldLoad(world);
        });
        ServerWorldEvents.UNLOAD.register((s, world) -> Leukocyte.get(s).onWorldUnload(world));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ProtectCommand.register(dispatcher);
            ShapeCommand.register(dispatcher);
            BuildCommand.register(dispatcher);
        });

        ServerPacketHandler.register();
        ServerBuildPacketHandler.register();

        BuildModeManager.register();

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (ToolItems.isAnyLeukocyteTool(player.getStackInHand(hand))) {
                return ActionResult.FAIL;
            }
            if (BuildModeManager.isInBuildMode(player.getUuid())
                && !BuildModeManager.isBlockInBuildArea(player, pos)) {
                player.sendMessage(Text.literal("§cYou can only build inside the build area!"), true);
                return ActionResult.FAIL;
            }
            if (!BuildModeManager.isInBuildMode(player.getUuid())
                && !player.hasPermissionLevel(4)
                && isInsideAnyBuildArea(world.getRegistryKey(), pos)) {
                if (isGraveBlock(world, pos)) return ActionResult.PASS;
                player.sendMessage(Text.literal("§cYou must be in build mode to modify blocks here!"), true);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
            if (BuildModeManager.isInBuildMode(player.getUuid())
                && !BuildModeManager.isBlockInBuildArea(player, pos)) {
                player.sendMessage(Text.literal("§cYou can only build inside the build area!"), true);
                return false;
            }
            if (!BuildModeManager.isInBuildMode(player.getUuid())
                && !player.hasPermissionLevel(4)
                && isInsideAnyBuildArea(world.getRegistryKey(), pos)) {
                if (isGraveBlock(world, pos)) return true;
                player.sendMessage(Text.literal("§cYou must be in build mode to modify blocks here!"), true);
                return false;
            }
            return true;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!BuildModeManager.isInBuildMode(player.getUuid())) {
                if (!player.hasPermissionLevel(4) && hitResult.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                    var clickedPos = hitResult.getBlockPos();
                    if (isInsideAnyBuildArea(world.getRegistryKey(), clickedPos)) {
                        var heldItem = player.getStackInHand(hand);
                        if (heldItem.getItem() instanceof net.minecraft.item.BlockItem) {
                            var placementPos = clickedPos.offset(hitResult.getSide());
                            if (isInsideAnyBuildArea(world.getRegistryKey(), placementPos)) {
                                player.sendMessage(Text.literal("§cYou must be in build mode to modify blocks here!"), true);
                                return ActionResult.FAIL;
                            }
                        }
                    }
                }
                return ActionResult.PASS;
            }
            if (hitResult.getType() != net.minecraft.util.hit.HitResult.Type.BLOCK) return ActionResult.PASS;

            var clickedPos = hitResult.getBlockPos();
            var heldItem = player.getStackInHand(hand);

            if (heldItem.getItem() instanceof net.minecraft.item.BlockItem) {
                var placementPos = clickedPos.offset(hitResult.getSide());
                if (!BuildModeManager.isBlockInBuildArea(player, placementPos)) {
                    player.sendMessage(Text.literal("§cYou can only build inside the build area!"), true);
                    return ActionResult.FAIL;
                }
            } else {
                if (!BuildModeManager.isBlockInBuildArea(player, clickedPos)) {
                    player.sendMessage(Text.literal("§cYou can only interact inside the build area!"), true);
                    return ActionResult.FAIL;
                }
            }

            return ActionResult.PASS;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (BuildModeManager.isInBuildMode(player.getUuid())) {
                if (isImmersivePaintingEntity(entity)) return ActionResult.PASS;
                player.sendMessage(Text.literal("§cYou cannot attack while in build mode!"), true);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (BuildModeManager.isInBuildMode(player.getUuid())) {
                if (isImmersivePaintingEntity(entity)) return ActionResult.PASS;
                player.sendMessage(Text.literal("§cYou cannot interact with entities in build mode!"), true);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
    }

    private static boolean isInsideAnyBuildArea(net.minecraft.registry.RegistryKey<net.minecraft.world.World> dimension, net.minecraft.util.math.BlockPos pos) {
        if (server == null) return false;
        var build = LeukocyteBuild.get(server.getOverworld());
        for (var area : build.getAreas()) {
            if (area.dimension().equals(dimension) && area.contains(pos)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isGraveBlock(net.minecraft.world.WorldView world, net.minecraft.util.math.BlockPos pos) {
        var block = world.getBlockState(pos).getBlock();
        var id = net.minecraft.registry.Registries.BLOCK.getId(block);
        return id.getNamespace().equals("universal_graves");
    }

    private static boolean isImmersivePaintingEntity(net.minecraft.entity.Entity entity) {
        var id = net.minecraft.registry.Registries.ENTITY_TYPE.getId(entity.getType());
        return id.getNamespace().equals("immersive_paintings");
    }
}