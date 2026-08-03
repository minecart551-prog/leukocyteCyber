package xyz.nucleoid.leukocyte.client.tool;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import xyz.nucleoid.leukocyte.client.network.ClientBuildPacketHandler;
import xyz.nucleoid.leukocyte.client.render.ShapeRenderer;

import java.util.ArrayList;
import java.util.List;

public final class BuildAreaMenuScreen extends Screen {
    private final BuildAreaToolState state;
    private TextFieldWidget searchField;
    private List<BuildAreaToolState.BuildAreaEntry> filteredAreas = new ArrayList<>();
    private int scrollOffset = 0;

    private static final int ENTRY_H = 22;
    private static final int HEADER_H = 40;
    private static final int BTN_H = 20;
    private static final int BTN_GAP = 4;
    private static final int BOTTOM_MARGIN = 8;

    public BuildAreaMenuScreen() {
        super(Text.literal("Build Area Tool"));
        this.state = BuildAreaToolState.getInstance();
    }

    @Override
    protected void init() {
        searchField = new TextFieldWidget(textRenderer, width / 2 - 100, 16, 200, 20, Text.literal(""));
        searchField.setPlaceholder(Text.literal("Search areas..."));
        searchField.setChangedListener(this::updateFilter);
        addDrawableChild(searchField);

        int btnW = 96;
        int btnY = height - BOTTOM_MARGIN - BTN_H;

        boolean isEditing = state.isEditingArea();
        var area = state.getSelectedArea();

        if (isEditing && area != null) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> {
                client.setScreen(null);
            }).dimensions(width / 2 - btnW / 2, btnY, btnW, BTN_H).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Blocked Items"), button -> {
                client.setScreen(new BlockedItemsScreen());
            }).dimensions(width / 2 - btnW - BTN_GAP / 2, btnY - BTN_H - BTN_GAP, btnW, BTN_H).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Whitelist"), button -> {
                client.setScreen(new WhitelistScreen(area.name(), area.whitelist(), area.whitelistEnabled()));
            }).dimensions(width / 2 + BTN_GAP / 2, btnY - BTN_H - BTN_GAP, btnW, BTN_H).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Deselect"), button -> {
                state.reset();
                ShapeRenderer.getInstance().markNeedsRebuild();
                client.setScreen(null);
            }).dimensions(width / 2 - btnW - BTN_GAP / 2, btnY - 2 * (BTN_H + BTN_GAP), btnW, BTN_H).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Teleport"), button -> {
                ClientBuildPacketHandler.teleportToArea(area.name());
                client.setScreen(null);
            }).dimensions(width / 2 + BTN_GAP / 2, btnY - 2 * (BTN_H + BTN_GAP), btnW, BTN_H).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Rename"), button -> {
                String oldName = area.name();
                client.setScreen(new xyz.nucleoid.leukocyte.client.screen.TextInputScreen(
                    "Rename Area", "New name for '" + oldName + "':", newName -> {
                        if (!newName.trim().isEmpty()) {
                            ClientBuildPacketHandler.renameBuildArea(oldName, newName.trim());
                            BuildAreaToolState.setPendingAutoSelect(newName.trim());
                            client.setScreen(new BuildAreaMenuScreen());
                        }
                    }
                ));
            }).dimensions(width / 2 - btnW - BTN_GAP / 2, btnY - 3 * (BTN_H + BTN_GAP), btnW, BTN_H).build());
        } else {
            addDrawableChild(ButtonWidget.builder(Text.literal("Blocked Items"), button -> {
                client.setScreen(new BlockedItemsScreen());
            }).dimensions(width / 2 - btnW / 2, btnY, btnW, BTN_H).build());

            btnY -= BTN_H + BTN_GAP;

            addDrawableChild(ButtonWidget.builder(Text.literal("Blocked Players"), button -> {
                client.setScreen(new BlockedPlayersScreen());
            }).dimensions(width / 2 - btnW / 2, btnY, btnW, BTN_H).build());

            btnY -= BTN_H + BTN_GAP;

            addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> {
                client.setScreen(null);
            }).dimensions(width / 2 - btnW / 2, btnY, btnW, BTN_H).build());
        }

        updateFilter("");
    }

    private void updateFilter(String query) {
        filteredAreas.clear();
        String lower = query.toLowerCase();
        var currentDim = client.world != null ? client.world.getRegistryKey() : null;

        for (var entry : state.getBuildAreas()) {
            if (currentDim != null && entry.dimension() != null && !entry.dimension().equals(currentDim)) continue;
            if (lower.isEmpty() || entry.name().toLowerCase().contains(lower)) {
                filteredAreas.add(entry);
            }
        }
        scrollOffset = 0;
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        int listTop = HEADER_H;
        boolean isEditing = state.isEditingArea();
        int bottomButtons = isEditing ? 4 : 3;
        int btnBlockH = bottomButtons * BTN_H + (bottomButtons - 1) * BTN_GAP + BTN_H + BTN_GAP;
        int listBottom = height - BOTTOM_MARGIN - btnBlockH - 4;
        int visible = Math.max(0, Math.min(filteredAreas.size(), (listBottom - listTop) / ENTRY_H));
        int delBtnW = 14;

        for (int i = 0; i < visible; i++) {
            int idx = scrollOffset + i;
            if (idx >= filteredAreas.size()) break;
            int ey = listTop + i * ENTRY_H;
            var entry = filteredAreas.get(idx);

            boolean hovering = mouseX >= 8 && mouseX <= width - 8
                && mouseY >= ey && mouseY < ey + ENTRY_H - 1;
            boolean hoveringDel = mouseX >= width - 8 - delBtnW && mouseX <= width - 8
                && mouseY >= ey && mouseY < ey + ENTRY_H - 1;
            boolean isSelected = entry == state.getSelectedArea();

            int bg = isSelected ? 0xFF226644 : (hovering ? 0xFF335577 : 0xFF222222);
            context.fill(8, ey, width - 8, ey + ENTRY_H - 1, bg);

            int textColor = isSelected ? 0xFF55FF55 : (hovering ? 0xFFFFFF55 : 0xFFFFFF);
            int textMaxW = width - 16 - delBtnW - 6;
            String name = textRenderer.trimToWidth(entry.name(), textMaxW);
            context.drawTextWithShadow(textRenderer, name, 12, ey + 3, textColor);

            String coords = "(" + entry.min().getX() + ", " + entry.min().getY() + ", " + entry.min().getZ() + ") - ("
                + entry.max().getX() + ", " + entry.max().getY() + ", " + entry.max().getZ() + ")";
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(coords, textMaxW),
                12, ey + 13, 0x888888);

            int delBg = hoveringDel ? 0xFFCC3333 : 0xFF552222;
            context.fill(width - 8 - delBtnW, ey, width - 8, ey + ENTRY_H - 1, delBg);
            context.drawCenteredTextWithShadow(textRenderer, "-",
                width - 8 - delBtnW / 2, ey + 5, hoveringDel ? 0xFFFF5555 : 0xFFCC4444);
        }

        if (filteredAreas.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, "No build areas",
                width / 2, listTop + 30, 0x666666);
        }

        if (filteredAreas.size() > visible) {
            String scroll = (scrollOffset + 1) + "-"
                + Math.min(scrollOffset + visible, filteredAreas.size())
                + " / " + filteredAreas.size();
            context.drawCenteredTextWithShadow(textRenderer, scroll,
                width / 2, listTop + visible * ENTRY_H + 2, 0x888888);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int listTop = HEADER_H;
        boolean isEditing = state.isEditingArea();
        int bottomButtons = isEditing ? 4 : 3;
        int btnBlockH = bottomButtons * BTN_H + (bottomButtons - 1) * BTN_GAP + BTN_H + BTN_GAP;
        int listBottom = height - BOTTOM_MARGIN - btnBlockH - 4;
        int visible = Math.max(0, Math.min(filteredAreas.size(), (listBottom - listTop) / ENTRY_H));
        int maxScroll = Math.max(0, filteredAreas.size() - visible);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) amount));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int listTop = HEADER_H;
        boolean isEditing = state.isEditingArea();
        int bottomButtons = isEditing ? 4 : 3;
        int btnBlockH = bottomButtons * BTN_H + (bottomButtons - 1) * BTN_GAP + BTN_H + BTN_GAP;
        int listBottom = height - BOTTOM_MARGIN - btnBlockH - 4;
        int visible = Math.max(0, Math.min(filteredAreas.size(), (listBottom - listTop) / ENTRY_H));
        int delBtnW = 14;

        for (int i = 0; i < visible; i++) {
            int idx = scrollOffset + i;
            if (idx >= filteredAreas.size()) break;
            int ey = listTop + i * ENTRY_H;
            if (mouseY >= ey && mouseY < ey + ENTRY_H - 1) {
                var entry = filteredAreas.get(idx);

                if (mouseX >= width - 8 - delBtnW) {
                    ClientBuildPacketHandler.deleteBuildArea(entry.name());
                    state.reset();
                    client.setScreen(new BuildAreaMenuScreen());
                    return true;
                }

                state.setSelectedIndex(state.getBuildAreas().indexOf(entry));
                state.setMode(BuildAreaToolState.Mode.SELECTED);
                state.clearCorners();
                ShapeRenderer.getInstance().markNeedsRebuild();
                client.setScreen(new BuildAreaMenuScreen());
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() { client.setScreen(null); }
}
