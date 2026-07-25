package xyz.nucleoid.leukocyte.client.screen;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import xyz.nucleoid.leukocyte.client.network.ClientPacketHandler;

import java.util.ArrayList;

public final class ShapesScreen extends Screen {
    private final String authorityKey;
    private final ArrayList<String> shapeNames;
    private final ArrayList<String> shapeDisplays;
    private final ArrayList<NbtCompound> shapeDataList;
    private int scrollOffset = 0;

    public ShapesScreen(String authorityKey, ArrayList<String> shapeNames, ArrayList<String> shapeDisplays, ArrayList<NbtCompound> shapeDataList) {
        super(Text.literal("Shapes: " + authorityKey));
        this.authorityKey = authorityKey;
        this.shapeNames = new ArrayList<>(shapeNames);
        this.shapeDisplays = new ArrayList<>(shapeDisplays);
        this.shapeDataList = new ArrayList<>(shapeDataList);
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("Add Shape"), button -> {
            client.setScreen(new AddShapeScreen(authorityKey));
        }).dimensions(10, height - 32, 98, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> {
            client.setScreen(new AuthorityDetailScreen(authorityKey));
        }).dimensions(width - 108, height - 32, 98, 20).build());
    }

    @Override
    public void close() {
        client.setScreen(null);
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);

        int listTop = 32;
        int listBottom = height - 40;
        int entryHeight = 24;
        int maxVisible = (listBottom - listTop) / entryHeight;

        int startIdx = scrollOffset;
        int endIdx = Math.min(startIdx + maxVisible, shapeNames.size());

        for (int i = startIdx; i < endIdx; i++) {
            int y = listTop + (i - startIdx) * entryHeight;

            boolean hovered = mouseX >= 10 && mouseX <= width - 10 && mouseY >= y && mouseY < y + entryHeight;
            if (hovered) {
                context.fill(10, y, width - 10, y + entryHeight, 0x40FFFFFF);
            }

            context.drawTextWithShadow(textRenderer, shapeNames.get(i), 14, y + 2, 0x55FFFF);
            context.drawTextWithShadow(textRenderer, shapeDisplays.get(i), 14, y + 12, 0xAAAAAA);

            context.drawTextWithShadow(textRenderer, "[Edit]", width / 2 + 40, y + 8, 0x55FF55);
            context.drawTextWithShadow(textRenderer, "[Remove]", width - 60, y + 8, 0xFF5555);
        }

        if (shapeNames.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, "No shapes defined", width / 2, listTop + 20, 0x888888);
        }

        String scrollText = shapeNames.isEmpty() ? "" : (startIdx + 1) + "-" + endIdx + " of " + shapeNames.size();
        context.drawCenteredTextWithShadow(textRenderer, scrollText, width / 2, listBottom + 4, 0x888888);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int entryHeight = 24;
        int listBottom = height - 40;
        int listTop = 32;
        int maxVisible = (listBottom - listTop) / entryHeight;

        if (amount < 0 && scrollOffset < Math.max(0, shapeNames.size() - maxVisible)) {
            scrollOffset++;
        } else if (amount > 0 && scrollOffset > 0) {
            scrollOffset--;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int listTop = 32;
            int entryHeight = 24;
            int listBottom = height - 40;
            int maxVisible = (listBottom - listTop) / entryHeight;
            int startIdx = scrollOffset;
            int endIdx = Math.min(startIdx + maxVisible, shapeNames.size());

            for (int i = startIdx; i < endIdx; i++) {
                int y = listTop + (i - startIdx) * entryHeight;
                if (mouseX >= 10 && mouseX <= width - 10 && mouseY >= y && mouseY < y + entryHeight) {
                    if (mouseX >= width - 60) {
                        String name = shapeNames.get(i);
                        ClientPacketHandler.removeShape(authorityKey, name);
                        shapeNames.remove(i);
                        shapeDisplays.remove(i);
                        shapeDataList.remove(i);
                        return true;
                    } else if (mouseX >= width / 2 + 40 && i < shapeDataList.size()) {
                        client.setScreen(new EditShapeScreen(authorityKey, shapeNames.get(i), shapeDataList.get(i)));
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
