package dev.minetorio.content.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.minetorio.core.blueprint.BlueprintBooks;
import dev.minetorio.core.blueprint.BlueprintStrings;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

/**
 * One blueprint or blueprint book as a value, everything in it included: what the library stores,
 * what a blueprint string carries and what a book holds per entry. A book has {@code book}; a
 * blueprint has neither or {@code blueprint} (an empty blueprint has no data).
 *
 * <p>Every way in ({@link #CODEC} for the item components, {@link #fromBytes} for strings,
 * uploads and the library file) ends in {@link #validate}: nesting depth, entries per book,
 * total parts, every blueprint within {@link BlueprintData#valid}; {@link #fromBytes} also bounds
 * the size ({@link BlueprintBooks#MAX_RECORD_BYTES}).
 */
public record BlueprintRecord(BlueprintMeta meta, Optional<BlueprintData> blueprint, Optional<BlueprintBookData> book) {
    public static BlueprintRecord ofBlueprint(BlueprintMeta meta, @Nullable BlueprintData data) {
        return new BlueprintRecord(meta, Optional.ofNullable(data).filter(d -> !d.isEmpty()), Optional.empty());
    }

    public static BlueprintRecord ofBook(BlueprintMeta meta, BlueprintBookData book) {
        return new BlueprintRecord(meta, Optional.empty(), Optional.of(book));
    }

    public BlueprintRecord {
        blueprint = blueprint.filter(d -> !d.isEmpty());
    }

    public boolean isBook() { return book.isPresent(); }

    @Nullable
    public BlueprintData data() { return blueprint.orElse(null); }

    public BlueprintRecord withMeta(BlueprintMeta m) { return new BlueprintRecord(m, blueprint, book); }

    /** This book with contents {@code b}. */
    public BlueprintRecord withBook(BlueprintBookData b) { return new BlueprintRecord(meta, Optional.empty(), Optional.of(b)); }

    /** Book levels: 0 for a blueprint, 1 for a book of blueprints, 2 for a book holding a book... */
    public int depth() {
        if (book.isEmpty()) return 0;
        int d = 0;
        for (BlueprintRecord r : book.get().entries()) d = Math.max(d, r.depth());
        return d + 1;
    }

    /** Parts of every blueprint in this record. */
    public int totalParts() {
        if (book.isEmpty()) return blueprint.map(b -> b.entries().size()).orElse(0);
        int n = 0;
        for (BlueprintRecord r : book.get().entries()) n += r.totalParts();
        return n;
    }

    /** The blueprint data a paste uses: this blueprint's, or the active entry's (down through nested books); null if empty. */
    @Nullable
    public BlueprintData activeData() {
        BlueprintRecord r = this;
        for (int guard = 0; r != null && guard <= BlueprintBooks.MAX_DEPTH; guard++) {
            if (!r.isBook()) return r.data();
            r = r.book.get().activeEntry();
        }
        return null;
    }

    /** Whether this record is within every limit but the byte size. */
    public DataResult<BlueprintRecord> validate() {
        if (blueprint.isPresent() && book.isPresent()) return DataResult.error(() -> "both a blueprint and a book");
        if (depth() > BlueprintBooks.MAX_DEPTH) return DataResult.error(() -> "books nested too deep");
        if (totalParts() > BlueprintBooks.MAX_TOTAL_PARTS) return DataResult.error(() -> "too many parts");
        return validParts(this) ? DataResult.success(this) : DataResult.error(() -> "blueprint outside the limits");
    }

    private static boolean validParts(BlueprintRecord r) {
        if (r.blueprint.isPresent() && !r.blueprint.get().valid()) return false;
        if (r.book.isPresent()) {
            if (r.book.get().entries().size() > BlueprintBooks.MAX_ENTRIES) return false;
            for (BlueprintRecord e : r.book.get().entries()) if (!validParts(e)) return false;
        }
        return true;
    }

    // --- codec -----------------------------------------------------------------------------------

    /** Not validated as a whole (a book entry is checked with its book); see {@link #VALIDATED}. */
    public static final Codec<BlueprintRecord> CODEC = Codec.recursive("blueprint_record", self -> RecordCodecBuilder.create(i -> i.group(
            BlueprintMeta.CODEC.optionalFieldOf("meta", BlueprintMeta.EMPTY).forGetter(BlueprintRecord::meta),
            BlueprintData.CODEC.optionalFieldOf("blueprint").forGetter(BlueprintRecord::blueprint),
            BlueprintBookData.codec(self).optionalFieldOf("book").forGetter(BlueprintRecord::book)).apply(i, BlueprintRecord::new)));

    public static final Codec<BlueprintRecord> VALIDATED = CODEC.validate(BlueprintRecord::validate);

    // --- bytes (strings, uploads, the library) ----------------------------------------------------

    public CompoundTag toTag() {
        return (CompoundTag) CODEC.encodeStart(NbtOps.INSTANCE, this).getOrThrow();
    }

    /** Binary NBT of this record (uncompressed). */
    public byte[] toBytes() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            NbtIo.write(toTag(), out);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    /** Whether this record is small enough to be one item ({@link BlueprintBooks#MAX_RECORD_BYTES}). */
    public boolean fitsSize() {
        return toTag().sizeInBytes() <= BlueprintBooks.MAX_RECORD_BYTES;
    }

    /** The record in {@code tag}, validated; refused when malformed or outside a limit. */
    public static BlueprintRecord fromTag(Tag tag) throws BlueprintStrings.BadBlueprintString {
        if (!(tag instanceof CompoundTag c) || c.sizeInBytes() > BlueprintBooks.MAX_RECORD_BYTES) {
            throw new BlueprintStrings.BadBlueprintString("too large");
        }
        DataResult<BlueprintRecord> r = VALIDATED.parse(NbtOps.INSTANCE, c);
        if (r.isError()) throw new BlueprintStrings.BadBlueprintString(r.error().map(e -> e.message()).orElse("invalid"));
        return r.getOrThrow();
    }

    /** The record in binary NBT {@code raw}, validated; refused when malformed, oversized or outside a limit. */
    public static BlueprintRecord fromBytes(byte[] raw) throws BlueprintStrings.BadBlueprintString {
        if (raw.length > BlueprintStrings.MAX_RAW_BYTES) throw new BlueprintStrings.BadBlueprintString("too large");
        CompoundTag tag;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw))) {
            tag = NbtIo.read(in, NbtAccounter.create(4L * BlueprintBooks.MAX_RECORD_BYTES));
            if (in.available() > 0) throw new BlueprintStrings.BadBlueprintString("trailing data");
        } catch (IOException | RuntimeException e) {
            throw new BlueprintStrings.BadBlueprintString("corrupt");
        }
        return fromTag(tag);
    }

    /** This record as a blueprint string. */
    public String toBlueprintString() {
        return BlueprintStrings.encode(toBytes());
    }

    /** The record in blueprint string {@code s}; refused when malformed, oversized or outside a limit. */
    public static BlueprintRecord fromBlueprintString(String s) throws BlueprintStrings.BadBlueprintString {
        return fromBytes(BlueprintStrings.decode(s));
    }
}
