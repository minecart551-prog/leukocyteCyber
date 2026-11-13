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
import net.minecraft.server.world.ServerWorld;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import xyz.nucleoid.leukocyte.build.BuildModeManager;
import xyz.nucleoid.leukocyte.build.BuildArea;
import xyz.nucleoid.leukocyte.build.LeukocyteBuild;
import xyz.nucleoid.leukocyte.command.BuildCommand;
import xyz.nucleoid.leukocyte.command.ProtectCommand;
import xyz.nucleoid.leukocyte.command.ShapeCommand;
import xyz.nucleoid.leukocyte.item.ToolItems;
import xyz.nucleoid.leukocyte.network.ServerBuildPacketHandler;
import xyz.nucleoid.leukocyte.network.ServerPacketHandler;
import xyz.nucleoid.leukocyte.rule.ProtectionRule;
import xyz.nucleoid.leukocyte.rule.RuleResult;
import xyz.nucleoid.leukocyte.rule.enforcer.LeukocyteRuleEnforcer;
import xyz.nucleoid.leukocyte.shape.*;
import xyz.nucleoid.stimuli.EventSource;
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
            // A block whose item is on the build-mode blocked list can't be broken there either.
            // The list already keeps the item out of the inventory, so leaving the placed block
            // breakable would be a way straight around it. BEFORE is the single choke point for
            // every break path (creative START_DESTROY_BLOCK and survival STOP both reach
            // tryBreakBlock), so this one check covers them all.
            if (BuildModeManager.isInBuildMode(player.getUuid())) {
                String blockedId = blockedBuildBlockId(state);
                if (blockedId != null) {
                    player.sendMessage(
                        Text.literal("§c'" + blockedId + "' is blocked in build mode!"), true);
                    return false;
                }
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
            // Block the Yuushya modelling tools (Gourd of Entitilization / GUI tool), which place or
            // modify show blocks, wherever break or place is denied — instead of only interact_blocks.
            if (hand == Hand.MAIN_HAND
                    && hitResult.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
                    && world instanceof ServerWorld) {
                var server = world.getServer();
                if (server != null && isBlockedModellingTool(player.getStackInHand(hand))) {
                    BlockPos clickedPos = hitResult.getBlockPos();
                    if (isRuleDenied(server, player, clickedPos, ProtectionRule.BREAK)
                            || isRuleDenied(server, player, clickedPos, ProtectionRule.PLACE)) {
                        return ActionResult.FAIL;
                    }
                }
            }
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
                // AW's creative mannequin is a build prop: builders need to right-click it to
                // open its wardrobe and dress it. Build mode already runs in creative, which is
                // exactly what AW's isUsableBy() requires for this variant. Matched on the
                // registry id so leukocyte needs no dependency on AW (unknown ids never match).
                if (isArmourersCreativeMannequin(entity)) return ActionResult.PASS;
                player.sendMessage(Text.literal("§cYou cannot interact with entities in build mode!"), true);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
    }

    // Yuushya modelling tools that place/modify show blocks. Identified by registry id so leukocyte
    // needs no hard dependency on the modelling mod (unknown ids simply never match).
    private static final String[] BLOCKED_MODELLING_TOOLS = {"get_showblock_item", "gui_item"};

    private static boolean isBlockedModellingTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var id = Registries.ITEM.getId(stack.getItem());
        if (!"yuushya".equals(id.getNamespace())) return false;
        for (var path : BLOCKED_MODELLING_TOOLS) {
            if (path.equals(id.getPath())) return true;
        }
        return false;
    }

    // True when the rules that actually apply to this player at this position deny the rule.
    // getEventFilter().accepts() already enforces shape containment, op bypass, build-mode
    // bypass, inclusions and exclusions.
    //
    // Authority conflicts are resolved exactly like leukocyte's own enforcement: authorities
    // iterate highest-level first (AuthoritySortedHashMap/Authority.compareTo) and the first
    // non-PASS result decides — matching LeukocyteRuleEnforcer and the stimuli invoker's
    // "first non-PASS wins" loop. So a higher-level authority with break/place = allow
    // overrides a lower authority's deny, and break stays usable there.
    private static boolean isRuleDenied(MinecraftServer server, PlayerEntity player, BlockPos pos, ProtectionRule rule) {
        // Level-4 admins bypass all protection, exactly like the rest of leukocyte.
        if (player.hasPermissionLevel(4)) return false;
        var leukocyte = Leukocyte.get(server);
        var source = EventSource.forEntityAt(player, pos);
        for (var authority : leukocyte.getAuthorities()) {
            if (authority.getEventFilter().accepts(source)) {
                var result = authority.getRules().test(rule);
                if (result != RuleResult.PASS) {
                    return result == RuleResult.DENY;
                }
            }
        }
        return false;
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

    // The block's item id when that item is on the build-mode blocked list, otherwise null.
    // Goes through Item.fromBlock rather than matching ids as strings: the mapping is the real
    // BlockItem registry, which matters because block and item ids are not always the same —
    // AW's `skinnable` block carries the item `armourers_workshop:skin`, so string-matching
    // would leave skinnable walls breakable. Blocks with no item form (Item.fromBlock yields
    // AIR) can never be on an item list, so they stay breakable.
    private static String blockedBuildBlockId(BlockState state) {
        if (server == null) return null;
        var blocked = LeukocyteBuild.get(server.getOverworld()).getGlobalBlockedItems();
        if (blocked.isEmpty()) return null;

        var item = Item.fromBlock(state.getBlock());
        if (item == Items.AIR) return null;

        String itemId = Registries.ITEM.getId(item).toString();
        return blocked.contains(itemId) ? itemId : null;
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

    private static boolean isArmourersCreativeMannequin(net.minecraft.entity.Entity entity) {
        var id = net.minecraft.registry.Registries.ENTITY_TYPE.getId(entity.getType());
        return id.getNamespace().equals("armourers_workshop")
            && id.getPath().equals("mannequin-creative");
    }
}