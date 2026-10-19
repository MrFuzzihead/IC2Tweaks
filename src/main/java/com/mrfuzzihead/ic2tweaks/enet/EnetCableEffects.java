package com.mrfuzzihead.ic2tweaks.enet;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import com.mrfuzzihead.ic2tweaks.IC2Tweaks;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import ic2.api.energy.EnergyNet;
import ic2.api.energy.NodeStats;
import ic2.api.energy.tile.IEnergyConductor;
import ic2.core.IC2DamageSource;
import ic2.core.energy.EnergyNetLocal;

/**
 * Applies the cable half of the energy-net overload system (meltdown + shock) using IC2's public
 * {@code NodeStats} API. With {@code useLinearTransferModel} forced on, {@code NodeStats} carries
 * {@code current * voltage}, i.e. the energy throughput of the node.
 * <p>
 * Meltdown and shock are computed in the same pass (1.12.2 applies both effects from the same solved
 * data), so a conductor that melts this tick still shocks. Conductors are only removed after the pass
 * has collected everything, which also avoids mutating the registry mid-iteration.
 * <p>
 * Shock energy is accumulated per entity and applied once per world tick. 1.12.2 takes the maximum
 * per entity along one energy path and sums across paths; 1.7.10 has no path data, so this pass takes
 * the maximum across all conductors (treating the tick as a single path). That is exact for a single
 * circuit and conservative for several independent circuits near the same entity.
 */
public final class EnetCableEffects {

    private final EnetConductorRegistry registry;
    private boolean warnedNonLinear;

    public EnetCableEffects(EnetConductorRegistry registry) {
        this.registry = registry;
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!EnetOverloadConfig.enabled) {
            return;
        }
        if (!EnetOverloadConfig.cableMeltdown && !EnetOverloadConfig.shocks) {
            return;
        }

        World world = event.world;
        if (world == null || world.isRemote) {
            return;
        }
        if (EnergyNet.instance == null) {
            return;
        }
        // Cable throughput is only physical on IC2's linear transfer model. If the override mixin did
        // not apply (e.g. a patched IC2), the NodeStats values are allocation-derived and meaningless.
        if (!EnergyNetLocal.useLinearTransferModel) {
            if (!warnedNonLinear) {
                warnedNonLinear = true;
                IC2Tweaks.LOG.warn(
                    "Cable overload/shocks disabled: IC2 useLinearTransferModel is false "
                        + "(the energy-net override mixin did not apply). Machine explosions still work.");
            }
            return;
        }

        // Copy: removing a conductor unloads the tile and mutates the registry while we iterate.
        List<TileEntity> conductors = new ArrayList<>(registry.getConductors(world));
        List<IEnergyConductor> melted = new ArrayList<>();
        List<TileEntity> stripped = new ArrayList<>();
        Map<EntityLivingBase, Double> shockEnergy = new IdentityHashMap<>();

        for (TileEntity te : conductors) {
            if (te == null || te.isInvalid()) {
                continue;
            }
            if (!(te instanceof IEnergyConductor)) {
                continue;
            }
            if (te.getWorldObj() != world) {
                continue;
            }

            IEnergyConductor conductor = (IEnergyConductor) te;
            NodeStats stats = EnergyNet.instance.getNodeStats(te);
            double throughput = Math.max(stats.getEnergyIn(), stats.getEnergyOut());

            if (EnetOverloadConfig.cableMeltdown
                && EnetOverloadMath.exceedsBreakdown(throughput, conductor.getConductorBreakdownEnergy())) {
                IC2Tweaks.LOG.info(
                    "Cable overload: melting {} at {}:{},{},{} ({} > {})",
                    te.getClass()
                        .getSimpleName(),
                    world.provider.dimensionId,
                    te.xCoord,
                    te.yCoord,
                    te.zCoord,
                    throughput,
                    conductor.getConductorBreakdownEnergy());
                melted.add(conductor);
            } else if (throughput > conductor.getInsulationBreakdownEnergy()) {
                // getInsulationBreakdownEnergy() is 9001 in 1.7.10 and removeInsulation() is a no-op,
                // so this branch is effectively dead; kept for parity with IC2 Experimental 1.12.2.
                stripped.add(te);
            }

            if (EnetOverloadConfig.shocks) {
                double energy = EnetOverloadMath.shockEnergy(throughput, conductor.getInsulationEnergyAbsorption());
                if (energy > 0) {
                    collectShockEnergy(world, te, energy, shockEnergy);
                }
            }
        }

        // 1.12.2 removes overloaded conductors before applying the effects it computed in the same
        // pass, so shock damage is dealt even by a cable that melts on this tick.
        for (IEnergyConductor conductor : melted) {
            conductor.removeConductor();
        }
        for (TileEntity te : stripped) {
            if (te instanceof IEnergyConductor && !te.isInvalid()) {
                ((IEnergyConductor) te).removeInsulation();
            }
        }

        if (EnetOverloadConfig.shocks) {
            applyShocks(shockEnergy);
        }
    }

    /**
     * Adds one conductor's shock energy to the per-entity accumulator, keeping the maximum. 1.12.2 uses
     * a max per conductor within one energy path before aggregating paths; without path data here, the
     * max keeps a long run from multiplying its damage along its length.
     */
    private static void collectShockEnergy(World world, TileEntity te, double energy,
        Map<EntityLivingBase, Double> shockEnergy) {
        AxisAlignedBB box = AxisAlignedBB
            .getBoundingBox(te.xCoord - 1, te.yCoord - 1, te.zCoord - 1, te.xCoord + 2, te.yCoord + 2, te.zCoord + 2);
        List<EntityLivingBase> entities = world.getEntitiesWithinAABB(EntityLivingBase.class, box);
        for (EntityLivingBase entity : entities) {
            Double previous = shockEnergy.get(entity);
            if (previous == null || energy > previous) {
                shockEnergy.put(entity, energy);
            }
        }
    }

    private static void applyShocks(Map<EntityLivingBase, Double> shockEnergy) {
        for (Map.Entry<EntityLivingBase, Double> entry : shockEnergy.entrySet()) {
            EntityLivingBase entity = entry.getKey();
            if (entity == null || entity.isDead) {
                continue;
            }
            int damage = EnetOverloadMath.shockDamageFromEnergy(entry.getValue());
            if (damage > 0) {
                entity.attackEntityFrom(IC2DamageSource.electricity, damage);
            }
        }
    }
}
