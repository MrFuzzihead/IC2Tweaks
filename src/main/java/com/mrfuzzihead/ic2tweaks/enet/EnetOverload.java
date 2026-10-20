package com.mrfuzzihead.ic2tweaks.enet;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.mrfuzzihead.ic2tweaks.IC2Tweaks;
import com.mrfuzzihead.ic2tweaks.api.IExplosionPowerOverride;
import com.mrfuzzihead.ic2tweaks.api.IOverloadHandler;

import ic2.api.energy.EnergyNet;
import ic2.api.energy.tile.IEnergySink;
import ic2.core.ExplosionIC2;

/**
 * Applies the machine half of the energy-net overload system. Data is captured by
 * {@code MixinEnergyNetLocalOverload} from IC2's own {@code injectEnergy} calls, so it uses exactly the
 * packet the sink was handed and knows how much of it the sink actually accepted.
 * <p>
 * The application order mirrors IC2 Experimental 1.12.2's {@code explodeTile}: config gate, then
 * {@link IOverloadHandler}, then {@link IExplosionPowerOverride}, then the blast.
 */
public final class EnetOverload {

    private static final float DEFAULT_EXPLOSION_POWER = 2.5F;
    private static final float EXPLOSION_DROP_RATE = 0.75F;

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
        if (EnetOverloadConfig.packetModel) {
            // Packet mode owns distribution and effects; this legacy pass must stay inert.
            return;
        }
        if (!EnetOverloadConfig.enabled || !EnetOverloadConfig.explosions) {
            return;
        }
        if (EnergyNet.instance == null) {
            return;
        }

        // 1.12.2 records one explosion per sink tile, with the largest offending packet.
        Map<TileEntity, Double> candidates = new IdentityHashMap<>();
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

            Double previous = candidates.get(te);
            if (previous == null || injection.accepted > previous) {
                candidates.put(te, injection.accepted);
            }
        }

        for (Map.Entry<TileEntity, Double> candidate : candidates.entrySet()) {
            explodeMachine(candidate.getKey(), candidate.getValue());
        }
    }

    public static void explodeMachine(TileEntity te, double amount) {
        World world = te.getWorldObj();
        if (world == null || world.isRemote) {
            return;
        }
        // The tile may already have been removed by an earlier explosion in the same pass.
        if (world.getTileEntity(te.xCoord, te.yCoord, te.zCoord) != te) {
            return;
        }

        int tier = EnergyNet.instance.getTierFromPower(amount);
        if (te instanceof IOverloadHandler && ((IOverloadHandler) te).onOverload(tier)) {
            return;
        }

        float power = DEFAULT_EXPLOSION_POWER;
        if (te instanceof IExplosionPowerOverride) {
            IExplosionPowerOverride override = (IExplosionPowerOverride) te;
            if (!override.shouldExplode()) {
                return;
            }
            power = override.getExplosionPower(tier, power);
        }

        IC2Tweaks.LOG.info(
            "Over-voltage: exploding {} at {}:{},{},{} (tier {}, power {})",
            te.getClass()
                .getSimpleName(),
            world.provider.dimensionId,
            te.xCoord,
            te.yCoord,
            te.zCoord,
            tier,
            power);
        explode(world, te.xCoord, te.yCoord, te.zCoord, power);
    }

    /**
     * 1.12.2's {@code explodeTile} for packet mode: checks the graceful hooks on the main tile and each
     * real sub-tile, then replaces the block and spawns the blast (without {@code destroy}, matching
     * 1.12.2's {@code setBlockToAir} + {@code ExplosionIC2} order).
     */
    public static void explodePacketTile(TileEntity mainTile, List<TileEntity> positions, double packet) {
        if (!EnetOverloadConfig.enabled || !EnetOverloadConfig.explosions || mainTile == null || positions == null) {
            return;
        }
        World world = mainTile.getWorldObj();
        if (world == null || world.isRemote || EnergyNet.instance == null) {
            return;
        }

        int tier = EnergyNet.instance.getTierFromPower(packet);
        for (TileEntity subTile : positions) {
            if (subTile == null || subTile.isInvalid()) {
                continue;
            }
            if (world.getTileEntity(subTile.xCoord, subTile.yCoord, subTile.zCoord) != subTile) {
                continue;
            }
            if (mainTile instanceof IOverloadHandler && ((IOverloadHandler) mainTile).onOverload(tier)) {
                continue;
            }
            if (subTile != mainTile && subTile instanceof IOverloadHandler
                && ((IOverloadHandler) subTile).onOverload(tier)) {
                continue;
            }

            float power = DEFAULT_EXPLOSION_POWER;
            IExplosionPowerOverride override = null;
            if (mainTile instanceof IExplosionPowerOverride) {
                override = (IExplosionPowerOverride) mainTile;
            } else if (subTile instanceof IExplosionPowerOverride) {
                override = (IExplosionPowerOverride) subTile;
            }
            if (override != null) {
                if (!override.shouldExplode()) {
                    continue;
                }
                power = override.getExplosionPower(tier, power);
            }

            IC2Tweaks.LOG.info(
                "Over-voltage: exploding {} at {}:{},{},{} (tier {}, power {})",
                mainTile.getClass()
                    .getSimpleName(),
                world.provider.dimensionId,
                subTile.xCoord,
                subTile.yCoord,
                subTile.zCoord,
                tier,
                power);
            world.setBlockToAir(subTile.xCoord, subTile.yCoord, subTile.zCoord);
            ExplosionIC2 explosion = new ExplosionIC2(
                world,
                null,
                subTile.xCoord + 0.5D,
                subTile.yCoord + 0.5D,
                subTile.zCoord + 0.5D,
                power,
                EXPLOSION_DROP_RATE);
            explosion.doExplosion();
        }
    }

    /**
     * Equivalent to {@code IC2.explodeMachineAt(world, x, y, z, true)} but with a configurable blast
     * power. 1.7.10's {@code ExplosionIC2} has no electrical type, so the plain blast is the closest
     * available match (1.12.2 uses its own {@code Type.Electrical}).
     */
    private static void explode(World world, int x, int y, int z, float power) {
        ExplosionIC2 explosion = new ExplosionIC2(
            world,
            null,
            x + 0.5D,
            y + 0.5D,
            z + 0.5D,
            power,
            EXPLOSION_DROP_RATE);
        explosion.destroy(x, y, z, true);
        explosion.doExplosion();
    }
}
