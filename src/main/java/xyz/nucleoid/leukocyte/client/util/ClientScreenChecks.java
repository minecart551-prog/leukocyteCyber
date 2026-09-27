package xyz.nucleoid.leukocyte.client.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.text.TranslatableTextContent;

@Environment(EnvType.CLIENT)
public final class ClientScreenChecks {
    private ClientScreenChecks() {
    }

    public static boolean isEnderChestScreenOpen() {
        Screen screen = MinecraftClient.getInstance().currentScreen;
        if (!(screen instanceof GenericContainerScreen)) {
            return false;
        }
        return screen.getTitle().getContent() instanceof TranslatableTextContent translatable
            && "container.enderchest".equals(translatable.getKey());
    }
}
