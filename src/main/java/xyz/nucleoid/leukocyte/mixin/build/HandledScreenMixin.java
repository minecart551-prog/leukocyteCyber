package xyz.nucleoid.leukocyte.mixin.build;

import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nucleoid.leukocyte.client.network.ClientBuildPacketHandler;

import java.util.List;

@Mixin(HandledScreen.class)
public class HandledScreenMixin {
    @Inject(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V", at = @At("HEAD"), cancellable = true)
    private void onMouseClick(Slot slot, int slotId, int button, SlotActionType action, CallbackInfo ci) {
        if (!ClientBuildPacketHandler.isInBuildMode()) return;
        if (!((Object) this instanceof CreativeInventoryScreen)) return;

        List<String> blocked = ClientBuildPacketHandler.getGlobalBlockedItems();
        if (blocked.isEmpty()) return;

        if (slot == null || !slot.hasStack()) return;

        ItemStack stack = slot.getStack();
        String itemId = Registries.ITEM.getId(stack.getItem()).toString();

        if (blocked.contains(itemId)) {
            ci.cancel();
        }
    }
}
