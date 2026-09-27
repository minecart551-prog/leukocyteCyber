package xyz.nucleoid.leukocyte.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import xyz.nucleoid.leukocyte.client.util.ClientScreenChecks;

/**
 * Blocks players from putting sugar into ender chests and shulker boxes.
 * Taking sugar out of those containers is always allowed.
 */
public final class SugarContainerRule {
    public static final boolean ENABLED = true;

    private SugarContainerRule() {
    }

    public static boolean shouldBlockClick(ScreenHandler handler, int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (!ENABLED) {
            return false;
        }

        int containerSlots = protectedContainerSlots(handler);
        if (containerSlots < 0) {
            return false;
        }

        switch (actionType) {
            // Dragging distributes the cursor stack into the hovered slots.
            // Stage 1 adds a slot to the drag set: block container slots so sugar
            // can never enter the container (stage 0/stage 2 then only ever
            // touch player inventory slots).
            case QUICK_CRAFT:
                if (ScreenHandler.unpackQuickCraftStage(button) != 1) {
                    return false;
                }
                return slotIndex >= 0 && slotIndex < containerSlots && containsSugar(handler.getCursorStack());

            // Normal click: deposits the cursor stack into a container slot.
            // An empty cursor means the player is taking items out, which is allowed.
            case PICKUP:
                return slotIndex >= 0 && slotIndex < containerSlots && containsSugar(handler.getCursorStack());

            // Shift-click: moves the clicked slot into the container when the
            // clicked slot is part of the player inventory portion.
            case QUICK_MOVE:
                if (slotIndex < containerSlots || slotIndex >= handler.slots.size()) {
                    return false;
                }
                return containsSugar(handler.getSlot(slotIndex).getStack());

            // Number key / hotbar swap into a container slot. An empty hotbar
            // slot means the player is taking items out, which is allowed.
            case SWAP:
                if (slotIndex < 0 || slotIndex >= containerSlots) {
                    return false;
                }
                var inventory = player.getInventory();
                if (button < 0 || button >= inventory.size()) {
                    return false;
                }
                return containsSugar(inventory.getStack(button));

            // THROW, CLONE, PICKUP_ALL only move items out of the container.
            default:
                return false;
        }
    }

    private static int protectedContainerSlots(ScreenHandler handler) {
        if (handler instanceof ShulkerBoxScreenHandler) {
            return 27;
        }
        if (handler instanceof GenericContainerScreenHandler generic) {
            if (generic.getInventory() instanceof EnderChestInventory) {
                return generic.getRows() * 9;
            }
            if (isEnderChestScreenOpen()) {
                return generic.getRows() * 9;
            }
        }
        return -1;
    }

    private static boolean containsSugar(ItemStack stack) {
        return !stack.isEmpty() && stack.isOf(Items.SUGAR);
    }

    // The client-side ender chest handler is backed by a dummy inventory, so it
    // can only be recognized by the screen that is currently open.
    private static boolean isEnderChestScreenOpen() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return false;
        }
        return ClientScreenChecks.isEnderChestScreenOpen();
    }
}
