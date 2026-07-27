package xyz.nucleoid.leukocyte.build;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BuildModeState extends PersistentState {
    public static final String ID = "leukocyte_build_mode";

    private final Map<UUID, SavedBuilder> builders = new HashMap<>();

    private BuildModeState() {}

    public static BuildModeState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
            BuildModeState::readNbt,
            BuildModeState::new,
            ID
        );
    }

    public void addBuilder(BuildModeManager.ActiveBuilder builder) {
        builders.put(builder.uuid(), new SavedBuilder(
            builder.uuid(),
            builder.buildArea(),
            builder.returnPos(),
            builder.returnWorld(),
            builder.savedGameMode(),
            builder.excludedAuthorityKeys()
        ));
        markDirty();
    }

    public void removeBuilder(UUID uuid) {
        builders.remove(uuid);
        markDirty();
    }

    public SavedBuilder getBuilder(UUID uuid) {
        return builders.get(uuid);
    }

    public Map<UUID, SavedBuilder> getBuilders() {
        return builders;
    }

    public record SavedBuilder(
        UUID uuid,
        BuildArea buildArea,
        BlockPos returnPos,
        RegistryKey<net.minecraft.world.World> returnWorld,
        GameMode savedGameMode,
        List<String> excludedAuthorityKeys
    ) {}

    @Override
    public boolean isDirty() {
        return true;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound root) {
        var list = new NbtList();
        for (var builder : builders.values()) {
            var tag = new NbtCompound();
            tag.putString("uuid", builder.uuid().toString());

            var areaTag = new NbtCompound();
            areaTag.putString("name", builder.buildArea().name());
            areaTag.putString("dimension", builder.buildArea().dimension().getValue().toString());
            var subBoxes = new NbtList();
            for (int[] box : builder.buildArea().subBoxes()) {
                var arr = new NbtList();
                for (int v : box) arr.add(NbtInt.of(v));
                subBoxes.add(arr);
            }
            areaTag.put("sub_boxes", subBoxes);
            tag.put("area", areaTag);

            var posTag = new NbtCompound();
            posTag.putInt("x", builder.returnPos().getX());
            posTag.putInt("y", builder.returnPos().getY());
            posTag.putInt("z", builder.returnPos().getZ());
            tag.put("return_pos", posTag);

            tag.putString("return_world", builder.returnWorld().getValue().toString());
            tag.putString("game_mode", builder.savedGameMode().getName());

            var excluded = new NbtList();
            for (String key : builder.excludedAuthorityKeys()) {
                excluded.add(NbtString.of(key));
            }
            tag.put("excluded_keys", excluded);

            list.add(tag);
        }
        root.put("builders", list);
        return root;
    }

    private static BuildModeState readNbt(NbtCompound root) {
        var state = new BuildModeState();
        var list = root.getList("builders", NbtElement.COMPOUND_TYPE);
        for (var elem : list) {
            var tag = (NbtCompound) elem;
            UUID uuid = UUID.fromString(tag.getString("uuid"));

            var areaTag = tag.getCompound("area");
            String name = areaTag.getString("name");
            var dimId = Identifier.tryParse(areaTag.getString("dimension"));
            if (dimId == null) continue;
            var dimension = RegistryKey.of(RegistryKeys.WORLD, dimId);

            var subBoxesList = areaTag.getList("sub_boxes", NbtElement.LIST_TYPE);
            var subBoxes = new ArrayList<int[]>();
            for (int i = 0; i < subBoxesList.size(); i++) {
                var arr = subBoxesList.getList(i);
                if (arr.size() >= 6) {
                    subBoxes.add(new int[]{
                        arr.getInt(0), arr.getInt(1), arr.getInt(2),
                        arr.getInt(3), arr.getInt(4), arr.getInt(5)
                    });
                }
            }
            if (subBoxes.isEmpty()) continue;
            var area = new BuildArea(name, dimension, subBoxes);

            var posTag = tag.getCompound("return_pos");
            var returnPos = new BlockPos(posTag.getInt("x"), posTag.getInt("y"), posTag.getInt("z"));

            var returnDimId = Identifier.tryParse(tag.getString("return_world"));
            if (returnDimId == null) continue;
            var returnWorld = RegistryKey.of(RegistryKeys.WORLD, returnDimId);

            GameMode gameMode = GameMode.byId(GameMode.byName(tag.getString("game_mode")).getId());

            var excludedList = tag.getList("excluded_keys", NbtElement.STRING_TYPE);
            var excludedKeys = new ArrayList<String>();
            for (int i = 0; i < excludedList.size(); i++) {
                excludedKeys.add(excludedList.getString(i));
            }

            state.builders.put(uuid, new SavedBuilder(uuid, area, returnPos, returnWorld, gameMode, excludedKeys));
        }
        return state;
    }
}
