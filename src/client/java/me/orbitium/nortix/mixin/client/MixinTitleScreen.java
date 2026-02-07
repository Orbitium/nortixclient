package me.orbitium.nortix.mixin.client;

import me.orbitium.nortix.client.gui.AccountOverlay;
import me.orbitium.nortix.client.gui.DiscoveryScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.util.math.ColorHelper;

@Mixin(TitleScreen.class)
public abstract class MixinTitleScreen extends net.minecraft.client.gui.screen.Screen {

    protected MixinTitleScreen(Text title) {
        super(title);
    }

    private float recordedFadeAlpha = 1.0f;

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        int x = this.width / 2 - 100;
        int rowHeight = 24;
        int startY = this.height / 4 + 48;
        int discoveryY = startY + rowHeight;

        // Collect and remove unwanted buttons/icons (Options, Quit, and small ones)
        java.util.List<net.minecraft.client.gui.Element> toRemove = new java.util.ArrayList<>();
        for (net.minecraft.client.gui.Element element : this.children()) {
            if (element instanceof net.minecraft.client.gui.widget.ClickableWidget widget) {
                String text = widget.getMessage().getString().toLowerCase();
                // Remove Options and Quit only (Realms is now kept)
                if (text.contains("options") || text.contains("quit")) {
                    toRemove.add(element);
                }
                // Remove small icon buttons (Language, Accessibility)
                if (widget.getWidth() <= 24 && widget.getHeight() <= 24) {
                    toRemove.add(element);
                }
            }
        }
        toRemove.forEach(this::remove);

        // Shift remaining buttons (like Realms) down further to make room for Discovery

        this.addDrawableChild(
                net.minecraft.client.gui.widget.ButtonWidget
                        .builder(Text.translatable("menu.craftcorps.discovery"), button -> {
                            this.client.setScreen(new DiscoveryScreen(this));
                        }).dimensions(x, discoveryY, 200, 20).build()).active = false;

        // Top right buttons
        int btnHeight = 20;
        int quitWidth = 60;
        int optionsWidth = 80;
        int quitX = this.width - quitWidth - 7;
        int optionsX = quitX - optionsWidth - 5;
        int topY = 7;

        // Add Options button simulation
        this.addSelectableChild(
                net.minecraft.client.gui.widget.ButtonWidget.builder(Text.literal("Settings #"), button -> {
                    this.client.setScreen(
                            new net.minecraft.client.gui.screen.option.OptionsScreen(this, this.client.options));
                }).dimensions(optionsX, topY, optionsWidth, btnHeight).build());

        // Add Quit button simulation
        this.addSelectableChild(net.minecraft.client.gui.widget.ButtonWidget.builder(Text.literal("Exit X"), button -> {
            this.client.stop();
        }).dimensions(quitX, topY, quitWidth, btnHeight).build());
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/ColorHelper;getWhite(F)I"))
    private int redirectGetWhite(float f) {
        this.recordedFadeAlpha = f;
        return 0; // Return 0 (Transparent) to hide the original text
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // The logo area is now empty because we cancelled renderLogo

        // Render custom backgrounds for top-right buttons
        int btnHeight = 20;
        int quitWidth = 60;
        int optionsWidth = 80;
        int quitX = this.width - quitWidth - 7;
        int optionsX = quitX - optionsWidth - 5;
        int topY = 7;

        // Quit Button Red Background
        boolean quitHovered = mouseX >= quitX && mouseX <= quitX + quitWidth && mouseY >= topY
                && mouseY <= topY + btnHeight;
        context.fill(quitX, topY, quitX + quitWidth, topY + btnHeight, quitHovered ? 0xFFFF0000 : 0xAAFF0000);
        context.drawCenteredTextWithShadow(this.textRenderer, "Exit X", quitX + quitWidth / 2,
                topY + (btnHeight - 8) / 2,
                0xFFFFFFFF);

        // Options Button Background
        boolean optHovered = mouseX >= optionsX && mouseX <= optionsX + optionsWidth && mouseY >= topY
                && mouseY <= topY + btnHeight;
        context.fill(optionsX, topY, optionsX + optionsWidth, topY + btnHeight, optHovered ? 0xFF444444 : 0xAA000000);
        context.drawCenteredTextWithShadow(this.textRenderer, "Settings #", optionsX + optionsWidth / 2,
                topY + (btnHeight - 8) / 2,
                0xFFFFFFFF);

        AccountOverlay.render(context, mouseX, mouseY, delta);

        // Draw our custom branding text
        context.drawTextWithShadow(this.textRenderer, "CraftCorps Minecraft 1.21.11 Client", 2, this.height - 10,
                ColorHelper.getWhite(this.recordedFadeAlpha) | 0xFF000000);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(net.minecraft.client.gui.Click click, boolean leftButton,
            CallbackInfoReturnable<Boolean> cir) {
        if (AccountOverlay.onMouseClicked(click, leftButton)) {
            cir.setReturnValue(true);
        }
    }
}
