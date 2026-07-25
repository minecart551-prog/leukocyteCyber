package xyz.nucleoid.leukocyte.client.tool;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import xyz.nucleoid.leukocyte.client.render.ShapeRenderer;

import java.util.ArrayList;
import java.util.List;

public final class ShapeToolState {
    private static final ShapeToolState INSTANCE = new ShapeToolState();

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

    public enum RenderMode {
        AUTHORITY_COLORS,
        SHAPE_COLORS
    }

    private boolean toolHeld = false;
    private Mode mode = Mode.IDLE;
    private RenderMode renderMode = RenderMode.AUTHORITY_COLORS;

    private final List<String> authorityKeys = new ArrayList<>();
    private int selectedAuthorityIndex = 0;

    private final List<ShapeEntry> shapeEntries = new ArrayList<>();
    private int selectedShapeIndex = -1;

    private BlockPos firstCorner = null;
    private BlockPos secondCorner = null;

    private static final int[] AUTHORITY_COLORS = {
        0xFF00FF00, 0xFF0088FF, 0xFFFF4444, 0xFFFFFF00, 0xFF00FFFF,
        0xFFFF00FF, 0xFFFF8800, 0xFF88FF88, 0xFF4444FF, 0xFFFFFFFF
    };

    private static final int[] SHAPE_COLORS = {
        0xFF00FF00, 0xFF00AAFF, 0xFFFFAA00, 0xFFFF00AA, 0xFFAAFF00,
        0xFF00FFFF, 0xFFFF5555, 0xFF55FF55, 0xFF5555FF, 0xFFFFFF55,
        0xFFFF8800, 0xFF88FF00, 0xFF00FF88, 0xFF8800FF, 0xFF0088FF
    };

    private ShapeToolState() {}

    public static ShapeToolState getInstance() { return INSTANCE; }

    public boolean isToolHeld() { return toolHeld; }
    public void setToolHeld(boolean held) { this.toolHeld = held; }

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }

    public RenderMode getRenderMode() { return renderMode; }
    public void cycleRenderMode() {
        renderMode = renderMode == RenderMode.AUTHORITY_COLORS ? RenderMode.SHAPE_COLORS : RenderMode.AUTHORITY_COLORS;
    }

    public List<String> getAuthorityKeys() { return authorityKeys; }
    public void setAuthorityKeys(List<String> keys) {
        authorityKeys.clear();
        authorityKeys.addAll(keys);
        if (selectedAuthorityIndex >= authorityKeys.size()) {
            selectedAuthorityIndex = 0;
        }
    }

    public void cycleAuthority() {
        if (authorityKeys.isEmpty()) return;
        selectedAuthorityIndex = (selectedAuthorityIndex + 1) % authorityKeys.size();
        selectedShapeIndex = -1;
        clearCorners();
        mode = Mode.IDLE;
    }

    public String getSelectedAuthority() {
        if (authorityKeys.isEmpty()) return null;
        return authorityKeys.get(selectedAuthorityIndex);
    }

    public int getSelectedAuthorityIndex() { return selectedAuthorityIndex; }
    public void setSelectedAuthorityIndex(int index) { this.selectedAuthorityIndex = index; }

    public List<ShapeEntry> getShapeEntries() { return shapeEntries; }
    public void setShapeEntries(List<ShapeEntry> entries) {
        String previouslySelectedName = null;
        if (selectedShapeIndex >= 0 && selectedShapeIndex < shapeEntries.size()) {
            previouslySelectedName = shapeEntries.get(selectedShapeIndex).name();
        }

        shapeEntries.clear();
        shapeEntries.addAll(entries);

        selectedShapeIndex = -1;
        if (previouslySelectedName != null) {
            for (int i = 0; i < shapeEntries.size(); i++) {
                if (shapeEntries.get(i).name().equals(previouslySelectedName)) {
                    selectedShapeIndex = i;
                    break;
                }
            }
        }
    }

    public ShapeEntry getSelectedShape() {
        if (selectedShapeIndex < 0 || selectedShapeIndex >= shapeEntries.size()) return null;
        return shapeEntries.get(selectedShapeIndex);
    }

    public int getSelectedShapeIndex() { return selectedShapeIndex; }
    public void setSelectedShapeIndex(int index) { selectedShapeIndex = index; }

    public boolean isEditingShape() {
        return mode == Mode.SELECTED || mode == Mode.ADD_CORNER_1 || mode == Mode.ADD_CORNER_2
            || mode == Mode.SUB_CORNER_1 || mode == Mode.SUB_CORNER_2;
    }

    public BlockPos getFirstCorner() { return firstCorner; }
    public void setFirstCorner(BlockPos pos) { this.firstCorner = pos; }

    public BlockPos getSecondCorner() { return secondCorner; }
    public void setSecondCorner(BlockPos pos) { this.secondCorner = pos; }

    public void clearCorners() {
        this.firstCorner = null;
        this.secondCorner = null;
    }

    public int getAuthorityColor(int index) {
        return AUTHORITY_COLORS[index % AUTHORITY_COLORS.length];
    }

    public int getShapeColor(int index) {
        return SHAPE_COLORS[index % SHAPE_COLORS.length];
    }

    public String getModeDescription() {
        return switch (mode) {
            case IDLE -> "Ready";
            case SELECTED -> "Editing shape";
            case CREATE_CORNER_1, CREATE_CORNER_2 -> "Creating";
            case ADD_CORNER_1, ADD_CORNER_2 -> "Adding";
            case SUB_CORNER_1, SUB_CORNER_2 -> "Subtracting";
        };
    }

    public void reset() {
        mode = Mode.IDLE;
        firstCorner = null;
        secondCorner = null;
    }

    private static String savedAuthority = null;
    private static String savedShapeName = null;

    public void fullReset() {
        if (selectedShapeIndex >= 0 && selectedShapeIndex < shapeEntries.size()) {
            var entry = shapeEntries.get(selectedShapeIndex);
            savedAuthority = entry.authority();
            savedShapeName = entry.name();
        } else {
            savedAuthority = null;
            savedShapeName = null;
        }
        reset();
        selectedShapeIndex = -1;
        selectedAuthorityIndex = 0;
        shapeEntries.clear();
        authorityKeys.clear();
    }

    public void tryRestoreSelection() {
        if (savedAuthority == null || savedShapeName == null) return;
        for (int i = 0; i < shapeEntries.size(); i++) {
            var entry = shapeEntries.get(i);
            if (entry.authority().equals(savedAuthority) && entry.name().equals(savedShapeName)) {
                selectedShapeIndex = i;
                int authIdx = authorityKeys.indexOf(savedAuthority);
                if (authIdx >= 0) selectedAuthorityIndex = authIdx;
                setMode(Mode.SELECTED);
                ShapeRenderer.getInstance().markNeedsRebuild();
                break;
            }
        }
        savedAuthority = null;
        savedShapeName = null;
    }

    public record ShapeEntry(String authority, String name, String type, RegistryKey<World> dimension,
                             BlockPos min, BlockPos max, int subShapeCount,
                             List<int[]> subBoxes) {}
}
