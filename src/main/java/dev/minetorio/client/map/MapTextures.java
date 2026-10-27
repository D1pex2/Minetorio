package dev.minetorio.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import dev.minetorio.Minetorio;
import dev.minetorio.content.map.ClientMapData;
import dev.minetorio.core.map.ChunkKeys;
import dev.minetorio.core.map.MapPalette;
import dev.minetorio.core.map.MapRegion;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.MapColor;

/**
 * The explored map as GPU textures, one 512×512 texture (1 px per block) per {@link MapRegion}, created
 * when the region's first chunk arrives. Updated incrementally: each frame the chunks received since
 * the last frame ({@link ClientMapData#pollChanged}) are written into their region's image and only
 * the changed regions are uploaded. Unexplored pixels stay transparent over a dark background.
 * Shared by the minimap and the full-screen map. Render thread only.
 */
public final class MapTextures {
    public static final int REGION_PX = MapRegion.SIZE * 16;
    public static final int UNEXPLORED = 0xFF0E0F12;
    /** ABGR of an explored column with no map color. */
    private static final int NO_COLOR_ABGR = 0xFF202020;
    /** ABGR of the {@link MapPalette} markers: machines light steel blue, poles wood brown. */
    private static final int MACHINE_ABGR = 0xFFD2B48C;
    private static final int POLE_ABGR = 0xFF2E5A8A;
    /** Chunks written per frame at most (a login backlog spreads over several frames). */
    private static final int CHUNKS_PER_FRAME = 512;

    private static final class Region {
        final DynamicTexture texture;
        final ResourceLocation id;
        boolean dirty;

        Region(long key) {
            texture = new DynamicTexture(REGION_PX, REGION_PX, true);
            id = ResourceLocation.fromNamespaceAndPath(Minetorio.MODID,
                    "map/region_" + (ChunkKeys.x(key) + "_" + ChunkKeys.z(key)).replace('-', 'm'));
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }
    }

    private static final Map<Long, Region> REGIONS = new HashMap<>();
    private static int generation = Integer.MIN_VALUE;

    private MapTextures() {}

    /** ABGR (NativeImage order) of a {@link MapPalette} value. */
    public static int abgr(byte value) {
        if (value == MapPalette.MARKER_MACHINE) return MACHINE_ABGR;
        if (value == MapPalette.MARKER_POLE) return POLE_ABGR;
        if (MapPalette.isMarker(value)) return MACHINE_ABGR;
        if (value == 0) return NO_COLOR_ABGR;
        int c = MapColor.getColorFromPackedId(value & 0xFF);
        return c == 0 ? NO_COLOR_ABGR : c;
    }

    /** Applies received chunks and uploads changed regions; drops everything after a reset. */
    public static void update() {
        if (generation != ClientMapData.generation()) {
            clear();
            generation = ClientMapData.generation();
        }
        for (long key : ClientMapData.pollChanged(CHUNKS_PER_FRAME)) write(key);
        for (Region r : REGIONS.values()) {
            if (r.dirty) {
                r.texture.upload();
                r.dirty = false;
            }
        }
    }

    private static void write(long chunk) {
        byte[] colors = ClientMapData.chunk(chunk);
        if (colors == null) return;
        long rk = ChunkKeys.region(chunk);
        Region r = REGIONS.computeIfAbsent(rk, Region::new);
        NativeImage img = r.texture.getPixels();
        if (img == null) return;
        int local = ChunkKeys.local(chunk);
        int px = (local % MapRegion.SIZE) * 16, pz = (local / MapRegion.SIZE) * 16;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) img.setPixelRGBA(px + x, pz + z, abgr(colors[x + z * 16]));
        }
        r.dirty = true;
    }

    private static void clear() {
        for (Iterator<Region> it = REGIONS.values().iterator(); it.hasNext(); ) {
            Minecraft.getInstance().getTextureManager().release(it.next().id);
            it.remove();
        }
    }

    /**
     * Draws the map into the GUI rectangle (x0, y0)–(x1, y1) with world position (cx, cz) at its
     * center and {@code scale} GUI pixels per block (north up). The caller clips (scissor) if needed.
     */
    public static void draw(GuiGraphics g, double cx, double cz, float scale, int x0, int y0, int x1, int y1) {
        update();
        g.fill(x0, y0, x1, y1, UNEXPLORED);
        double halfW = (x1 - x0) / 2.0 / scale, halfH = (y1 - y0) / 2.0 / scale;
        int rx0 = Math.floorDiv((int) Math.floor(cx - halfW), REGION_PX), rx1 = Math.floorDiv((int) Math.floor(cx + halfW), REGION_PX);
        int rz0 = Math.floorDiv((int) Math.floor(cz - halfH), REGION_PX), rz1 = Math.floorDiv((int) Math.floor(cz + halfH), REGION_PX);
        int bx = (int) Math.floor(cx), bz = (int) Math.floor(cz);
        g.pose().pushPose();
        g.pose().translate((x0 + x1) / 2.0, (y0 + y1) / 2.0, 0);
        g.pose().scale(scale, scale, 1);
        g.pose().translate(-(cx - bx), -(cz - bz), 0);
        for (int rz = rz0; rz <= rz1; rz++) {
            for (int rx = rx0; rx <= rx1; rx++) {
                Region r = REGIONS.get(ChunkKeys.of(rx, rz));
                if (r == null) continue;
                g.blit(r.id, rx * REGION_PX - bx, rz * REGION_PX - bz, 0, 0, REGION_PX, REGION_PX, REGION_PX, REGION_PX);
            }
        }
        g.pose().popPose();
    }
}
