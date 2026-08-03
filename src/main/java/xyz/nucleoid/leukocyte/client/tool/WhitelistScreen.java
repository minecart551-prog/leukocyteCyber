package xyz.nucleoid.leukocyte.client.tool;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import xyz.nucleoid.leukocyte.client.network.ClientBuildPacketHandler;

import java.util.ArrayList;
import java.util.List;

public final class WhitelistScreen extends Screen {
    private final String areaName;
    private TextFieldWidget addPlayerField;
    private TextFieldWidget searchField;
    private List<String> whitelist = new ArrayList<>();
    private List<String> filteredList = new ArrayList<>();
    private boolean whitelistEnabled = false;
    private int scrollOffset = 0;

    private static final int ENTRY_H = 16;
    private static final int HEADER_H = 84;
    private static final int BTN_H = 20;

    public WhitelistScreen(String areaName, List<String> whitelist, boolean whitelistEnabled) {
        super(Text.literal("Whitelist - " + areaName));
        this.areaName = areaName;
        this.whitelist = new ArrayList<>(whitelist);
        this.whitelistEnabled = whitelistEnabled;
    }

    @Override
    protected void init() {
        addPlayerField = new TextFieldWidget(textRenderer, width / 2 - 100, 30, 170, 20, Text.literal(""));
        addPlayerField.setPlaceholder(Text.literal("PlayerName"));
        addPlayerField.setMaxLength(64);
        addDrawableChild(addPlayerField);

        addDrawableChild(ButtonWidget.builder(Text.literal("+ Add"), button -> {
            String playerName = addPlayerField.getText().trim();
            if (!playerName.isEmpty()) {
                ClientBuildPacketHandler.addWhitelistPlayer(areaName, playerName);
                if (!whitelist.contains(playerName)) {
                    whitelist.add(playerName);
                }
                applySearch();
                addPlayerField.setText("");
            }
        }).dimensions(width / 2 + 72, 30, 28, 20).build());

        addDrawableChild(ButtonWidget.builder(
            Text.literal(whitelistEnabled ? "§aWhitelist: ON" : "§cWhitelist: OFF"), button -> {
                ClientBuildPacketHandler.toggleWhitelist(areaName);
                whitelistEnabled = !whitelistEnabled;
                button.setMessage(Text.literal(whitelistEnabled ? "§aWhitelist: ON" : "§cWhitelist: OFF"));
            }).dimensions(width / 2 - 80, 52, 160, 20).build());

        searchField = new TextFieldWidget(textRenderer, width / 2 - 100, 76, 200, 20, Text.literal(""));
        searchField.setPlaceholder(Text.literal("Search..."));
        searchField.setChangedListener(text -> applySearch());
        addDrawableChild(searchField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> {
            client.setScreen(new BuildAreaMenuScreen());
        }).dimensions(width / 2 - 49, height - BTN_H - 8, 98, BTN_H).build());

        applySearch();
    }

    private void applySearch() {
        String query = searchField.getText().trim().toLowerCase();
        if (query.isEmpty()) {
            filteredList = new ArrayList<>(whitelist);
        } else {
            filteredList = new ArrayList<>();
            for (String player : whitelist) {
                if (player.toLowerCase().contains(query)) {
                    filteredList.add(player);
                }
            }
        }
        scrollOffset = 0;
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        context.drawCenteredTextWithShadow(textRenderer, "Whitelist - " + areaName,
            width / 2, 12, 0xFFFFFF);

        addPlayerField.render(context, mouseX, mouseY, delta);

        int listTop = HEADER_H;
        int listBottom = height - BTN_H - 16;
        int visible = Math.max(0, Math.min(filteredList.size(), (listBottom - listTop) / ENTRY_H));
        int removeBtnW = 14;

        for (int i = 0; i < visible; i++) {
            int idx = scrollOffset + i;
            if (idx >= filteredList.size()) break;
            int ey = listTop + i * ENTRY_H;
            String player = filteredList.get(idx);

            boolean hovering = mouseX >= 8 && mouseX <= width - 8
                && mouseY >= ey && mouseY < ey + ENTRY_H - 1;
            boolean hoveringRemove = mouseX >= width - 8 - removeBtnW && mouseX <= width - 8
                && mouseY >= ey && mouseY < ey + ENTRY_H - 1;

            int bg = hovering ? 0xFF335577 : 0xFF222222;
            context.fill(8, ey, width - 8, ey + ENTRY_H - 1, bg);

            context.drawTextWithShadow(textRenderer, player, 12, ey + 4, 0xFFFFFF);

            int removeBg = hoveringRemove ? 0xFFCC3333 : 0xFF552222;
            context.fill(width - 8 - removeBtnW, ey, width - 8, ey + ENTRY_H - 1, removeBg);
            context.drawCenteredTextWithShadow(textRenderer, "-",
                width - 8 - removeBtnW / 2, ey + 3, hoveringRemove ? 0xFFFF5555 : 0xFFCC4444);
        }

        if (filteredList.isEmpty()) {
            String msg = whitelist.isEmpty() ? "No players in whitelist" : "No matching players";
            context.drawCenteredTextWithShadow(textRenderer, msg,
                width / 2, listTop + 20, 0x666666);
        }

        if (filteredList.size() > visible) {
            String scroll = (scrollOffset + 1) + "-"
                + Math.min(scrollOffset + visible, filteredList.size())
                + " / " + filteredList.size();
            context.drawCenteredTextWithShadow(textRenderer, scroll,
                width / 2, listTop + visible * ENTRY_H + 2, 0x888888);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int listTop = HEADER_H;
        int listBottom = height - BTN_H - 16;
        int visible = Math.max(0, Math.min(filteredList.size(), (listBottom - listTop) / ENTRY_H));
        int maxScroll = Math.max(0, filteredList.size() - visible);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) amount));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int listTop = HEADER_H;
        int listBottom = height - BTN_H - 16;
        int visible = Math.max(0, Math.min(filteredList.size(), (listBottom - listTop) / ENTRY_H));
        int removeBtnW = 14;

        for (int i = 0; i < visible; i++) {
            int idx = scrollOffset + i;
            if (idx >= filteredList.size()) break;
            int ey = listTop + i * ENTRY_H;
            if (mouseY >= ey && mouseY < ey + ENTRY_H - 1 && mouseX >= width - 8 - removeBtnW) {
                String player = filteredList.get(idx);
                ClientBuildPacketHandler.removeWhitelistPlayer(areaName, player);
                whitelist.remove(player);
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
