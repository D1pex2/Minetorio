package dev.minetorio.gametest;

import com.mojang.logging.LogUtils;
import dev.minetorio.content.demo.DemoBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Builds {@code demo/Minetorio-Demo.zip} headless ({@code ./gradlew buildDemoWorld}): not a test.
 * The {@code demoWorld} run is a GameTest server in its own game directory ({@code run-demo/},
 * fresh world each time) that enables only this class's namespace, so none of the real tests run
 * in it. Its one "test" builds the showcase with {@code /minetorio demo 0 -60 0} on the server's
 * superflat world, keeps its chunks loaded and lets it run for {@value #RUN_TICKS} ticks; once the
 * server has stopped and saved, the world's {@code level.dat} is turned into a playable creative
 * world (name, spawn and look at the entrance, always day, no weather or mobs, no experimental data
 * packs) and the world folder (the showcase's regions only) is zipped to the path given in the
 * system property {@value #OUT_PROPERTY}. Nothing here does anything without that property.
 */
@GameTestHolder(DemoWorldBuild.NAMESPACE)
@PrefixGameTestTemplate(false)
public class DemoWorldBuild {
    /** The namespace only the {@code demoWorld} run enables (no template exists in it: see {@link #copyTemplate}). */
    public static final String NAMESPACE = "minetorio_demoworld";
    /** System property: where to write the zip. */
    public static final String OUT_PROPERTY = "minetorio.demoWorld.out";
    /** Where {@code /minetorio demo} is run (feet; the superflat floor is one below). */
    private static final BlockPos FEET = new BlockPos(0, -60, 0);
    /** How long the showcase runs before the world is saved: 2 minutes. */
    private static final int RUN_TICKS = 2400;
    private static final String WORLD_NAME = "Minetorio Demo";
    private static final String FOLDER = "Minetorio-Demo";
    /** Region files kept: the four around the origin hold the whole showcase. */
    private static final Pattern KEPT_REGION = Pattern.compile("r\\.(-1|0)\\.(-1|0)\\.mca");
    private static final Logger LOGGER = LogUtils.getLogger();

    static {
        if (System.getProperty(OUT_PROPERTY) != null) {
            NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> copyTemplate(e.getServer()));
            NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> pack(e.getServer(), Path.of(System.getProperty(OUT_PROPERTY))));
        }
    }

    /** The test template of this namespace: a copy of {@code minetorio:empty16}, made in memory. */
    private static void copyTemplate(MinecraftServer server) {
        StructureTemplateManager templates = server.getStructureManager();
        StructureTemplate source = templates.get(ResourceLocation.fromNamespaceAndPath("minetorio", "empty16")).orElseThrow();
        templates.getOrCreate(ResourceLocation.fromNamespaceAndPath(NAMESPACE, "empty16"))
                .load(server.registryAccess().lookupOrThrow(Registries.BLOCK), source.save(new CompoundTag()));
    }

    private static List<ChunkPos> chunks(BoundingBox area) {
        List<ChunkPos> out = new ArrayList<>();
        for (int cx = area.minX() >> 4; cx <= area.maxX() >> 4; cx++) {
            for (int cz = area.minZ() >> 4; cz <= area.maxZ() >> 4; cz++) out.add(new ChunkPos(cx, cz));
        }
        return out;
    }

    @GameTest(template = "empty16", batch = "demo_world", timeoutTicks = RUN_TICKS + 200)
    public static void buildDemoWorld(GameTestHelper h) throws Exception {
        ServerLevel level = h.getLevel();
        MinecraftServer server = level.getServer();
        List<ChunkPos> chunks = chunks(DemoBuilder.layout(DemoBuilder.originAround(FEET)).area());
        for (ChunkPos c : chunks) level.setChunkForced(c.x, c.z, true);
        int result = server.getCommands().getDispatcher().execute(
                "minetorio demo " + FEET.getX() + " " + FEET.getY() + " " + FEET.getZ(), server.createCommandSourceStack());
        h.assertTrue(result == 1, "demo command returned " + result);
        h.runAtTickTime(RUN_TICKS, () -> {
            for (ChunkPos c : chunks) level.setChunkForced(c.x, c.z, false);
            h.succeed();
        });
    }

    // --- packing (after the server saved and stopped) ------------------------------------------------

    private static void pack(MinecraftServer server, Path out) {
        Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        try {
            patchLevelDat(server, world.resolve("level.dat"));
            zip(world, out.toAbsolutePath());
            LOGGER.info("Demo world written to {}", out.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("Could not write the demo world to {}", out, e);
        }
    }

    /** Turns the GameTest server's level.dat into the demo world's. */
    private static void patchLevelDat(MinecraftServer server, Path file) throws IOException {
        CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        CompoundTag data = root.getCompound("Data");
        BlockPos in = DemoBuilder.layout(DemoBuilder.originAround(FEET)).entrance();
        data.putString("LevelName", WORLD_NAME);
        data.putInt("GameType", 1); // creative
        data.putBoolean("allowCommands", true);
        data.putByte("Difficulty", (byte) 0); // peaceful
        data.putLong("DayTime", 6000);
        data.putInt("SpawnX", in.getX());
        data.putInt("SpawnY", in.getY());
        data.putInt("SpawnZ", in.getZ());
        data.putFloat("SpawnAngle", -90f);

        CompoundTag rules = data.getCompound("GameRules");
        rules.putString("doDaylightCycle", "false");
        rules.putString("doWeatherCycle", "false");
        rules.putString("doMobSpawning", "false");
        rules.putString("spawnRadius", "0");
        rules.putString("randomTickSpeed", "3"); // the GameTest server's 0 back to normal
        rules.putString("doFireTick", "true");
        data.put("GameRules", rules);

        // the host player of the single-player world starts at the entrance looking east
        CompoundTag player = new CompoundTag();
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(in.getX() + 0.5));
        pos.add(DoubleTag.valueOf(in.getY()));
        pos.add(DoubleTag.valueOf(in.getZ() + 0.5));
        player.put("Pos", pos);
        ListTag rotation = new ListTag();
        rotation.add(FloatTag.valueOf(-90f));
        rotation.add(FloatTag.valueOf(0f));
        player.put("Rotation", rotation);
        player.putString("Dimension", "minecraft:overworld");
        player.putInt("playerGameType", 1);
        data.put("Player", player);

        // the GameTest server enables every data pack and feature flag: keep only the non-experimental ones
        CompoundTag packs = data.getCompound("DataPacks");
        ListTag enabled = new ListTag();
        ListTag disabled = packs.getList("Disabled", Tag.TAG_STRING).copy();
        for (Tag t : packs.getList("Enabled", Tag.TAG_STRING)) {
            Pack pack = server.getPackRepository().getPack(t.getAsString());
            boolean experimental = pack != null && !pack.getRequestedFeatures().isSubsetOf(FeatureFlags.DEFAULT_FLAGS);
            (experimental ? disabled : enabled).add(StringTag.valueOf(t.getAsString()));
        }
        packs.put("Enabled", enabled);
        packs.put("Disabled", disabled);
        data.put("DataPacks", packs);
        ListTag features = new ListTag();
        for (ResourceLocation f : FeatureFlags.REGISTRY.toNames(FeatureFlags.DEFAULT_FLAGS)) features.add(StringTag.valueOf(f.toString()));
        data.put("enabled_features", features);

        root.put("Data", data);
        NbtIo.writeCompressed(root, file);
    }

    /** Zips the world as {@value #FOLDER}/…, without the lock, the backup level.dat and regions outside the showcase. */
    private static void zip(Path world, Path out) throws IOException {
        byte[] icon = oldIcon(out);
        Path tmp = out.resolveSibling(out.getFileName() + ".tmp");
        Files.createDirectories(out.getParent());
        try (OutputStream os = Files.newOutputStream(tmp); ZipOutputStream zip = new ZipOutputStream(os);
                Stream<Path> files = Files.walk(world)) {
            for (Path f : files.filter(Files::isRegularFile).sorted().toList()) {
                Path rel = world.relativize(f);
                if (!kept(rel)) continue;
                zip.putNextEntry(new ZipEntry(FOLDER + "/" + rel.toString().replace('\\', '/')));
                Files.copy(f, zip);
                zip.closeEntry();
            }
            if (icon != null) { // the world's thumbnail comes from a client; the previous one is kept
                zip.putNextEntry(new ZipEntry(FOLDER + "/icon.png"));
                zip.write(icon);
                zip.closeEntry();
            }
        }
        Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
    }

    private static boolean kept(Path rel) {
        String name = rel.getFileName().toString();
        if (name.equals("session.lock") || name.equals("level.dat_old") || name.equals("icon.png")) return false;
        String top = rel.getName(0).toString();
        if (name.endsWith(".mca")) {
            // only the Overworld's regions around the origin (the showcase); other dimensions have none worth keeping
            return rel.getNameCount() == 2 && (top.equals("region") || top.equals("entities") || top.equals("poi"))
                    && KEPT_REGION.matcher(name).matches();
        }
        return true;
    }

    private static byte[] oldIcon(Path zip) {
        if (!Files.isRegularFile(zip)) return null;
        try (ZipFile z = new ZipFile(zip.toFile())) {
            ZipEntry e = z.getEntry(FOLDER + "/icon.png");
            if (e == null) return null;
            try (InputStream in = z.getInputStream(e)) {
                return in.readAllBytes();
            }
        } catch (IOException e) {
            return null;
        }
    }
}
