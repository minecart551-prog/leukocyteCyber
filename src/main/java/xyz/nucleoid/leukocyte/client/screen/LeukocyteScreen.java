package xyz.nucleoid.leukocyte.client.screen;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import xyz.nucleoid.leukocyte.client.network.ClientPacketHandler;

import java.util.ArrayList;

public final class LeukocyteScreen extends Screen {
    private final ArrayList<ClientPacketHandler.AuthorityListEntry> authorities = new ArrayList<>();
    private ArrayList<ClientPacketHandler.AuthorityListEntry> filtered = new ArrayList<>();
    private TextFieldWidget searchField;
    private int scrollOffset = 0;
    private String statusMessage = "";
    private int statusTicks = 0;

    public LeukocyteScreen() {
        super(Text.literal("Leukocyte - Protection Authorities"));
    }

    @Override
    protected void init() {
        searchField = new TextFieldWidget(textRenderer, width / 2 - 100, 32, 200, 20, Text.literal(""));
        searchField.setChangedListener(this::onSearchChanged);
        addDrawableChild(searchField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Add Authority"), button -> {
            client.setScreen(new TextInputScreen("Add Authority", "Name:", name -> {
                if (!name.isEmpty()) {
                    ClientPacketHandler.addAuthority(name);
                    refreshAfterAction();
                }
                client.setScreen(null);
            }));
        }).dimensions(width / 2 - 100, height - 52, 98, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Refresh"), button -> {
            ClientPacketHandler.requestAll();
        }).dimensions(width / 2 + 2, height - 52, 98, 20).build());

        ClientPacketHandler.setListCallback(entries -> {
            this.authorities.clear();
            this.authorities.addAll(entries);
            onSearchChanged(searchField != null ? searchField.getText() : "");
        });

        ClientPacketHandler.setResultCallback(message -> {
            this.statusMessage = message;
            this.statusTicks = 100;
            ClientPacketHandler.requestAll();
        });

        ClientPacketHandler.requestAll();
    }

    @Override
    public void close() {
        ClientPacketHandler.setListCallback(null);
        ClientPacketHandler.setResultCallback(null);
        client.setScreen(null);
    }

    private void onSearchChanged(String query) {
        filtered.clear();
        String lower = query.toLowerCase();
        for (var entry : authorities) {
            if (lower.isEmpty() || entry.key().toLowerCase().contains(lower)) {
                filtered.add(entry);
            }
        }
        scrollOffset = 0;
    }

    private void refreshAfterAction() {
        ClientPacketHandler.requestAll();
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);

        if (searchField != null) {
            searchField.render(context, mouseX, mouseY, delta);
        }

        int listTop = 58;
        int listBottom = height - 60;
        int entryHeight = 24;
        int maxVisible = (listBottom - listTop) / entryHeight;

        int startIdx = scrollOffset;
        int endIdx = Math.min(startIdx + maxVisible, filtered.size());

        for (int i = startIdx; i < endIdx; i++) {
            var entry = filtered.get(i);
            int y = listTop + (i - startIdx) * entryHeight;

            boolean hovered = mouseX >= 10 && mouseX <= width - 10 && mouseY >= y && mouseY < y + entryHeight;
            if (hovered) {
                context.fill(10, y, width - 10, y + entryHeight, 0x40FFFFFF);
            }

            context.drawTextWithShadow(textRenderer, entry.key(), 14, y + 2, 0x55FFFF);
            context.drawTextWithShadow(textRenderer, "Level: " + entry.level(), 14, y + 12, 0xAAAAAA);
            context.drawTextWithShadow(textRenderer, entry.shapeDisplay(), width - 100, y + 8, 0xAAAAAA);
        }

        if (filtered.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, "No authorities found", width / 2, listTop + 20, 0x888888);
        }

        int total = filtered.size();
        if (total > maxVisible) {
            String scrollText = (startIdx + 1) + "-" + endIdx + " of " + total;
            context.drawCenteredTextWithShadow(textRenderer, scrollText, width / 2, height - 68, 0x888888);
        }

        if (statusTicks > 0) {
            statusTicks--;
            context.drawCenteredTextWithShadow(textRenderer, statusMessage, width / 2, height - 80, 0xFFFFFF);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int entryHeight = 24;
        int listBottom = height - 60;
        int listTop = 58;
        int maxVisible = (listBottom - listTop) / entryHeight;

        if (amount < 0 && scrollOffset < Math.max(0, filtered.size() - maxVisible)) {
            scrollOffset++;
        } else if (amount > 0 && scrollOffset > 0) {
            scrollOffset--;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int listTop = 58;
            int entryHeight = 24;
            int listBottom = height - 60;
            int maxVisible = (listBottom - listTop) / entryHeight;
            int startIdx = scrollOffset;
            int endIdx = Math.min(startIdx + maxVisible, filtered.size());

            for (int i = startIdx; i < endIdx; i++) {
                int y = listTop + (i - startIdx) * entryHeight;
                if (mouseX >= 10 && mouseX <= width - 10 && mouseY >= y && mouseY < y + entryHeight) {
                    var entry = filtered.get(i);
                    client.setScreen(new AuthorityDetailScreen(entry.key()));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
