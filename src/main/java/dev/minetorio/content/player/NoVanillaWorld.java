package dev.minetorio.content.player;

import dev.minetorio.core.player.CharacterRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSpawnPhantomsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * No mobs, no Nether, no End. The biome modifier {@code minetorio:no_vanilla_spawns} empties the spawn
 * lists of every biome (except the mod's own entities); as a safety net any spawn the world causes
 * (natural, chunk generation, spawners, structures, patrols, raids, wandering traders, reinforcements;
 * see {@link CharacterRules#allowsSpawn}) of a non-minetorio mob is refused here, and phantoms never
 * come. Spawn eggs and {@code /summon} still work (creative testing). Nether portals do not light,
 * eyes of ender do not go into portal frames, and no entity travels to the Nether or the End.
 */
public final class NoVanillaWorld {
    private NoVanillaWorld() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener((FinalizeSpawnEvent e) -> {
            if (!allowed(e.getEntity().getType(), e.getSpawnType())) {
                e.setSpawnCancelled(true);
                e.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((MobSpawnEvent.PositionCheck e) -> {
            if (!allowed(e.getEntity().getType(), e.getSpawnType())) e.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        });
        NeoForge.EVENT_BUS.addListener((PlayerSpawnPhantomsEvent e) -> e.setResult(PlayerSpawnPhantomsEvent.Result.DENY));

        NeoForge.EVENT_BUS.addListener((BlockEvent.PortalSpawnEvent e) -> e.setCanceled(true));
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock e) -> {
            var block=e.getLevel().getBlockState(e.getPos()).getBlock();
            if (!e.getEntity().isCreative() && (block instanceof net.minecraft.world.level.block.CraftingTableBlock
                    || block instanceof net.minecraft.world.level.block.AbstractFurnaceBlock
                    || block instanceof net.minecraft.world.level.block.EnchantingTableBlock
                    || block instanceof net.minecraft.world.level.block.AnvilBlock
                    || block instanceof net.minecraft.world.level.block.GrindstoneBlock
                    || block instanceof net.minecraft.world.level.block.SmithingTableBlock
                    || block instanceof net.minecraft.world.level.block.StonecutterBlock
                    || block instanceof net.minecraft.world.level.block.LoomBlock
                    || block instanceof net.minecraft.world.level.block.CartographyTableBlock
                    || block instanceof net.minecraft.world.level.block.BrewingStandBlock)) {
                e.setCanceled(true);
            }
            if (e.getItemStack().is(Items.ENDER_EYE) && e.getLevel().getBlockState(e.getPos()).is(Blocks.END_PORTAL_FRAME)) {
                e.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((EntityTravelToDimensionEvent e) -> {
            if (e.getDimension() == Level.NETHER || e.getDimension() == Level.END) e.setCanceled(true);
        });
    }

    public static boolean allowed(EntityType<?> type, MobSpawnType reason) {
        return CharacterRules.allowsSpawn(BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace(), reason.name());
    }
}
