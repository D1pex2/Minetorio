package dev.minetorio.content.blueprint;

import static org.junit.jupiter.api.Assertions.*;

import dev.minetorio.core.blueprint.BlueprintBooks;
import dev.minetorio.core.blueprint.BlueprintStrings;
import dev.minetorio.core.blueprint.BlueprintStrings.BadBlueprintString;
import dev.minetorio.core.blueprint.Blueprints;
import dev.minetorio.core.drag.DragDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Blueprint records: strings and the library file round-trip; malformed or oversized ones are refused. */
class BlueprintRecordTest {
    static BlueprintData belts(int n) {
        List<BlueprintData.Entry> e = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            e.add(new BlueprintData.Entry("minetorio:transport_belt", i % 60, i / 3600, (i / 60) % 60, DragDir.EAST.id(), 0, Optional.empty()));
        }
        return new BlueprintData(e);
    }

    static BlueprintRecord sample() {
        CompoundTag recipe = new CompoundTag();
        recipe.putString("recipe", "iron_gear_wheel");
        BlueprintData data = new BlueprintData(List.of(
                new BlueprintData.Entry("minetorio:transport_belt", 0, 0, 0, DragDir.EAST.id(), 0, Optional.empty()),
                new BlueprintData.Entry("minetorio:underground_belt", 1, 0, 0, DragDir.EAST.id(), 1, Optional.empty()),
                new BlueprintData.Entry("minetorio:assembling_machine_1", 4, 0, 1, DragDir.SOUTH.id(), 0, Optional.of(recipe))));
        return BlueprintRecord.ofBlueprint(new BlueprintMeta("Gears", List.of(ResourceLocation.parse("minetorio:iron_gear_wheel"))), data);
    }

    static BlueprintRecord book(BlueprintRecord... entries) {
        return BlueprintRecord.ofBook(new BlueprintMeta("Book", List.of()), new BlueprintBookData(List.of(entries), 0));
    }

    @Test
    void blueprintStringRoundTrip() throws BadBlueprintString {
        BlueprintRecord r = sample();
        String s = r.toBlueprintString();
        assertEquals('0', s.charAt(0));
        assertEquals(r, BlueprintRecord.fromBlueprintString(s));
    }

    @Test
    void bookStringRoundTripKeepsNestingOrderAndActive() throws BadBlueprintString {
        BlueprintRecord empty = BlueprintRecord.ofBlueprint(BlueprintMeta.EMPTY, null);
        BlueprintRecord inner = book(sample(), empty);
        BlueprintRecord outer = BlueprintRecord.ofBook(new BlueprintMeta("Outer", List.of()),
                new BlueprintBookData(List.of(empty, inner, sample()), 2));
        BlueprintRecord back = BlueprintRecord.fromBlueprintString(outer.toBlueprintString());
        assertEquals(outer, back);
        assertEquals(2, back.book().get().active());
        assertEquals(sample().data(), back.activeData());
        assertEquals(2, back.depth());
    }

    @Test
    void bookOperations() {
        BlueprintRecord a = sample().withMeta(new BlueprintMeta("A", List.of()));
        BlueprintRecord b = sample().withMeta(new BlueprintMeta("B", List.of()));
        BlueprintRecord c = sample().withMeta(new BlueprintMeta("C", List.of()));
        BlueprintBookData d = BlueprintBookData.EMPTY.inserted(0, a);
        assertEquals(0, d.active(), "the first entry is active");
        d = d.inserted(1, b).inserted(0, c); // [C A B], A active
        assertEquals("A", d.activeEntry().meta().label());
        d = d.cycled(1);
        assertEquals("B", d.activeEntry().meta().label());
        d = d.moved(2, 0); // [B C A]
        assertEquals("B", d.activeEntry().meta().label());
        d = d.removed(0);
        assertEquals("C", d.activeEntry().meta().label());
        assertEquals(List.of("C", "A"), d.entries().stream().map(r -> r.meta().label()).toList());
        // set active through the ops' path helpers
        BlueprintRecord root = BlueprintRecord.ofBook(BlueprintMeta.EMPTY, d);
        BlueprintRecord renamed = BlueprintBookOps.replaced(root, List.of(1), d.entries().get(1).withMeta(new BlueprintMeta("A2", List.of())));
        assertEquals("A2", BlueprintBookOps.at(renamed, List.of(1)).meta().label());
        assertNull(BlueprintBookOps.at(renamed, List.of(5)));
        assertNull(BlueprintBookOps.at(renamed, List.of(0, 0)), "a blueprint has no entries");
    }

    @Test
    void oversizedAndMalformedRecordsAreRefused() {
        // more parts than a blueprint may have
        BlueprintRecord big = BlueprintRecord.ofBlueprint(BlueprintMeta.EMPTY, belts(Blueprints.MAX_PARTS + 1));
        assertThrows(BadBlueprintString.class, () -> BlueprintRecord.fromBlueprintString(big.toBlueprintString()));
        // books nested too deep
        BlueprintRecord deep = sample();
        for (int i = 0; i <= BlueprintBooks.MAX_DEPTH; i++) deep = book(deep);
        BlueprintRecord tooDeep = deep;
        assertThrows(BadBlueprintString.class, () -> BlueprintRecord.fromBlueprintString(tooDeep.toBlueprintString()));
        // a name too long, too many icons, a bad active index is clamped
        CompoundTag tag = sample().toTag();
        CompoundTag meta = tag.getCompound("meta");
        meta.putString("label", "x".repeat(BlueprintBooks.MAX_LABEL + 1));
        assertThrows(BadBlueprintString.class, () -> BlueprintRecord.fromTag(tag));
        CompoundTag tag2 = sample().toTag();
        ListTag icons = new ListTag();
        for (int i = 0; i < 5; i++) icons.add(net.minecraft.nbt.StringTag.valueOf("minecraft:stone"));
        tag2.getCompound("meta").put("icons", icons);
        assertThrows(BadBlueprintString.class, () -> BlueprintRecord.fromTag(tag2));
        // a part outside the span limits (x = 200)
        BlueprintData wide = new BlueprintData(List.of(
                new BlueprintData.Entry("minetorio:transport_belt", 0, 0, 0, -1, 0, Optional.empty()),
                new BlueprintData.Entry("minetorio:transport_belt", 200, 0, 0, -1, 0, Optional.empty())));
        assertThrows(BadBlueprintString.class, () -> BlueprintRecord.fromBlueprintString(BlueprintRecord.ofBlueprint(BlueprintMeta.EMPTY, wide).toBlueprintString()));
        // too many parts in total across a book
        List<BlueprintRecord> many = new ArrayList<>();
        for (int i = 0; i < 5; i++) many.add(BlueprintRecord.ofBlueprint(BlueprintMeta.EMPTY, belts(Blueprints.MAX_PARTS)));
        BlueprintRecord heavy = BlueprintRecord.ofBook(BlueprintMeta.EMPTY, new BlueprintBookData(many, 0));
        assertTrue(heavy.validate().isError(), "book over the total part limit");
        // not NBT at all, and an NBT that is not a record
        assertThrows(BadBlueprintString.class, () -> BlueprintRecord.fromBlueprintString(BlueprintStrings.encode("garbage".getBytes())));
        CompoundTag junk = new CompoundTag();
        junk.putInt("book", 5);
        assertThrows(BadBlueprintString.class, () -> BlueprintRecord.fromTag(junk));
    }

    @Test
    void activeIsClamped() {
        BlueprintBookData d = new BlueprintBookData(List.of(sample()), 7);
        assertEquals(0, d.active());
        assertEquals(-1, new BlueprintBookData(List.of(), 3).active());
    }

    @Test
    void libraryFileRoundTrip(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("minetorio").resolve("blueprint-library.dat");
        assertTrue(BlueprintLibraryFile.load(f).entries().isEmpty(), "no file: empty library");
        List<BlueprintRecord> entries = List.of(sample(), book(sample(), BlueprintRecord.ofBlueprint(BlueprintMeta.EMPTY, null)));
        BlueprintLibraryFile.save(f, entries);
        BlueprintLibraryFile.Loaded l = BlueprintLibraryFile.load(f);
        assertEquals(entries, l.entries());
        assertEquals(0, l.dropped());
        assertFalse(Files.exists(f.resolveSibling("blueprint-library.dat.tmp")));
    }

    @Test
    void libraryFileVersionsAndBadEntries(@TempDir Path dir) throws IOException {
        CompoundTag root = BlueprintLibraryFile.write(List.of(sample()));
        assertEquals(BlueprintLibraryFile.VERSION, root.getInt("version"));
        // a bad entry is dropped, the good one kept
        CompoundTag bad = sample().toTag();
        bad.getCompound("meta").putString("label", "x".repeat(500));
        root.getList("entries", 10).add(bad);
        BlueprintLibraryFile.Loaded l = assertDoesNotThrow(() -> BlueprintLibraryFile.read(root));
        assertEquals(1, l.entries().size());
        assertEquals(1, l.dropped());
        // a newer version is refused as such
        CompoundTag newer = BlueprintLibraryFile.write(List.of());
        newer.putInt("version", BlueprintLibraryFile.VERSION + 1);
        BlueprintLibraryFile.BadLibrary e = assertThrows(BlueprintLibraryFile.BadLibrary.class, () -> BlueprintLibraryFile.read(newer));
        assertTrue(e.newer);
        // another format, and a file that is not gzipped NBT
        CompoundTag other = new CompoundTag();
        other.putString("format", "something_else");
        assertThrows(BlueprintLibraryFile.BadLibrary.class, () -> BlueprintLibraryFile.read(other));
        Path junk = dir.resolve("junk.dat");
        Files.writeString(junk, "not nbt");
        assertThrows(BlueprintLibraryFile.BadLibrary.class, () -> BlueprintLibraryFile.load(junk));
        Path ok = dir.resolve("ok.dat");
        NbtIo.writeCompressed(BlueprintLibraryFile.write(List.of(sample())), ok);
        assertEquals(List.of(sample()), assertDoesNotThrow(() -> BlueprintLibraryFile.load(ok)).entries());
    }
}
