package xyz.nucleoid.leukocyte.client.screen;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import xyz.nucleoid.leukocyte.client.network.ClientPacketHandler;
import xyz.nucleoid.leukocyte.network.LeukocyteNetworking;

import java.util.ArrayList;

public final class ExclusionsScreen extends Screen {
    // A single row in the list. kind is one of "player", "role", "perm".
    // display is what the user sees; removeValue is what is sent to the server on removal
    // (a UUID for players, the raw value for roles/permissions).
    private record Entry(String kind, String display, String removeValue) {}

    private final String authorityKey;
    private final ArrayList<String> roles;
    private final ArrayList<String> permissions;
    private final ArrayList<String> playerUuids;
    private final ArrayList<String> playerNames;
    private final boolean isInclusion;
    private TextFieldWidget valueField;
    private byte selectedType = LeukocyteNetworking.EXCLUSION_TYPE_ROLE;
    private int scrollOffset = 0;

    public ExclusionsScreen(String authorityKey, ArrayList<String> roles, ArrayList<String> permissions,
                            ArrayList<String> playerUuids, ArrayList<String> playerNames, boolean isInclusion) {
        super(Text.literal((isInclusion ? "Inclusions" : "Exclusions") + ": " + authorityKey));
        this.authorityKey = authorityKey;
        this.roles = new ArrayList<>(roles);
        this.permissions = new ArrayList<>(permissions);
        this.playerUuids = new ArrayList<>(playerUuids);
        this.playerNames = new ArrayList<>(playerNames);
        this.isInclusion = isInclusion;
    }

    private ArrayList<Entry> buildEntries() {
        var entries = new ArrayList<Entry>();
        for (int i = 0; i < playerNames.size(); i++) {
            String uuid = i < playerUuids.size() ? playerUuids.get(i) : "";
            entries.add(new Entry("player", playerNames.get(i), uuid.isEmpty() ? playerNames.get(i) : uuid));
        }
        for (String role : roles) {
            entries.add(new Entry("role", role, role));
        }
        for (String perm : permissions) {
            entries.add(new Entry("perm", perm, perm));
        }
        return entries;
    }

