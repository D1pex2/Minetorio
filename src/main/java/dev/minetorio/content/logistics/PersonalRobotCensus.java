package dev.minetorio.content.logistics;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;

/** Пассивная перепись канонических entity NBT до запуска writers и загрузки уровней. */
public final class PersonalRobotCensus {
    private PersonalRobotCensus() {}
    public record Entry(UUID owner,String dimension,int chunkX,int chunkZ) {}
    public record Result(boolean complete,Map<UUID,Entry> robots,long files,long chunks,long bytes,long nanos,String error) {}
    public static Result scan(Path root) {
        long start=System.nanoTime(),files=0,chunks=0,bytes=0;Map<UUID,Entry> robots=new LinkedHashMap<>();
        try {
            if(Files.notExists(root))return new Result(true,Map.of(),0,0,0,System.nanoTime()-start,"");
            Set<Path> externals=new HashSet<>();List<Path> regions;
            try(var paths=Files.walk(root)) {regions=paths.filter(p->p.getFileName().toString().endsWith(".mca")&&(p.getParent().getFileName().toString().equals("entities")||p.getParent().getFileName().toString().equals("region"))).sorted().toList();}
            for(Path file:regions) {
                String dimension=dimension(root,file.getParent().getParent());var result=RobotRegionReader.read(file);files++;chunks+=result.chunks().size();bytes+=result.bytes();externals.addAll(result.externals());
                boolean entities=file.getParent().getFileName().toString().equals("entities");
                for(var chunk:result.chunks()) {
                    CompoundTag tag=chunk.nbt();Entry location=new Entry(new UUID(0,0),dimension,chunk.x(),chunk.z());
                    if(entities) { if(!tag.contains("Entities",Tag.TAG_LIST))throw new IOException("Нет Entities в "+file);readList(tag,"Entities",location,robots); }
                    else {readList(tag,"entities",location,robots);if(tag.contains("Level",Tag.TAG_COMPOUND))readList(tag.getCompound("Level"),"Entities",location,robots);}
                }
            }
            // Неадресованный внешний файл может содержать старую сущность, его нельзя принять за пустоту.
            try(var paths=Files.walk(root)) {
                for(Path file:paths.filter(p->p.getFileName().toString().endsWith(".mcc")&&(p.getParent().getFileName().toString().equals("entities")||p.getParent().getFileName().toString().equals("region"))).toList())
                    if(!externals.contains(file.toAbsolutePath().normalize()))throw new IOException("Неадресованный внешний chunk: "+file);
            }
            return new Result(true,Map.copyOf(robots),files,chunks,bytes,System.nanoTime()-start,"");
        } catch(IOException|RuntimeException error) {return new Result(false,Map.copyOf(robots),files,chunks,bytes,System.nanoTime()-start,error.toString());}
    }
    private static String dimension(Path root,Path folder) throws IOException {
        String relative=root.toAbsolutePath().normalize().relativize(folder.toAbsolutePath().normalize()).toString().replace('\\','/');
        if(relative.isEmpty())return "minecraft:overworld";
        if(relative.equals("DIM-1"))return "minecraft:the_nether";if(relative.equals("DIM1"))return "minecraft:the_end";
        if(relative.startsWith("dimensions/")) {String rest=relative.substring(11);int slash=rest.indexOf('/');if(slash>0&&slash<rest.length()-1)return rest.substring(0,slash)+":"+rest.substring(slash+1);}
        throw new IOException("Неизвестная папка измерения: "+folder);
    }
    private static void readList(CompoundTag tag,String key,Entry location,Map<UUID,Entry> robots) throws IOException {
        if(!tag.contains(key))return;
        if(!tag.contains(key,Tag.TAG_LIST))throw new IOException("Некорректный список "+key);
        ListTag list=(ListTag)tag.get(key);if(!list.isEmpty()&&list.getElementType()!=Tag.TAG_COMPOUND)throw new IOException("Некорректные entity NBT");
        for(Tag raw:list)readEntity((CompoundTag)raw,location,robots);
    }
    private static void readEntity(CompoundTag tag,Entry location,Map<UUID,Entry> robots) throws IOException {
        if(tag.getString("id").equals("minetorio:logistic_robot")&&tag.getString("kind").equals("PERSONAL_CONSTRUCTION")) {
            if(!tag.hasUUID("UUID")||!tag.hasUUID("home_player"))throw new IOException("Робот без UUID/owner");
            UUID id=tag.getUUID("UUID");Entry entry=new Entry(tag.getUUID("home_player"),location.dimension(),location.chunkX(),location.chunkZ());
            Entry old=robots.putIfAbsent(id,entry);if(old!=null&&!old.equals(entry))throw new IOException("Неоднозначный UUID робота "+id);
        }
        readList(tag,"Passengers",location,robots);
    }
}
