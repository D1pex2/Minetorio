import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.io.File;

/**
 * One-off generator for src/main/resources/data/minetorio/structure/empty16.nbt.
 * Needs the Minecraft classes on the classpath (e.g. paste into a scratch source set
 * or run via a temporary Gradle JavaExec task). Output path is args[0].
 */
public class MakeEmptyStructure {
    public static void main(String[] args) throws Exception {
        CompoundTag root = new CompoundTag();
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(16));
        size.add(IntTag.valueOf(8));
        size.add(IntTag.valueOf(16));
        root.put("size", size);
        root.put("palette", new ListTag());
        root.put("blocks", new ListTag());
        root.put("entities", new ListTag());
        root.putInt("DataVersion", 3955);
        NbtIo.writeCompressed(root, new File(args.length > 0 ? args[0] : "empty16.nbt").toPath());
    }
}
