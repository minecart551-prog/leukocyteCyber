package xyz.nucleoid.leukocyte.mixin.build;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nucleoid.leukocyte.client.network.ClientBuildPacketHandler;

@Mixin(ScreenHandler.class)
public class ScreenHandlerMixin {
    @Inject(method = "setCursorStack", at = @At("HEAD"), cancellable = true)
    private void onSetCursorStack(ItemStack stack, CallbackInfo ci) {
        if (!ClientBuildPacketHandler.isInBuildMode()) return;
        if (stack.isEmpty()) return;

        java.util.List<String> blocked = ClientBuildPacketHandler.getGlobalBlockedItems();
        if (blocked.isEmpty()) return;

        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        if (blocked.contains(itemId)) {
            ci.cancel();
        }
    }
}
