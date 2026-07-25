package xyz.nucleoid.leukocyte.client.screen;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import xyz.nucleoid.leukocyte.client.network.ClientPacketHandler;

import java.util.ArrayList;

public final class AddShapeScreen extends Screen {
    private final String authorityKey;
    private TextFieldWidget nameField;
    private final ArrayList<NbtCompound> subShapes = new ArrayList<>();
    private final ArrayList<String> subShapeLabels = new ArrayList<>();
    private int scrollOffset = 0;

    public AddShapeScreen(String authorityKey) {
        super(Text.literal("Add Shape: " + authorityKey));
        this.authorityKey = authorityKey;
    }

    @Override
    protected void init() {
        nameField = new TextFieldWidget(textRenderer, width / 2 - 100, 32, 200, 20, Text.literal(""));
        nameField.setPlaceholder(Text.literal("Shape name"));
        addDrawableChild(nameField);

        int btnWidth = 62;
        int startX = width / 2 - (btnWidth * 3 + 8) / 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("Universal"), button -> addSubShape("universal"))
                .dimensions(startX, 60, btnWidth, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Dimension"), button -> openSubShapeDialog("dimension"))
                .dimensions(startX + btnWidth + 4, 60, btnWidth, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Box"), button -> openSubShapeDialog("box"))
                .dimensions(startX + (btnWidth + 4) * 2, 60, btnWidth, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> saveShape())
                .dimensions(width / 2 - 100, height - 52, 98, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> client.setScreen(new ShapesScreen(authorityKey, new ArrayList<>(), new ArrayList<>(), new ArrayList<>())))
                .dimensions(width / 2 + 2, height - 52, 98, 20).build());
    }

    private void addSubShape(String type) {
        var sub = new NbtCompound();
        sub.putString("type", type);
        subShapes.add(sub);
        subShapeLabels.add(type);
    }

    private void openSubShapeDialog(String type) {
        client.setScreen(new SubShapeInputScreen(type, sub -> {
            subShapes.add(sub);
            subShapeLabels.add(type + ": " + formatSubShape(sub));
            client.setScreen(this);
        }));
    }

    private void saveShape() {
        String name = nameField.getText().trim();
        if (name.isEmpty() || subShapes.isEmpty()) return;

        var list = new NbtList();
        for (var sub : subShapes) {
            list.add(sub);
        }

        ClientPacketHandler.addShape(authorityKey, name, list);
        client.setScreen(new AuthorityDetailScreen(authorityKey));
    }

    static String formatSubShape(NbtCompound sub) {
        String type = sub.getString("type");
        return switch (type) {
            case "universal" -> "Universal";
            case "dimension" -> sub.getString("dimension");
            case "box" -> sub.getString("dimension") + " [" +
                    sub.getInt("min_x") + "," + sub.getInt("min_y") + "," + sub.getInt("min_z") + "] -> [" +
                    sub.getInt("max_x") + "," + sub.getInt("max_y") + "," + sub.getInt("max_z") + "]";
            default -> type;
        };
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, "Sub-shapes (click to remove):", width / 2, 86, 0xAAAAAA);

        nameField.render(context, mouseX, mouseY, delta);

        int listTop = 98;
        int listBottom = height - 60;
        int entryHeight = 14;
        int maxVisible = (listBottom - listTop) / entryHeight;
        int startIdx = scrollOffset;
        int endIdx = Math.min(startIdx + maxVisible, subShapeLabels.size());

        for (int i = startIdx; i < endIdx; i++) {
            int y = listTop + (i - startIdx) * entryHeight;
            boolean hovered = mouseX >= 10 && mouseX <= width - 10 && mouseY >= y && mouseY < y + entryHeight;
            if (hovered) {
                context.fill(10, y, width - 10, y + entryHeight, 0x40FFFFFF);
            }
            context.drawTextWithShadow(textRenderer, (i + 1) + ". " + subShapeLabels.get(i), 14, y + 3, 0x55FFFF);
            if (hovered) {
                context.drawTextWithShadow(textRenderer, "[Remove]", width - 55, y + 3, 0xFF5555);
            }
        }

        if (subShapeLabels.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, "No sub-shapes added yet", width / 2, listTop + 10, 0x888888);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int entryHeight = 14;
        int listBottom = height - 60;
        int listTop = 98;
        int maxVisible = (listBottom - listTop) / entryHeight;
        if (amount < 0 && scrollOffset < Math.max(0, subShapeLabels.size() - maxVisible)) {
            scrollOffset++;
        } else if (amount > 0 && scrollOffset > 0) {
            scrollOffset--;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int listTop = 98;
            int entryHeight = 14;
            int listBottom = height - 60;
            int maxVisible = (listBottom - listTop) / entryHeight;
            int startIdx = scrollOffset;
            int endIdx = Math.min(startIdx + maxVisible, subShapeLabels.size());

            for (int i = startIdx; i < endIdx; i++) {
                int y = listTop + (i - startIdx) * entryHeight;
                if (mouseX >= 10 && mouseX <= width - 10 && mouseY >= y && mouseY < y + entryHeight) {
                    subShapes.remove(i);
                    subShapeLabels.remove(i);
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
}
