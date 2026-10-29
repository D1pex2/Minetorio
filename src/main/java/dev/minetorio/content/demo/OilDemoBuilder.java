package dev.minetorio.content.demo;

import dev.minetorio.content.registry.BaseMaterials;
import dev.minetorio.content.machine.MachineItems;
import dev.minetorio.content.multiblock.MultiblockBlockItem;
import dev.minetorio.content.oil.OilProcessingBlockEntity;
import dev.minetorio.content.oil.OilRegistry;
import dev.minetorio.content.oil.OilSourceBlockEntity;
import dev.minetorio.content.power.BoilerBlockEntity;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.core.data.OilData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import dev.minetorio.content.world.BasicChests;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** A 32x32 showcase: coal-fired steam, oil, plastic, sulfur, acid and batteries. */
public final class OilDemoBuilder {
    public static final int SIZE=32;
    public record Layout(BlockPos pumpjack,BlockPos refinery,BlockPos plasticPlant,BlockPos sulfurPlant,
                         BlockPos acidPlant,BlockPos batteryPlant,BlockPos plasticOutput,BlockPos batteryOutput) {}
    private final ServerLevel level;
    private final BlockPos floor;
    private OilDemoBuilder(ServerLevel level,BlockPos floor) { this.level=level; this.floor=floor; }
    private BlockPos p(int x,int z) { return floor.offset(x,1,z); }
    public static Layout build(ServerLevel level,BlockPos floor) { return new OilDemoBuilder(level,floor).build(); }
    private Layout build() {
        // A single offshore pump and boiler feed the engine and the chemical water line.
        level.setBlock(p(1,8).below(),Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);
        level.setBlock(p(2,8).below(),Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);
        for(Direction d:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.WEST})level.setBlock(p(1,8).relative(d),Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);
        level.setBlock(p(1,8),Blocks.WATER.defaultBlockState(),Block.UPDATE_ALL);
        pipe(4,8); place(ModItems.OFFSHORE_PUMP.get(),3,8,Direction.WEST);
        place(ModItems.BOILER.get(),5,8,Direction.NORTH); place(ModItems.STEAM_ENGINE.get(),5,6,Direction.NORTH);
        ((BoilerBlockEntity)level.getBlockEntity(p(5,8))).addFuel("minetorio:coal",50);
        for(int[] xy:new int[][]{{9,4},{12,8},{15,12},{16,17},{22,17},{24,14},{30,10},{15,7},{17,4},{17,2},{22,3},{20,8},{24,8}})
            place(ModItems.SMALL_ELECTRIC_POLE.get(),xy[0],xy[1],Direction.NORTH);
        BlockPos source=floor.offset(18,0,18); level.setBlock(source,OilRegistry.OIL_SOURCE.get().defaultBlockState(),Block.UPDATE_ALL);
        ((OilSourceBlockEntity)level.getBlockEntity(source)).initialize(2);
        place(OilRegistry.PUMPJACK_ITEM.get(),17,19,Direction.NORTH);
        machine(OilRegistry.OIL_REFINERY_ITEM.get(),23,19,"basic_oil_processing");
        machine(OilRegistry.CHEMICAL_PLANT_ITEM.get(),26,13,"plastic_bar");
        machine(OilRegistry.CHEMICAL_PLANT_ITEM.get(),26,7,"sulfur");
        machine(OilRegistry.CHEMICAL_PLANT_ITEM.get(),22,7,"sulfuric_acid");
        machine(OilRegistry.CHEMICAL_PLANT_ITEM.get(),19,2,"battery");
        // Crude oil to refinery; gas branches to plastic and sulfur.
        for(int x=19;x<=21;x++)pipe(x,16); for(int z=17;z<=20;z++)pipe(21,z); for(int x=22;x<=26;x++)pipe(x,20);
        for(int x=26;x<=31;x++)pipe(x,14); for(int z=8;z<=13;z++)pipe(31,z); for(int x=28;x<=30;x++)pipe(x,8);
        // Boiler's pass-through water port supplies both sulfur and acid.
        pipe(8,8); for(int x=8;x<=26;x++)pipe(x,9); pipe(22,8); pipe(26,8);
        // Acid goes from plant output to battery plant's back input.
        for(int x=19;x<=22;x++)pipe(x,4); pipe(19,3);
        chest(24,12,new ItemStack(BaseMaterials.COAL.get(),50)); chest(30,12); inserter(25,12,Direction.EAST); inserter(29,12,Direction.EAST);
        chest(20,6,MachineItems.stack("minetorio:iron_plate",64)); inserter(21,6,Direction.EAST); inserter(25,6,Direction.WEST);
        chest(17,1,MachineItems.stack("minetorio:iron_plate",64),MachineItems.stack("minetorio:copper_plate",64));
        chest(23,1); inserter(18,1,Direction.EAST); inserter(22,1,Direction.EAST);
        sign(16,22,"minetorio.demo.oil"); sign(29,10,"minetorio.demo.plastic"); sign(23,3,"minetorio.demo.battery");
        return new Layout(p(17,19),p(23,19),p(26,13),p(26,7),p(22,7),p(19,2),p(30,12),p(23,1));
    }
    private void machine(Item item,int x,int z,String recipe) {
        place(item,x,z,Direction.NORTH);
        if(!((OilProcessingBlockEntity)level.getBlockEntity(p(x,z))).select(OilData.recipe(recipe)))throw new IllegalStateException("oil demo recipe "+recipe);
    }
    private void pipe(int x,int z) { place(ModItems.PIPE.get(),x,z,Direction.NORTH); }
    private void inserter(int x,int z,Direction facing) { place(ModItems.INSERTER.get(),x,z,facing); }
    private void chest(int x,int z,ItemStack... stacks) {
        level.setBlock(p(x,z),BasicChests.IRON.get().defaultBlockState(),Block.UPDATE_ALL);
        var handler=level.getCapability(Capabilities.ItemHandler.BLOCK,p(x,z),null);
        for(ItemStack stack:stacks) if(!ItemHandlerHelper.insertItemStacked(handler,stack,false).isEmpty())throw new IllegalStateException("oil demo chest full");
    }
    private void place(Item item,int x,int z,Direction facing) {
        BlockPos target=MultiblockBlockItem.targetFor(item,p(x,z),facing);
        var hit=new BlockHitResult(Vec3.atCenterOf(target),Direction.UP,target,false);
        var ctx=new BlockPlaceContext(level,null,InteractionHand.MAIN_HAND,new ItemStack(item),hit) {
            @Override public Direction getHorizontalDirection() { return facing; }
        };
        if(!((BlockItem)item).place(ctx).consumesAction())throw new IllegalStateException("oil demo could not place "+item+" at "+target);
    }
    private void sign(int x,int z,String key) {
        BlockPos pos=p(x,z); level.setBlock(pos,Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION,0),Block.UPDATE_ALL);
        if(level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            SignText text=new SignText().setMessage(1,Component.translatable(key)); sign.setText(text,true); sign.setText(text,false); sign.setWaxed(true);
        }
    }
}
