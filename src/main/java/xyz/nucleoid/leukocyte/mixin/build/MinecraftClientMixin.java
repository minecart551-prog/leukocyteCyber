package xyz.nucleoid.leukocyte.mixin.build;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nucleoid.leukocyte.client.network.ClientBuildPacketHandler;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        if (!ClientBuildPacketHandler.isInBuildMode()) return;
        if (screen == null) return;

        if (screen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen
            && !(screen instanceof CreativeInventoryScreen)
            && !(screen instanceof InventoryScreen)) {
            ci.cancel();
            MinecraftClient mc = (MinecraftClient) (Object) this;
            if (mc.player != null) {
                mc.player.sendMessage(
                    net.minecraft.text.Text.literal("§cYou cannot open containers in build mode!"), true);
            }
        }
    }
}
