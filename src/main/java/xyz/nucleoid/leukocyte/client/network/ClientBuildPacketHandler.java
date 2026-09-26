package xyz.nucleoid.leukocyte.client.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import xyz.nucleoid.leukocyte.client.tool.BuildAreaToolState;
import xyz.nucleoid.leukocyte.network.LeukocyteNetworking;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class ClientBuildPacketHandler {
    private static Consumer<String> resultCallback;
    private static List<String> globalBlockedItems = new ArrayList<>();
    private static List<String> globalBlockedPlayers = new ArrayList<>();
    private static List<String> crackedWhitelist = new ArrayList<>();
    private static boolean inBuildMode = false;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(LeukocyteNetworking.BUILD_S2C_CHANNEL, (client, handler, buf, responseSender) -> {
            byte responseType = buf.readByte();
            NbtCompound data = buf.readNbt();
            if (data == null) return;

            client.execute(() -> {
                switch (responseType) {
                    case LeukocyteNetworking.BUILD_RESPONSE_DATA -> handleBuildData(data);
                    case LeukocyteNetworking.BUILD_RESPONSE_RESULT -> handleResult(data);
                    case LeukocyteNetworking.BUILD_RESPONSE_BUILD_MODE_STATUS -> handleBuildModeStatus(data);
                }
            });
        });
    }

    public static void setResultCallback(Consumer<String> callback) {
        resultCallback = callback;
    }

    private static void handleBuildData(NbtCompound data) {
        var list = data.getList("areas", NbtElement.COMPOUND_TYPE);
        var areas = new ArrayList<BuildAreaToolState.BuildAreaEntry>();

        for (int i = 0; i < list.size(); i++) {
            var tag = list.getCompound(i);
            String name = tag.getString("name");
            String dimStr = tag.getString("dimension");
            var dimId = Identifier.tryParse(dimStr);
            RegistryKey<net.minecraft.world.World> dimension = dimId != null
                ? RegistryKey.of(RegistryKeys.WORLD, dimId) : null;

            var subBoxes = new ArrayList<int[]>();
            if (tag.contains("sub_boxes", NbtElement.LIST_TYPE)) {
                var subBoxesList = tag.getList("sub_boxes", NbtElement.LIST_TYPE);
                for (int j = 0; j < subBoxesList.size(); j++) {
                    var arr = subBoxesList.getList(j);
                    if (arr.size() >= 6) {
                        subBoxes.add(new int[]{
                            arr.getInt(0), arr.getInt(1), arr.getInt(2),
                            arr.getInt(3), arr.getInt(4), arr.getInt(5)
                        });
                    }
                }
            }

            var wl = new ArrayList<String>();
            if (tag.contains("whitelist", NbtElement.LIST_TYPE)) {
                var wlList = tag.getList("whitelist", NbtElement.STRING_TYPE);
                for (int j = 0; j < wlList.size(); j++) {
                    wl.add(wlList.getString(j));
                }
            }
            boolean wlEnabled = tag.getBoolean("whitelist_enabled");

            areas.add(new BuildAreaToolState.BuildAreaEntry(name, dimension, subBoxes, wl, wlEnabled));
        }

        BuildAreaToolState.getInstance().setBuildAreas(areas);
        BuildAreaToolState.getInstance().tryRestoreSelection();

        var blockedList = data.getList("global_blocked_items", NbtElement.STRING_TYPE);
        globalBlockedItems.clear();
        for (int i = 0; i < blockedList.size(); i++) {
            globalBlockedItems.add(blockedList.getString(i));
        }

        var blockedPlayersList = data.getList("global_blocked_players", NbtElement.STRING_TYPE);
        globalBlockedPlayers.clear();
        for (int i = 0; i < blockedPlayersList.size(); i++) {
            globalBlockedPlayers.add(blockedPlayersList.getString(i));
        }

        var crackedWlList = data.getList("cracked_whitelist", NbtElement.STRING_TYPE);
        crackedWhitelist.clear();
        for (int i = 0; i < crackedWlList.size(); i++) {
            crackedWhitelist.add(crackedWlList.getString(i));
        }

        xyz.nucleoid.leukocyte.client.render.ShapeRenderer.getInstance().markNeedsRebuild();
    }

    private static void handleResult(NbtCompound data) {
        boolean success = data.getBoolean("success");
        String message = data.getString("message");
        if (resultCallback != null) {
            resultCallback.accept((success ? "§a" : "§c") + message);
        }
        if (success && BuildAreaToolState.getInstance().isToolHeld()) {
            requestBuildAreaData();
        }
    }

    public static List<String> getGlobalBlockedItems() {
        return globalBlockedItems;
    }

    public static List<String> getGlobalBlockedPlayers() {
        return globalBlockedPlayers;
    }

    public static List<String> getCrackedWhitelist() {
        return crackedWhitelist;
    }

    public static boolean isInBuildMode() {
        return inBuildMode;
    }

    private static void handleBuildModeStatus(NbtCompound data) {
        inBuildMode = data.getBoolean("in_build_mode");
    }

    public static void requestBuildAreaData() {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.BUILD_ACTION_REQUEST_DATA);
        buf.writeNbt(new NbtCompound());
        ClientPlayNetworking.send(LeukocyteNetworking.BUILD_C2S_CHANNEL, buf);
    }

    public static void createBuildArea(String name, String dimensionId, BlockPos min, BlockPos max) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.BUILD_ACTION_CREATE);
        var data = new NbtCompound();
        data.putString("name", name);
        data.putString("dimension", dimensionId);
        data.putInt("min_x", min.getX());
        data.putInt("min_y", min.getY());
        data.putInt("min_z", min.getZ());
        data.putInt("max_x", max.getX());
        data.putInt("max_y", max.getY());
        data.putInt("max_z", max.getZ());
        buf.writeNbt(data);
        ClientPlayNetworking.send(LeukocyteNetworking.BUILD_C2S_CHANNEL, buf);
    }

    public static void addBoxToArea(String name, BlockPos a, BlockPos b) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.BUILD_ACTION_ADD_BOX);
        var data = new NbtCompound();
        data.putString("name", name);
        data.putInt("min_x", Math.min(a.getX(), b.getX()));
        data.putInt("min_y", Math.min(a.getY(), b.getY()));
        data.putInt("min_z", Math.min(a.getZ(), b.getZ()));
        data.putInt("max_x", Math.max(a.getX(), b.getX()));
        data.putInt("max_y", Math.max(a.getY(), b.getY()));
        data.putInt("max_z", Math.max(a.getZ(), b.getZ()));
        buf.writeNbt(data);
        ClientPlayNetworking.send(LeukocyteNetworking.BUILD_C2S_CHANNEL, buf);
    }

    public static void subtractBoxFromArea(String name, BlockPos a, BlockPos b) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.BUILD_ACTION_SUBTRACT_BOX);
        var data = new NbtCompound();
        data.putString("name", name);
        data.putInt("min_x", Math.min(a.getX(), b.getX()));
        data.putInt("min_y", Math.min(a.getY(), b.getY()));
        data.putInt("min_z", Math.min(a.getZ(), b.getZ()));
        data.putInt("max_x", Math.max(a.getX(), b.getX()));
        data.putInt("max_y", Math.max(a.getY(), b.getY()));
        data.putInt("max_z", Math.max(a.getZ(), b.getZ()));
        buf.writeNbt(data);
        ClientPlayNetworking.send(LeukocyteNetworking.BUILD_C2S_CHANNEL, buf);
    }

    public static void renameBuildArea(String oldName, String newName) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.BUILD_ACTION_RENAME);
        var data = new NbtCompound();
        data.putString("old_name", oldName);
        data.putString("new_name", newName);
        buf.writeNbt(data);
        ClientPlayNetworking.send(LeukocyteNetworking.BUILD_C2S_CHANNEL, buf);
    }

    public static void deleteBuildArea(String name) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.BUILD_ACTION_DELETE);
        var data = new NbtCompound();
        data.putString("name", name);
        buf.writeNbt(data);
        ClientPlayNetworking.send(LeukocyteNetworking.BUILD_C2S_CHANNEL, buf);
    }

    public static void addBlockedItem(String itemId) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.BUILD_ACTION_ADD_BLOCKED_ITEM);
        var data = new NbtCompound();
        data.putString("item", itemId);
        buf.writeNbt(data);
        ClientPlayNetworking.send(LeukocyteNetworking.BUILD_C2S_CHANNEL, buf);
    }

    public static void removeBlockedItem(String itemId) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.BUILD_ACTION_REMOVE_BLOCKED_ITEM);
        var data = new NbtCompound();
        data.putString("item", itemId);
        buf.writeNbt(data);
        ClientPlayNetworking.send(LeukocyteNetworking.BUILD_C2S_CHANNEL, buf);
    }

    public static void teleportToArea(String name) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.BUILD_ACTION_TELEPORT_TO_AREA);
        var data = new NbtCompound();
        data.putString("name", name);
        buf.writeNbt(data);
        ClientPlayNetworking.send(LeukocyteNetworking.BUILD_C2S_CHANNEL, buf);
    }

    public static void addWhitelistPlayer(String areaName, String playerName) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.BUILD_ACTION_ADD_WHITELIST_PLAYER);
        var data = new NbtCompound();
        data.putString("name", areaName);
        data.putString("player", playerName);
        buf.writeNbt(data);
        ClientPlayNetworking.send(LeukocyteNetworking.BUILD_C2S_CHANNEL, buf);
    }

    public static void removeWhitelistPlayer(String areaName, String playerName) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.BUILD_ACTION_REMOVE_WHITELIST_PLAYER);
        var data = new NbtCompound();
        data.putString("name", areaName);
        data.putString("player", playerName);
        buf.writeNbt(data);
        ClientPlayNetworking.send(LeukocyteNetworking.BUILD_C2S_CHANNEL, buf);
    }

    public static void toggleWhitelist(String areaName) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.BUILD_ACTION_TOGGLE_WHITELIST);
        var data = new NbtCompound();
        data.putString("name", areaName);
        buf.writeNbt(data);
        ClientPlayNetworking.send(LeukocyteNetworking.BUILD_C2S_CHANNEL, buf);
    }

    public static void addBlockedPlayer(String playerName) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.BUILD_ACTION_ADD_BLOCKED_PLAYER);
        var data = new NbtCompound();
        data.putString("player", playerName);
        buf.writeNbt(data);
        ClientPlayNetworking.send(LeukocyteNetworking.BUILD_C2S_CHANNEL, buf);
    }

    public static void removeBlockedPlayer(String playerName) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.BUILD_ACTION_REMOVE_BLOCKED_PLAYER);
        var data = new NbtCompound();
        data.putString("player", playerName);
        buf.writeNbt(data);
        ClientPlayNetworking.send(LeukocyteNetworking.BUILD_C2S_CHANNEL, buf);
    }

    public static void addCrackedWhitelistedPlayer(String playerName) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.BUILD_ACTION_ADD_CRACKED_WHITELIST_PLAYER);
        var data = new NbtCompound();
        data.putString("player", playerName);
        buf.writeNbt(data);
        ClientPlayNetworking.send(LeukocyteNetworking.BUILD_C2S_CHANNEL, buf);
    }

    public static void removeCrackedWhitelistedPlayer(String playerName) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.BUILD_ACTION_REMOVE_CRACKED_WHITELIST_PLAYER);
        var data = new NbtCompound();
        data.putString("player", playerName);
        buf.writeNbt(data);
        ClientPlayNetworking.send(LeukocyteNetworking.BUILD_C2S_CHANNEL, buf);
    }
}
