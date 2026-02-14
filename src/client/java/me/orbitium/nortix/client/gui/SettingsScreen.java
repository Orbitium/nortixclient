package me.orbitium.nortix.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class SettingsScreen extends Screen {
    private final Screen parent;

    public SettingsScreen(Screen parent) {
        super(Text.translatable("menu.nortix.settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 100;
        int y = 50;
        int rowHeight = 24;

        me.orbitium.nortix.client.util.ConfigManager.Config config = me.orbitium.nortix.client.util.ConfigManager
                .getInstance();

        // Singleplayer Toggle
        this.addDrawableChild(
                net.minecraft.client.gui.widget.CyclingButtonWidget.onOffBuilder(config.showSingleplayerRPC)
                        .build(x, y, 200, 20, Text.translatable("menu.nortix.settings.show_singleplayer"),
                                (button, value) -> {
                                    config.showSingleplayerRPC = value;
                                    me.orbitium.nortix.client.util.ConfigManager.save();
                                }));

        y += rowHeight;

        // Multiplayer Toggle
        this.addDrawableChild(
                net.minecraft.client.gui.widget.CyclingButtonWidget.onOffBuilder(config.showMultiplayerRPC)
                        .build(x, y, 200, 20, Text.translatable("menu.nortix.settings.show_multiplayer"),
                                (button, value) -> {
                                    config.showMultiplayerRPC = value;
                                    me.orbitium.nortix.client.util.ConfigManager.save();
                                }));

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> {
            this.client.setScreen(this.parent);
        }).dimensions(x, this.height - 30, 200, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("menu.nortix.settings.discord_rpc"),
                this.width / 2, 38,
                0xFFAAAAAA);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
