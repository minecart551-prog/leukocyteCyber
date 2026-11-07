package xyz.nucleoid.leukocyte.mixin.build;

import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.StonecutterScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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

        // Armourer's Workshop is allowed in build mode — its containers, workstations, the
        // wardrobe and the mannequin alike. It is recognised purely by the package its classes
        // live in, so leukocyte needs no dependency on AW and this is inert when AW is absent.
        //
        // Checked against the factory first so an AW menu is only ever built once: every AW GUI
        // funnels through AbstractFabricMenuType.openMenu(), whose anonymous factory is declared
        // in AW's own package, so returning here means vanilla still calls createMenu() exactly
        // once. The menu check below is a fallback for factories AW doesn't declare itself.
        if (leukocyte$isArmourersWorkshop(factory)) return;

        try {
            net.minecraft.screen.ScreenHandler menu = factory.createMenu(0, self.getInventory(), self);
            if (menu instanceof StonecutterScreenHandler) return;
            if (leukocyte$isArmourersWorkshop(menu)) return;
        } catch (Exception ignored) {
        }

        self.sendMessage(
            net.minecraft.text.Text.literal("§cYou cannot open containers in build mode!"), true);
        cir.setReturnValue(OptionalInt.empty());
    }

    @Unique
    private static boolean leukocyte$isArmourersWorkshop(Object obj) {
        return obj != null && obj.getClass().getName().startsWith("moe.plushie.armourers_workshop.");
    }
}
