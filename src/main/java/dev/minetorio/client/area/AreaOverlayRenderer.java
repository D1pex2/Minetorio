package dev.minetorio.client.area;

import dev.minetorio.Minetorio;
import dev.minetorio.content.multiblock.MultiblockBlockItem;
import dev.minetorio.content.multiblock.MultiblockPartBlockEntity;
import dev.minetorio.content.power.ElectricPoleBlock;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Draws the {@link AreaOverlays} of what the player holds (previewed at the cell a right-click
 * would place it) or else of the block the player looks at. The geometry is rebuilt on the client
 * tick when the target changes and every {@value #REBUILD_TICKS} ticks (to follow terrain and
 * nearby poles), never per frame; the frame only replays it into one buffer.
 */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class AreaOverlayRenderer {
    public static final int REBUILD_TICKS = 10;

    private record Key(AreaOverlays.Provider provider, AreaOverlays.Target target) {}

    @Nullable
    private static GroundMesh mesh;
    @Nullable
    private static Key key;
    private static int age;

    private AreaOverlayRenderer() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Key now = mc.level == null || mc.player == null ? null : find(mc, mc.level, mc.player);
        if (now == null) {
            mesh = null;
            key = null;
            return;
        }
        if (now.equals(key) && ++age < REBUILD_TICKS) return;
        GroundMesh m = new GroundMesh(mc.level, now.target().pos());
        now.provider().areas(now.target(), m);
        mesh = m.isEmpty() ? null : m;
        key = now;
        age = 0;
    }

    @Nullable
    private static Key find(Minecraft mc, ClientLevel level, LocalPlayer player) {
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return null;
        Key held = held(level, player, InteractionHand.MAIN_HAND, hit);
        if (held != null) return held;
        ItemStack main = player.getMainHandItem();
        if (main.isEmpty() || !(main.getItem() instanceof BlockItem)) {
            held = held(level, player, InteractionHand.OFF_HAND, hit);
            if (held != null) return held;
        }
        return lookedAt(level, hit.getBlockPos());
    }

    /** The held item's areas at the cell a right-click with it would place it. */
    @Nullable
    private static Key held(ClientLevel level, LocalPlayer player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) return null;
        AreaOverlays.Provider p = AreaOverlays.forItem(stack.getItem());
        if (p == null) return null;
        BlockPlaceContext ctx = new BlockPlaceContext(new UseOnContext(player, hand, hit));
        BlockPlaceContext placed = ctx;
        if (stack.getItem() instanceof MultiblockBlockItem mb) placed = mb.updatePlacementContext(ctx);
        else if (stack.getItem() instanceof BlockItem bi) placed = bi.updatePlacementContext(ctx);
        if (placed == null || !placed.canPlace()) return null;
        return new Key(p, new AreaOverlays.Target(level, placed.getClickedPos().immutable(), placed.getHorizontalDirection(), true));
    }

    /** The areas of the placed block at {@code pos} (a machine part counts as its controller, a pole part as its base). */
    @Nullable
    private static Key lookedAt(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (level.getBlockEntity(pos) instanceof MultiblockPartBlockEntity part && part.isLinked()) {
            pos = part.controllerPos();
            state = level.getBlockState(pos);
        } else if (state.getBlock() instanceof ElectricPoleBlock && state.hasProperty(ElectricPoleBlock.PART)) {
            pos = pos.below(state.getValue(ElectricPoleBlock.PART));
            state = level.getBlockState(pos);
        }
        AreaOverlays.Provider p = AreaOverlays.forBlock(state.getBlock());
        if (p == null) return null;
        Direction facing = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
        return new Key(p, new AreaOverlays.Target(level, pos.immutable(), facing, false));
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        GroundMesh m = mesh;
        if (m == null) return;
        MultiBufferSource.BufferSource buffers = OverlayQuads.buffers();
        m.render(event.getPoseStack(), event.getCamera().getPosition(), buffers.getBuffer(OverlayQuads.GROUND));
        buffers.endBatch(OverlayQuads.GROUND);
    }
}
