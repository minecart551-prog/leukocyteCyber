package xyz.nucleoid.leukocyte.client.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import xyz.nucleoid.leukocyte.client.LeukocyteClient;
import xyz.nucleoid.leukocyte.client.tool.ShapeToolState;
import xyz.nucleoid.leukocyte.network.LeukocyteNetworking;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class ClientPacketHandler {
    private static Consumer<String> resultCallback;
    private static Consumer<ArrayList<AuthorityListEntry>> listCallback;
    private static Consumer<NbtCompound> detailCallback;
    private static Consumer<ArrayList<TestResultEntry>> testCallback;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(LeukocyteNetworking.S2C_CHANNEL, (client, handler, buf, responseSender) -> {
            byte responseType = buf.readByte();
            NbtCompound data = buf.readNbt();

            if (data == null) return;

            client.execute(() -> {
                switch (responseType) {
                    case LeukocyteNetworking.RESPONSE_AUTHORITY_LIST -> handleAuthorityList(data);
                    case LeukocyteNetworking.RESPONSE_AUTHORITY_DETAIL -> handleAuthorityDetail(data);
                    case LeukocyteNetworking.RESPONSE_RESULT -> handleResult(data);
                    case LeukocyteNetworking.RESPONSE_TEST_RESULT -> handleTestResult(data);
                    case LeukocyteNetworking.RESPONSE_OPEN_SCREEN -> handleOpenScreen();
                    case LeukocyteNetworking.RESPONSE_SHAPE_TOOL_DATA -> handleShapeToolData(data);
                }
            });
        });
    }

    public static void setListCallback(Consumer<ArrayList<AuthorityListEntry>> callback) {
        listCallback = callback;
    }

    public static void setDetailCallback(Consumer<NbtCompound> callback) {
        detailCallback = callback;
    }

    public static void setResultCallback(Consumer<String> callback) {
        resultCallback = callback;
    }

    public static void setTestCallback(Consumer<ArrayList<TestResultEntry>> callback) {
        testCallback = callback;
    }

    private static void handleAuthorityList(NbtCompound data) {
        var listTag = data.getList("authorities", NbtElement.COMPOUND_TYPE);
        var entries = new ArrayList<AuthorityListEntry>();

        for (int i = 0; i < listTag.size(); i++) {
            var entry = listTag.getCompound(i);
            String key = entry.getString("key");
            int level = entry.getInt("level");

            int shapeCount = 0;
            String shapeDisplay = "Empty";
            if (entry.contains("shapes", NbtElement.LIST_TYPE)) {
                var shapesList = entry.getList("shapes", NbtElement.COMPOUND_TYPE);
                shapeCount = shapesList.size();
                if (shapeCount > 0) {
                    shapeDisplay = shapeCount + " shape" + (shapeCount != 1 ? "s" : "");
                }
            }

            entries.add(new AuthorityListEntry(key, level, shapeCount, shapeDisplay));
        }

        if (listCallback != null) {
            listCallback.accept(entries);
        }
    }

    private static void handleAuthorityDetail(NbtCompound data) {
        if (detailCallback != null) {
            detailCallback.accept(data);
        }
    }

    private static void handleResult(NbtCompound data) {
        boolean success = data.getBoolean("success");
        String message = data.getString("message");
        if (resultCallback != null) {
            resultCallback.accept((success ? "§a" : "§c") + message);
        }
        if (success && xyz.nucleoid.leukocyte.client.tool.ShapeToolState.getInstance().isToolHeld()) {
            if (data.contains("createdAuthority") && data.contains("createdShapeName")) {
                String auth = data.getString("createdAuthority");
                String name = data.getString("createdShapeName");
                xyz.nucleoid.leukocyte.client.tool.ShapeToolState.setPendingAutoSelect(auth, name);
            }
            requestShapeToolData();
        }
    }

    private static void handleTestResult(NbtCompound data) {
        var listTag = data.getList("authorities", NbtElement.COMPOUND_TYPE);
        var entries = new ArrayList<TestResultEntry>();

        for (int i = 0; i < listTag.size(); i++) {
            var authTag = listTag.getCompound(i);
            String key = authTag.getString("key");
            var rulesTag = authTag.getCompound("rules");
            var rules = new LinkedHashMap<String, String>();

            for (String ruleKey : rulesTag.getKeys()) {
                rules.put(ruleKey, rulesTag.getString(ruleKey));
            }

            entries.add(new TestResultEntry(key, rules));
        }

        if (testCallback != null) {
            testCallback.accept(entries);
        }
    }

    private static void handleOpenScreen() {
        LeukocyteClient.requestOpenScreen();
    }

    private static void handleShapeToolData(NbtCompound data) {
        var shapesList = data.getList("shapes", NbtElement.COMPOUND_TYPE);
        var merged = new LinkedHashMap<String, ShapeToolState.ShapeEntry>();

        for (int i = 0; i < shapesList.size(); i++) {
            var tag = shapesList.getCompound(i);
            String authority = tag.getString("authority");
            String name = tag.getString("name");
            String type = tag.getString("type");
            String dimStr = tag.getString("dimension");

            var dimId = Identifier.tryParse(dimStr);
            RegistryKey<World> dimension = dimId != null
                ? RegistryKey.of(RegistryKeys.WORLD, dimId) : null;

            BlockPos min = null;
            BlockPos max = null;
            if (tag.contains("min_x")) {
                min = new BlockPos(tag.getInt("min_x"), tag.getInt("min_y"), tag.getInt("min_z"));
                max = new BlockPos(tag.getInt("max_x"), tag.getInt("max_y"), tag.getInt("max_z"));
            }

            String key = authority + "\0" + name;
            boolean enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
            var existing = merged.get(key);
            if (existing != null) {
                var subBoxes = new ArrayList<>(existing.subBoxes());
                if (min != null && max != null) {
                    subBoxes.add(new int[]{min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ()});
                }
                BlockPos newMin = existing.min();
                BlockPos newMax = existing.max();
                if (min != null && newMin != null) {
                    newMin = new BlockPos(Math.min(min.getX(), newMin.getX()), Math.min(min.getY(), newMin.getY()), Math.min(min.getZ(), newMin.getZ()));
                    newMax = new BlockPos(Math.max(max.getX(), newMax.getX()), Math.max(max.getY(), newMax.getY()), Math.max(max.getZ(), newMax.getZ()));
                }
                merged.put(key, new ShapeToolState.ShapeEntry(authority, name, existing.type(),
                    dimension != null ? dimension : existing.dimension(), newMin, newMax,
                    existing.subShapeCount() + 1, subBoxes, existing.enabled() && enabled));
            } else {
                var subBoxes = new ArrayList<int[]>();
                if (min != null && max != null) {
                    subBoxes.add(new int[]{min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ()});
                }
                merged.put(key, new ShapeToolState.ShapeEntry(authority, name, type, dimension, min, max, 0, subBoxes, enabled));
            }
        }

        ShapeToolState state = ShapeToolState.getInstance();

        var authList = data.getList("authorities", NbtElement.STRING_TYPE);
        var keys = new ArrayList<String>();
        for (int i = 0; i < authList.size(); i++) {
            keys.add(authList.getString(i));
        }
        state.setAuthorityKeys(keys);

        state.setShapeEntries(new ArrayList<>(merged.values()));
        state.tryRestoreSelection();

        xyz.nucleoid.leukocyte.client.render.ShapeRenderer.getInstance().markNeedsRebuild();
    }

    public static void requestShapeToolData() {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.ACTION_REQUEST_SHAPE_TOOL_DATA);
        buf.writeNbt(new NbtCompound());
        ClientPlayNetworking.send(LeukocyteNetworking.C2S_CHANNEL, buf);
    }

    public static void createBoxShape(String authorityKey, String dimensionId, BlockPos min, BlockPos max) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.ACTION_CREATE_BOX_SHAPE);
        var data = new NbtCompound();
        data.putString("authority", authorityKey);
        data.putString("dimension", dimensionId);
        data.putInt("min_x", min.getX());
        data.putInt("min_y", min.getY());
        data.putInt("min_z", min.getZ());
        data.putInt("max_x", max.getX());
        data.putInt("max_y", max.getY());
        data.putInt("max_z", max.getZ());
        buf.writeNbt(data);
        ClientPlayNetworking.send(LeukocyteNetworking.C2S_CHANNEL, buf);
    }

    public static void renameShape(String authorityKey, String oldName, String newName) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.ACTION_RENAME_SHAPE);
        var data = new NbtCompound();
        data.putString("authority", authorityKey);
        data.putString("old_name", oldName);
        data.putString("new_name", newName);
        buf.writeNbt(data);
        ClientPlayNetworking.send(LeukocyteNetworking.C2S_CHANNEL, buf);
    }

    public static void combineShapes(String authorityKey, String shapeA, String shapeB) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.ACTION_COMBINE_SHAPES);
        var data = new NbtCompound();
        data.putString("authority", authorityKey);
        data.putString("shape_a", shapeA);
        data.putString("shape_b", shapeB);
        buf.writeNbt(data);
        ClientPlayNetworking.send(LeukocyteNetworking.C2S_CHANNEL, buf);
    }

    public static void teleportToShape(String authorityKey, String shapeName) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.ACTION_TELEPORT_TO_SHAPE);
        var data = new NbtCompound();
        data.putString("authority", authorityKey);
        data.putString("shape_name", shapeName);
        buf.writeNbt(data);
        ClientPlayNetworking.send(LeukocyteNetworking.C2S_CHANNEL, buf);
    }

    public static void subtractBoxFromShape(String authorityKey, String shapeName, String dimensionId, BlockPos min, BlockPos max) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.ACTION_SUBTRACT_BOX);
        var data = new NbtCompound();
        data.putString("authority", authorityKey);
        data.putString("shape_name", shapeName);
        data.putString("dimension", dimensionId);
        data.putInt("min_x", min.getX());
        data.putInt("min_y", min.getY());
        data.putInt("min_z", min.getZ());
        data.putInt("max_x", max.getX());
        data.putInt("max_y", max.getY());
        data.putInt("max_z", max.getZ());
        buf.writeNbt(data);
        ClientPlayNetworking.send(LeukocyteNetworking.C2S_CHANNEL, buf);
    }

    public static void addBoxToShape(String authorityKey, String shapeName, String dimensionId, BlockPos min, BlockPos max) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.ACTION_ADD_BOX_TO_SHAPE);
        var data = new NbtCompound();
        data.putString("authority", authorityKey);
        data.putString("shape_name", shapeName);
        data.putString("dimension", dimensionId);
        data.putInt("min_x", min.getX());
        data.putInt("min_y", min.getY());
        data.putInt("min_z", min.getZ());
        data.putInt("max_x", max.getX());
        data.putInt("max_y", max.getY());
        data.putInt("max_z", max.getZ());
        buf.writeNbt(data);
        ClientPlayNetworking.send(LeukocyteNetworking.C2S_CHANNEL, buf);
    }

    public static void requestAll() {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.ACTION_REQUEST_ALL);
        buf.writeNbt(new NbtCompound());
        ClientPlayNetworking.send(LeukocyteNetworking.C2S_CHANNEL, buf);
    }

    public static void requestDetail(String authorityKey) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.ACTION_REQUEST_DETAIL);
        var data = new NbtCompound();
        data.putString("authority", authorityKey);
        buf.writeNbt(data);
        ClientPlayNetworking.send(LeukocyteNetworking.C2S_CHANNEL, buf);
    }

    public static void addAuthority(String name) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.ACTION_ADD_AUTHORITY);
        var data = new NbtCompound();
        data.putString("name", name);
        buf.writeNbt(data);
        ClientPlayNetworking.send(LeukocyteNetworking.C2S_CHANNEL, buf);
    }

    public static void removeAuthority(String key) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.ACTION_REMOVE_AUTHORITY);
        var data = new NbtCompound();
        data.putString("authority", key);
        buf.writeNbt(data);
        ClientPlayNetworking.send(LeukocyteNetworking.C2S_CHANNEL, buf);
    }

    public static void setLevel(String key, int level) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.ACTION_SET_LEVEL);
        var data = new NbtCompound();
        data.putString("authority", key);
        data.putInt("level", level);
        buf.writeNbt(data);
        ClientPlayNetworking.send(LeukocyteNetworking.C2S_CHANNEL, buf);
    }

    public static void setRule(String authorityKey, String ruleKey, String resultKey) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.ACTION_SET_RULE);
        var data = new NbtCompound();
        data.putString("authority", authorityKey);
        data.putString("rule", ruleKey);
        data.putString("result", resultKey);
        buf.writeNbt(data);
        ClientPlayNetworking.send(LeukocyteNetworking.C2S_CHANNEL, buf);
    }

    public static void addShape(String authorityKey, String shapeName, net.minecraft.nbt.NbtList subShapes) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.ACTION_ADD_SHAPE);
        var data = new NbtCompound();
        data.putString("authority", authorityKey);
        data.putString("shape_name", shapeName);
        data.put("sub_shapes", subShapes);
        buf.writeNbt(data);
        ClientPlayNetworking.send(LeukocyteNetworking.C2S_CHANNEL, buf);
    }

    public static void setShape(String authorityKey, String shapeName, net.minecraft.nbt.NbtList subShapes) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.ACTION_SET_SHAPE);
        var data = new NbtCompound();
        data.putString("authority", authorityKey);
        data.putString("shape_name", shapeName);
        data.put("sub_shapes", subShapes);
        buf.writeNbt(data);
        ClientPlayNetworking.send(LeukocyteNetworking.C2S_CHANNEL, buf);
    }

    public static void removeShape(String authorityKey, String shapeName) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.ACTION_REMOVE_SHAPE);
        var data = new NbtCompound();
        data.putString("authority", authorityKey);
        data.putString("shape_name", shapeName);
        buf.writeNbt(data);
        ClientPlayNetworking.send(LeukocyteNetworking.C2S_CHANNEL, buf);
    }

    public static void setShapeEnabled(String authorityKey, String shapeName, boolean enabled) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.ACTION_SET_SHAPE_ENABLED);
        var data = new NbtCompound();
        data.putString("authority", authorityKey);
        data.putString("shape_name", shapeName);
        data.putBoolean("enabled", enabled);
        buf.writeNbt(data);
        ClientPlayNetworking.send(LeukocyteNetworking.C2S_CHANNEL, buf);
    }

    public static void addExclusion(String authorityKey, byte type, String value) {
        sendExclusionAction(LeukocyteNetworking.ACTION_ADD_EXCLUSION, authorityKey, type, value);
    }

    public static void removeExclusion(String authorityKey, byte type, String value) {
        sendExclusionAction(LeukocyteNetworking.ACTION_REMOVE_EXCLUSION, authorityKey, type, value);
    }

    public static void addInclusion(String authorityKey, byte type, String value) {
        sendExclusionAction(LeukocyteNetworking.ACTION_ADD_INCLUSION, authorityKey, type, value);
    }

    public static void removeInclusion(String authorityKey, byte type, String value) {
        sendExclusionAction(LeukocyteNetworking.ACTION_REMOVE_INCLUSION, authorityKey, type, value);
    }

    public static void testRules() {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.ACTION_TEST_RULES);
        buf.writeNbt(new NbtCompound());
        ClientPlayNetworking.send(LeukocyteNetworking.C2S_CHANNEL, buf);
    }

    private static void sendExclusionAction(byte action, String authorityKey, byte type, String value) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(action);
        var data = new NbtCompound();
        data.putString("authority", authorityKey);
        data.putByte("type", type);
        data.putString("value", value);
        buf.writeNbt(data);
        ClientPlayNetworking.send(LeukocyteNetworking.C2S_CHANNEL, buf);
    }

    public record AuthorityListEntry(String key, int level, int shapeCount, String shapeDisplay) {
    }

    public record TestResultEntry(String authorityKey, Map<String, String> rules) {
    }
}
