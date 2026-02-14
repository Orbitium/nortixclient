package me.orbitium.nortix.mixin.client;

import me.orbitium.nortix.client.gui.AccountOverlay;
import me.orbitium.nortix.client.gui.DiscoveryScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.util.math.ColorHelper;

@Mixin(value = TitleScreen.class, priority = 2000)
public abstract class MixinTitleScreen extends net.minecraft.client.gui.screen.Screen {

    protected MixinTitleScreen(Text title) {
        super(title);
    }

    private net.minecraft.client.gui.widget.ButtonWidget modsButtonRef = null;
    private float recordedFadeAlpha = 1.0f;

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        // Center alignment
        int centerX = this.width / 2 - 100;
        // Y offset: Shifted down by 10% of screen height to be further from the title
        int yOffset = (int) (this.height * 0.1);

        // Collect and remove unwanted buttons/icons
        java.util.List<net.minecraft.client.gui.Element> toRemove = new java.util.ArrayList<>();

        for (net.minecraft.client.gui.Element element : this.children()) {
            if (element instanceof ClickableWidget widget) {
                String text = widget.getMessage().getString().toLowerCase();

                // Identify the main 3 buttons we want to keep and move
                boolean isMainButton = text.contains("singleplayer") ||
                        text.contains("multiplayer") ||
                        text.contains("realms") ||
                        text.contains("play"); // Catch-all for some language packs

                if (isMainButton && widget.getWidth() == 200) {
                    widget.setX(centerX);
                    widget.setY(widget.getY() + yOffset);
                } else {
                    // Remove anything else in the main stack area or specific unwanted buttons
                    // Width 200 is typical for main stack buttons (including Mods)
                    if (widget.getWidth() == 200 || widget.getWidth() <= 48 ||
                            text.contains("options") || text.contains("quit") || text.contains("mods")) {

                        toRemove.add(element);

                        // Try to preserve a reference to the Mods button specifically if possible
                        if (text.contains("mods")
                                && widget instanceof net.minecraft.client.gui.widget.ButtonWidget bw) {
                            this.modsButtonRef = bw;
                        }
                    }
                }
            }
        }
        toRemove.forEach(this::remove);

        // Top right buttons
        int btnHeight = 20;
        int quitWidth = 60;
        int optionsWidth = 80;
        int modsWidth = 60;
        int quitX = this.width - quitWidth - 7;
        int optionsX = quitX - optionsWidth - 5;
        int modsX = optionsX - modsWidth - 5;
        int topY = 7;

        // Add Mods button simulation (Top Right)
        this.addSelectableChild(net.minecraft.client.gui.widget.ButtonWidget.builder(Text.literal("Mods"), button -> {
            try {
                // Primary approach: Try to open ModMenu screen directly via reflection
                Class<?> screenClass = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
                this.client.setScreen((net.minecraft.client.gui.screen.Screen) screenClass
                        .getConstructor(net.minecraft.client.gui.screen.Screen.class).newInstance(this));
            } catch (Exception e) {
                // Secondary approach: If reflection fails, use the button press if we captured
                // it
                if (modsButtonRef != null) {
                    // This is version-dependent, so we rely on reflection mostly
                }
            }
        }).dimensions(modsX, topY, modsWidth, btnHeight).build());

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
        // Render custom backgrounds for top-right buttons
        int btnHeight = 20;
        int quitWidth = 60;
        int optionsWidth = 80;
        int modsWidth = 60;
        int quitX = this.width - quitWidth - 7;
        int optionsX = quitX - optionsWidth - 5;
        int modsX = optionsX - modsWidth - 5;
        int topY = 7;

        // Keep a reference for center alignment
        int centerX = this.width / 2 - 100;

        // Extra safety: Identify and hide any 200-width buttons that shouldn't be here
        // (added by other mods after init)
        for (net.minecraft.client.gui.Element element : this.children()) {
            if (element instanceof ClickableWidget widget && widget.visible) {
                if (widget.getWidth() == 200) {
                    String text = widget.getMessage().getString().toLowerCase();
                    boolean isMainButton = text.contains("singleplayer") ||
                            text.contains("multiplayer") ||
                            text.contains("realms") ||
                            text.contains("play");

                    // If it's a 200-width button that ISN'T a main button, hide it
                    // This catches buttons added late by Loader/Mods
                    if (!isMainButton && !text.isEmpty()) {
                        widget.visible = false;
                        continue;
                    }

                    // Draw main buttons with custom black background
                    boolean hovered = mouseX >= widget.getX() && mouseX <= widget.getX() + widget.getWidth()
                            && mouseY >= widget.getY() && mouseY <= widget.getY() + widget.getHeight();

                    context.fill(widget.getX(), widget.getY(), widget.getX() + widget.getWidth(),
                            widget.getY() + widget.getHeight(), hovered ? 0xFF444444 : 0xAA000000);
                    context.drawCenteredTextWithShadow(this.textRenderer, widget.getMessage(),
                            widget.getX() + widget.getWidth() / 2, widget.getY() + (widget.getHeight() - 8) / 2,
                            0xFFFFFFFF);
                }
            }
        }

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

        // Mods Button Background
        boolean modsHovered = mouseX >= modsX && mouseX <= modsX + modsWidth && mouseY >= topY
                && mouseY <= topY + btnHeight;
        context.fill(modsX, topY, modsX + modsWidth, topY + btnHeight, modsHovered ? 0xFF444444 : 0xAA000000);
        context.drawCenteredTextWithShadow(this.textRenderer, "Mods", modsX + modsWidth / 2,
                topY + (btnHeight - 8) / 2,
                0xFFFFFFFF);

        AccountOverlay.render(context, mouseX, mouseY, delta);

        // Draw our custom branding text
        context.drawTextWithShadow(this.textRenderer, "Nortix Minecraft 1.21.11 Client", 2, this.height - 10,
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
