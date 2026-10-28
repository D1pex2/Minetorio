package dev.minetorio.gametest;

import dev.minetorio.content.belt.BeltBlock;
import dev.minetorio.content.blueprint.BlueprintBookData;
import dev.minetorio.content.blueprint.BlueprintBookItem;
import dev.minetorio.content.blueprint.BlueprintBookOps;
import dev.minetorio.content.blueprint.BlueprintBookOps.Op;
import dev.minetorio.content.blueprint.BlueprintData;
import dev.minetorio.content.blueprint.BlueprintImport;
import dev.minetorio.content.blueprint.BlueprintMeta;
import dev.minetorio.content.blueprint.BlueprintPaste;
import dev.minetorio.content.blueprint.BlueprintRecord;
import dev.minetorio.content.blueprint.BlueprintStacks;
import dev.minetorio.content.registry.ModBlocks;
import dev.minetorio.core.blueprint.BlueprintBooks;
import dev.minetorio.core.blueprint.BlueprintStrings;
import dev.minetorio.core.blueprint.BlueprintTransform;
import dev.minetorio.core.blueprint.Blueprints;
import dev.minetorio.core.drag.DragDir;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Blueprint books, strings and the server side of the library: a book stores blueprints (put in,
 * reorder, take out), the wheel picks the active one and a paste with the book pastes it; a
 * blueprint string imported through the server makes an equal blueprint (whole and in upload
 * chunks); oversized, malformed and flooding imports and over-limit book edits are refused.
 */