    @Override
    protected void init() {
        valueField = new TextFieldWidget(textRenderer, width / 2 - 100, 42, 200, 20, Text.literal(""));
        valueField.setPlaceholder(Text.literal("Value (player / role / permission)"));
        addDrawableChild(valueField);

        int btnWidth = 64;
        int startX = width / 2 - (btnWidth * 3 + 8) / 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("Player"), button -> {
            selectedType = LeukocyteNetworking.EXCLUSION_TYPE_PLAYER;
        }).dimensions(startX, 72, btnWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Role"), button -> {
            selectedType = LeukocyteNetworking.EXCLUSION_TYPE_ROLE;
        }).dimensions(startX + btnWidth + 4, 72, btnWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Permission"), button -> {
            selectedType = LeukocyteNetworking.EXCLUSION_TYPE_PERMISSION;
        }).dimensions(startX + (btnWidth + 4) * 2, 72, btnWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Add"), button -> {
            String value = valueField.getText().trim();
            if (!value.isEmpty()) {
                if (isInclusion) {
                    ClientPacketHandler.addInclusion(authorityKey, selectedType, value);
                } else {
                    ClientPacketHandler.addExclusion(authorityKey, selectedType, value);
                }
                switch (selectedType) {
                    case LeukocyteNetworking.EXCLUSION_TYPE_ROLE -> roles.add(value);
                    case LeukocyteNetworking.EXCLUSION_TYPE_PERMISSION -> permissions.add(value);
                    case LeukocyteNetworking.EXCLUSION_TYPE_PLAYER -> {
                        // Optimistic local add: the resolved UUID comes back on the next
                        // detail refresh, but removal already works by name in the meantime.
                        playerUuids.add("");
                        playerNames.add(value);
                    }
                }
                valueField.setText("");
            }
        }).dimensions(width / 2 - 100, 100, 98, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Refresh"), button -> {
            client.setScreen(new AuthorityDetailScreen(authorityKey));
        }).dimensions(width / 2 + 2, 100, 98, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> {
            client.setScreen(new AuthorityDetailScreen(authorityKey));
        }).dimensions(width / 2 - 49, height - 32, 98, 20).build());
    }

    @Override
    public void close() {
        client.setScreen(null);
    }

    private String prefixFor(String kind) {
        return switch (kind) {
            case "player" -> "[Player] ";
            case "role" -> "[Role] ";
            default -> "[Perm] ";
        };
    }

    private void removeEntry(Entry entry) {
        byte type = switch (entry.kind()) {
            case "player" -> LeukocyteNetworking.EXCLUSION_TYPE_PLAYER;
            case "role" -> LeukocyteNetworking.EXCLUSION_TYPE_ROLE;
            default -> LeukocyteNetworking.EXCLUSION_TYPE_PERMISSION;
        };
        if (isInclusion) {
            ClientPacketHandler.removeInclusion(authorityKey, type, entry.removeValue());
        } else {
            ClientPacketHandler.removeExclusion(authorityKey, type, entry.removeValue());
        }

        switch (entry.kind()) {
            case "player" -> {
                int idx = indexOfPlayer(entry);
                if (idx >= 0) {
                    playerNames.remove(idx);
                    if (idx < playerUuids.size()) playerUuids.remove(idx);
                }
            }
            case "role" -> roles.remove(entry.removeValue());
            case "perm" -> permissions.remove(entry.removeValue());
        }
    }

    private int indexOfPlayer(Entry entry) {
        // Match on the display name; entries added optimistically may share names.
        for (int i = 0; i < playerNames.size(); i++) {
            if (playerNames.get(i).equals(entry.display())) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);

        String typeLabel = switch (selectedType) {
            case LeukocyteNetworking.EXCLUSION_TYPE_PLAYER -> "Player";
            case LeukocyteNetworking.EXCLUSION_TYPE_PERMISSION -> "Permission";
            default -> "Role";
        };
        context.drawCenteredTextWithShadow(textRenderer, "Adding: " + typeLabel, width / 2, 32, 0x888888);

        valueField.render(context, mouseX, mouseY, delta);

        int listTop = 130;
        int listBottom = height - 40;
        int entryHeight = 14;
        int maxVisible = (listBottom - listTop) / entryHeight;

        var allEntries = buildEntries();

        int startIdx = scrollOffset;
        int endIdx = Math.min(startIdx + maxVisible, allEntries.size());

        for (int i = startIdx; i < endIdx; i++) {
            var entry = allEntries.get(i);
            int y = listTop + (i - startIdx) * entryHeight;

            boolean hovered = mouseX >= 10 && mouseX <= width - 10 && mouseY >= y && mouseY < y + entryHeight;
            if (hovered) {
                context.fill(10, y, width - 10, y + entryHeight, 0x40FFFFFF);
            }

            context.drawTextWithShadow(textRenderer, prefixFor(entry.kind()) + entry.display(), 14, y + 3, 0xAAAAAA);

            if (hovered) {
                context.drawTextWithShadow(textRenderer, "[Remove]", width - 60, y + 3, 0xFF5555);
            }
        }

        if (allEntries.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, "No " + (isInclusion ? "inclusions" : "exclusions") + " defined", width / 2, listTop + 20, 0x888888);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int entryHeight = 14;
        int listBottom = height - 40;
        int listTop = 130;
        int maxVisible = (listBottom - listTop) / entryHeight;
        int totalEntries = buildEntries().size();

        if (amount < 0 && scrollOffset < Math.max(0, totalEntries - maxVisible)) {
            scrollOffset++;
        } else if (amount > 0 && scrollOffset > 0) {
            scrollOffset--;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int listTop = 130;
            int entryHeight = 14;
            int listBottom = height - 40;
            int maxVisible = (listBottom - listTop) / entryHeight;
            int startIdx = scrollOffset;

            var allEntries = buildEntries();
            int endIdx = Math.min(startIdx + maxVisible, allEntries.size());

            for (int i = startIdx; i < endIdx; i++) {
                int y = listTop + (i - startIdx) * entryHeight;
                if (mouseX >= 10 && mouseX <= width - 10 && mouseY >= y && mouseY < y + entryHeight) {
                    if (mouseX >= width - 60) {
                        removeEntry(allEntries.get(i));
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
