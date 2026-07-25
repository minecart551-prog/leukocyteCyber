package xyz.nucleoid.leukocyte.client.screen;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import xyz.nucleoid.leukocyte.client.network.ClientPacketHandler;

import java.util.ArrayList;

public final class EditShapeScreen extends Screen {
    private final String authorityKey;
    private final String shapeName;
    private final ArrayList<NbtCompound> subShapes = new ArrayList<>();
    private final ArrayList<String> subShapeLabels = new ArrayList<>();
    private int scrollOffset = 0;

    public EditShapeScreen(String authorityKey, String shapeName, NbtCompound shapeData) {
        super(Text.literal("Edit Shape: " + shapeName));
        this.authorityKey = authorityKey;
        this.shapeName = shapeName;

        parseShapeData(shapeData);
    }

    private void parseShapeData(NbtCompound shapeData) {
        String type = shapeData.contains("type") ? shapeData.getString("type") : "";

        if (type.equals("union")) {
            var value = shapeData.getList("value", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < value.size(); i++) {
                var sub = value.getCompound(i);
                subShapes.add(sub);
                subShapeLabels.add(AddShapeScreen.formatSubShape(sub));
            }
        } else if (!type.isEmpty()) {
            subShapes.add(shapeData);
            subShapeLabels.add(AddShapeScreen.formatSubShape(shapeData));
        }
    }

    @Override
    protected void init() {
        int btnWidth = 62;
        int startX = width / 2 - (btnWidth * 3 + 8) / 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("Universal"), button -> addSubShape("universal"))
                .dimensions(startX, 32, btnWidth, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Dimension"), button -> openSubShapeDialog("dimension"))
                .dimensions(startX + btnWidth + 4, 32, btnWidth, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Box"), button -> openSubShapeDialog("box"))
                .dimensions(startX + (btnWidth + 4) * 2, 32, btnWidth, 18).build());

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
            subShapeLabels.add(type + ": " + AddShapeScreen.formatSubShape(sub));
            client.setScreen(this);
        }));
    }

    private void saveShape() {
        if (subShapes.isEmpty()) {
            ClientPacketHandler.removeShape(authorityKey, shapeName);
            client.setScreen(new ShapesScreen(authorityKey, new ArrayList<>(), new ArrayList<>(), new ArrayList<>()));
            return;
        }

        var list = new NbtList();
        for (var sub : subShapes) {
            list.add(sub);
        }

        ClientPacketHandler.setShape(authorityKey, shapeName, list);
        client.setScreen(new AuthorityDetailScreen(authorityKey));
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, "Sub-shapes (click to remove):", width / 2, 58, 0xAAAAAA);

        int listTop = 70;
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
            context.drawCenteredTextWithShadow(textRenderer, "No sub-shapes (shape will be deleted on save)", width / 2, listTop + 10, 0xFF5555);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int entryHeight = 14;
        int listBottom = height - 60;
        int listTop = 70;
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
            int listTop = 70;
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
