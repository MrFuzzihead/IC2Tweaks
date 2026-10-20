package com.mrfuzzihead.ic2tweaks.dev;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.mrfuzzihead.ic2tweaks.IC2Tweaks;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import ic2.api.energy.EnergyNet;
import ic2.api.energy.NodeStats;
import ic2.api.energy.tile.IEnergySink;
import ic2.api.energy.tile.IEnergySource;
import ic2.core.Ic2Items;
import ic2.core.block.wiring.TileEntityElectricBlock;
import ic2.core.block.wiring.TileEntityTransformer;

/**
 * Temporary Phase-3 canary harness. Enabled with {@code -Dic2tweaks.canary=true}; builds four IC2 rigs in
 * the overworld, lets the energy net tick, logs PASS/FAIL and stops the server. Not part of normal play.
 */
public final class CanaryHarness {

    private static final int Y = 160;
    private static final int WAIT_TICKS = 80;

    private final List<String> failures = new ArrayList<>();
    private World world;
    private int ticks;
    private boolean started;
    private boolean reported;
    private EntityCow shockCow;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.worldServerForDimension(0) == null) {
            return;
        }
        if (!started) {
            world = server.worldServerForDimension(0);
            buildRigs();
            started = true;
            return;
        }
        if (reported) {
            return;
        }
        if (++ticks < WAIT_TICKS) {
            return;
        }
        reported = true;
        report();
        server.initiateShutdown();
    }

    private void buildRigs() {
        // Clear the harness area so repeated runs start from a clean slate.
        for (int x = -2; x <= 3; x++) {
            for (int z = -1; z <= 26; z++) {
                world.setBlockToAir(x, Y, z);
            }
        }

        // 1. MFSU -> BatBox directly: over-voltage explodes the sink.
        TileEntity mfsu1 = place(Ic2Items.mfsUnit, 0, 0, ForgeDirection.EAST);
        TileEntity sink1 = place(Ic2Items.batBox, 1, 0, ForgeDirection.DOWN);
        charge(mfsu1, 1_000_000.0D, 3);

        // 2. MFSU -> copper cable -> BatBox: the cable acts as a fuse; the sink survives.
        TileEntity mfsu2 = place(Ic2Items.mfsUnit, 0, 6, ForgeDirection.EAST);
        TileEntity cable2 = place(Ic2Items.copperCableBlock, 1, 6);
        TileEntity sink2 = place(Ic2Items.batBox, 2, 6, ForgeDirection.DOWN);
        charge(mfsu2, 1_000_000.0D, 3);

        // 3. Two full BatBoxes -> one empty BatBox directly: two 32 EU/t packets, no explosion.
        TileEntity source3a = place(Ic2Items.batBox, -1, 12, ForgeDirection.EAST);
        TileEntity sink3 = place(Ic2Items.batBox, 0, 12, ForgeDirection.DOWN);
        TileEntity source3b = place(Ic2Items.batBox, 1, 12, ForgeDirection.WEST);
        charge(source3a, 40_000.0D, 1);
        charge(source3b, 40_000.0D, 1);

        // 4. Two full BatBoxes through one tin cable (breakdown 33) to one sink: per-path max, no melt.
        TileEntity source4a = place(Ic2Items.batBox, -1, 18, ForgeDirection.EAST);
        TileEntity cable4 = place(Ic2Items.tinCableBlock, 0, 18);
        TileEntity source4b = place(Ic2Items.batBox, 1, 18, ForgeDirection.WEST);
        TileEntity sink4 = place(Ic2Items.batBox, 0, 19, ForgeDirection.DOWN);
        charge(source4a, 40_000.0D, 1);
        charge(source4b, 40_000.0D, 1);

        // 5. MFE -> MV transformer (step-down) with two independent output grids: the transformer is a
        // multi-grid source; the ledger must share its 128 EU/t offer and the grid order must shuffle.
        TileEntity mfe5 = place(Ic2Items.mfeUnit, 0, 24, ForgeDirection.EAST);
        TileEntity transformer5 = place(Ic2Items.mvTransformer, 1, 24, ForgeDirection.WEST);
        TileEntity sink5a = place(Ic2Items.cesuUnit, 2, 24, ForgeDirection.DOWN);
        TileEntity sink5b = place(Ic2Items.cesuUnit, 1, 25, ForgeDirection.DOWN);
        charge(mfe5, 1_000_000.0D, 3);

        shockCow = new EntityCow(world);
        shockCow.setPosition(0.5D, Y, 18.5D);
        world.spawnEntityInWorld(shockCow);

        if (mfsu1 == null || sink1 == null
            || mfsu2 == null
            || cable2 == null
            || sink2 == null
            || source3a == null
            || sink3 == null
            || source3b == null
            || source4a == null
            || cable4 == null
            || source4b == null
            || sink4 == null) {
            failures.add("rig placement returned a null tile entity");
            IC2Tweaks.LOG.error("[canary] placement failed; see above");
        }
    }

    private TileEntity place(ItemStack stack, int x, int z) {
        Block block = Block.getBlockFromItem(stack.getItem());
        world.setBlock(x, Y, z, block, stack.getItemDamage(), 3);
        return world.getTileEntity(x, Y, z);
    }

    private TileEntity place(ItemStack stack, int x, int z, ForgeDirection facing) {
        TileEntity tile = place(stack, x, z);
        if (tile instanceof TileEntityTransformer) {
            ((TileEntityTransformer) tile).setFacing((short) facing.ordinal());
        } else if (tile instanceof TileEntityElectricBlock) {
            ((TileEntityElectricBlock) tile).setFacing((short) facing.ordinal());
        }
        return tile;
    }

    private static void charge(TileEntity tile, double amount, int tier) {
        if (tile instanceof IEnergySink) {
            ((IEnergySink) tile).injectEnergy(ForgeDirection.UNKNOWN, amount, tier);
        }
    }

    private void report() {
        logStorage("S3 source A", world.getTileEntity(-1, Y, 12));
        logStorage("S3 source B", world.getTileEntity(1, Y, 12));
        logStorage("S3 sink", world.getTileEntity(0, Y, 12));
        logStorage("S4 source A", world.getTileEntity(-1, Y, 18));
        logStorage("S4 source B", world.getTileEntity(1, Y, 18));
        logStorage("S4 sink", world.getTileEntity(0, Y, 19));
        logStorage("S5 MFE", world.getTileEntity(0, Y, 24));
        logStorage("S5 transformer", world.getTileEntity(1, Y, 24));
        logStorage("S5 CESU A", world.getTileEntity(2, Y, 24));
        logStorage("S5 CESU B", world.getTileEntity(1, Y, 25));

        check("direct MFSU -> LV sink explodes", world.isAirBlock(1, Y, 0), blockName(1, 0));
        check("copper cable on MFSU path melts", world.isAirBlock(1, Y, 6), blockName(1, 6));
        check(
            "melting cable suppresses sink explosion",
            !world.isAirBlock(2, Y, 6) && world.getTileEntity(2, Y, 6) != null,
            blockName(2, 6));
        check(
            "two 32 EU/t sources do not explode the LV sink",
            !world.isAirBlock(0, Y, 12) && world.getTileEntity(0, Y, 12) != null,
            blockName(0, 12));
        check("tin cable on two 32 EU/t paths does not melt", !world.isAirBlock(0, Y, 18), blockName(0, 18));
        check(
            "junction sink survives",
            !world.isAirBlock(0, Y, 19) && world.getTileEntity(0, Y, 19) != null,
            blockName(0, 19));
        check(
            "shock damages an entity near the overloaded tin cable",
            shockCow != null && (shockCow.isDead || shockCow.getHealth() < shockCow.getMaxHealth()),
            "health=" + (shockCow == null ? "null" : shockCow.getHealth()));

        if (EnergyNet.instance != null) {
            TileEntity sink3 = world.getTileEntity(0, Y, 12);
            if (sink3 != null) {
                NodeStats stats = EnergyNet.instance.getNodeStats(sink3);
                IC2Tweaks.LOG.info(
                    "[canary] two-source sink stats: in={} out={} voltage={}",
                    stats.getEnergyIn(),
                    stats.getEnergyOut(),
                    stats.getVoltage());
                check(
                    "two-source sink received both packets",
                    stats.getEnergyIn() > 60.0D && stats.getEnergyIn() < 64.5D,
                    "in=" + stats.getEnergyIn());
                check("packet stats report rated voltage", stats.getVoltage() == 32.0D, "v=" + stats.getVoltage());
            }

            TileEntity source3 = world.getTileEntity(-1, Y, 12);
            if (source3 != null) {
                NodeStats stats = EnergyNet.instance.getNodeStats(source3);
                IC2Tweaks.LOG.info(
                    "[canary] two-source storage stats: in={} out={} voltage={}",
                    stats.getEnergyIn(),
                    stats.getEnergyOut(),
                    stats.getVoltage());
                check(
                    "source storage reports out + rated voltage",
                    stats.getEnergyOut() > 30.0D && stats.getVoltage() == 32.0D,
                    "out=" + stats.getEnergyOut() + " v=" + stats.getVoltage());
            }

            TileEntity cable4 = world.getTileEntity(0, Y, 18);
            if (cable4 != null) {
                NodeStats stats = EnergyNet.instance.getNodeStats(cable4);
                IC2Tweaks.LOG.info(
                    "[canary] junction cable stats: in={} out={} voltage={}",
                    stats.getEnergyIn(),
                    stats.getEnergyOut(),
                    stats.getVoltage());
                check(
                    "junction cable carried both packets without melting",
                    stats.getEnergyIn() > 60.0D && stats.getEnergyIn() < 64.5D,
                    "in=" + stats.getEnergyIn());
            }
        }

        TileEntity cesuA = world.getTileEntity(2, Y, 24);
        TileEntity cesuB = world.getTileEntity(1, Y, 25);
        double energyA = cesuA instanceof TileEntityElectricBlock ? ((TileEntityElectricBlock) cesuA).energy : -1.0D;
        double energyB = cesuB instanceof TileEntityElectricBlock ? ((TileEntityElectricBlock) cesuB).energy : -1.0D;
        IC2Tweaks.LOG.info("[canary] multi-grid transformer sinks: A={} B={}", energyA, energyB);
        check(
            "multi-grid transformer feeds both output grids",
            energyA > 0.0D && energyB > 0.0D,
            "A=" + energyA + " B=" + energyB);

        if (failures.isEmpty()) {
            IC2Tweaks.LOG.info("[canary] RESULT: ALL PASS");
        } else {
            IC2Tweaks.LOG.error("[canary] RESULT: {} FAILURE(S): {}", failures.size(), failures);
        }
    }

    private void logStorage(String name, TileEntity tile) {
        if (tile instanceof TileEntityElectricBlock) {
            TileEntityElectricBlock storage = (TileEntityElectricBlock) tile;
            double offered = tile instanceof IEnergySource ? ((IEnergySource) tile).getOfferedEnergy() : -1.0D;
            double demanded = tile instanceof IEnergySink ? ((IEnergySink) tile).getDemandedEnergy() : -1.0D;
            IC2Tweaks.LOG
                .info("[canary] {}: energy={} offered={} demanded={}", name, storage.energy, offered, demanded);
        } else if (tile != null) {
            IC2Tweaks.LOG.info(
                "[canary] {}: {}",
                name,
                tile.getClass()
                    .getSimpleName());
        } else {
            IC2Tweaks.LOG.info("[canary] {}: null", name);
        }
    }

    private void check(String name, boolean passed, String detail) {
        if (passed) {
            IC2Tweaks.LOG.info("[canary] PASS {} ({})", name, detail);
        } else {
            failures.add(name);
            IC2Tweaks.LOG.error("[canary] FAIL {} ({})", name, detail);
        }
    }

    private String blockName(int x, int z) {
        Block block = world.getBlock(x, Y, z);
        return String.valueOf(Block.blockRegistry.getNameForObject(block)) + " meta=" + world.getBlockMetadata(x, Y, z);
    }
}
