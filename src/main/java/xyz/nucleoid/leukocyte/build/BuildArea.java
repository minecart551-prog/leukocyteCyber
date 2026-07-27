package xyz.nucleoid.leukocyte.build;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public record BuildArea(
    String name,
    RegistryKey<World> dimension,
    List<int[]> subBoxes
) {
    public BuildArea(String name, RegistryKey<World> dimension, BlockPos min, BlockPos max) {
        this(name, dimension, List.of(new int[]{
            min.getX(), min.getY(), min.getZ(),
            max.getX(), max.getY(), max.getZ()
        }));
    }

    public boolean contains(BlockPos pos) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        for (int[] b : subBoxes) {
            if (x >= b[0] && x <= b[3] && y >= b[1] && y <= b[4] && z >= b[2] && z <= b[5]) {
                return true;
            }
        }
        return false;
    }

    public BlockPos min() {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (int[] b : subBoxes) {
            minX = Math.min(minX, b[0]);
            minY = Math.min(minY, b[1]);
            minZ = Math.min(minZ, b[2]);
        }
        return new BlockPos(minX, minY, minZ);
    }

    public BlockPos max() {
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] b : subBoxes) {
            maxX = Math.max(maxX, b[3]);
            maxY = Math.max(maxY, b[4]);
            maxZ = Math.max(maxZ, b[5]);
        }
        return new BlockPos(maxX, maxY, maxZ);
    }

    public BlockPos center() {
        BlockPos mn = min();
        BlockPos mx = max();
        return new BlockPos(
            (mn.getX() + mx.getX()) / 2,
            mn.getY(),
            (mn.getZ() + mx.getZ()) / 2
        );
    }

    public MutableText display() {
        BlockPos mn = min();
        BlockPos mx = max();
        return Text.literal("[")
            .append(Text.literal("(" + mn.getX() + ", " + mn.getY() + ", " + mn.getZ() + ")").formatted(Formatting.AQUA))
            .append(" - ")
            .append(Text.literal("(" + mx.getX() + ", " + mx.getY() + ", " + mx.getZ() + ")").formatted(Formatting.AQUA))
            .append("] in ")
            .append(Text.literal(dimension.getValue().toString()).formatted(Formatting.YELLOW))
            .formatted(Formatting.GRAY);
    }

    public static BuildArea addBox(BuildArea area, BlockPos a, BlockPos b) {
        var newSubBoxes = new ArrayList<>(area.subBoxes());
        newSubBoxes.add(new int[]{
            Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
            Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())
        });
        return new BuildArea(area.name(), area.dimension(), newSubBoxes);
    }

    public static BuildArea subtractBox(BuildArea area, BlockPos a, BlockPos b) {
        int sMinX = Math.min(a.getX(), b.getX()), sMinY = Math.min(a.getY(), b.getY()), sMinZ = Math.min(a.getZ(), b.getZ());
        int sMaxX = Math.max(a.getX(), b.getX()), sMaxY = Math.max(a.getY(), b.getY()), sMaxZ = Math.max(a.getZ(), b.getZ());

        var remaining = new ArrayList<int[]>();
        for (int[] box : area.subBoxes()) {
            subtractBoxFromBox(box[0], box[1], box[2], box[3], box[4], box[5],
                sMinX, sMinY, sMinZ, sMaxX, sMaxY, sMaxZ, remaining);
        }
        return new BuildArea(area.name(), area.dimension(), remaining);
    }

    private static void subtractBoxFromBox(
        int bMinX, int bMinY, int bMinZ, int bMaxX, int bMaxY, int bMaxZ,
        int sMinX, int sMinY, int sMinZ, int sMaxX, int sMaxY, int sMaxZ,
        List<int[]> remaining
    ) {
        int x1 = Math.max(bMinX, sMinX), y1 = Math.max(bMinY, sMinY), z1 = Math.max(bMinZ, sMinZ);
        int x2 = Math.min(bMaxX, sMaxX), y2 = Math.min(bMaxY, sMaxY), z2 = Math.min(bMaxZ, sMaxZ);

        if (x1 > x2 || y1 > y2 || z1 > z2) {
            remaining.add(new int[]{bMinX, bMinY, bMinZ, bMaxX, bMaxY, bMaxZ});
            return;
        }

        if (bMinX < x1) {
            remaining.add(new int[]{bMinX, bMinY, bMinZ, x1 - 1, bMaxY, bMaxZ});
        }
        if (bMaxX > x2) {
            remaining.add(new int[]{x2 + 1, bMinY, bMinZ, bMaxX, bMaxY, bMaxZ});
        }
        if (bMinZ < z1) {
            remaining.add(new int[]{x1, bMinY, bMinZ, x2, bMaxY, z1 - 1});
        }
        if (bMaxZ > z2) {
            remaining.add(new int[]{x1, bMinY, z2 + 1, x2, bMaxY, bMaxZ});
        }
        if (bMinY < y1) {
            remaining.add(new int[]{x1, bMinY, z1, x2, y1 - 1, z2});
        }
        if (bMaxY > y2) {
            remaining.add(new int[]{x1, y2 + 1, z1, x2, bMaxY, z2});
        }
    }
}
