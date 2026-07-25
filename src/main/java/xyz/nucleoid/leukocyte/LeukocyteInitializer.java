package xyz.nucleoid.leukocyte;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import xyz.nucleoid.leukocyte.command.ProtectCommand;
import xyz.nucleoid.leukocyte.command.ShapeCommand;
import xyz.nucleoid.leukocyte.item.LeukocyteShapeTool;
import xyz.nucleoid.leukocyte.network.ServerPacketHandler;
import xyz.nucleoid.leukocyte.rule.enforcer.LeukocyteRuleEnforcer;
import xyz.nucleoid.leukocyte.shape.*;
import xyz.nucleoid.stimuli.Stimuli;

public final class LeukocyteInitializer implements ModInitializer {
    public static final LeukocyteShapeTool SHAPE_TOOL = new LeukocyteShapeTool(new FabricItemSettings());

    @Override
    public void onInitialize() {
        Registry.register(Registries.ITEM, new Identifier("leukocyte", "shape_tool"), SHAPE_TOOL);

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
        });

        ServerPacketHandler.register();

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (player.getStackInHand(hand).getItem() instanceof LeukocyteShapeTool) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
    }
}