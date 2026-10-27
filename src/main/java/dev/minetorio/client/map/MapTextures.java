package dev.minetorio.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import dev.minetorio.Minetorio;
import dev.minetorio.content.map.ClientMapData;
import dev.minetorio.core.map.ChunkKeys;
import dev.minetorio.core.map.MapPalette;
import dev.minetorio.core.map.MapRegion;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.MapColor;

/**
 * A bounded cache of GPU textures of the explored map, one 512×512 texture (1 px per block) per
 * {@link MapRegion}, shared by the minimap and the full-screen map. {@link ClientMapData} is the
 * source of truth: a region's texture is built from it when the region is first drawn and dropped
 * again (least recently drawn first) once more than {@link #cacheSize} are cached — {@value #MIN_CACHED}
 * or the number visible in the last frame plus a few, whichever is larger.
 *
 * <p>Per frame at most {@value #BUILDS_PER_FRAME} textures are built and {@value #UPLOADS_PER_FRAME}
 * uploaded; chunks received for a cached region are written into its image and only the changed
 * rectangle is uploaded. A region not uploaded yet is not drawn (the dark background shows).
 * Render thread only.
 */
public final class MapTextures {
    public static final int REGION_PX = MapRegion.SIZE * 16;
    public static final int UNEXPLORED = 0xFF0E0F12;
    static final int MIN_CACHED = 32;
    private static final int BUILDS_PER_FRAME = 2;
    private static final int UPLOADS_PER_FRAME = 2;
    /** ABGR (NativeImage order) of an explored column with no map color. */
    private static final int NO_COLOR_ABGR = 0xFF202020;
    /** ABGR of the {@link MapPalette} markers: machines light steel blue, poles wood brown. */
    private static final int MACHINE_ABGR = 0xFFD2B48C;
    private static final int POLE_ABGR = 0xFF2E5A8A;
    /** Received chunks applied per frame at most (a login backlog spreads over several frames). */
    private static final int CHUNKS_PER_FRAME = 512;

    private static final class Region {
        final DynamicTexture texture;
        final ResourceLocation id;
        boolean uploaded;
        /** Changed pixel rectangle not uploaded yet (inclusive min, exclusive max); empty when minX >= maxX. */
        int minX = REGION_PX, minY = REGION_PX, maxX, maxY;

        Region(long key) {
            texture = new DynamicTexture(REGION_PX, REGION_PX, true);
            id = ResourceLocation.fromNamespaceAndPath(Minetorio.MODID,
                    "map/region_" + (ChunkKeys.x(key) + "_" + ChunkKeys.z(key)).replace('-', 'm'));
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }

        boolean dirty() { return minX < maxX; }

        void touch(int x0, int y0, int x1, int y1) {
            minX = Math.min(minX, x0);
            minY = Math.min(minY, y0);
            maxX = Math.max(maxX, x1);
            maxY = Math.max(maxY, y1);
        }

        void upload() {
            NativeImage img = texture.getPixels();
            if (img == null) return;
            texture.bind();
            img.upload(0, minX, minY, minX, minY, maxX - minX, maxY - minY, false, false);
            minX = minY = REGION_PX;
            maxX = maxY = 0;
            uploaded = true;
        }
    }

    /** Access order: the least recently drawn region first. */
    private static final LinkedHashMap<Long, Region> REGIONS = new LinkedHashMap<>(16, 0.75f, true);
    private static int generation = Integer.MIN_VALUE;
    private static int cacheSize = MIN_CACHED;
    private static int buildsLeft;
    private static int uploadsLeft;

    private MapTextures() {}

    /** ABGR (NativeImage order) of a {@link MapPalette} value. */
    public static int abgr(byte value) {
        if (value == MapPalette.MARKER_POLE) return POLE_ABGR;
        if (MapPalette.isMarker(value)) return MACHINE_ABGR;
        if (value == 0) return NO_COLOR_ABGR;
        int c = MapColor.getColorFromPackedId(value & 0xFF);
        return c == 0 ? NO_COLOR_ABGR : c;
    }

