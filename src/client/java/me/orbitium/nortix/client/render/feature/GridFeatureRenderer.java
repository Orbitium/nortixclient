package me.orbitium.nortix.client.render.feature;

import net.fabricmc.fabric.api.renderer.v1.render.RenderLayerHelper;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.joml.Matrix4f;

public class GridFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    public GridFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices,
            net.minecraft.client.render.command.OrderedRenderCommandQueue vertexConsumers, int light,
            PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        if (state.invisible)
            return;

        // Use getDebugLineStrip from RenderLayer
        VertexConsumer vertexConsumer = null;

        matrices.push();
        // Scale slightly to be outside the player model
        matrices.scale(1.1f, 1.1f, 1.1f);

        Matrix4f matrix4f = matrices.peek().getPositionMatrix();

        // Simple grid box around the player
        drawGrid(vertexConsumer, matrix4f);

        matrices.pop();
    }

    private void drawGrid(VertexConsumer consumer, Matrix4f matrix) {
        // Front face
        drawLine(consumer, matrix, -0.5f, 0, -0.5f, 0.5f, 0, -0.5f);
        drawLine(consumer, matrix, -0.5f, 1, -0.5f, 0.5f, 1, -0.5f);
        drawLine(consumer, matrix, -0.5f, 2, -0.5f, 0.5f, 2, -0.5f);

        drawLine(consumer, matrix, -0.5f, 0, -0.5f, -0.5f, 2, -0.5f);
        drawLine(consumer, matrix, 0, 0, -0.5f, 0, 2, -0.5f);
        drawLine(consumer, matrix, 0.5f, 0, -0.5f, 0.5f, 2, -0.5f);

        // Back face
        drawLine(consumer, matrix, -0.5f, 0, 0.5f, 0.5f, 0, 0.5f);
        drawLine(consumer, matrix, -0.5f, 1, 0.5f, 0.5f, 1, 0.5f);
        drawLine(consumer, matrix, -0.5f, 2, 0.5f, 0.5f, 2, 0.5f);

        drawLine(consumer, matrix, -0.5f, 0, 0.5f, -0.5f, 2, 0.5f);
        drawLine(consumer, matrix, 0, 0, 0.5f, 0, 2, 0.5f);
        drawLine(consumer, matrix, 0.5f, 0, 0.5f, 0.5f, 2, 0.5f);

        // Sides
        drawLine(consumer, matrix, -0.5f, 0, -0.5f, -0.5f, 0, 0.5f);
        drawLine(consumer, matrix, -0.5f, 1, -0.5f, -0.5f, 1, 0.5f);
        drawLine(consumer, matrix, -0.5f, 2, -0.5f, -0.5f, 2, 0.5f);

        drawLine(consumer, matrix, 0.5f, 0, -0.5f, 0.5f, 0, 0.5f);
        drawLine(consumer, matrix, 0.5f, 1, -0.5f, 0.5f, 1, 0.5f);
        drawLine(consumer, matrix, 0.5f, 2, -0.5f, 0.5f, 2, 0.5f);
    }

    private void drawLine(VertexConsumer consumer, Matrix4f matrix, float x1, float y1, float z1, float x2, float y2,
            float z2) {
        consumer.vertex(matrix, x1, y1, z1).color(0, 255, 0, 255).normal(0, 1, 0);
        consumer.vertex(matrix, x2, y2, z2).color(0, 255, 0, 255).normal(0, 1, 0);
    }
}
