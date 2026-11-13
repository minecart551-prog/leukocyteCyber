package xyz.nucleoid.leukocyte.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import xyz.nucleoid.leukocyte.client.util.ClientScreenChecks;

import java.util.Set;

/**
 * Blocks players from putting certain items into ender chests and shulker boxes.
 * Taking those items back out of the containers is always allowed.
 *
 * <p>Blocked insertions:
 * <ul>
 *   <li>sugar — ender chests and shulker boxes</li>
 *   <li>Immersive Aircraft vehicles — ender chests and shulker boxes</li>
 *   <li>shulker boxes — ender chests only; vanilla already refuses to nest one
 *       shulker box inside another via {@code ShulkerBoxSlot.canInsert()}</li>
 * </ul>
 */
public final class ContainerInsertRule {
    public static final boolean ENABLED = true;

    // Immersive Aircraft vehicles. Matched on the full item id so leukocyte needs no
    // dependency on the mod (unknown ids simply never match).
    private static final Set<String> AIRCRAFT_ITEMS = Set.of(
        "immersive_aircraft:airship",
        "immersive_aircraft:bamboo_hopper",
        "immersive_aircraft:biplane",
        "immersive_aircraft:cargo_airship",
        "immersive_aircraft:gyrodyne",
        "immersive_aircraft:quadrocopter",
        "immersive_aircraft:warship"
    );

    private enum ContainerKind {
        ENDER_CHEST,
        SHULKER_BOX
    }

    private ContainerInsertRule() {
    }

    /**
     * @return the message to show the player, or null when the click is allowed.
     */
    public static String blockedMessage(ScreenHandler handler, int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (!ENABLED) {
            return null;
        }

        ContainerKind kind = kindOf(handler);
        if (kind == null) {
            return null;
        }
        int containerSlots = containerSlots(handler, kind);

        switch (actionType) {
            // Dragging distributes the cursor stack into the hovered slots.
            // Stage 1 adds a slot to the drag set: block container slots so the item
            // can never enter the container (stage 0/stage 2 then only ever touch
            // player inventory slots).
            case QUICK_CRAFT:
                if (ScreenHandler.unpackQuickCraftStage(button) != 1) {
                    return null;
                }
                if (slotIndex < 0 || slotIndex >= containerSlots) {
                    return null;
                }
                return blockReason(handler.getCursorStack(), kind);

            // Normal click: deposits the cursor stack into a container slot.
            // An empty cursor means the player is taking items out, which is allowed.
            case PICKUP:
                if (slotIndex < 0 || slotIndex >= containerSlots) {
                    return null;
                }
                return blockReason(handler.getCursorStack(), kind);

            // Shift-click: moves the clicked slot into the container when the
            // clicked slot is part of the player inventory portion.
            case QUICK_MOVE:
                if (slotIndex < containerSlots || slotIndex >= handler.slots.size()) {
                    return null;
                }
                return blockReason(handler.getSlot(slotIndex).getStack(), kind);

            // Number key / hotbar swap into a container slot. An empty hotbar
            // slot means the player is taking items out, which is allowed.
            case SWAP:
                if (slotIndex < 0 || slotIndex >= containerSlots) {
                    return null;
                }
                var inventory = player.getInventory();
                if (button < 0 || button >= inventory.size()) {
                    return null;
                }
                return blockReason(inventory.getStack(button), kind);

            // THROW, CLONE, PICKUP_ALL only move items out of the container.
            default:
                return null;
        }
    }

    private static String blockReason(ItemStack stack, ContainerKind kind) {
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.isOf(Items.SUGAR)) {
            return "§cYou cannot put sugar into ender chests or shulker boxes!";
        }
        if (isAircraft(stack)) {
            return "§cYou cannot put aircraft into ender chests or shulker boxes!";
        }
        // Only ender chests: vanilla already blocks shulker-in-shulker, so a shulker
        // box here is only ever trying to reach an ender chest.
        if (kind == ContainerKind.ENDER_CHEST && isShulkerBox(stack)) {
            return "§cYou cannot put shulker boxes into ender chests!";
        }
        return null;
    }

    private static ContainerKind kindOf(ScreenHandler handler) {
        if (handler instanceof ShulkerBoxScreenHandler) {
            return ContainerKind.SHULKER_BOX;
        }
        if (handler instanceof GenericContainerScreenHandler) {
            if (isEnderChestHandler(handler)) {
                return ContainerKind.ENDER_CHEST;
            }
        }
        return null;
    }

    private static int containerSlots(ScreenHandler handler, ContainerKind kind) {
        if (kind == ContainerKind.SHULKER_BOX) {
            return 27;
        }
        return ((GenericContainerScreenHandler) handler).getRows() * 9;
    }

    // The server side is handed the real EnderChestInventory, but the client side is
    // backed by a dummy inventory, so it can only be recognized by the screen that is
    // currently open.
    private static boolean isEnderChestHandler(ScreenHandler handler) {
        if (handler instanceof GenericContainerScreenHandler generic
            && generic.getInventory() instanceof EnderChestInventory) {
            return true;
        }
        return isEnderChestScreenOpen();
    }

    private static boolean isAircraft(ItemStack stack) {
        return AIRCRAFT_ITEMS.contains(Registries.ITEM.getId(stack.getItem()).toString());
    }

    // All 17 vanilla shulker boxes share this block class; there are no modded ones.
    // Mirrors vanilla's own BlockItem.canBeNested() check.
    private static boolean isShulkerBox(ItemStack stack) {
        var item = stack.getItem();
        return item instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    private static boolean isEnderChestScreenOpen() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return false;
        }
        return ClientScreenChecks.isEnderChestScreenOpen();
    }
}
