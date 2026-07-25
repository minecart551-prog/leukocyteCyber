package xyz.nucleoid.leukocyte.client.screen;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public final class SubShapeInputScreen extends Screen {
    private final String shapeType;
    private final Consumer<NbtCompound> onComplete;
    private TextFieldWidget dimField;
    private TextFieldWidget minXField, minYField, minZField;
    private TextFieldWidget maxXField, maxYField, maxZField;

    public SubShapeInputScreen(String shapeType, Consumer<NbtCompound> onComplete) {
        super(Text.literal("Add " + shapeType + " shape"));
        this.shapeType = shapeType;
        this.onComplete = onComplete;
    }

    @Override
    protected void init() {
        int fieldW = 200;
        int fieldX = width / 2 - fieldW / 2;

        dimField = new TextFieldWidget(textRenderer, fieldX, 40, fieldW, 20, Text.literal(""));
        dimField.setPlaceholder(Text.literal("Dimension (e.g. minecraft:overworld)"));
        addDrawableChild(dimField);

        boolean isBox = shapeType.equals("box");

        int inputW = 60;
        int inputGap = 4;
        int inputStartX = width / 2 - (inputW * 3 + inputGap * 2) / 2;

        minXField = new TextFieldWidget(textRenderer, inputStartX, 80, inputW, 20, Text.literal(""));
        minXField.setPlaceholder(Text.literal("Min X"));
        minXField.setVisible(isBox);
        addDrawableChild(minXField);

        minYField = new TextFieldWidget(textRenderer, inputStartX + inputW + inputGap, 80, inputW, 20, Text.literal(""));
        minYField.setPlaceholder(Text.literal("Min Y"));
        minYField.setVisible(isBox);
        addDrawableChild(minYField);

        minZField = new TextFieldWidget(textRenderer, inputStartX + (inputW + inputGap) * 2, 80, inputW, 20, Text.literal(""));
        minZField.setPlaceholder(Text.literal("Min Z"));
        minZField.setVisible(isBox);
        addDrawableChild(minZField);

        maxXField = new TextFieldWidget(textRenderer, inputStartX, 110, inputW, 20, Text.literal(""));
        maxXField.setPlaceholder(Text.literal("Max X"));
        maxXField.setVisible(isBox);
        addDrawableChild(maxXField);

        maxYField = new TextFieldWidget(textRenderer, inputStartX + inputW + inputGap, 110, inputW, 20, Text.literal(""));
        maxYField.setPlaceholder(Text.literal("Max Y"));
        maxYField.setVisible(isBox);
        addDrawableChild(maxYField);

        maxZField = new TextFieldWidget(textRenderer, inputStartX + (inputW + inputGap) * 2, 110, inputW, 20, Text.literal(""));
        maxZField.setPlaceholder(Text.literal("Max Z"));
        maxZField.setVisible(isBox);
        addDrawableChild(maxZField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Add"), button -> addSubShape())
                .dimensions(width / 2 - 100, height - 52, 98, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> client.setScreen(null))
                .dimensions(width / 2 + 2, height - 52, 98, 20).build());
    }

    private void addSubShape() {
        var sub = new NbtCompound();
        sub.putString("type", shapeType);

        if (shapeType.equals("dimension") || shapeType.equals("box")) {
            sub.putString("dimension", dimField.getText().trim());
        }

        if (shapeType.equals("box")) {
            sub.putInt("min_x", parseIntSafe(minXField.getText()));
            sub.putInt("min_y", parseIntSafe(minYField.getText()));
            sub.putInt("min_z", parseIntSafe(minZField.getText()));
            sub.putInt("max_x", parseIntSafe(maxXField.getText()));
            sub.putInt("max_y", parseIntSafe(maxYField.getText()));
            sub.putInt("max_z", parseIntSafe(maxZField.getText()));
        }

        onComplete.accept(sub);
    }

    private int parseIntSafe(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);

        if (shapeType.equals("box")) {
            context.drawCenteredTextWithShadow(textRenderer, "Min:", width / 2 - 80, 70, 0x888888);
            context.drawCenteredTextWithShadow(textRenderer, "Max:", width / 2 - 80, 100, 0x888888);
        }

        dimField.render(context, mouseX, mouseY, delta);
        minXField.render(context, mouseX, mouseY, delta);
        minYField.render(context, mouseX, mouseY, delta);
        minZField.render(context, mouseX, mouseY, delta);
        maxXField.render(context, mouseX, mouseY, delta);
        maxYField.render(context, mouseX, mouseY, delta);
        maxZField.render(context, mouseX, mouseY, delta);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
