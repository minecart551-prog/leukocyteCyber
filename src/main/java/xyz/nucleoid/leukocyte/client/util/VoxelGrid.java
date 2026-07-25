package xyz.nucleoid.leukocyte.client.util;

import java.util.HashSet;
import java.util.Set;

public class VoxelGrid {
    private Set<Long> occupiedVoxels = new HashSet<>();
    private int minX, minY, minZ;
    private int maxX, maxY, maxZ;
    private boolean hasBounds = false;

    public static long encode(int x, int y, int z) {
        return ((long)(x & 0xFFFFF) << 40) | ((long)(y & 0xFFFFF) << 20) | (z & 0xFFFFF);
    }

    public static int decodeX(long encoded) {
        int value = (int)((encoded >> 40) & 0xFFFFF);
        if ((value & 0x80000) != 0) {
            value |= 0xFFF00000;
        }
        return value;
    }

    public static int decodeY(long encoded) {
        int value = (int)((encoded >> 20) & 0xFFFFF);
        if ((value & 0x80000) != 0) {
            value |= 0xFFF00000;
        }
        return value;
    }

    public static int decodeZ(long encoded) {
        int value = (int)(encoded & 0xFFFFF);
        if ((value & 0x80000) != 0) {
            value |= 0xFFF00000;
        }
        return value;
    }

    public void setOccupied(int x, int y, int z) {
        occupiedVoxels.add(encode(x, y, z));
        if (!hasBounds) {
            minX = maxX = x;
            minY = maxY = y;
            minZ = maxZ = z;
            hasBounds = true;
        } else {
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
    }

    public boolean isOccupied(int x, int y, int z) {
        return occupiedVoxels.contains(encode(x, y, z));
    }

    public void setEmpty(int x, int y, int z) {
        occupiedVoxels.remove(encode(x, y, z));
    }

    public int getMinX() { return hasBounds ? minX : 0; }
    public int getMaxX() { return hasBounds ? maxX : 0; }
    public int getMinY() { return hasBounds ? minY : 0; }
    public int getMaxY() { return hasBounds ? maxY : 0; }
    public int getMinZ() { return hasBounds ? minZ : 0; }
    public int getMaxZ() { return hasBounds ? maxZ : 0; }

    public boolean isEmpty() {
        return occupiedVoxels.isEmpty();
    }

    public Set<Long> getOccupiedVoxels() {
        return occupiedVoxels;
    }

    public void fillBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    setOccupied(x, y, z);
                }
            }
        }
    }
}
