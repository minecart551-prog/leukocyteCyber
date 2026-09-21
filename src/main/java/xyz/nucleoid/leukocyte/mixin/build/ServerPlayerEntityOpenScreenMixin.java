package xyz.nucleoid.leukocyte.mixin.build;

import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.StonecutterScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nucleoid.leukocyte.build.BuildModeManager;

import java.util.OptionalInt;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityOpenScreenMixin {
    @Inject(method = "openHandledScreen", at = @At("HEAD"), cancellable = true)
    private void onOpenHandledScreen(NamedScreenHandlerFactory factory, CallbackInfoReturnable<OptionalInt> cir) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        if (!BuildModeManager.isInBuildMode(self.getUuid())) return;

        if (factory instanceof PlayerScreenHandler) return;

        try {
            net.minecraft.screen.ScreenHandler menu = factory.createMenu(0, self.getInventory(), self);
            if (menu instanceof StonecutterScreenHandler) return;
        } catch (Exception ignored) {
        }

        self.sendMessage(
            net.minecraft.text.Text.literal("§cYou cannot open containers in build mode!"), true);
        cir.setReturnValue(OptionalInt.empty());
    }
}
