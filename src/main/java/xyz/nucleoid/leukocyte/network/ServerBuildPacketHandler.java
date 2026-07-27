package xyz.nucleoid.leukocyte.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import xyz.nucleoid.leukocyte.build.BuildArea;
import xyz.nucleoid.leukocyte.build.BuildModeManager;
import xyz.nucleoid.leukocyte.build.LeukocyteBuild;

import java.util.ArrayList;

public final class ServerBuildPacketHandler {
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(LeukocyteNetworking.BUILD_C2S_CHANNEL, (server, player, handler, buf, responseSender) -> {
            byte action = buf.readByte();
            NbtCompound data = buf.readNbt();
            if (data == null) return;

            server.execute(() -> {
                if (!player.hasPermissionLevel(4)) {
                    sendResult(player, false, "You do not have permission to use this.");
                    return;
                }

                var build = LeukocyteBuild.get(server.getOverworld());

                switch (action) {
                    case LeukocyteNetworking.BUILD_ACTION_REQUEST_DATA -> handleRequestData(player, build);
                    case LeukocyteNetworking.BUILD_ACTION_CREATE -> handleCreate(player, build, data);
                    case LeukocyteNetworking.BUILD_ACTION_RENAME -> handleRename(player, build, data);
                    case LeukocyteNetworking.BUILD_ACTION_DELETE -> handleDelete(player, build, data);
                    case LeukocyteNetworking.BUILD_ACTION_ADD_BLOCKED_ITEM -> handleAddBlockedItem(player, build, data);
                    case LeukocyteNetworking.BUILD_ACTION_REMOVE_BLOCKED_ITEM -> handleRemoveBlockedItem(player, build, data);
                    case LeukocyteNetworking.BUILD_ACTION_TELEPORT_TO_AREA -> handleTeleport(player, build, data);
                    case LeukocyteNetworking.BUILD_ACTION_ADD_BOX -> handleAddBox(player, build, data);
                    case LeukocyteNetworking.BUILD_ACTION_SUBTRACT_BOX -> handleSubtractBox(player, build, data);
                }
            });
        });
    }

    public static void sendBuildData(ServerPlayerEntity player) {
        var build = LeukocyteBuild.get(player.getServer().getOverworld());
        handleRequestData(player, build);
    }

    public static void sendBuildModeStatus(ServerPlayerEntity player, boolean inBuildMode) {
        var buf = PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.BUILD_RESPONSE_BUILD_MODE_STATUS);
        var root = new NbtCompound();
        root.putBoolean("in_build_mode", inBuildMode);
        buf.writeNbt(root);
        ServerPlayNetworking.send(player, LeukocyteNetworking.BUILD_S2C_CHANNEL, buf);
    }

    private static void handleRequestData(ServerPlayerEntity player, LeukocyteBuild build) {
        var buf = PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.BUILD_RESPONSE_DATA);

        var root = new NbtCompound();
        var list = new NbtList();
        for (var area : build.getAreas()) {
            var tag = new NbtCompound();
            tag.putString("name", area.name());
            tag.putString("dimension", area.dimension().getValue().toString());

            var subBoxes = new NbtList();
            for (int[] box : area.subBoxes()) {
                var arr = new NbtList();
                for (int v : box) arr.add(NbtInt.of(v));
                subBoxes.add(arr);
            }
            tag.put("sub_boxes", subBoxes);
            list.add(tag);
        }
        root.put("areas", list);

        var blocked = new NbtList();
        for (String item : build.getGlobalBlockedItems()) {
            blocked.add(NbtString.of(item));
        }
        root.put("global_blocked_items", blocked);

        buf.writeNbt(root);

        ServerPlayNetworking.send(player, LeukocyteNetworking.BUILD_S2C_CHANNEL, buf);
    }

    private static void handleCreate(ServerPlayerEntity player, LeukocyteBuild build, NbtCompound data) {
        String name = data.getString("name");
        if (name.isEmpty()) {
            sendResult(player, false, "Name cannot be empty.");
            return;
        }

        var dimId = Identifier.tryParse(data.getString("dimension"));
        if (dimId == null) {
            sendResult(player, false, "Invalid dimension.");
            return;
        }
        var dimension = RegistryKey.of(RegistryKeys.WORLD, dimId);
        var min = new BlockPos(data.getInt("min_x"), data.getInt("min_y"), data.getInt("min_z"));
        var max = new BlockPos(data.getInt("max_x"), data.getInt("max_y"), data.getInt("max_z"));

        var area = new BuildArea(name, dimension, min, max);
        if (build.addArea(area)) {
            sendResult(player, true, "Created build area '" + name + "'.");
        } else {
            sendResult(player, false, "Build area '" + name + "' already exists.");
        }
    }

    private static void handleRename(ServerPlayerEntity player, LeukocyteBuild build, NbtCompound data) {
        String oldName = data.getString("old_name");
        String newName = data.getString("new_name");
        if (newName.isEmpty()) {
            sendResult(player, false, "New name cannot be empty.");
            return;
        }

        var area = build.getArea(oldName);
        if (area == null) {
            sendResult(player, false, "Build area '" + oldName + "' not found.");
            return;
        }

        if (build.getArea(newName) != null) {
            sendResult(player, false, "Build area '" + newName + "' already exists.");
            return;
        }

        build.removeArea(oldName);
        build.addArea(new BuildArea(newName, area.dimension(), area.subBoxes()));
        sendResult(player, true, "Renamed '" + oldName + "' to '" + newName + "'.");
    }

    private static void handleDelete(ServerPlayerEntity player, LeukocyteBuild build, NbtCompound data) {
        String name = data.getString("name");
        var removed = build.removeArea(name);
        if (removed != null) {
            sendResult(player, true, "Deleted build area '" + name + "'.");
        } else {
            sendResult(player, false, "Build area '" + name + "' not found.");
        }
    }

    private static void handleAddBlockedItem(ServerPlayerEntity player, LeukocyteBuild build, NbtCompound data) {
        String itemId = data.getString("item");
        if (itemId.isEmpty()) {
            sendResult(player, false, "Item ID cannot be empty.");
            return;
        }

        if (build.addGlobalBlockedItem(itemId)) {
            sendResult(player, true, "Added '" + itemId + "' to global blocked items.");
        } else {
            sendResult(player, false, "'" + itemId + "' is already blocked.");
        }
    }

    private static void handleRemoveBlockedItem(ServerPlayerEntity player, LeukocyteBuild build, NbtCompound data) {
        String itemId = data.getString("item");
        if (build.removeGlobalBlockedItem(itemId)) {
            sendResult(player, true, "Removed '" + itemId + "' from global blocked items.");
        } else {
            sendResult(player, false, "'" + itemId + "' is not in the blocked items list.");
        }
    }

    private static void handleTeleport(ServerPlayerEntity player, LeukocyteBuild build, NbtCompound data) {
        String name = data.getString("name");
        var area = build.getArea(name);
        if (area == null) {
            sendResult(player, false, "Build area '" + name + "' not found.");
            return;
        }

        var center = area.center();
        var targetWorld = player.getServer().getWorld(area.dimension());
        if (targetWorld != null) {
            player.teleport(targetWorld, center.getX() + 0.5, center.getY(), center.getZ() + 0.5,
                player.getYaw(), player.getPitch());
            sendResult(player, true, "Teleported to '" + name + "'.");
        } else {
            sendResult(player, false, "Dimension not found.");
        }
    }

    private static void handleAddBox(ServerPlayerEntity player, LeukocyteBuild build, NbtCompound data) {
        String name = data.getString("name");
        var area = build.getArea(name);
        if (area == null) {
            sendResult(player, false, "Build area '" + name + "' not found.");
            return;
        }

        var a = new BlockPos(data.getInt("min_x"), data.getInt("min_y"), data.getInt("min_z"));
        var b = new BlockPos(data.getInt("max_x"), data.getInt("max_y"), data.getInt("max_z"));

        var newArea = BuildArea.addBox(area, a, b);
        build.replaceArea(name, newArea);
        sendResult(player, true, "Added box to '" + name + "'.");
    }

    private static void handleSubtractBox(ServerPlayerEntity player, LeukocyteBuild build, NbtCompound data) {
        String name = data.getString("name");
        var area = build.getArea(name);
        if (area == null) {
            sendResult(player, false, "Build area '" + name + "' not found.");
            return;
        }

        var a = new BlockPos(data.getInt("min_x"), data.getInt("min_y"), data.getInt("min_z"));
        var b = new BlockPos(data.getInt("max_x"), data.getInt("max_y"), data.getInt("max_z"));

        var newArea = BuildArea.subtractBox(area, a, b);
        if (newArea.subBoxes().isEmpty()) {
            build.removeArea(name);
            sendResult(player, true, "Subtraction removed all of '" + name + "'. Area deleted.");
        } else {
            build.replaceArea(name, newArea);
            sendResult(player, true, "Subtracted box from '" + name + "'.");
        }
    }

    private static void sendResult(ServerPlayerEntity player, boolean success, String message) {
        var buf = PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.BUILD_RESPONSE_RESULT);
        var root = new NbtCompound();
        root.putBoolean("success", success);
        root.putString("message", message);
        buf.writeNbt(root);
        ServerPlayNetworking.send(player, LeukocyteNetworking.BUILD_S2C_CHANNEL, buf);
    }
}
