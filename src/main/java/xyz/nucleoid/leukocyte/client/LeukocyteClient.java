package xyz.nucleoid.leukocyte.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import xyz.nucleoid.leukocyte.client.network.ClientBuildPacketHandler;
import xyz.nucleoid.leukocyte.client.network.ClientPacketHandler;
import xyz.nucleoid.leukocyte.client.screen.LeukocyteScreen;
import xyz.nucleoid.leukocyte.client.tool.BuildAreaToolHandler;
import xyz.nucleoid.leukocyte.client.tool.ShapeToolHandler;

public final class LeukocyteClient implements ClientModInitializer {
    private static boolean screenRequested = false;

    @Override
    public void onInitializeClient() {
        ClientPacketHandler.register();
        ClientBuildPacketHandler.register();
        ShapeToolHandler.register();
        BuildAreaToolHandler.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (screenRequested) {
                screenRequested = false;
                client.setScreen(new LeukocyteScreen());
            }
        });
    }

    public static void requestOpenScreen() {
        screenRequested = true;
    }
}
