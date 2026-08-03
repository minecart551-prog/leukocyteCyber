package xyz.nucleoid.leukocyte.client.tool;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BuildAreaToolState {
    private static final BuildAreaToolState INSTANCE = new BuildAreaToolState();

    public enum Mode {
        IDLE,
        SELECTED,
        CREATE_CORNER_1,
        CREATE_CORNER_2,
        ADD_CORNER_1,
        ADD_CORNER_2,
        SUB_CORNER_1,
        SUB_CORNER_2
    }

    private boolean toolHeld = false;
    private Mode mode = Mode.IDLE;

    private final List<BuildAreaEntry> buildAreas = new ArrayList<>();
    private int selectedIndex = -1;

    private BlockPos firstCorner = null;
    private BlockPos secondCorner = null;
    private BlockPos previewPos = null;

    private BuildAreaToolState() {}

    public static BuildAreaToolState getInstance() { return INSTANCE; }

    public boolean isToolHeld() { return toolHeld; }
    public void setToolHeld(boolean held) { this.toolHeld = held; }

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }

    public List<BuildAreaEntry> getBuildAreas() { return buildAreas; }
    public void setBuildAreas(List<BuildAreaEntry> areas) {
        String previouslySelected = null;
        if (selectedIndex >= 0 && selectedIndex < buildAreas.size()) {
            previouslySelected = buildAreas.get(selectedIndex).name();
        }

        buildAreas.clear();
        buildAreas.addAll(areas);

        selectedIndex = -1;
        if (previouslySelected != null) {
            for (int i = 0; i < buildAreas.size(); i++) {
                if (buildAreas.get(i).name().equals(previouslySelected)) {
                    selectedIndex = i;
                    break;
                }
            }
        }
    }

    public BuildAreaEntry getSelectedArea() {
        if (selectedIndex < 0 || selectedIndex >= buildAreas.size()) return null;
        return buildAreas.get(selectedIndex);
    }

    public int getSelectedIndex() { return selectedIndex; }
    public void setSelectedIndex(int index) { this.selectedIndex = index; }

    public boolean isEditingArea() {
        return mode == Mode.SELECTED || mode == Mode.ADD_CORNER_1 || mode == Mode.ADD_CORNER_2
            || mode == Mode.SUB_CORNER_1 || mode == Mode.SUB_CORNER_2;
    }

    public boolean isPlacingCorners() {
        return mode == Mode.CREATE_CORNER_2 || mode == Mode.ADD_CORNER_2 || mode == Mode.SUB_CORNER_2;
    }

    public BlockPos getFirstCorner() { return firstCorner; }
    public void setFirstCorner(BlockPos pos) { this.firstCorner = pos; }

    public BlockPos getSecondCorner() { return secondCorner; }
    public void setSecondCorner(BlockPos pos) { this.secondCorner = pos; }

    public BlockPos getPreviewPos() { return previewPos; }
    public void setPreviewPos(BlockPos pos) { this.previewPos = pos; }

    public void clearCorners() {
        this.firstCorner = null;
        this.secondCorner = null;
        this.previewPos = null;
    }

    public void reset() {
        mode = Mode.IDLE;
        selectedIndex = -1;
        firstCorner = null;
        secondCorner = null;
        previewPos = null;
    }

    private static String savedAreaName = null;
    private static String pendingAutoSelectName = null;

    public static void setPendingAutoSelect(String name) {
        pendingAutoSelectName = name;
    }

    public void fullReset() {
        if (selectedIndex >= 0 && selectedIndex < buildAreas.size()) {
            savedAreaName = buildAreas.get(selectedIndex).name();
        } else {
            savedAreaName = null;
        }
        reset();
        buildAreas.clear();
    }

    public void tryRestoreSelection() {
        if (savedAreaName != null) {
            for (int i = 0; i < buildAreas.size(); i++) {
                if (buildAreas.get(i).name().equals(savedAreaName)) {
                    selectedIndex = i;
                    setMode(Mode.SELECTED);
                    savedAreaName = null;
                    pendingAutoSelectName = null;
                    return;
                }
            }
        }

        if (pendingAutoSelectName != null) {
            for (int i = 0; i < buildAreas.size(); i++) {
                if (buildAreas.get(i).name().equals(pendingAutoSelectName)) {
                    selectedIndex = i;
                    setMode(Mode.SELECTED);
                    break;
                }
            }
            pendingAutoSelectName = null;
        }

        savedAreaName = null;
    }

    public record BuildAreaEntry(String name, RegistryKey<World> dimension, List<int[]> subBoxes,
                                  List<String> whitelist, boolean whitelistEnabled) {
        public BlockPos min() {
            if (subBoxes.isEmpty()) return BlockPos.ORIGIN;
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            for (int[] b : subBoxes) {
                minX = Math.min(minX, b[0]);
                minY = Math.min(minY, b[1]);
                minZ = Math.min(minZ, b[2]);
            }
            return new BlockPos(minX, minY, minZ);
        }

        public BlockPos max() {
            if (subBoxes.isEmpty()) return BlockPos.ORIGIN;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (int[] b : subBoxes) {
                maxX = Math.max(maxX, b[3]);
                maxY = Math.max(maxY, b[4]);
                maxZ = Math.max(maxZ, b[5]);
            }
            return new BlockPos(maxX, maxY, maxZ);
        }
    }
}
