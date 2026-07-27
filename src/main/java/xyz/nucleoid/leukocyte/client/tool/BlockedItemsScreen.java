package xyz.nucleoid.leukocyte.client.tool;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import xyz.nucleoid.leukocyte.client.network.ClientBuildPacketHandler;

import java.util.ArrayList;
import java.util.List;

public final class BlockedItemsScreen extends Screen {
    private TextFieldWidget addItemField;
    private TextFieldWidget searchField;
    private List<String> blockedItems = new ArrayList<>();
    private List<String> filteredItems = new ArrayList<>();
    private int scrollOffset = 0;

    private static final int ENTRY_H = 16;
    private static final int HEADER_H = 68;
    private static final int BTN_H = 20;

    public BlockedItemsScreen() {
        super(Text.literal("Global Blocked Items"));
    }

    @Override
    protected void init() {
        blockedItems = new ArrayList<>(ClientBuildPacketHandler.getGlobalBlockedItems());
        filteredItems = new ArrayList<>(blockedItems);

        addItemField = new TextFieldWidget(textRenderer, width / 2 - 100, 30, 170, 20, Text.literal(""));
        addItemField.setPlaceholder(Text.literal("minecraft:stone"));
        addItemField.setMaxLength(256);
        addDrawableChild(addItemField);

        addDrawableChild(ButtonWidget.builder(Text.literal("+ Add"), button -> {
            String itemId = addItemField.getText().trim();
            if (!itemId.isEmpty()) {
                ClientBuildPacketHandler.addBlockedItem(itemId);
                if (!blockedItems.contains(itemId)) {
                    blockedItems.add(itemId);
                }
                applySearch();
                addItemField.setText("");
            }
        }).dimensions(width / 2 + 72, 30, 28, 20).build());

        searchField = new TextFieldWidget(textRenderer, width / 2 - 100, 52, 200, 20, Text.literal(""));
        searchField.setPlaceholder(Text.literal("Search..."));
        searchField.setChangedListener(text -> applySearch());
        addDrawableChild(searchField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> {
            client.setScreen(new BuildAreaMenuScreen());
        }).dimensions(width / 2 - 49, height - BTN_H - 8, 98, BTN_H).build());
    }

    private void applySearch() {
        String query = searchField.getText().trim().toLowerCase();
        if (query.isEmpty()) {
            filteredItems = new ArrayList<>(blockedItems);
        } else {
            filteredItems = new ArrayList<>();
            for (String item : blockedItems) {
                if (item.toLowerCase().contains(query)) {
                    filteredItems.add(item);
                }
            }
        }
        scrollOffset = 0;
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        context.drawCenteredTextWithShadow(textRenderer, "Global Blocked Items",
            width / 2, 12, 0xFFFFFF);

        addItemField.render(context, mouseX, mouseY, delta);
        searchField.render(context, mouseX, mouseY, delta);

        int listTop = HEADER_H;
        int listBottom = height - BTN_H - 16;
        int visible = Math.max(0, Math.min(filteredItems.size(), (listBottom - listTop) / ENTRY_H));
        int removeBtnW = 14;

        for (int i = 0; i < visible; i++) {
            int idx = scrollOffset + i;
            if (idx >= filteredItems.size()) break;
            int ey = listTop + i * ENTRY_H;
            String item = filteredItems.get(idx);

            boolean hovering = mouseX >= 8 && mouseX <= width - 8
                && mouseY >= ey && mouseY < ey + ENTRY_H - 1;
            boolean hoveringRemove = mouseX >= width - 8 - removeBtnW && mouseX <= width - 8
                && mouseY >= ey && mouseY < ey + ENTRY_H - 1;

            int bg = hovering ? 0xFF335577 : 0xFF222222;
            context.fill(8, ey, width - 8, ey + ENTRY_H - 1, bg);

            context.drawTextWithShadow(textRenderer, item, 12, ey + 4, 0xFFFFFF);

            int removeBg = hoveringRemove ? 0xFFCC3333 : 0xFF552222;
            context.fill(width - 8 - removeBtnW, ey, width - 8, ey + ENTRY_H - 1, removeBg);
            context.drawCenteredTextWithShadow(textRenderer, "-",
                width - 8 - removeBtnW / 2, ey + 3, hoveringRemove ? 0xFFFF5555 : 0xFFCC4444);
        }

        if (filteredItems.isEmpty()) {
            String msg = blockedItems.isEmpty() ? "No blocked items" : "No matching items";
            context.drawCenteredTextWithShadow(textRenderer, msg,
                width / 2, listTop + 20, 0x666666);
        }

        if (filteredItems.size() > visible) {
            String scroll = (scrollOffset + 1) + "-"
                + Math.min(scrollOffset + visible, filteredItems.size())
                + " / " + filteredItems.size();
            context.drawCenteredTextWithShadow(textRenderer, scroll,
                width / 2, listTop + visible * ENTRY_H + 2, 0x888888);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int listTop = HEADER_H;
        int listBottom = height - BTN_H - 16;
        int visible = Math.max(0, Math.min(filteredItems.size(), (listBottom - listTop) / ENTRY_H));
        int maxScroll = Math.max(0, filteredItems.size() - visible);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) amount));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int listTop = HEADER_H;
        int listBottom = height - BTN_H - 16;
        int visible = Math.max(0, Math.min(filteredItems.size(), (listBottom - listTop) / ENTRY_H));
        int removeBtnW = 14;

        for (int i = 0; i < visible; i++) {
            int idx = scrollOffset + i;
            if (idx >= filteredItems.size()) break;
            int ey = listTop + i * ENTRY_H;
            if (mouseY >= ey && mouseY < ey + ENTRY_H - 1 && mouseX >= width - 8 - removeBtnW) {
                String item = filteredItems.get(idx);
                ClientBuildPacketHandler.removeBlockedItem(item);
                blockedItems.remove(item);
                applySearch();
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
