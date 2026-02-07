package me.orbitium.nortix.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class DiscoveryScreen extends Screen {
    private final Screen parent;

    public DiscoveryScreen(Screen parent) {
        super(Text.translatable("menu.craftcorps.discovery"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 100;
        int y = this.height - 30;

        // Back button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> {
            this.client.setScreen(this.parent);
        }).dimensions(x, y, 200, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Discovery Menu"), this.width / 2, 20,
                0xFFFFFFFF);

        // Mockup server list background
        int listWidth = 300;
        int listHeight = this.height - 80;
        int listX = (this.width - listWidth) / 2;
        int listY = 40;

        context.fill(listX, listY, listX + listWidth, listY + listHeight, 0x88000000);

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Coming Soon: Featured Servers"),
                this.width / 2, this.height / 2, 0xFFAAAAAA);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
