package me.orbitium.nortix.mixin.client;

import net.minecraft.client.render.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import java.util.UUID;

@Mixin(EntityRenderState.class)
public class EntityRenderStateMixin implements IEntityRenderStateExtension {
    @Unique
    private UUID nortix$uuid;

    @Override
    public void nortix$setUuid(UUID uuid) {
        this.nortix$uuid = uuid;
    }

    @Override
    public UUID nortix$getUuid() {
        return this.nortix$uuid;
    }
}
