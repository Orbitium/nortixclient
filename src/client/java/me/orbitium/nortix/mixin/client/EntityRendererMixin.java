package me.orbitium.nortix.mixin.client;

import me.orbitium.nortix.client.NametagManager;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.client.render.entity.state.EntityRenderState;

import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {

        @Inject(at = @At("TAIL"), method = "updateRenderState")
        public void updateRenderState(T entity, S state, float d, CallbackInfo info) {
                if (!(entity instanceof AbstractClientPlayerEntity player))
                        return;

                Identifier icon = NametagManager.getNametagIcon(player.getUuid());
                if (icon != null && state.displayName != null) {
                        // Create a special placeholder component for the icon
                        // We use a specific color (invisible or unique) to identify it in the
                        // TextRenderer
                        // Using a unique font identifier might be safer if possible, but color is
                        // easier for now.
                        // Let's use a magic marker char, e.g. a private use area char, or just a known
                        // string.

                        // We'll use a Style with a custom placeholder font or color.
                        // Since we can't easily add fonts, let's use a unique, unused TextColor or just
                        // detect via content if simpler.
                        // But content detection is brittle.

                        // Let's try appending a component with a special Style.
                        // We will inject into TextRenderer to intercept this.

                        // We use a very specific color that we can check for: instance of a custom
                        // style? No, style is final/data.
                        // Let's mark it by inserting a specifically styled empty text.

                        // Actually, inserting " " (two spaces) with a specific Obfuscated style?
                        // Or just a specific dummy placeholder string.
                        // Let's use a specific Unicode character from the Private Use Area.
                        String ICON_CHAR = "\uE001";

                        Text originalName = state.displayName;
                        state.displayName = Text.literal(ICON_CHAR).append(" ").append(originalName);
                }
        }
}
