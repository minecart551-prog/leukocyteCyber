package xyz.nucleoid.leukocyte.client.screen;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public final class TextInputScreen extends Screen {
    private final String label;
    private final Consumer<String> onComplete;
    private TextFieldWidget textField;

    public TextInputScreen(String title, String label, Consumer<String> onComplete) {
        super(Text.literal(title));
        this.label = label;
        this.onComplete = onComplete;
    }

    @Override
    protected void init() {
        textField = new TextFieldWidget(textRenderer, width / 2 - 100, height / 2 - 10, 200, 20, Text.literal(""));
        textField.setFocused(true);
        addDrawableChild(textField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Confirm"), button -> {
            onComplete.accept(textField.getText());
            client.setScreen(null);
        }).dimensions(width / 2 - 100, height / 2 + 20, 98, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> {
            client.setScreen(null);
        }).dimensions(width / 2 + 2, height / 2 + 20, 98, 20).build());
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, label, width / 2, height / 2 - 28, 0xAAAAAA);
        textField.render(context, mouseX, mouseY, delta);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
