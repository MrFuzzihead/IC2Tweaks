package com.mrfuzzihead.ic2tweaks.enet;

import java.util.List;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.mrfuzzihead.ic2tweaks.IC2Tweaks;

import ic2.api.energy.EnergyNet;
import ic2.api.energy.tile.IEnergySink;
import ic2.core.IC2;

/**
 * Applies the machine half of the energy-net overload system. Data is captured by
 * {@code MixinEnergyNetLocalOverload} from IC2's own {@code injectEnergy} calls, so it uses exactly the
 * packet the sink was handed and knows how much of it the sink actually accepted.
 */
public final class EnetOverload {

    private EnetOverload() {}

    /** One {@code injectEnergy} call observed during an energy-net tick. */
    public static final class Injection {

        public final IEnergySink sink;
        public final double accepted;

        public Injection(IEnergySink sink, double accepted) {
            this.sink = sink;
            this.accepted = accepted;
        }
    }

    public static void applyMachineOverloads(List<Injection> injections) {
        if (!EnetOverloadConfig.enabled || !EnetOverloadConfig.explosions) {
            return;
        }
        if (EnergyNet.instance == null) {
            return;
        }

        for (Injection injection : injections) {
            IEnergySink sink = injection.sink;
            if (!(sink instanceof TileEntity)) {
                continue;
            }
            TileEntity te = (TileEntity) sink;
            if (te.isInvalid()) {
                continue;
            }

            double tierPower = EnergyNet.instance.getPowerFromTier(sink.getSinkTier());
            if (!EnetOverloadMath.exceedsTierPower(injection.accepted, tierPower)) {
                continue;
            }

            explodeMachine(te);
        }
    }

    public static void explodeMachine(TileEntity te) {
        World world = te.getWorldObj();
        if (world == null || world.isRemote) {
            return;
        }
        // The tile may already have been removed by an earlier explosion in the same pass.
        if (world.getTileEntity(te.xCoord, te.yCoord, te.zCoord) != te) {
            return;
        }
        IC2Tweaks.LOG.info(
            "Over-voltage: exploding {} at {}:{},{},{}",
            te.getClass()
                .getSimpleName(),
            world.provider.dimensionId,
            te.xCoord,
            te.yCoord,
            te.zCoord);
        IC2.explodeMachineAt(world, te.xCoord, te.yCoord, te.zCoord, true);
    }
}
