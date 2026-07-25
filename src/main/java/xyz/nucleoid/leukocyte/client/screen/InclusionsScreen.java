package xyz.nucleoid.leukocyte.client.screen;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import xyz.nucleoid.leukocyte.client.network.ClientPacketHandler;
import xyz.nucleoid.leukocyte.network.LeukocyteNetworking;

import java.util.ArrayList;

public final class InclusionsScreen extends Screen {
    private final String authorityKey;
    private final ArrayList<String> roles;
    private final ArrayList<String> permissions;
    private TextFieldWidget valueField;
    private byte selectedType = LeukocyteNetworking.EXCLUSION_TYPE_ROLE;
    private int scrollOffset = 0;
    private int totalEntries = 0;

    public InclusionsScreen(String authorityKey, ArrayList<String> roles, ArrayList<String> permissions) {
        super(Text.literal("Inclusions: " + authorityKey));
        this.authorityKey = authorityKey;
        this.roles = new ArrayList<>(roles);
        this.permissions = new ArrayList<>(permissions);
        this.totalEntries = roles.size() + permissions.size();
    }

    @Override
    protected void init() {
        valueField = new TextFieldWidget(textRenderer, width / 2 - 100, 42, 200, 20, Text.literal(""));
        valueField.setPlaceholder(Text.literal("Value (role or permission)"));
        addDrawableChild(valueField);

        int btnWidth = 64;
        int startX = width / 2 - (btnWidth * 2 + 4) / 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("Role"), button -> {
            selectedType = LeukocyteNetworking.EXCLUSION_TYPE_ROLE;
        }).dimensions(startX, 72, btnWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Permission"), button -> {
            selectedType = LeukocyteNetworking.EXCLUSION_TYPE_PERMISSION;
        }).dimensions(startX + btnWidth + 4, 72, btnWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Add"), button -> {
            String value = valueField.getText().trim();
            if (!value.isEmpty()) {
                ClientPacketHandler.addInclusion(authorityKey, selectedType, value);
                if (selectedType == LeukocyteNetworking.EXCLUSION_TYPE_ROLE) {
                    roles.add(value);
                } else {
                    permissions.add(value);
                }
                totalEntries = roles.size() + permissions.size();
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

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);

        String typeLabel = selectedType == LeukocyteNetworking.EXCLUSION_TYPE_ROLE ? "Role" : "Permission";
        context.drawCenteredTextWithShadow(textRenderer, "Adding: " + typeLabel, width / 2, 32, 0x888888);

        valueField.render(context, mouseX, mouseY, delta);

        int listTop = 130;
        int listBottom = height - 40;
        int entryHeight = 14;
        int maxVisible = (listBottom - listTop) / entryHeight;

        ArrayList<String[]> allEntries = new ArrayList<>();
        for (String role : roles) {
            allEntries.add(new String[]{"role", role});
        }
        for (String perm : permissions) {
            allEntries.add(new String[]{"perm", perm});
        }

        int startIdx = scrollOffset;
        int endIdx = Math.min(startIdx + maxVisible, allEntries.size());

        for (int i = startIdx; i < endIdx; i++) {
            var entry = allEntries.get(i);
            int y = listTop + (i - startIdx) * entryHeight;

            boolean hovered = mouseX >= 10 && mouseX <= width - 10 && mouseY >= y && mouseY < y + entryHeight;
            if (hovered) {
                context.fill(10, y, width - 10, y + entryHeight, 0x40FFFFFF);
            }

            String prefix = entry[0].equals("role") ? "[R] " : "[P] ";
            context.drawTextWithShadow(textRenderer, prefix + entry[1], 14, y + 3, 0xAAAAAA);

            if (hovered) {
                context.drawTextWithShadow(textRenderer, "[Remove]", width - 60, y + 3, 0xFF5555);
            }
        }

        if (allEntries.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, "No inclusions defined", width / 2, listTop + 20, 0x888888);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int entryHeight = 14;
        int listBottom = height - 40;
        int listTop = 130;
        int maxVisible = (listBottom - listTop) / entryHeight;

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

            ArrayList<String> allRoles = new ArrayList<>(roles);
            ArrayList<String> allPerms = new ArrayList<>(permissions);

            int endIdx = Math.min(startIdx + maxVisible, allRoles.size() + allPerms.size());

            for (int i = startIdx; i < endIdx; i++) {
                int y = listTop + (i - startIdx) * entryHeight;
                if (mouseX >= 10 && mouseX <= width - 10 && mouseY >= y && mouseY < y + entryHeight) {
                    if (mouseX >= width - 60) {
                        if (i < allRoles.size()) {
                            String role = allRoles.get(i);
                            ClientPacketHandler.removeInclusion(authorityKey, LeukocyteNetworking.EXCLUSION_TYPE_ROLE, role);
                            roles.remove(role);
                        } else {
                            int permIdx = i - allRoles.size();
                            String perm = allPerms.get(permIdx);
                            ClientPacketHandler.removeInclusion(authorityKey, LeukocyteNetworking.EXCLUSION_TYPE_PERMISSION, perm);
                            permissions.remove(perm);
                        }
                        totalEntries = roles.size() + permissions.size();
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
