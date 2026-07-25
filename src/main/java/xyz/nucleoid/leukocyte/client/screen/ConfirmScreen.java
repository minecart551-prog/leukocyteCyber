package xyz.nucleoid.leukocyte.client.screen;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public final class ConfirmScreen extends Screen {
    private final String message;
    private final Consumer<Boolean> callback;

    public ConfirmScreen(String message, Consumer<Boolean> callback) {
        super(Text.literal("Confirm"));
        this.message = message;
        this.callback = callback;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("Yes"), button -> {
            callback.accept(true);
        }).dimensions(width / 2 - 100, height / 2, 98, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("No"), button -> {
            callback.accept(false);
        }).dimensions(width / 2 + 2, height / 2, 98, 20).build());
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, message, width / 2, height / 2 - 20, 0xFFFFFF);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
