package xyz.nucleoid.leukocyte.build;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class LeukocyteBuild extends PersistentState {
    public static final String ID = "leukocyte_build";

    private final List<BuildArea> areas = new ArrayList<>();
    private final List<String> globalBlockedItems = new ArrayList<>();

    private LeukocyteBuild() {
    }

    public static LeukocyteBuild get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
            LeukocyteBuild::readNbt,
            LeukocyteBuild::new,
            ID
        );
    }

    public boolean addArea(BuildArea area) {
        for (var existing : this.areas) {
            if (existing.name().equals(area.name())) {
                return false;
            }
        }
        this.areas.add(area);
        this.markDirty();
        return true;
    }

    @Nullable
    public BuildArea removeArea(String name) {
        var it = this.areas.iterator();
        while (it.hasNext()) {
            var area = it.next();
            if (area.name().equals(name)) {
                it.remove();
                this.markDirty();
                return area;
            }
        }
        return null;
    }

    @Nullable
    public BuildArea getArea(String name) {
        for (var area : this.areas) {
            if (area.name().equals(name)) {
                return area;
            }
        }
        return null;
    }

    public void replaceArea(String name, BuildArea newArea) {
        for (int i = 0; i < this.areas.size(); i++) {
            if (this.areas.get(i).name().equals(name)) {
                this.areas.set(i, newArea);
                this.markDirty();
                return;
            }
        }
    }

    public List<BuildArea> getAreas() {
        return this.areas;
    }

    public List<String> getGlobalBlockedItems() {
        return this.globalBlockedItems;
    }

    public boolean addGlobalBlockedItem(String itemId) {
        if (this.globalBlockedItems.contains(itemId)) {
            return false;
        }
        this.globalBlockedItems.add(itemId);
        this.markDirty();
        return true;
    }

    public boolean removeGlobalBlockedItem(String itemId) {
        if (this.globalBlockedItems.remove(itemId)) {
            this.markDirty();
            return true;
        }
        return false;
    }

    @Override
    public boolean isDirty() {
        return true;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound root) {
        var list = new NbtList();
        for (var area : this.areas) {
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
        for (String item : this.globalBlockedItems) {
            blocked.add(NbtString.of(item));
        }
        root.put("global_blocked_items", blocked);

        return root;
    }

    private static LeukocyteBuild readNbt(NbtCompound root) {
        var build = new LeukocyteBuild();
        var list = root.getList("areas", NbtElement.COMPOUND_TYPE);
        for (var elem : list) {
            var tag = (NbtCompound) elem;
            String name = tag.getString("name");
            String dimStr = tag.getString("dimension");
            var dimId = Identifier.tryParse(dimStr);
            RegistryKey<net.minecraft.world.World> dimension = dimId != null
                ? RegistryKey.of(RegistryKeys.WORLD, dimId) : null;
            if (dimension == null) continue;

            if (tag.contains("sub_boxes", NbtElement.LIST_TYPE)) {
                var subBoxesList = tag.getList("sub_boxes", NbtElement.LIST_TYPE);
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
                if (!subBoxes.isEmpty()) {
                    build.areas.add(new BuildArea(name, dimension, subBoxes));
                }
            } else if (tag.contains("min") && tag.contains("max")) {
                var min = readBlockPos(tag.getCompound("min"));
                var max = readBlockPos(tag.getCompound("max"));
                build.areas.add(new BuildArea(name, dimension, min, max));
            }
        }
        var blocked = root.getList("global_blocked_items", NbtElement.STRING_TYPE);
        var seen = new java.util.LinkedHashSet<String>();
        for (int i = 0; i < blocked.size(); i++) {
            seen.add(blocked.getString(i));
        }
        if (seen.isEmpty()) {
            seen.addAll(getDefaultBlockedItems());
        }
        build.globalBlockedItems.addAll(seen);
        return build;
    }

    private static net.minecraft.util.math.BlockPos readBlockPos(NbtCompound tag) {
        return new net.minecraft.util.math.BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }

    public static List<String> getDefaultBlockedItems() {
        var defaults = new ArrayList<String>();

        var blockedNamespaces = Set.of("cyberwarecore", "tacz", "automobility", "customnpcs", "bbs", "armourers_workshop", "coins", "mtr");

        for (var item : Registries.ITEM) {
            var id = Registries.ITEM.getId(item);
            String namespace = id.getNamespace();
            String path = id.getPath();

            if (blockedNamespaces.contains(namespace)) {
                defaults.add(id.toString());
                continue;
            }

            if (!namespace.equals("minecraft")) continue;

            if (path.endsWith("_spawn_egg")
                || path.endsWith("_shulker_box")
                || path.equals("chest")
                || path.equals("ender_chest")
                || path.equals("trapped_chest")
                || path.endsWith("_boat")
                || path.endsWith("_raft")) {
                defaults.add(id.toString());
            }
        }

        return defaults;
    }
}
