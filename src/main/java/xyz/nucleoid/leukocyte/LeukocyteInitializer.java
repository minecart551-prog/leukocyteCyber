package xyz.nucleoid.leukocyte;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import xyz.nucleoid.leukocyte.build.BuildModeManager;
import xyz.nucleoid.leukocyte.command.BuildCommand;
import xyz.nucleoid.leukocyte.command.ProtectCommand;
import xyz.nucleoid.leukocyte.command.ShapeCommand;
import xyz.nucleoid.leukocyte.item.LeukocyteBuildAreaTool;
import xyz.nucleoid.leukocyte.item.LeukocyteShapeTool;
import xyz.nucleoid.leukocyte.network.ServerBuildPacketHandler;
import xyz.nucleoid.leukocyte.network.ServerPacketHandler;
import xyz.nucleoid.leukocyte.rule.enforcer.LeukocyteRuleEnforcer;
import xyz.nucleoid.leukocyte.shape.*;
import xyz.nucleoid.stimuli.Stimuli;

public final class LeukocyteInitializer implements ModInitializer {
    public static final LeukocyteShapeTool SHAPE_TOOL = new LeukocyteShapeTool(new FabricItemSettings());
    public static final LeukocyteBuildAreaTool BUILD_AREA_TOOL = new LeukocyteBuildAreaTool(new FabricItemSettings());

    @Override
    public void onInitialize() {
        Registry.register(Registries.ITEM, new Identifier("leukocyte", "shape_tool"), SHAPE_TOOL);
        Registry.register(Registries.ITEM, new Identifier("leukocyte", "build_area_tool"), BUILD_AREA_TOOL);

        ProtectionShape.register("universal", UniversalShape.CODEC);
        ProtectionShape.register("dimension", DimensionShape.CODEC);
        ProtectionShape.register("box", BoxShape.CODEC);
        ProtectionShape.register("union", UnionShape.CODEC);

        Leukocyte.registerRuleEnforcer(LeukocyteRuleEnforcer.INSTANCE);

        Stimuli.registerSelector(new LeukocyteEventListenerSelector());

        ServerWorldEvents.LOAD.register((server, world) -> Leukocyte.get(server).onWorldLoad(world));
        ServerWorldEvents.UNLOAD.register((server, world) -> Leukocyte.get(server).onWorldUnload(world));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ProtectCommand.register(dispatcher);
            ShapeCommand.register(dispatcher);
            BuildCommand.register(dispatcher);
        });

        ServerPacketHandler.register();
        ServerBuildPacketHandler.register();

        BuildModeManager.register();

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (player.getStackInHand(hand).getItem() instanceof LeukocyteShapeTool
                || player.getStackInHand(hand).getItem() instanceof LeukocyteBuildAreaTool) {
                return ActionResult.FAIL;
            }
            if (BuildModeManager.isInBuildMode(player.getUuid())
                && !BuildModeManager.isBlockInBuildArea(player, pos)) {
                player.sendMessage(Text.literal("§cYou can only build inside the build area!"), true);
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
            return true;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!BuildModeManager.isInBuildMode(player.getUuid())) return ActionResult.PASS;
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
                player.sendMessage(Text.literal("§cYou cannot attack while in build mode!"), true);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (BuildModeManager.isInBuildMode(player.getUuid())) {
                player.sendMessage(Text.literal("§cYou cannot interact with entities in build mode!"), true);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
    }
}