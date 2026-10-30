package dev.minetorio.content.logistics;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class RobotRegionReaderTest {
    @TempDir Path folder;
    @Test void allVanillaCompressionsAndExternalPayloadsRemainByteIdentical() throws Exception {
        for(int compression=1;compression<=4;compression++) for(boolean external:new boolean[]{false,true}) {
            CompoundTag tag=new CompoundTag();tag.putString("marker","robot-census");
            ByteArrayOutputStream encoded=new ByteArrayOutputStream();try(var stream=new DataOutputStream(RegionFileVersion.fromId(compression).wrap((OutputStream)encoded))) {NbtIo.write(tag,stream);}
            byte[] payload=encoded.toByteArray();ByteBuffer region=ByteBuffer.allocate(12288);region.putInt(0,(2<<8)|1);region.position(8192);region.putInt(external?1:payload.length+1);region.put((byte)(compression|(external?128:0)));if(!external)region.put(payload);
            Path file=folder.resolve("r.-1.2.mca");Files.write(file,external?region.array():java.util.Arrays.copyOf(region.array(),8192+payload.length+5));Path ext=folder.resolve("c.-32.64.mcc");if(external)Files.write(ext,payload);
            byte[] before=Files.readAllBytes(file);var result=RobotRegionReader.read(file);
            assertEquals(1,result.chunks().size(),"Занятый sector должен быть прочитан");assertEquals(-32,result.chunks().getFirst().x());assertEquals(64,result.chunks().getFirst().z());
            assertEquals("robot-census",result.chunks().getFirst().nbt().getString("marker"));assertArrayEquals(before,Files.readAllBytes(file));if(external)assertArrayEquals(payload,Files.readAllBytes(ext));
        }
    }
    @Test void truncatedAllocatedSectorIsAnErrorAndNeverAnEmptyWorld() throws Exception {
        ByteBuffer region=ByteBuffer.allocate(8192);region.putInt(0,(2<<8)|1);Path file=folder.resolve("r.0.0.mca");Files.write(file,region.array());byte[] before=Files.readAllBytes(file);
        assertThrows(IOException.class,()->RobotRegionReader.read(file));assertArrayEquals(before,Files.readAllBytes(file));
    }
    private static void region(Path directory, CompoundTag tag) throws Exception {
        Files.createDirectories(directory);
        ByteArrayOutputStream encoded=new ByteArrayOutputStream();
        try(var stream=new DataOutputStream(RegionFileVersion.fromId(2).wrap((OutputStream)encoded))) { NbtIo.write(tag,stream); }
        byte[] payload=encoded.toByteArray();ByteBuffer bytes=ByteBuffer.allocate(8192+payload.length+5);
        bytes.putInt(0,(2<<8)|1);bytes.position(8192);bytes.putInt(payload.length+1);bytes.put((byte)2);bytes.put(payload);
        Files.write(directory.resolve("r.0.0.mca"),bytes.array());
    }
    @Test void censusReadsNestedPassengersLegacyAndAllDimensionsWithoutChangingBytes() throws Exception {
        java.util.Set<java.util.UUID> ids=new java.util.HashSet<>();
        String[] stores={"entities","DIM-1/entities","DIM1/region","dimensions/minetorio/test/region"};
        for(int i=0;i<stores.length;i++) {
            CompoundTag robot=new CompoundTag();var id=java.util.UUID.randomUUID();ids.add(id);
            robot.putString("id","minetorio:logistic_robot");robot.putString("kind","PERSONAL_CONSTRUCTION");robot.putUUID("UUID",id);robot.putUUID("home_player",new java.util.UUID(0,7));
            ListTag passengers=new ListTag();passengers.add(robot);CompoundTag vehicle=new CompoundTag();vehicle.putString("id","minecraft:boat");vehicle.put("Passengers",passengers);
            ListTag list=new ListTag();list.add(vehicle);CompoundTag chunk=new CompoundTag();
            if(i<2)chunk.put("Entities",list);else if(i==2){CompoundTag legacy=new CompoundTag();legacy.put("Entities",list);chunk.put("Level",legacy);}else chunk.put("entities",list);
            region(folder.resolve(stores[i]),chunk);
        }
        java.util.Map<Path,byte[]> before=new java.util.HashMap<>();try(var paths=Files.walk(folder)){for(Path file:paths.filter(Files::isRegularFile).toList())before.put(file,Files.readAllBytes(file));}
        var result=PersonalRobotCensus.scan(folder);assertTrue(result.complete(),result.error());assertEquals(ids,result.robots().keySet());assertEquals(4,result.chunks());
        assertEquals(java.util.Set.of("minecraft:overworld","minecraft:the_nether","minecraft:the_end","minetorio:test"),result.robots().values().stream().map(PersonalRobotCensus.Entry::dimension).collect(java.util.stream.Collectors.toSet()));
        for(var entry:before.entrySet())assertArrayEquals(entry.getValue(),Files.readAllBytes(entry.getKey()));
    }
    @Test void censusRejectsCorruptionAndOrphanExternalWithoutRewritingOriginals() throws Exception {
        Path entities=folder.resolve("entities");Files.createDirectories(entities);Path file=entities.resolve("r.0.0.mca");byte[] corrupt={1,2,3};Files.write(file,corrupt);
        assertFalse(PersonalRobotCensus.scan(folder).complete());assertArrayEquals(corrupt,Files.readAllBytes(file));
        Files.write(file,new byte[0]);Path external=entities.resolve("c.0.0.mcc");Files.write(external,corrupt);
        assertFalse(PersonalRobotCensus.scan(folder).complete());assertArrayEquals(corrupt,Files.readAllBytes(external));assertEquals(0,Files.size(file));
    }
}
