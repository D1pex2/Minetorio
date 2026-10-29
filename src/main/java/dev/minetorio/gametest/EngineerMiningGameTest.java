package dev.minetorio.gametest;

import dev.minetorio.content.character.CharacterInventory;
import dev.minetorio.content.registry.BaseMaterials;
import dev.minetorio.content.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class EngineerMiningGameTest {
    @GameTest(template="empty16") public static void lockedToolMinesOreWithoutHoldingVanillaPickaxe(GameTestHelper h) {
        var p=MachineGameTest.player(h,new BlockPos(3,1,3));
        p.getAbilities().instabuild=false;
        p.getInventory().clearContent();
        p.getInventory().setItem(CharacterInventory.TOOL_SLOT,new ItemStack(BaseMaterials.ENGINEER_TOOL.get()));
        var local=new BlockPos(5,1,5);h.setBlock(local,ModBlocks.IRON_ORE_FIELD.get());
        var pos=h.absolutePos(local);var state=h.getLevel().getBlockState(pos);
        h.assertTrue(p.getMainHandItem().isEmpty(),"test must mine with empty main hand");
        h.assertTrue(p.hasCorrectToolForDrops(state,h.getLevel(),pos),"locked engineer tool cannot harvest mod ore");
        float progress=state.getDestroyProgress(p,h.getLevel(),pos);
        h.assertTrue(Math.abs(progress-0.025f)<0.0001 || Math.abs(progress-0.05f)<0.0001,
            "ore mining must take40ticks, or20 with researchedsteelaxe: "+progress);
        h.succeed();
    }
}
