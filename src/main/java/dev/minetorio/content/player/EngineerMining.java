package dev.minetorio.content.player;

import dev.minetorio.content.character.CharacterInventory;
import dev.minetorio.content.orefield.OreFieldBlock;
import dev.minetorio.content.registry.BaseMaterials;
import dev.minetorio.content.research.ClientResearch;
import dev.minetorio.content.research.ResearchSavedData;
import dev.minetorio.content.world.NaturalObjectBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** The locked tool is cosmetic equipment; Factorio's character speed and steel-axe drive mining. */
public final class EngineerMining {
    private EngineerMining() {}
    public static void register() {
        NeoForge.EVENT_BUS.addListener(EngineerMining::speed);
        NeoForge.EVENT_BUS.addListener(EngineerMining::harvest);
    }
    private static boolean equipped(Player p) {
        return p.getInventory().getItem(CharacterInventory.TOOL_SLOT).is(BaseMaterials.ENGINEER_TOOL.get());
    }
    private static boolean ours(BlockState s) {
        return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getNamespace().equals("minetorio");
    }
    private static void harvest(PlayerEvent.HarvestCheck e) {
        if (equipped(e.getEntity()) && ours(e.getTargetBlock())) e.setCanHarvest(true);
    }
    private static void speed(PlayerEvent.BreakSpeed e) {
        Player p=e.getEntity(); BlockState s=e.getState();
        if (!equipped(p) || !ours(s) || e.getPosition().isEmpty()) return;
        float hardness=s.getDestroySpeed(p.level(),e.getPosition().get());
        if (hardness<=0) return;
        boolean steel=p.level() instanceof ServerLevel l
            ? ResearchSavedData.get(l.getServer()).state().isResearched("steel_axe")
            : ClientResearch.state().isResearched("steel_axe");
        // BlockBehaviour divides speed by hardness and 30; 20 ticks per real second.
        String id=BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        e.setNewSpeed((float)(hardness*30/dev.minetorio.core.player.EngineerMiningRules.ticks(id,steel)));
    }
}
