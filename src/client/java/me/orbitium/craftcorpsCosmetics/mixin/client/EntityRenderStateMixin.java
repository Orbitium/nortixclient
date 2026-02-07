package me.orbitium.craftcorpsCosmetics.mixin.client;

import net.minecraft.client.render.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import java.util.UUID;

@Mixin(EntityRenderState.class)
public class EntityRenderStateMixin implements IEntityRenderStateExtension {
    @Unique
    private UUID craftcorps$uuid;

    @Override
    public void craftcorps$setUuid(UUID uuid) {
        this.craftcorps$uuid = uuid;
    }

    @Override
    public UUID craftcorps$getUuid() {
        return this.craftcorps$uuid;
    }
}
