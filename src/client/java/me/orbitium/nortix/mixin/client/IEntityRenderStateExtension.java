package me.orbitium.nortix.mixin.client;

import java.util.UUID;

public interface IEntityRenderStateExtension {
    void nortix$setUuid(UUID uuid);

    UUID nortix$getUuid();
}
