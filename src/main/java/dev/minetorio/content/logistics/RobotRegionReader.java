package dev.minetorio.content.logistics;

import java.nio.file.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
import net.minecraft.nbt.*;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;

/** Только READ: не создает, не дополняет и не переписывает исходные region файлы. */
public final class RobotRegionReader {
    private static final Pattern NAME=Pattern.compile("r\\.(-?[0-9]+)\\.(-?[0-9]+)\\.mca");
    private RobotRegionReader() {}
    public record Chunk(int x,int z,CompoundTag nbt) {}
    public record Result(List<Chunk> chunks,long bytes,Set<Path> externals) {}
    public static Result read(Path path) throws IOException {
        var match=NAME.matcher(path.getFileName().toString());if(!match.matches())throw new IOException("Неверное имя region: "+path);
        int rx=Integer.parseInt(match.group(1)),rz=Integer.parseInt(match.group(2));List<Chunk> chunks=new ArrayList<>();Set<Path> externals=new HashSet<>();long bytes=8192;
        try(FileChannel channel=FileChannel.open(path,StandardOpenOption.READ)) {
            long size=channel.size();if(size==0)return new Result(List.of(),0,Set.of());if(size<8192)throw new IOException("Обрезан header region: "+path);
            ByteBuffer header=read(channel,0,8192);Set<Integer> allocated=new HashSet<>(Set.of(0,1));
            for(int i=0;i<1024;i++) {
                int location=header.getInt(i*4);if(location==0)continue;int sector=location>>>8,count=location&255;
                if(sector<2||count==0||(long)sector*4096+5>size)throw new IOException("Обрезан занятый sector: "+path);
                for(int n=sector;n<sector+count;n++)if(!allocated.add(n))throw new IOException("Пересечение sectors: "+path);
                ByteBuffer prefix=read(channel,(long)sector*4096,5);int length=prefix.getInt();int format=prefix.get()&255;
                boolean external=(format&128)!=0;int compression=format&127;
                if(length<1||length>(long)count*4096-4||compression<1||compression>4||external&&length!=1||!external&&(long)sector*4096+4+length>size)throw new IOException("Некорректный payload: "+path);
                int x=Math.addExact(Math.multiplyExact(rx,32),i%32),z=Math.addExact(Math.multiplyExact(rz,32),i/32);
                InputStream input;
                if(external) {
                    Path file=path.getParent().resolve("c."+x+"."+z+".mcc").toAbsolutePath().normalize();long externalSize=Files.size(file);
                    if(externalSize==0||externalSize>64*1024*1024)throw new IOException("Неверный внешний payload: "+file);
                    bytes+=externalSize;externals.add(file);input=Files.newInputStream(file,StandardOpenOption.READ);
                } else {byte[] payload=read(channel,(long)sector*4096+5,length-1).array();bytes+=length+4;input=new ByteArrayInputStream(payload);}
                try(input;var decoded=new DataInputStream(RegionFileVersion.fromId(compression).wrap(input))) {
                    CompoundTag nbt=NbtIo.read(decoded,NbtAccounter.create(64L*1024*1024));if(nbt==null)throw new IOException("Пустой занятый payload");chunks.add(new Chunk(x,z,nbt));
                }
            }
        }
        return new Result(List.copyOf(chunks),bytes,Set.copyOf(externals));
    }
    private static ByteBuffer read(FileChannel channel,long offset,int length) throws IOException {
        ByteBuffer buffer=ByteBuffer.allocate(length);while(buffer.hasRemaining()){int n=channel.read(buffer,offset+buffer.position());if(n<0)throw new EOFException("Обрезан region payload");}return buffer.flip();
    }
}
