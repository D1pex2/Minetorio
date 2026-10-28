package dev.minetorio.client.area;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/**
 * Translucent colored quads for the overlays and alt-mode backdrops, in a buffer of their own so
 * they batch into one draw call per frame regardless of the items drawn in between.
 *
 * <ul>
 *   <li>{@link #GROUND}: no depth write and a view offset, so ground overlays sit on the terrain
 *       without z-fighting and never hide what is drawn after them;</li>
 *   <li>{@link #SOLID}: depth write (backdrops, arrows), so the icon in front wins.</li>
 * </ul>
 */
public final class OverlayQuads extends RenderType {
    public static final RenderType GROUND = create("minetorio_ground_overlay", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1 << 16, false, true,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .createCompositeState(false));
    public static final RenderType SOLID = create("minetorio_alt_backdrop", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1 << 16, false, true,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    private static MultiBufferSource.BufferSource buffers;

    private OverlayQuads() {
        super("minetorio_dummy", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
    }

    /** The overlays' own buffer source (render thread only). */
    public static MultiBufferSource.BufferSource buffers() {
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 18));
        return buffers;
    }

    /** One quad with a single ARGB color. */
    public static void quad(VertexConsumer vc, Matrix4f m, int argb,
                            float x0, float y0, float z0, float x1, float y1, float z1,
                            float x2, float y2, float z2, float x3, float y3, float z3) {
        vc.addVertex(m, x0, y0, z0).setColor(argb);
        vc.addVertex(m, x1, y1, z1).setColor(argb);
        vc.addVertex(m, x2, y2, z2).setColor(argb);
        vc.addVertex(m, x3, y3, z3).setColor(argb);
    }
}
