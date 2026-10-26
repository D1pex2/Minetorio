package dev.minetorio.content.machine;

import dev.minetorio.content.registry.ModBlockEntities;
import dev.minetorio.core.Units;
import dev.minetorio.core.power.PowerConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Electric mining drill: 0.5 ore/s at full satisfaction, 90 kW while it can mine, mining area 5×5.
 * A {@link PowerConsumer}: mines at the satisfaction last {@link #supply supplied}. Until the
 * power grid exists (Task 14) nothing supplies it, so it stands still in normal play; GameTests
 * use {@link #setTestPower}.
 */
public class ElectricDrillBlockEntity extends MiningDrillBlockEntity implements PowerConsumer {
    public static final double RATE = 0.5;
    public static final int AREA = 5;
    public static final long POWER = Units.kw(90);

    /** Satisfaction last supplied by the power network. */
    private double supplied;
    /** GameTest override of the satisfaction; NaN when unused. Not saved. */
    private double testPower = Double.NaN;

    public ElectricDrillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELECTRIC_MINING_DRILL.get(), pos, state);
    }

    /** Fixes the satisfaction (0..1) for GameTests until the power grid exists. */
    public void setTestPower(double satisfaction) { testPower = satisfaction; }

    @Override
    protected double miningRate() { return RATE; }

    @Override
    protected int areaSize() { return AREA; }

    @Override
    protected double energy() { return Double.isNaN(testPower) ? supplied : testPower; }

    @Override
    protected String noEnergyKey() { return "minetorio.drill.no_power"; }

    /** 90 kW while the drill can mine (buffer empty, ore left), else nothing. */
    @Override
    public long demand() { return isActive() ? POWER : 0; }

    @Override
    public void supply(double satisfaction) { supplied = satisfaction; }
}
