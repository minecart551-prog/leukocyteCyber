package xyz.nucleoid.leukocyte.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import xyz.nucleoid.leukocyte.Leukocyte;
import xyz.nucleoid.leukocyte.authority.Authority;
import xyz.nucleoid.leukocyte.rule.ProtectionRule;
import xyz.nucleoid.leukocyte.rule.RuleResult;
import xyz.nucleoid.leukocyte.shape.ProtectionShape;

import java.util.ArrayList;

public final class ServerPacketHandler {
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(LeukocyteNetworking.C2S_CHANNEL, (server, player, handler, buf, responseSender) -> {
            byte action = buf.readByte();
            NbtCompound data = buf.readNbt();

            if (data == null) return;

            server.execute(() -> {
                if (!player.hasPermissionLevel(4)) {
                    sendResult(player, false, "You do not have permission to use this.");
                    return;
                }

                var leukocyte = Leukocyte.get(server);

                switch (action) {
                    case LeukocyteNetworking.ACTION_REQUEST_ALL -> handleRequestAll(player, leukocyte);
                    case LeukocyteNetworking.ACTION_REQUEST_DETAIL -> handleRequestDetail(player, leukocyte, data);
                    case LeukocyteNetworking.ACTION_ADD_AUTHORITY -> handleAddAuthority(player, leukocyte, data);
                    case LeukocyteNetworking.ACTION_REMOVE_AUTHORITY -> handleRemoveAuthority(player, leukocyte, data);
                    case LeukocyteNetworking.ACTION_SET_LEVEL -> handleSetLevel(player, leukocyte, data);
                    case LeukocyteNetworking.ACTION_SET_RULE -> handleSetRule(player, leukocyte, data);
                    case LeukocyteNetworking.ACTION_ADD_SHAPE -> handleAddShape(player, leukocyte, data);
                    case LeukocyteNetworking.ACTION_REMOVE_SHAPE -> handleRemoveShape(player, leukocyte, data);
                    case LeukocyteNetworking.ACTION_SET_SHAPE -> handleSetShape(player, leukocyte, data);
                    case LeukocyteNetworking.ACTION_ADD_EXCLUSION -> handleAddExclusion(player, leukocyte, data, false);
                    case LeukocyteNetworking.ACTION_REMOVE_EXCLUSION -> handleRemoveExclusion(player, leukocyte, data, false);
                    case LeukocyteNetworking.ACTION_ADD_INCLUSION -> handleAddExclusion(player, leukocyte, data, true);
                    case LeukocyteNetworking.ACTION_REMOVE_INCLUSION -> handleRemoveExclusion(player, leukocyte, data, true);
                    case LeukocyteNetworking.ACTION_TEST_RULES -> handleTestRules(player, leukocyte);
                    case LeukocyteNetworking.ACTION_REQUEST_SHAPE_TOOL_DATA -> handleRequestShapeToolData(player, leukocyte, data);
                    case LeukocyteNetworking.ACTION_CREATE_BOX_SHAPE -> handleCreateBoxShape(player, leukocyte, data);
                    case LeukocyteNetworking.ACTION_RENAME_SHAPE -> handleRenameShape(player, leukocyte, data);
                    case LeukocyteNetworking.ACTION_COMBINE_SHAPES -> handleCombineShapes(player, leukocyte, data);
                    case LeukocyteNetworking.ACTION_TELEPORT_TO_SHAPE -> handleTeleportToShape(player, leukocyte, data);
                    case LeukocyteNetworking.ACTION_SUBTRACT_BOX -> handleSubtractBox(player, leukocyte, data);
                    case LeukocyteNetworking.ACTION_ADD_BOX_TO_SHAPE -> handleAddBoxToShape(player, leukocyte, data);
                    case LeukocyteNetworking.ACTION_SET_SHAPE_ENABLED -> handleSetShapeEnabled(player, leukocyte, data);
                }
            });
        });
    }

    private static void handleRequestAll(ServerPlayerEntity player, Leukocyte leukocyte) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.RESPONSE_AUTHORITY_LIST);

        var listTag = new net.minecraft.nbt.NbtList();
        for (var authority : leukocyte.getAuthorities()) {
            var entry = new NbtCompound();
            entry.putString("key", authority.getKey());
            entry.putInt("level", authority.getLevel());
            entry.put("shapes", Authority.CODEC.encodeStart(NbtOps.INSTANCE, authority)
                    .result().map(n -> ((NbtCompound) n).get("shapes")).orElse(new NbtCompound()));
            listTag.add(entry);
        }
        var root = new NbtCompound();
        root.put("authorities", listTag);
        buf.writeNbt(root);

        ServerPlayNetworking.send(player, LeukocyteNetworking.S2C_CHANNEL, buf);
    }

    private static void handleRequestDetail(ServerPlayerEntity player, Leukocyte leukocyte, NbtCompound data) {
        String key = data.getString("authority");
        var authority = leukocyte.getAuthorityByKey(key);

        if (authority == null) {
            sendResult(player, false, "Authority '" + key + "' not found.");
            return;
        }

        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.RESPONSE_AUTHORITY_DETAIL);

        var root = (NbtCompound) Authority.CODEC.encodeStart(NbtOps.INSTANCE, authority).result().orElse(new NbtCompound());
        buf.writeNbt(root);

        ServerPlayNetworking.send(player, LeukocyteNetworking.S2C_CHANNEL, buf);
    }

    private static void handleAddAuthority(ServerPlayerEntity player, Leukocyte leukocyte, NbtCompound data) {
        String key = data.getString("name");
        if (key.isEmpty()) {
            sendResult(player, false, "Name cannot be empty.");
            return;
        }

        var authority = Authority.create(key);
        if (leukocyte.addAuthority(authority)) {
            sendResult(player, true, "Added authority '" + key + "'.");
        } else {
            sendResult(player, false, "Authority '" + key + "' already exists.");
        }
    }

    private static void handleRemoveAuthority(ServerPlayerEntity player, Leukocyte leukocyte, NbtCompound data) {
        String key = data.getString("authority");
        var removed = leukocyte.removeAuthority(key);
        if (removed != null) {
            sendResult(player, true, "Removed authority '" + key + "'.");
        } else {
            sendResult(player, false, "Authority '" + key + "' not found.");
        }
    }

    private static void handleSetLevel(ServerPlayerEntity player, Leukocyte leukocyte, NbtCompound data) {
        String key = data.getString("authority");
        int level = data.getInt("level");

        var authority = leukocyte.getAuthorityByKey(key);
        if (authority == null) {
            sendResult(player, false, "Authority '" + key + "' not found.");
            return;
        }

        var newAuthority = authority.withLevel(level);
        leukocyte.replaceAuthority(authority, newAuthority);
        sendResult(player, true, "Set level of '" + key + "' to " + level + ".");
    }

    private static void handleSetRule(ServerPlayerEntity player, Leukocyte leukocyte, NbtCompound data) {
        String key = data.getString("authority");
        String ruleKey = data.getString("rule");
        String resultKey = data.getString("result");

        var authority = leukocyte.getAuthorityByKey(key);
        if (authority == null) {
            sendResult(player, false, "Authority '" + key + "' not found.");
            return;
        }

        var rule = ProtectionRule.byKey(ruleKey);
        if (rule == null) {
            sendResult(player, false, "Unknown rule '" + ruleKey + "'.");
            return;
        }

        var result = RuleResult.byKey(resultKey);
        if (result == null) {
            sendResult(player, false, "Unknown result '" + resultKey + "'.");
            return;
        }

        var newAuthority = authority.withRule(rule, result);
        leukocyte.replaceAuthority(authority, newAuthority);
        sendResult(player, true, "Set rule " + ruleKey + " = " + resultKey + " for '" + key + "'.");
    }

    private static void handleAddShape(ServerPlayerEntity player, Leukocyte leukocyte, NbtCompound data) {
        String key = data.getString("authority");
        String shapeName = data.getString("shape_name");

        var authority = leukocyte.getAuthorityByKey(key);
        if (authority == null) {
            sendResult(player, false, "Authority '" + key + "' not found.");
            return;
        }

        var subShapes = data.getList("sub_shapes", NbtElement.COMPOUND_TYPE);
        var shape = decodeCompositeShape(subShapes);
        if (shape == null) {
            sendResult(player, false, "Invalid shape data.");
            return;
        }

        var newAuthority = authority.addShape(shapeName, shape);
        leukocyte.replaceAuthority(authority, newAuthority);
        sendResult(player, true, "Added shape '" + shapeName + "' to '" + key + "'.");
    }

    private static void handleSetShape(ServerPlayerEntity player, Leukocyte leukocyte, NbtCompound data) {
        String key = data.getString("authority");
        String shapeName = data.getString("shape_name");

        var authority = leukocyte.getAuthorityByKey(key);
        if (authority == null) {
            sendResult(player, false, "Authority '" + key + "' not found.");
            return;
        }

        var subShapes = data.getList("sub_shapes", NbtElement.COMPOUND_TYPE);
        var shape = decodeCompositeShape(subShapes);
        if (shape == null) {
            sendResult(player, false, "Invalid shape data.");
            return;
        }

        var newAuthority = authority.removeShape(shapeName).addShape(shapeName, shape);
        leukocyte.replaceAuthority(authority, newAuthority);
        sendResult(player, true, "Updated shape '" + shapeName + "' in '" + key + "'.");
    }

    private static ProtectionShape decodeCompositeShape(net.minecraft.nbt.NbtList subShapes) {
        if (subShapes.isEmpty()) return null;

        var shapes = new java.util.ArrayList<ProtectionShape>();
        for (int i = 0; i < subShapes.size(); i++) {
            var sub = subShapes.getCompound(i);
            var shape = decodeSingleShape(sub);
            if (shape == null) return null;
            shapes.add(shape);
        }

        if (shapes.size() == 1) {
            return shapes.get(0);
        }
        return ProtectionShape.union(shapes.toArray(new ProtectionShape[0]));
    }

    private static ProtectionShape decodeSingleShape(NbtCompound sub) {
        String type = sub.getString("type");
        return switch (type) {
            case "universal" -> ProtectionShape.universe();
            case "dimension" -> {
                var dimId = Identifier.tryParse(sub.getString("dimension"));
                if (dimId == null) yield null;
                yield ProtectionShape.dimension(RegistryKey.of(RegistryKeys.WORLD, dimId));
            }
            case "box" -> {
                var dimId = Identifier.tryParse(sub.getString("dimension"));
                if (dimId == null) yield null;
                var dimension = RegistryKey.of(RegistryKeys.WORLD, dimId);
                var min = new BlockPos(sub.getInt("min_x"), sub.getInt("min_y"), sub.getInt("min_z"));
                var max = new BlockPos(sub.getInt("max_x"), sub.getInt("max_y"), sub.getInt("max_z"));
                yield ProtectionShape.box(dimension, min, max);
            }
            default -> null;
        };
    }

    private static void handleRemoveShape(ServerPlayerEntity player, Leukocyte leukocyte, NbtCompound data) {
        String key = data.getString("authority");
        String shapeName = data.getString("shape_name");

        var authority = leukocyte.getAuthorityByKey(key);
        if (authority == null) {
            sendResult(player, false, "Authority '" + key + "' not found.");
            return;
        }

        var newAuthority = authority.removeShape(shapeName);
        if (newAuthority == authority) {
            sendResult(player, false, "Shape '" + shapeName + "' not found.");
            return;
        }

        leukocyte.replaceAuthority(authority, newAuthority);
        sendResult(player, true, "Removed shape '" + shapeName + "' from '" + key + "'.");
    }

    private static void handleSetShapeEnabled(ServerPlayerEntity player, Leukocyte leukocyte, NbtCompound data) {
        String key = data.getString("authority");
        String shapeName = data.getString("shape_name");
        boolean enabled = data.getBoolean("enabled");

        var authority = leukocyte.getAuthorityByKey(key);
        if (authority == null) {
            sendResult(player, false, "Authority '" + key + "' not found.");
            return;
        }

        var newAuthority = authority.withShapeEnabled(shapeName, enabled);
        if (newAuthority == authority) {
            sendResult(player, false, "Shape '" + shapeName + "' not found or already " + (enabled ? "enabled" : "disabled") + ".");
            return;
        }

        leukocyte.replaceAuthority(authority, newAuthority);
        sendResult(player, true, (enabled ? "Enabled" : "Disabled") + " shape '" + shapeName + "' in '" + key + "'.");
    }

    private static void handleAddExclusion(ServerPlayerEntity player, Leukocyte leukocyte, NbtCompound data, boolean isInclusion) {
        String key = data.getString("authority");
        byte type = data.getByte("type");
        String value = data.getString("value");

        var authority = leukocyte.getAuthorityByKey(key);
        if (authority == null) {
            sendResult(player, false, "Authority '" + key + "' not found.");
            return;
        }

        boolean success;
        String kind = isInclusion ? "inclusion" : "exclusion";

        if (isInclusion) {
            var inclusions = authority.getIncluded();
            success = switch (type) {
                case LeukocyteNetworking.EXCLUSION_TYPE_ROLE -> inclusions.addRole(value);
                case LeukocyteNetworking.EXCLUSION_TYPE_PERMISSION -> inclusions.addPermission(value);
                default -> false;
            };
        } else {
            var exclusions = authority.getExclusions();
            success = switch (type) {
                case LeukocyteNetworking.EXCLUSION_TYPE_ROLE -> exclusions.addRole(value);
                case LeukocyteNetworking.EXCLUSION_TYPE_PERMISSION -> exclusions.addPermission(value);
                default -> false;
            };
        }

        if (success) {
            sendResult(player, true, "Added " + kind + " '" + value + "' to '" + key + "'.");
        } else {
            sendResult(player, false, "Failed to add " + kind + " (may already exist).");
        }
    }

    private static void handleRemoveExclusion(ServerPlayerEntity player, Leukocyte leukocyte, NbtCompound data, boolean isInclusion) {
        String key = data.getString("authority");
        byte type = data.getByte("type");
        String value = data.getString("value");

        var authority = leukocyte.getAuthorityByKey(key);
        if (authority == null) {
            sendResult(player, false, "Authority '" + key + "' not found.");
            return;
        }

        boolean success;
        String kind = isInclusion ? "inclusion" : "exclusion";

        if (isInclusion) {
            var inclusions = authority.getIncluded();
            success = switch (type) {
                case LeukocyteNetworking.EXCLUSION_TYPE_ROLE -> inclusions.removeRole(value);
                case LeukocyteNetworking.EXCLUSION_TYPE_PERMISSION -> inclusions.removePermission(value);
                default -> false;
            };
        } else {
            var exclusions = authority.getExclusions();
            success = switch (type) {
                case LeukocyteNetworking.EXCLUSION_TYPE_ROLE -> exclusions.removeRole(value);
                case LeukocyteNetworking.EXCLUSION_TYPE_PERMISSION -> exclusions.removePermission(value);
                default -> false;
            };
        }

        if (success) {
            sendResult(player, true, "Removed " + kind + " '" + value + "' from '" + key + "'.");
        } else {
            sendResult(player, false, "Failed to remove " + kind + " (may not exist).");
        }
    }

    private static void handleTestRules(ServerPlayerEntity player, Leukocyte leukocyte) {
        var eventSource = xyz.nucleoid.stimuli.EventSource.forEntity(player);
        var applicable = new ArrayList<Authority>();

        for (var authority : leukocyte.getAuthorities()) {
            if (authority.getEventFilter().accepts(eventSource)) {
                applicable.add(authority);
            }
        }

        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.RESPONSE_TEST_RESULT);

        var root = new NbtCompound();
        var listTag = new net.minecraft.nbt.NbtList();

        for (var authority : applicable) {
            var authTag = new NbtCompound();
            authTag.putString("key", authority.getKey());
            var rulesTag = new NbtCompound();
            for (var rule : ProtectionRule.REGISTRY) {
                var result = authority.getRules().test(rule);
                if (result != RuleResult.PASS) {
                    rulesTag.putString(rule.getKey(), result.getKey());
                }
            }
            authTag.put("rules", rulesTag);
            listTag.add(authTag);
        }

        root.put("authorities", listTag);
        buf.writeNbt(root);

        ServerPlayNetworking.send(player, LeukocyteNetworking.S2C_CHANNEL, buf);
    }

    private static void handleRequestShapeToolData(ServerPlayerEntity player, Leukocyte leukocyte, NbtCompound data) {
        var currentDim = player.getWorld().getRegistryKey();
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.RESPONSE_SHAPE_TOOL_DATA);

        var root = new NbtCompound();
        var listTag = new net.minecraft.nbt.NbtList();

        for (var authority : leukocyte.getAuthorities()) {
            for (var entry : authority.getShapes().entries) {
                extractBoxShapes(listTag, authority.getKey(), entry.name(), entry.shape(), currentDim, entry.enabled());
            }
        }

        var authList = new net.minecraft.nbt.NbtList();
        for (var authority : leukocyte.getAuthorities()) {
            authList.add(NbtString.of(authority.getKey()));
        }

        root.put("shapes", listTag);
        root.put("authorities", authList);
        buf.writeNbt(root);

        ServerPlayNetworking.send(player, LeukocyteNetworking.S2C_CHANNEL, buf);
    }

    private static void extractBoxShapes(net.minecraft.nbt.NbtList listTag, String authorityKey, String shapeName,
                                          xyz.nucleoid.leukocyte.shape.ProtectionShape shape,
                                          net.minecraft.registry.RegistryKey<net.minecraft.world.World> currentDim,
                                          boolean enabled) {
        if (shape instanceof xyz.nucleoid.leukocyte.shape.BoxShape box) {
            addBoxEntry(listTag, authorityKey, shapeName, "box", box, currentDim, enabled);
        } else if (shape instanceof xyz.nucleoid.leukocyte.shape.DimensionShape dim) {
            var dimEntry = new NbtCompound();
            dimEntry.putString("authority", authorityKey);
            dimEntry.putString("name", shapeName);
            dimEntry.putString("type", "dimension");
            dimEntry.putString("dimension", dim.getDimension().getValue().toString());
            dimEntry.putBoolean("in_current_dim", dim.getDimension().equals(currentDim));
            dimEntry.putBoolean("enabled", enabled);
            listTag.add(dimEntry);
        } else if (shape instanceof xyz.nucleoid.leukocyte.shape.UnionShape union) {
            for (var sub : union.getScopes()) {
                extractBoxShapes(listTag, authorityKey, shapeName, sub, currentDim, enabled);
            }
        } else if (shape instanceof xyz.nucleoid.leukocyte.shape.UniversalShape) {
            var uniEntry = new NbtCompound();
            uniEntry.putString("authority", authorityKey);
            uniEntry.putString("name", shapeName);
            uniEntry.putString("type", "universal");
            uniEntry.putBoolean("in_current_dim", true);
            uniEntry.putBoolean("enabled", enabled);
            listTag.add(uniEntry);
        }
    }

    private static void addBoxEntry(net.minecraft.nbt.NbtList listTag, String authorityKey, String shapeName,
                                     String type, xyz.nucleoid.leukocyte.shape.BoxShape box,
                                     net.minecraft.registry.RegistryKey<net.minecraft.world.World> currentDim,
                                     boolean enabled) {
        var entry = new NbtCompound();
        entry.putString("authority", authorityKey);
        entry.putString("name", shapeName);
        entry.putString("type", type);
        entry.putString("dimension", box.getDimension().getValue().toString());
        entry.putBoolean("in_current_dim", box.getDimension().equals(currentDim));
        entry.putBoolean("enabled", enabled);
        var min = box.getMin();
        var max = box.getMax();
        entry.putInt("min_x", min.getX());
        entry.putInt("min_y", min.getY());
        entry.putInt("min_z", min.getZ());
        entry.putInt("max_x", max.getX());
        entry.putInt("max_y", max.getY());
        entry.putInt("max_z", max.getZ());
        listTag.add(entry);
    }

    private static void handleCreateBoxShape(ServerPlayerEntity player, Leukocyte leukocyte, NbtCompound data) {
        String key = data.getString("authority");
        var authority = leukocyte.getAuthorityByKey(key);
        if (authority == null) {
            sendResult(player, false, "Authority '" + key + "' not found.");
            return;
        }

        var dimId = net.minecraft.util.Identifier.tryParse(data.getString("dimension"));
        if (dimId == null) {
            sendResult(player, false, "Invalid dimension.");
            return;
        }
        var dimension = net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, dimId);
        var min = new BlockPos(data.getInt("min_x"), data.getInt("min_y"), data.getInt("min_z"));
        var max = new BlockPos(data.getInt("max_x"), data.getInt("max_y"), data.getInt("max_z"));

        String shapeName = "shape_" + System.currentTimeMillis();
        var shape = ProtectionShape.box(dimension, min, max);
        var newAuthority = authority.addShape(shapeName, shape);
        leukocyte.replaceAuthority(authority, newAuthority);
        sendResultWithShape(player, true, "Created shape '" + shapeName + "' in '" + key + "'.", key, shapeName);
    }

    private static void handleRenameShape(ServerPlayerEntity player, Leukocyte leukocyte, NbtCompound data) {
        String key = data.getString("authority");
        String oldName = data.getString("old_name");
        String newName = data.getString("new_name");

        var authority = leukocyte.getAuthorityByKey(key);
        if (authority == null) {
            sendResult(player, false, "Authority '" + key + "' not found.");
            return;
        }

        xyz.nucleoid.leukocyte.shape.ProtectionShape shape = null;
        for (var entry : authority.getShapes().entries) {
            if (entry.name().equals(oldName)) {
                shape = entry.shape();
                break;
            }
        }

        if (shape == null) {
            sendResult(player, false, "Shape '" + oldName + "' not found.");
            return;
        }

        var removed = authority.removeShape(oldName);
        var newAuthority = removed.addShape(newName, shape);
        leukocyte.replaceAuthority(authority, newAuthority);
        sendResult(player, true, "Renamed '" + oldName + "' to '" + newName + "'.");
    }

    private static void handleCombineShapes(ServerPlayerEntity player, Leukocyte leukocyte, NbtCompound data) {
        String key = data.getString("authority");
        String shapeA = data.getString("shape_a");
        String shapeB = data.getString("shape_b");

        var authority = leukocyte.getAuthorityByKey(key);
        if (authority == null) {
            sendResult(player, false, "Authority '" + key + "' not found.");
            return;
        }

        xyz.nucleoid.leukocyte.shape.ProtectionShape shapeAShape = null;
        xyz.nucleoid.leukocyte.shape.ProtectionShape shapeBShape = null;
        for (var entry : authority.getShapes().entries) {
            if (entry.name().equals(shapeA)) shapeAShape = entry.shape();
            if (entry.name().equals(shapeB)) shapeBShape = entry.shape();
        }

        if (shapeAShape == null || shapeBShape == null) {
            sendResult(player, false, "One or both shapes not found.");
            return;
        }

        var union = ProtectionShape.union(shapeAShape, shapeBShape);
        var removed = authority.removeShape(shapeA).removeShape(shapeB);
        var newAuthority = removed.addShape(shapeA, union);
        leukocyte.replaceAuthority(authority, newAuthority);
        sendResult(player, true, "Combined '" + shapeA + "' and '" + shapeB + "' into UnionShape.");
    }

    private static void handleTeleportToShape(ServerPlayerEntity player, Leukocyte leukocyte, NbtCompound data) {
        String key = data.getString("authority");
        String shapeName = data.getString("shape_name");

        var authority = leukocyte.getAuthorityByKey(key);
        if (authority == null) {
            sendResult(player, false, "Authority '" + key + "' not found.");
            return;
        }

        xyz.nucleoid.leukocyte.shape.ProtectionShape shape = null;
        for (var entry : authority.getShapes().entries) {
            if (entry.name().equals(shapeName)) {
                shape = entry.shape();
                break;
            }
        }

        if (shape == null) {
            sendResult(player, false, "Shape '" + shapeName + "' not found.");
            return;
        }

        BlockPos teleportTarget = null;
        if (shape instanceof xyz.nucleoid.leukocyte.shape.BoxShape box) {
            teleportTarget = new BlockPos(
                (box.getMin().getX() + box.getMax().getX()) / 2,
                box.getMin().getY(),
                (box.getMin().getZ() + box.getMax().getZ()) / 2
            );
        } else if (shape instanceof xyz.nucleoid.leukocyte.shape.UnionShape union) {
            for (var sub : union.getScopes()) {
                if (sub instanceof xyz.nucleoid.leukocyte.shape.BoxShape subBox) {
                    teleportTarget = new BlockPos(
                        (subBox.getMin().getX() + subBox.getMax().getX()) / 2,
                        subBox.getMin().getY(),
                        (subBox.getMin().getZ() + subBox.getMax().getZ()) / 2
                    );
                    break;
                }
            }
        }

        if (teleportTarget == null) {
            sendResult(player, false, "Shape has no box to teleport to.");
            return;
        }

        var targetDim = xyz.nucleoid.leukocyte.shape.BoxShape.class.isInstance(shape)
            ? ((xyz.nucleoid.leukocyte.shape.BoxShape) shape).getDimension()
            : player.getWorld().getRegistryKey();

        var targetWorld = player.getServer().getWorld(targetDim);
        if (targetWorld == null) {
            sendResult(player, false, "Dimension not loaded.");
            return;
        }

        player.teleport(targetWorld, teleportTarget.getX() + 0.5, teleportTarget.getY(), teleportTarget.getZ() + 0.5,
            player.getYaw(), player.getPitch());
        sendResult(player, true, "Teleported to '" + shapeName + "'.");
    }

    private static void handleSubtractBox(ServerPlayerEntity player, Leukocyte leukocyte, NbtCompound data) {
        String key = data.getString("authority");
        String shapeName = data.getString("shape_name");

        var authority = leukocyte.getAuthorityByKey(key);
        if (authority == null) {
            sendResult(player, false, "Authority '" + key + "' not found.");
            return;
        }

        var dimId = net.minecraft.util.Identifier.tryParse(data.getString("dimension"));
        if (dimId == null) {
            sendResult(player, false, "Invalid dimension.");
            return;
        }
        var dimension = net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, dimId);
        var sMin = new BlockPos(data.getInt("min_x"), data.getInt("min_y"), data.getInt("min_z"));
        var sMax = new BlockPos(data.getInt("max_x"), data.getInt("max_y"), data.getInt("max_z"));

        xyz.nucleoid.leukocyte.shape.ProtectionShape existingShape = null;
        for (var entry : authority.getShapes().entries) {
            if (entry.name().equals(shapeName)) {
                existingShape = entry.shape();
                break;
            }
        }

        if (existingShape == null) {
            sendResult(player, false, "Shape '" + shapeName + "' not found.");
            return;
        }

        var remaining = new java.util.ArrayList<xyz.nucleoid.leukocyte.shape.ProtectionShape>();
        subtractFromShape(existingShape, dimension, sMin, sMax, remaining);

        if (remaining.isEmpty()) {
            var newAuthority = authority.removeShape(shapeName);
            leukocyte.replaceAuthority(authority, newAuthority);
            sendResult(player, true, "Subtraction removed all of '" + shapeName + "'.");
            return;
        }

        xyz.nucleoid.leukocyte.shape.ProtectionShape newShape;
        if (remaining.size() == 1) {
            newShape = remaining.get(0);
        } else {
            newShape = ProtectionShape.union(remaining.toArray(new xyz.nucleoid.leukocyte.shape.ProtectionShape[0]));
        }

        var removed = authority.removeShape(shapeName);
        var newAuthority = removed.addShape(shapeName, newShape);
        leukocyte.replaceAuthority(authority, newAuthority);
        sendResult(player, true, "Subtracted box from '" + shapeName + "'.");
    }

    private static void subtractFromShape(xyz.nucleoid.leukocyte.shape.ProtectionShape shape,
                                           net.minecraft.registry.RegistryKey<net.minecraft.world.World> dimension,
                                           BlockPos sMin, BlockPos sMax,
                                           java.util.ArrayList<xyz.nucleoid.leukocyte.shape.ProtectionShape> remaining) {
        if (shape instanceof xyz.nucleoid.leukocyte.shape.BoxShape box) {
            if (!box.getDimension().equals(dimension)) {
                remaining.add(box);
                return;
            }
            subtractBoxFromBox(box.getDimension(), box.getMin(), box.getMax(), sMin, sMax, remaining);
        } else if (shape instanceof xyz.nucleoid.leukocyte.shape.UnionShape union) {
            for (var sub : union.getScopes()) {
                subtractFromShape(sub, dimension, sMin, sMax, remaining);
            }
        } else {
            remaining.add(shape);
        }
    }

    private static void subtractBoxFromBox(net.minecraft.registry.RegistryKey<net.minecraft.world.World> dim,
                                            BlockPos bMin, BlockPos bMax, BlockPos sMin, BlockPos sMax,
                                            java.util.ArrayList<xyz.nucleoid.leukocyte.shape.ProtectionShape> remaining) {
        int x1 = Math.max(bMin.getX(), sMin.getX());
        int y1 = Math.max(bMin.getY(), sMin.getY());
        int z1 = Math.max(bMin.getZ(), sMin.getZ());
        int x2 = Math.min(bMax.getX(), sMax.getX());
        int y2 = Math.min(bMax.getY(), sMax.getY());
        int z2 = Math.min(bMax.getZ(), sMax.getZ());

        if (x1 > x2 || y1 > y2 || z1 > z2) {
            remaining.add(new xyz.nucleoid.leukocyte.shape.BoxShape(dim, bMin, bMax));
            return;
        }

        if (bMin.getX() < x1) {
            remaining.add(new xyz.nucleoid.leukocyte.shape.BoxShape(dim,
                new BlockPos(bMin.getX(), bMin.getY(), bMin.getZ()),
                new BlockPos(x1 - 1, bMax.getY(), bMax.getZ())));
        }
        if (bMax.getX() > x2) {
            remaining.add(new xyz.nucleoid.leukocyte.shape.BoxShape(dim,
                new BlockPos(x2 + 1, bMin.getY(), bMin.getZ()),
                new BlockPos(bMax.getX(), bMax.getY(), bMax.getZ())));
        }
        if (bMin.getZ() < z1) {
            remaining.add(new xyz.nucleoid.leukocyte.shape.BoxShape(dim,
                new BlockPos(x1, bMin.getY(), bMin.getZ()),
                new BlockPos(x2, bMax.getY(), z1 - 1)));
        }
        if (bMax.getZ() > z2) {
            remaining.add(new xyz.nucleoid.leukocyte.shape.BoxShape(dim,
                new BlockPos(x1, bMin.getY(), z2 + 1),
                new BlockPos(x2, bMax.getY(), bMax.getZ())));
        }
        if (bMin.getY() < y1) {
            remaining.add(new xyz.nucleoid.leukocyte.shape.BoxShape(dim,
                new BlockPos(x1, bMin.getY(), z1),
                new BlockPos(x2, y1 - 1, z2)));
        }
        if (bMax.getY() > y2) {
            remaining.add(new xyz.nucleoid.leukocyte.shape.BoxShape(dim,
                new BlockPos(x1, y2 + 1, z1),
                new BlockPos(x2, bMax.getY(), z2)));
        }
    }

    private static void handleAddBoxToShape(ServerPlayerEntity player, Leukocyte leukocyte, NbtCompound data) {
        String key = data.getString("authority");
        String shapeName = data.getString("shape_name");

        var authority = leukocyte.getAuthorityByKey(key);
        if (authority == null) {
            sendResult(player, false, "Authority '" + key + "' not found.");
            return;
        }

        var dimId = net.minecraft.util.Identifier.tryParse(data.getString("dimension"));
        if (dimId == null) {
            sendResult(player, false, "Invalid dimension.");
            return;
        }
        var dimension = net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, dimId);
        var min = new BlockPos(data.getInt("min_x"), data.getInt("min_y"), data.getInt("min_z"));
        var max = new BlockPos(data.getInt("max_x"), data.getInt("max_y"), data.getInt("max_z"));

        xyz.nucleoid.leukocyte.shape.ProtectionShape existingShape = null;
        for (var entry : authority.getShapes().entries) {
            if (entry.name().equals(shapeName)) {
                existingShape = entry.shape();
                break;
            }
        }

        if (existingShape == null) {
            sendResult(player, false, "Shape '" + shapeName + "' not found.");
            return;
        }

        var newBox = ProtectionShape.box(dimension, min, max);

        xyz.nucleoid.leukocyte.shape.ProtectionShape merged;
        if (existingShape instanceof xyz.nucleoid.leukocyte.shape.UnionShape union) {
            var scopes = new java.util.ArrayList<xyz.nucleoid.leukocyte.shape.ProtectionShape>();
            for (var sub : union.getScopes()) {
                scopes.add(sub);
            }
            scopes.add(newBox);
            merged = ProtectionShape.union(scopes.toArray(new xyz.nucleoid.leukocyte.shape.ProtectionShape[0]));
        } else {
            merged = ProtectionShape.union(existingShape, newBox);
        }

        var removed = authority.removeShape(shapeName);
        var newAuthority = removed.addShape(shapeName, merged);
        leukocyte.replaceAuthority(authority, newAuthority);
        sendResult(player, true, "Added box to '" + shapeName + "'.");
    }

    private static void sendResult(ServerPlayerEntity player, boolean success, String message) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.RESPONSE_RESULT);
        var root = new NbtCompound();
        root.putBoolean("success", success);
        root.putString("message", message);
        buf.writeNbt(root);
        ServerPlayNetworking.send(player, LeukocyteNetworking.S2C_CHANNEL, buf);
    }

    private static void sendResultWithShape(ServerPlayerEntity player, boolean success, String message, String authority, String shapeName) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeByte(LeukocyteNetworking.RESPONSE_RESULT);
        var root = new NbtCompound();
        root.putBoolean("success", success);
        root.putString("message", message);
        root.putString("createdAuthority", authority);
        root.putString("createdShapeName", shapeName);
        buf.writeNbt(root);
        ServerPlayNetworking.send(player, LeukocyteNetworking.S2C_CHANNEL, buf);
    }
}
