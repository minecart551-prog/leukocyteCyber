package xyz.nucleoid.leukocyte.mixin.build;

import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nucleoid.leukocyte.build.BuildModeManager;
import xyz.nucleoid.leukocyte.build.LeukocyteBuild;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {
    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "onPlayerAction", at = @At("HEAD"), cancellable = true)
    private void onPlayerAction(PlayerActionC2SPacket packet, CallbackInfo ci) {
        if (!BuildModeManager.isInBuildMode(this.player.getUuid())) return;

        var action = packet.getAction();
        if (action == PlayerActionC2SPacket.Action.DROP_ITEM
            || action == PlayerActionC2SPacket.Action.DROP_ALL_ITEMS) {
            ci.cancel();
            this.player.sendMessage(
                net.minecraft.text.Text.literal("§cYou cannot drop items in build mode!"), true);
        }
    }

    @Inject(method = "onCreativeInventoryAction", at = @At("HEAD"), cancellable = true)
    private void onCreativeInventoryAction(CreativeInventoryActionC2SPacket packet, CallbackInfo ci) {
        if (!BuildModeManager.isInBuildMode(this.player.getUuid())) return;

        var stack = packet.getItemStack();
        int slot = packet.getSlot();

        // The creative screen sends any drop with a negative slot: -1 when dragging a
        // held item out of the window (dropCreativeStack) and -999 on other drop paths.
        // The vanilla server treats ANY negative slot as "drop this stack", so block them
        // all — not just -999, which is what let the drag-out drop slip through.
        if (slot < 0) {
            if (!stack.isEmpty()) {
                this.player.sendMessage(
                    net.minecraft.text.Text.literal("§cYou cannot drop items in build mode!"), true);
                ci.cancel();
                leukocyte$resyncCursor();
            }
            return;
        }

        if (stack.isEmpty()) return;

        var server = this.player.getServer();
        if (server == null) return;
        var build = LeukocyteBuild.get(server.getOverworld());
        var blocked = build.getGlobalBlockedItems();
        String itemId = Registries.ITEM.getId(stack.getItem()).toString();

        if (blocked.contains(itemId)) {
            this.player.sendMessage(
                net.minecraft.text.Text.literal("§c'" + itemId + "' is blocked in build mode!"), true);
            ci.cancel();
            leukocyte$clearSlot(slot);
            leukocyte$resyncCursor();
        }
    }

    @Inject(method = "onClickSlot", at = @At("HEAD"), cancellable = true)
    private void onClickSlot(ClickSlotC2SPacket packet, CallbackInfo ci) {
        if (!BuildModeManager.isInBuildMode(this.player.getUuid())) return;

        var handler = this.player.currentScreenHandler;
        int slotId = packet.getSlot();
        int button = packet.getButton();
        var actionType = packet.getActionType();

        // Block dropping items through an open inventory GUI, which is a different packet
        // from the hotbar Q-drop handled above. Releasing a held item outside the window
        // sends PICKUP/QUICK_MOVE at slot -999; pressing Q while hovering a slot sends
        // THROW. Both conditions mirror exactly when the vanilla server would drop.
        boolean isGuiDrop =
            (slotId == -999
                && (actionType == SlotActionType.PICKUP || actionType == SlotActionType.QUICK_MOVE)
                && (button == 0 || button == 1)
                && !handler.getCursorStack().isEmpty())
            || (actionType == SlotActionType.THROW
                && slotId >= 0
                && handler.getCursorStack().isEmpty());

        if (isGuiDrop) {
            ci.cancel();
            this.player.sendMessage(
                net.minecraft.text.Text.literal("§cYou cannot drop items in build mode!"), true);
            // The client already predicted the drop, so push the server's authoritative
            // state back. Use the non-wiping sync helpers: the server still holds the item,
            // so we must re-send it (not clear the cursor).
            if (actionType == SlotActionType.THROW) {
                leukocyte$syncSlotToClient(slotId);
            } else {
                leukocyte$syncCursorToClient();
            }
            return;
        }

        var server = this.player.getServer();
        if (server == null) return;
        var build = LeukocyteBuild.get(server.getOverworld());
        var blocked = build.getGlobalBlockedItems();
        if (blocked.isEmpty()) return;

        if (slotId < 0) return;

        var slot = handler.getSlot(slotId);
        if (slot != null && slot.hasStack()) {
            String itemId = Registries.ITEM.getId(slot.getStack().getItem()).toString();
            if (blocked.contains(itemId)) {
                this.player.sendMessage(
                    net.minecraft.text.Text.literal("§c'" + itemId + "' is blocked in build mode!"), true);
                ci.cancel();
                slot.setStack(ItemStack.EMPTY);
                leukocyte$resyncCursor();
            }
        }
    }

    @Unique
    private void leukocyte$clearSlot(int slot) {
        var handler = this.player.currentScreenHandler;
        var slotObj = handler.getSlot(slot);
        if (slotObj != null) {
            slotObj.setStack(ItemStack.EMPTY);
        }
        this.player.networkHandler.sendPacket(
            new ScreenHandlerSlotUpdateS2CPacket(
                handler.syncId,
                handler.getRevision(),
                slot,
                ItemStack.EMPTY)
        );
    }

    @Unique
    private void leukocyte$resyncCursor() {
        var handler = this.player.currentScreenHandler;
        handler.setCursorStack(ItemStack.EMPTY);
        this.player.networkHandler.sendPacket(
            new ScreenHandlerSlotUpdateS2CPacket(
                handler.syncId,
                handler.getRevision(),
                -1,
                ItemStack.EMPTY)
        );
    }

    // Re-sends the server's actual cursor stack WITHOUT clearing it. Used to undo a
    // client-predicted drop while the server still holds the item on the cursor.
    @Unique
    private void leukocyte$syncCursorToClient() {
        var handler = this.player.currentScreenHandler;
        this.player.networkHandler.sendPacket(
            new ScreenHandlerSlotUpdateS2CPacket(
                handler.syncId,
                handler.getRevision(),
                -1,
                handler.getCursorStack())
        );
    }

    // Re-sends a single slot's actual stack to undo a client-predicted drop (THROW).
    @Unique
    private void leukocyte$syncSlotToClient(int slotId) {
        var handler = this.player.currentScreenHandler;
        if (slotId < 0 || slotId >= handler.slots.size()) return;
        this.player.networkHandler.sendPacket(
            new ScreenHandlerSlotUpdateS2CPacket(
                handler.syncId,
                handler.getRevision(),
                slotId,
                handler.getSlot(slotId).getStack())
        );
    }
}