@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class BlueprintBookGameTest {
    private static final String BATCH = "blueprint_book";

    static BlueprintRecord blueprint(String name, BlueprintData data) {
        return BlueprintRecord.ofBlueprint(new BlueprintMeta(name, List.of()), data);
    }

    /** Three belts east in a row. */
    static BlueprintData eastLine() {
        List<BlueprintData.Entry> e = new ArrayList<>();
        for (int x = 0; x < 3; x++) e.add(new BlueprintData.Entry("minetorio:transport_belt", x, 0, 0, DragDir.EAST.id(), 0, Optional.empty()));
        return new BlueprintData(e);
    }

    /** Two belts south in a column. */
    static BlueprintData southLine() {
        return new BlueprintData(List.of(
                new BlueprintData.Entry("minetorio:transport_belt", 0, 0, 0, DragDir.SOUTH.id(), 0, Optional.empty()),
                new BlueprintData.Entry("minetorio:transport_belt", 0, 0, 1, DragDir.SOUTH.id(), 0, Optional.empty())));
    }

    static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = BlueprintGameTest.player(h, new BlockPos(8, 1, 14));
        p.getInventory().setItem(0, ItemStack.EMPTY);
        BlueprintImport.reset(p);
        return p;
    }

    static void clean(GameTestHelper h) {
        for (BlockPos rel : BlockPos.betweenClosed(0, 0, 0, 15, 8, 15)) {
            if (h.getBlockState(rel).is(ModBlocks.GHOST.get())) h.setBlock(rel, Blocks.AIR);
        }
        h.succeed();
    }

    /**
     * Two blueprints put into a book from the inventory (the first becomes active), reordered,
     * the wheel moves the active entry, and a paste with the book in hand places the active
     * blueprint's ghosts; taking an entry out gives back an equal blueprint.
     */
    @GameTest(template = "empty16", batch = BATCH, timeoutTicks = 100)
    public static void bookStoresBlueprintsAndPastesTheActive(GameTestHelper h) {
        BlueprintGameTest.floor(h);
        ServerPlayer p = player(h);
        Inventory inv = p.getInventory();
        BlueprintRecord east = blueprint("East", eastLine()), south = blueprint("South", southLine());
        inv.setItem(0, BlueprintBookItem.empty());
        inv.setItem(1, BlueprintStacks.stack(east));
        inv.setItem(2, BlueprintStacks.stack(south));

        h.assertTrue(BlueprintBookOps.edit(p, 0, List.of(), Op.INSERT, 1, -1), "insert east");
        h.assertTrue(BlueprintBookOps.edit(p, 0, List.of(), Op.INSERT, 2, -1), "insert south");
        h.assertTrue(inv.getItem(1).isEmpty() && inv.getItem(2).isEmpty(), "inserted blueprints left the inventory");
        BlueprintBookData d = BlueprintStacks.book(inv.getItem(0));
        h.assertTrue(d.size() == 2 && d.active() == 0, "book " + d.size() + " active " + d.active());
        h.assertTrue(d.entries().get(0).equals(east) && d.entries().get(1).equals(south), "entries kept as they were");
        h.assertFalse(BlueprintBookOps.edit(p, 0, List.of(), Op.INSERT, 0, -1), "a book cannot go into itself");
        h.assertFalse(BlueprintBookOps.edit(p, 0, List.of(5), Op.SELECT, 0, 0), "no such sub-book");

        // reorder: [south, east], the active entry (east) follows
        h.assertTrue(BlueprintBookOps.edit(p, 0, List.of(), Op.MOVE, 0, 1), "move");
        d = BlueprintStacks.book(inv.getItem(0));
        h.assertTrue(d.entries().get(0).equals(south) && d.active() == 1, "after move, active " + d.active());

        // the wheel: east -> south (wraps)
        h.assertTrue(BlueprintBookOps.scroll(p, 1), "scroll");
        h.assertTrue(BlueprintStacks.book(inv.getItem(0)).active() == 0, "scrolled to south");
        h.assertTrue(BlueprintStacks.activeData(p.getMainHandItem()).equals(southLine()), "active data");

        // paste with the book: the south column, base (8,1,7)
        BlueprintPaste.Result r = BlueprintGameTest.paste(h, p, new BlockPos(8, 1, 8), BlueprintTransform.IDENTITY, false);
        h.assertTrue(r.accepted() && r.placed() == 2, "book paste " + r);
        h.assertTrue(BlueprintGameTest.ghostAt(h, new BlockPos(8, 1, 7)).target().getValue(BeltBlock.FACING) == Direction.SOUTH, "south ghost");
        BlueprintGameTest.ghostAt(h, new BlockPos(8, 1, 8));
        h.assertFalse(h.getBlockState(new BlockPos(9, 1, 8)).is(ModBlocks.GHOST.get()), "east line pasted instead");

        // wheel back to east and paste: three belts east, base (2,1,3)
        h.assertTrue(BlueprintBookOps.scroll(p, -1), "scroll back");
        r = BlueprintGameTest.paste(h, p, new BlockPos(3, 1, 3), BlueprintTransform.IDENTITY, false);
        h.assertTrue(r.accepted() && r.placed() == 3, "second paste " + r);
        for (int x = 2; x <= 4; x++) {
            h.assertTrue(BlueprintGameTest.ghostAt(h, new BlockPos(x, 1, 3)).target().getValue(BeltBlock.FACING) == Direction.EAST, "east ghost " + x);
        }

        // take the active entry out into slot 7
        h.assertTrue(BlueprintBookOps.edit(p, 0, List.of(), Op.EXTRACT, 1, 7), "extract");
        h.assertTrue(east.equals(BlueprintStacks.record(inv.getItem(7))), "extracted blueprint differs: " + BlueprintStacks.record(inv.getItem(7)));
        d = BlueprintStacks.book(inv.getItem(0));
        h.assertTrue(d.size() == 1 && d.active() == 0, "after extract " + d.size() + " active " + d.active());

        // rename the book and its entry
        h.assertTrue(BlueprintBookOps.setMeta(p, 0, List.of(), new BlueprintMeta("Main bus", List.of(ResourceLocation.parse("minetorio:transport_belt")))), "rename");
        h.assertTrue(BlueprintBookOps.setMeta(p, 0, List.of(0), new BlueprintMeta("  Column\n", List.of())), "rename entry");
        h.assertTrue("Main bus".equals(inv.getItem(0).getHoverName().getString()), "book name " + inv.getItem(0).getHoverName().getString());
        h.assertTrue("Column".equals(BlueprintStacks.book(inv.getItem(0)).entries().getFirst().meta().label()), "entry name cleaned");
        clean(h);
    }

    /** A blueprint string (and a book's) imported through the server gives an equal item, whole or in chunks. */
    @GameTest(template = "empty16", batch = BATCH)
    public static void importedStringMakesAnEqualBlueprint(GameTestHelper h) {
        ServerPlayer p = player(h);
        Inventory inv = p.getInventory();
        CompoundTag recipe = new CompoundTag();
        recipe.putString("recipe", "iron_gear_wheel");
        BlueprintData data = new BlueprintData(List.of(
                new BlueprintData.Entry("minetorio:transport_belt", 0, 0, 0, DragDir.EAST.id(), 0, Optional.empty()),
                new BlueprintData.Entry("minetorio:assembling_machine_1", 2, 0, 0, DragDir.SOUTH.id(), 0, Optional.of(recipe))));
        BlueprintRecord bp = BlueprintRecord.ofBlueprint(new BlueprintMeta("Gears", List.of(ResourceLocation.parse("minetorio:iron_gear_wheel"))), data);

        BlueprintImport.Result r = BlueprintImport.importString(p, bp.toBlueprintString(), 3);
        h.assertTrue(r == BlueprintImport.Result.OK, "import " + r);
        h.assertTrue(bp.equals(BlueprintStacks.record(inv.getItem(3))), "imported blueprint differs: " + BlueprintStacks.record(inv.getItem(3)));
        h.assertTrue(BlueprintStacks.activeData(inv.getItem(3)).equals(data), "data differs");

        // a book, in three upload chunks, into slot 4
        BlueprintRecord book = BlueprintRecord.ofBook(new BlueprintMeta("Book", List.of()),
                new BlueprintBookData(List.of(bp, BlueprintRecord.ofBlueprint(BlueprintMeta.EMPTY, null)), 1));
        byte[] packed = BlueprintStrings.deflate(book.toBytes());
        int n = 3, step = (packed.length + n - 1) / n;
        BlueprintImport.Result last = null;
        for (int i = 0; i < n; i++) {
            last = BlueprintImport.chunk(p, 9, i, n, 4, Arrays.copyOfRange(packed, Math.min(packed.length, i * step), Math.min(packed.length, (i + 1) * step)));
            if (i < n - 1) h.assertTrue(last == BlueprintImport.Result.PENDING, "chunk " + i + ": " + last);
        }
        h.assertTrue(last == BlueprintImport.Result.OK, "chunked import " + last);
        h.assertTrue(book.equals(BlueprintStacks.record(inv.getItem(4))), "imported book differs");
        h.assertTrue(inv.getItem(4).getItem() instanceof BlueprintBookItem, "not a book item");

        // the slot taken: goes to the hand (slot 0 is selected and empty)
        h.assertTrue(BlueprintImport.importString(p, bp.toBlueprintString(), 3) == BlueprintImport.Result.OK, "second import");
        h.assertTrue(bp.equals(BlueprintStacks.record(inv.getItem(0))), "not in the hand");
        h.succeed();
    }

    /**
     * Refused: too many parts, a deflate bomb, garbage, a foreign version, deep nesting, chunk
     * floods (too many chunks, out of order, over the byte limit), import floods (budget), and a
     * book edit that would break the total part limit. Nothing reaches the inventory.
     */
    @GameTest(template = "empty16", batch = BATCH)
    public static void oversizedOrMaliciousImportsRefused(GameTestHelper h) {
        ServerPlayer p = player(h);
        Inventory inv = p.getInventory();
        List<BlueprintData.Entry> many = new ArrayList<>();
        for (int i = 0; i <= Blueprints.MAX_PARTS; i++) {
            many.add(new BlueprintData.Entry("minetorio:transport_belt", i % 60, i / 3600, (i / 60) % 60, -1, 0, Optional.empty()));
        }
        String tooMany = BlueprintRecord.ofBlueprint(BlueprintMeta.EMPTY, new BlueprintData(many)).toBlueprintString();
        String bomb = BlueprintStrings.encode(new byte[BlueprintStrings.MAX_RAW_BYTES + 10]);
        BlueprintRecord deep = blueprint("x", eastLine());
        for (int i = 0; i <= BlueprintBooks.MAX_DEPTH; i++) deep = BlueprintRecord.ofBook(BlueprintMeta.EMPTY, new BlueprintBookData(List.of(deep), 0));
        List<String> bad = List.of(tooMany, bomb, deep.toBlueprintString(), "0" + Base64.getEncoder().encodeToString("garbage".getBytes()),
                "1" + blueprint("x", eastLine()).toBlueprintString().substring(1), "hello");
        for (int i = 0; i < bad.size(); i++) {
            BlueprintImport.reset(p);
            BlueprintImport.Result r = BlueprintImport.importString(p, bad.get(i), 2);
            h.assertTrue(r == BlueprintImport.Result.BAD, "bad string " + i + " gave " + r);
        }
        // chunks: too many, out of order, more bytes than allowed
        BlueprintImport.reset(p);
        h.assertTrue(BlueprintImport.chunk(p, 1, 0, BlueprintImport.MAX_CHUNKS + 1, 2, new byte[10]) == BlueprintImport.Result.BAD, "too many chunks");
        BlueprintImport.reset(p);
        h.assertTrue(BlueprintImport.chunk(p, 2, 0, 3, 2, new byte[10]) == BlueprintImport.Result.PENDING, "first chunk");
        h.assertTrue(BlueprintImport.chunk(p, 2, 2, 3, 2, new byte[10]) == BlueprintImport.Result.BAD, "skipped chunk");
        h.assertTrue(BlueprintImport.chunk(p, 2, 1, 3, 2, new byte[10]) == BlueprintImport.Result.BAD, "upload dropped after a bad chunk");
        BlueprintImport.reset(p);
        int total = BlueprintImport.MAX_CHUNKS;
        BlueprintImport.Result r = null;
        for (int i = 0; i < total; i++) {
            r = BlueprintImport.chunk(p, 3, i, total, 2, new byte[BlueprintImport.CHUNK]);
            if (r != BlueprintImport.Result.PENDING) break;
        }
        h.assertTrue(r == BlueprintImport.Result.BAD, "over the byte limit gave " + r);
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) h.assertTrue(inv.getItem(i).isEmpty(), "something reached slot " + i);

        // the budget: a burst, then refused
        BlueprintImport.reset(p);
        String good = blueprint("ok", eastLine()).toBlueprintString();
        int ok = 0;
        BlueprintImport.Result flood = null;
        for (int i = 0; i < 10; i++) {
            flood = BlueprintImport.importString(p, good, -1);
            if (flood != BlueprintImport.Result.OK) break;
            ok++;
        }
        h.assertTrue(flood == BlueprintImport.Result.TOO_SOON && ok >= 1 && ok < 10, "flood: " + ok + " then " + flood);

        // a book edit past the total part limit is refused and changes nothing
        inv.clearContent();
        List<BlueprintRecord> full = new ArrayList<>();
        List<BlueprintData.Entry> max = many.subList(0, Blueprints.MAX_PARTS);
        for (int i = 0; i < BlueprintBooks.MAX_TOTAL_PARTS / Blueprints.MAX_PARTS; i++) full.add(BlueprintRecord.ofBlueprint(BlueprintMeta.EMPTY, new BlueprintData(max)));
        BlueprintRecord heavy = BlueprintRecord.ofBook(BlueprintMeta.EMPTY, new BlueprintBookData(full, 0));
        h.assertTrue(heavy.validate().isSuccess(), "setup: book at the limit");
        inv.setItem(0, BlueprintStacks.stack(heavy));
        inv.setItem(1, BlueprintStacks.stack(blueprint("one more", eastLine())));
        h.assertFalse(BlueprintBookOps.edit(p, 0, List.of(), Op.INSERT, 1, -1), "insert past the limit accepted");
        h.assertTrue(!inv.getItem(1).isEmpty() && BlueprintStacks.book(inv.getItem(0)).size() == full.size(), "refused insert changed things");
        h.succeed();
    }
}
