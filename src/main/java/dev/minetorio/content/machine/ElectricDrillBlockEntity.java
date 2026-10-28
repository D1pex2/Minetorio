package dev.minetorio.content.machine;

import dev.minetorio.content.power.ElectricConsumerHelper;
import dev.minetorio.content.registry.ModBlockEntities;
import dev.minetorio.core.Units;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Electric mining drill: 0.5 ore/s at full satisfaction, 90 kW while it can mine, mining area 5×5.
 * Powered through an {@link ElectricConsumerHelper}: mines at the satisfaction the grid supplied at
 * the end of the previous tick (0 outside every pole's supply area).
 */
public class ElectricDrillBlockEntity extends MiningDrillBlockEntity {
    public static final double RATE = 0.5;
    public static final int AREA = 5;
    public static final long POWER = Units.kw(90);

    private final ElectricConsumerHelper power = new ElectricConsumerHelper(this, this::demand,
            () -> ElectricConsumerHelper.box(shape().footprint(worldPosition, facing())));

    public ElectricDrillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELECTRIC_MINING_DRILL.get(), pos, state);
    }

    @Override
    protected double miningRate() { return RATE; }

    @Override
    protected int areaSize() { return AREA; }

    @Override
    protected double energy() { return power.satisfaction(); }

    @Override
    protected String noEnergyKey() { return "minetorio.drill.no_power"; }

    @Override
    protected dev.minetorio.core.alt.AltStatus energyStatus() {
        return dev.minetorio.core.alt.AltRules.electric(power.satisfaction());
    }

    /** 90 kW while the drill can mine (buffer empty, ore left), else nothing. */
    public long demand() { return isActive() ? POWER : 0; }

    /** Satisfaction last supplied by the grid. */
    public double satisfaction() { return power.satisfaction(); }

    @Override
    public void onLoad() {
        super.onLoad();
        power.onLoad();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        power.onRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        power.onRemoved();
    }
}