    /** Starts a frame: drops everything after a reset and applies received chunks to cached regions. */
    private static void update() {
        if (generation != ClientMapData.generation()) {
            clear();
            generation = ClientMapData.generation();
        }
        buildsLeft = BUILDS_PER_FRAME;
        uploadsLeft = UPLOADS_PER_FRAME;
        for (long key : ClientMapData.pollChanged(CHUNKS_PER_FRAME)) {
            Region r = REGIONS.get(ChunkKeys.region(key));
            if (r != null) writeChunk(r, key);
        }
    }

    private static void writeChunk(Region r, long chunk) {
        byte[] colors = ClientMapData.chunk(chunk);
        NativeImage img = r.texture.getPixels();
        if (colors == null || img == null) return;
        int local = ChunkKeys.local(chunk);
        int px = (local % MapRegion.SIZE) * 16, pz = (local / MapRegion.SIZE) * 16;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) img.setPixelRGBA(px + x, pz + z, abgr(colors[x + z * 16]));
        }
        r.touch(px, pz, px + 16, pz + 16);
    }

    /** The region's texture, building it from {@link ClientMapData} if allowed this frame; null if not (yet) drawable. */
    private static Region drawable(long key) {
        Region r = REGIONS.get(key);
        if (r == null) {
            if (buildsLeft <= 0 || !ClientMapData.hasRegion(key)) return null;
            buildsLeft--;
            r = new Region(key);
            REGIONS.put(key, r);
            for (int i = 0; i < MapRegion.CHUNKS; i++) writeChunk(r, ChunkKeys.fromLocal(key, i));
            r.touch(0, 0, REGION_PX, REGION_PX); // the GPU copy starts undefined
        }
        if (r.dirty() && uploadsLeft > 0) {
            uploadsLeft--;
            r.upload();
        }
        return r.uploaded ? r : null;
    }

    private static void evict() {
        for (Iterator<Map.Entry<Long, Region>> it = REGIONS.entrySet().iterator(); REGIONS.size() > cacheSize && it.hasNext(); ) {
            Minecraft.getInstance().getTextureManager().release(it.next().getValue().id);
            it.remove();
        }
    }

    private static void clear() {
        for (Region r : REGIONS.values()) Minecraft.getInstance().getTextureManager().release(r.id);
        REGIONS.clear();
    }

    /** Textures currently cached (for debugging and tests). */
    public static int cached() { return REGIONS.size(); }

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
        // nearest regions first, so they are built and uploaded first
        List<long[]> visible = new ArrayList<>();
        for (int rz = rz0; rz <= rz1; rz++) {
            for (int rx = rx0; rx <= rx1; rx++) {
                double dx = (rx + 0.5) * REGION_PX - cx, dz = (rz + 0.5) * REGION_PX - cz;
                visible.add(new long[] {rx, rz, (long) (dx * dx + dz * dz)});
            }
        }
        visible.sort((a, b) -> Long.compare(a[2], b[2]));
        cacheSize = Math.max(MIN_CACHED, visible.size() + 4);
        int bx = (int) Math.floor(cx), bz = (int) Math.floor(cz);
        g.pose().pushPose();
        g.pose().translate((x0 + x1) / 2.0, (y0 + y1) / 2.0, 0);
        g.pose().scale(scale, scale, 1);
        g.pose().translate(-(cx - bx), -(cz - bz), 0);
        for (long[] v : visible) {
            int rx = (int) v[0], rz = (int) v[1];
            Region r = drawable(ChunkKeys.of(rx, rz));
            if (r != null) g.blit(r.id, rx * REGION_PX - bx, rz * REGION_PX - bz, 0, 0, REGION_PX, REGION_PX, REGION_PX, REGION_PX);
        }
        g.pose().popPose();
        evict();
    }
}
