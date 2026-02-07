package me.orbitium.nortix.client.model;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public class HatModel<T extends GeoAnimatable> extends GeoModel<T> {

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return Identifier.of("craftcorps-cosmetics", "geo/beaver_hat.json");
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return Identifier.of("craftcorps-cosmetics", "textures/item/beaver_hat.png");
    }

    @Override
    public Identifier getAnimationResource(T animatable) {
        return Identifier.of("craftcorps-cosmetics", "animations/beaver_hat.animation.json");
    }
}
