package xyz.nucleoid.leukocyte.client.tool;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import xyz.nucleoid.leukocyte.client.network.ClientPacketHandler;
import xyz.nucleoid.leukocyte.client.render.ShapeRenderer;

import java.util.ArrayList;
import java.util.List;

public final class ToolMenuScreen extends Screen {
    private final ShapeToolState state;
    private TextFieldWidget searchField;
    private List<ShapeToolState.ShapeEntry> filteredShapes = new ArrayList<>();
    private int shapeScrollOffset = 0;
    private int selectedAuthorityIdx = -1;

    private static final int AUTH_ENTRY_H = 18;
    private static final int SHAPE_ENTRY_H = 20;
    private static final int HEADER_H = 52;
    private static final int BTN_H = 20;
    private static final int BTN_GAP = 4;
    private static final int BOTTOM_MARGIN = 8;

    public ToolMenuScreen() {
        super(Text.literal("Shape Tool"));
        this.state = ShapeToolState.getInstance();
    }

    @Override
    protected void init() {
        var keys = state.getAuthorityKeys();
        selectedAuthorityIdx = state.getSelectedAuthorityIndex();
        if (selectedAuthorityIdx < 0 && !keys.isEmpty()) {
            selectedAuthorityIdx = 0;
        }

        searchField = new TextFieldWidget(textRenderer, width / 2 + 2, 30, 140, 20, Text.literal(""));
        searchField.setPlaceholder(Text.literal("Search..."));
        searchField.setChangedListener(this::updateShapeFilter);
        addDrawableChild(searchField);

        int btnW = 98;
        int col1 = width / 2 - btnW - 2;
        int col2 = width / 2 + 2;

        boolean isEditing = state.isEditingShape();
        var shape = state.getSelectedShape();

        int btnRows = isEditing ? 2 : 1;
        int btnBlockH = btnRows * BTN_H + (btnRows - 1) * BTN_GAP + BTN_H + BTN_GAP;
        int btnY = height - BOTTOM_MARGIN - btnBlockH;

        if (isEditing && shape != null) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Rename"), button -> {
                String oldName = shape.name();
                String auth = shape.authority();
                client.setScreen(new xyz.nucleoid.leukocyte.client.screen.TextInputScreen(
                    "Rename Shape", "New name for '" + oldName + "':", newName -> {
                        if (!newName.trim().isEmpty()) {
                            ClientPacketHandler.renameShape(auth, oldName, newName.trim());
                            xyz.nucleoid.leukocyte.client.tool.ShapeToolState.setPendingAutoSelect(auth, newName.trim());
                            client.setScreen(new ToolMenuScreen());
                        }
                    }
                ));
            }).dimensions(col1, btnY, btnW, BTN_H).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Teleport"), button -> {
                ClientPacketHandler.teleportToShape(shape.authority(), shape.name());
                client.setScreen(null);
            }).dimensions(col2, btnY, btnW, BTN_H).build());

            btnY += BTN_H + BTN_GAP;

            addDrawableChild(ButtonWidget.builder(Text.literal("Deselect"), button -> {
                state.reset();
                ShapeRenderer.getInstance().markNeedsRebuild();
                client.setScreen(null);
            }).dimensions(width / 2 - btnW / 2, btnY, btnW, BTN_H).build());

            btnY += BTN_H + BTN_GAP;
        } else {
            addDrawableChild(ButtonWidget.builder(Text.literal("Create Shape"), button -> {
                state.setMode(ShapeToolState.Mode.CREATE_CORNER_1);
                state.clearCorners();
                client.setScreen(null);
            }).dimensions(width / 2 - btnW / 2, btnY, btnW, BTN_H).build());

            btnY += BTN_H + BTN_GAP;
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> {
            client.setScreen(null);
        }).dimensions(width / 2 - btnW / 2, btnY, btnW, BTN_H).build());

        updateShapeFilter("");
    }

    private int getButtonAreaHeight() {
        boolean isEditing = state.isEditingShape();
        int rows = isEditing ? 2 : 1;
        return rows * BTN_H + (rows - 1) * BTN_GAP + BTN_H + BTN_GAP;
    }

    private void updateShapeFilter(String query) {
        filteredShapes.clear();
        String lower = query.toLowerCase();
        var currentDim = client.world != null ? client.world.getRegistryKey() : null;
        var keys = state.getAuthorityKeys();
        String authFilter = (selectedAuthorityIdx >= 0 && selectedAuthorityIdx < keys.size())
            ? keys.get(selectedAuthorityIdx) : null;

        for (ShapeToolState.ShapeEntry entry : state.getShapeEntries()) {
            if (currentDim != null && !entry.dimension().equals(currentDim)) continue;
            if (authFilter != null && !entry.authority().equals(authFilter)) continue;
            if (lower.isEmpty() || entry.name().toLowerCase().contains(lower)) {
                filteredShapes.add(entry);
            }
        }
        shapeScrollOffset = 0;
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        var keys = state.getAuthorityKeys();
        int listBottom = height - BOTTOM_MARGIN - getButtonAreaHeight() - 4;
        int authEntryH = AUTH_ENTRY_H;
        int shapeEntryH = SHAPE_ENTRY_H;

        // --- LEFT COLUMN: Authority list ---
        int leftX = 4;
        int leftW = (width / 2) - 8;
        int authVisible = Math.max(0, Math.min(keys.size(), (listBottom - HEADER_H) / authEntryH));

        context.drawCenteredTextWithShadow(textRenderer, "Authorities", leftX + leftW / 2, HEADER_H - 10, 0xFFFFFF);

        for (int i = 0; i < authVisible; i++) {
            int ey = HEADER_H + i * authEntryH;
            boolean isSelected = (i == selectedAuthorityIdx);
            boolean hovering = mouseX >= leftX && mouseX <= leftX + leftW
                && mouseY >= ey && mouseY < ey + authEntryH - 1;

            int bg = isSelected ? 0xFF226644 : (hovering ? 0xFF335577 : 0xFF222222);
            context.fill(leftX, ey, leftX + leftW, ey + authEntryH - 1, bg);

            int textColor = isSelected ? 0xFF55FF55 : (hovering ? 0xFFFFFF55 : 0xFFFFFF);
            context.drawTextWithShadow(textRenderer, keys.get(i), leftX + 4, ey + 5, textColor);
        }

        // --- RIGHT COLUMN: Shape search + list ---
        int rightX = width / 2 + 2;
        int rightW = (width / 2) - 6;
        int searchY = HEADER_H - 22;
        searchField.setY(searchY);
        searchField.render(context, mouseX, mouseY, delta);

        int listY = HEADER_H + 4;
        int shapeVisible = Math.max(0, Math.min(filteredShapes.size(), (listBottom - listY) / shapeEntryH));
        int delBtnW = 14;

        for (int i = 0; i < shapeVisible; i++) {
            int idx = shapeScrollOffset + i;
            if (idx >= filteredShapes.size()) break;
            int ey = listY + i * shapeEntryH;
            ShapeToolState.ShapeEntry entry = filteredShapes.get(idx);

            boolean hovering = mouseX >= rightX && mouseX <= rightX + rightW
                && mouseY >= ey && mouseY < ey + shapeEntryH - 1;
            boolean hoveringDel = mouseX >= rightX + rightW - delBtnW && mouseX <= rightX + rightW
                && mouseY >= ey && mouseY < ey + shapeEntryH - 1;
            boolean isSelected = entry == state.getSelectedShape();

            int bg = isSelected ? 0xFF226644 : (hovering ? 0xFF335577 : 0xFF222222);
            context.fill(rightX, ey, rightX + rightW, ey + shapeEntryH - 1, bg);

            int textColor = isSelected ? 0xFF55FF55 : (hovering ? 0xFFFFFF55 : 0xFFFFFF);
            int textMaxW = rightW - delBtnW - 6;
            String name = textRenderer.trimToWidth(entry.name(), textMaxW);
            context.drawTextWithShadow(textRenderer, name, rightX + 4, ey + 3, textColor);
            context.drawTextWithShadow(textRenderer, entry.type(),
                rightX + 4, ey + 12, 0x888888);

            int delBg = hoveringDel ? 0xFFCC3333 : 0xFF552222;
            context.fill(rightX + rightW - delBtnW, ey, rightX + rightW, ey + shapeEntryH - 1, delBg);
            context.drawCenteredTextWithShadow(textRenderer, "-",
                rightX + rightW - delBtnW / 2, ey + 4, hoveringDel ? 0xFFFF5555 : 0xFFCC4444);
        }

        if (filteredShapes.isEmpty()) {
            String msg = keys.isEmpty() ? "No authorities" : "No shapes";
            context.drawCenteredTextWithShadow(textRenderer, msg,
                rightX + rightW / 2, listY + 30, 0x666666);
        }

        if (filteredShapes.size() > shapeVisible) {
            String scroll = (shapeScrollOffset + 1) + "-"
                + Math.min(shapeScrollOffset + shapeVisible, filteredShapes.size())
                + " / " + filteredShapes.size();
            context.drawCenteredTextWithShadow(textRenderer, scroll,
                rightX + rightW / 2, listY + shapeVisible * shapeEntryH + 2, 0x888888);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (mouseX >= width / 2) {
            int listBottom = height - BOTTOM_MARGIN - getButtonAreaHeight() - 4;
            int listY = HEADER_H + 4;
            int shapeVisible = Math.max(0, Math.min(filteredShapes.size(), (listBottom - listY) / SHAPE_ENTRY_H));
            int maxScroll = Math.max(0, filteredShapes.size() - shapeVisible);
            shapeScrollOffset = Math.max(0, Math.min(maxScroll, shapeScrollOffset - (int) amount));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        var keys = state.getAuthorityKeys();
        int listBottom = height - BOTTOM_MARGIN - getButtonAreaHeight() - 4;

        // Left column: authority click
        int leftX = 4;
        int leftW = (width / 2) - 8;
        int authVisible = Math.max(0, Math.min(keys.size(), (listBottom - HEADER_H) / AUTH_ENTRY_H));

        if (mouseX >= leftX && mouseX <= leftX + leftW) {
            for (int i = 0; i < authVisible; i++) {
                int ey = HEADER_H + i * AUTH_ENTRY_H;
                if (mouseY >= ey && mouseY < ey + AUTH_ENTRY_H - 1) {
                    selectedAuthorityIdx = i;
                    state.setAuthorityKeys(new ArrayList<>(keys));
                    state.setSelectedAuthorityIndex(i);
                    updateShapeFilter(searchField.getText());
                    return true;
                }
            }
        }

        // Right column: shape click
        int rightX = width / 2 + 2;
        int rightW = (width / 2) - 6;
        int listY = HEADER_H + 4;
        int shapeVisible = Math.max(0, Math.min(filteredShapes.size(), (listBottom - listY) / SHAPE_ENTRY_H));
        int delBtnW = 14;

        if (mouseX >= rightX && mouseX <= rightX + rightW) {
            for (int i = 0; i < shapeVisible; i++) {
                int idx = shapeScrollOffset + i;
                if (idx >= filteredShapes.size()) break;
                int ey = listY + i * SHAPE_ENTRY_H;
                if (mouseY >= ey && mouseY < ey + SHAPE_ENTRY_H - 1) {
                    ShapeToolState.ShapeEntry entry = filteredShapes.get(idx);

                    if (mouseX >= rightX + rightW - delBtnW) {
                        ClientPacketHandler.removeShape(entry.authority(), entry.name());
                        state.reset();
                        client.setScreen(new ToolMenuScreen());
                        return true;
                    }

                    state.setSelectedShapeIndex(state.getShapeEntries().indexOf(entry));
                    state.setMode(ShapeToolState.Mode.SELECTED);
                    state.clearCorners();
                    ShapeRenderer.getInstance().markNeedsRebuild();
                    client.setScreen(new ToolMenuScreen());
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        client.setScreen(null);
    }
}
