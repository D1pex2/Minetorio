package dev.minetorio.gametest;

import dev.minetorio.content.power.PowerGridManager;
import dev.minetorio.content.registry.ModBlocks;
import dev.minetorio.core.power.PowerConsumer;
import dev.minetorio.core.power.PowerProducer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * GameTest-only power sources and loads, registered with the real {@link PowerGridManager} (so
 * the grid code under test does the work). Each one is tied to a block in the test area and
 * unregisters itself once that block is gone (the test area is cleared after the test), so it
 * cannot leak into later tests reusing the position.
 */
final class TestPower {
    /** More than any test can draw; small enough that sums never overflow. */
    static final long UNLIMITED_J = 1_000_000_000L;

    private TestPower() {}

    /** Places a small electric pole at {@code rel} with an unlimited producer on its cell: everything in its 5×5×5 area is powered. */
    static void powerAt(GameTestHelper h, BlockPos rel) {
        h.setBlock(rel, ModBlocks.SMALL_ELECTRIC_POLE.get());
        unlimitedProducer(h, rel);
    }

    /**
     * An unlimited producer occupying the cell at {@code rel}, alive while the block there stays
     * (the same block entity instance, if it has one).
     */
    static void unlimitedProducer(GameTestHelper h, BlockPos rel) {
        BlockPos abs = h.absolutePos(rel);
        ServerLevel level = h.getLevel();
        Block block = level.getBlockState(abs).getBlock();
        BlockEntity be = level.getBlockEntity(abs);
        PowerGridManager grid = PowerGridManager.get(level);
        grid.registerProducer(abs, new AABB(abs), new PowerProducer() {
            @Override
            public long maxOutput() {
                boolean alive = be != null ? !be.isRemoved() && level.getBlockEntity(abs) == be : level.getBlockState(abs).is(block);
                if (alive) return UNLIMITED_J;
                grid.unregister(abs, this);
                return 0;
            }

            @Override
            public void produce(long joules) {}
        });
    }

    /** A load of {@code demandJ} joules per tick at {@code rel} (an iron block marks it); records what it is supplied. */
    static Load load(GameTestHelper h, BlockPos rel, long demandJ) {
        h.setBlock(rel, Blocks.IRON_BLOCK);
        BlockPos abs = h.absolutePos(rel);
        Load load = new Load(h.getLevel(), abs, demandJ);
        PowerGridManager.get(h.getLevel()).registerConsumer(abs, new AABB(abs), load);
        return load;
    }

    static final class Load implements PowerConsumer {
        private final ServerLevel level;
        private final BlockPos pos;
        private final long demandJ;
        /** Last supplied satisfaction; NaN before the first supply. */
        private double satisfaction = Double.NaN;

        private Load(ServerLevel level, BlockPos pos, long demandJ) {
            this.level = level;
            this.pos = pos;
            this.demandJ = demandJ;
        }

        double satisfaction() { return satisfaction; }

        @Override
        public long demand() { return demandJ; }

        @Override
        public void supply(double s) {
            satisfaction = s;
            if (!level.getBlockState(pos).is(Blocks.IRON_BLOCK)) PowerGridManager.get(level).unregister(pos, this);
        }
    }
}
