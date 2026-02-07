package me.orbitium.nortix.mixin.client;

import java.util.UUID;

public interface IEntityRenderStateExtension {
    void craftcorps$setUuid(UUID uuid);

    UUID craftcorps$getUuid();
}
