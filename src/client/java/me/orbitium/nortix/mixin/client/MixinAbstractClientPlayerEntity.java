package me.orbitium.nortix.mixin.client;

import com.mojang.authlib.GameProfile;
import me.orbitium.nortix.client.CapeManager;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListEntry.class)
public abstract class MixinAbstractClientPlayerEntity {

    @Shadow
    @Final
    private GameProfile profile;

    @Inject(method = "getSkinTextures", at = @At("TAIL"), cancellable = true)
    private void getCapeTexture(CallbackInfoReturnable<SkinTextures> cir) {
        try {
            SkinTextures oldTextures = cir.getReturnValue();
            if (oldTextures == null || profile == null) {
                return;
            }

            // Get custom cape from CapeManager (will trigger API fetch if needed)
            Identifier customCape = CapeManager.getCape(profile.id());

            if (customCape != null) {
                try {
                    // Create a TextureAssetInfo for the custom cape
                    AssetInfo.TextureAsset capeTexture = new AssetInfo.TextureAssetInfo(customCape, customCape);

                    // Keep the original elytra texture
                    AssetInfo.TextureAsset elytraTexture = oldTextures.elytra();

                    // Create new SkinTextures with the custom cape
                    SkinTextures newTextures = new SkinTextures(
                            oldTextures.body(),
                            capeTexture,
                            elytraTexture,
                            oldTextures.model(),
                            oldTextures.secure());

                    cir.setReturnValue(newTextures);
                } catch (Exception e) {
                    // If there's any error creating the custom cape texture, just use the original
                    // This prevents crashes from missing textures or other rendering issues
                    System.err.println("[CraftCorps-Cosmetics] Failed to apply custom cape: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (Throwable t) {
            // Catch absolutely everything to prevent crashes
            System.err.println("[CraftCorps-Cosmetics] Critical error in cape rendering: " + t.getMessage());
            t.printStackTrace();
        }
    }
}
