package com.mrfuzzihead.ic2tweaks.compat.waila;

import static mcp.mobius.waila.api.SpecialChars.ALIGNRIGHT;
import static mcp.mobius.waila.api.SpecialChars.TAB;

import java.util.List;
import java.util.Locale;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import ic2.api.energy.EnergyNet;
import ic2.api.energy.IEnergyNet;
import ic2.api.energy.tile.IEnergySink;
import ic2.api.energy.tile.IEnergySource;
import ic2.api.tile.IEnergyStorage;
import ic2.core.block.machine.tileentity.TileEntityElectricMachine;
import ic2.core.block.machine.tileentity.TileEntityStandardMachine;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaRegistrar;
import mcp.mobius.waila.api.SpecialChars;

/**
 * Optional Waila integration for base IC2.
 * <p>
 * Waila's built-in {@code ic2} addon only covers the five generator families it can reflect into
 * ({@code TileEntityBaseGenerator}, geo, kinetic, semifluid and stirling). It registers nothing for
 * {@link IEnergyStorage} or for ordinary electric machines, so a BatBox/CESU/MFE/MFSU (or a macerator)
 * gets no Waila body at all. Wawla has no IC2 module whatsoever. This provider fills that gap and draws
 * stored energy and machine progress with Waila's {@code waila.bar} renderer (the same gradient bar the
 * vanilla furnace uses).
 * <p>
 * The provider is registered through Waila's IMC callback
 * ({@code FMLInterModComms.sendMessage("Waila", "register", ...)}) rather than a hard dependency. The
 * class is therefore only loaded when Waila is present. It also has a client-side fallback that reads
 * the tile entity directly, so it still works when Waila is installed only on the client and
 * {@code serverPresent} is false.
 */
public final class WailaCompat {

    private static final String CONFIG_ENERGY = "ic2tweaks.energy";
    private static final String CONFIG_TIER = "ic2tweaks.tier";
    private static final String CONFIG_OUTPUT = "ic2tweaks.output";
    private static final String CONFIG_PROGRESS = "ic2tweaks.progress";

    private static final String NBT_KIND = "ic2tweaks.kind";
    private static final String NBT_STORED = "ic2tweaks.stored";
    private static final String NBT_CAPACITY = "ic2tweaks.capacity";
    private static final String NBT_TIER = "ic2tweaks.tier";
    private static final String NBT_OUTPUT = "ic2tweaks.output";
    private static final String NBT_PROGRESS = "ic2tweaks.progressTicks";
    private static final String NBT_OPERATION_LENGTH = "ic2tweaks.operationLength";

    private static final int KIND_NONE = 0;
    private static final int KIND_STORAGE = 1;
    private static final int KIND_MACHINE = 2;

    // Waila's "waila.bar" renderer takes ARGB colours and a 0..1 fill ratio.
    private static final int ENERGY_BAR_TOP = 0xFF00C8FF;
    private static final int ENERGY_BAR_BOTTOM = 0xFF0066CC;
    private static final int PROGRESS_BAR_TOP = 0xFFFF0000;
    private static final int PROGRESS_BAR_BOTTOM = 0xFF8B0000;

    private WailaCompat() {}

    /**
     * Waila IMC callback. Signature must stay {@code public static void register(IWailaRegistrar)}.
     */
    public static void register(IWailaRegistrar registrar) {
        if (WailaIntegrationState.registered) {
            return;
        }

        Provider provider = new Provider();

        // IEnergyStorage covers BatBox/CESU/MFE/MFSU, chargepads and any addon storage block.
        registrar.registerBodyProvider(provider, IEnergyStorage.class);
        registrar.registerNBTProvider(provider, IEnergyStorage.class);

        // Ordinary IC2 electric machines have an internal buffer but do not implement IEnergyStorage.
        registrar.registerBodyProvider(provider, TileEntityElectricMachine.class);
        registrar.registerNBTProvider(provider, TileEntityElectricMachine.class);

        registrar.addConfig("IC2Tweaks", CONFIG_ENERGY, "ic2tweaks.waila.config.energy", true);
        registrar.addConfig("IC2Tweaks", CONFIG_TIER, "ic2tweaks.waila.config.tier", true);
        registrar.addConfig("IC2Tweaks", CONFIG_OUTPUT, "ic2tweaks.waila.config.output", true);
        registrar.addConfig("IC2Tweaks", CONFIG_PROGRESS, "ic2tweaks.waila.config.progress", true);

        WailaIntegrationState.registered = true;
    }

    private static final class Provider implements IWailaDataProvider {

        @Override
        public ItemStack getWailaStack(IWailaDataAccessor accessor, IWailaConfigHandler config) {
            return null;
        }

        @Override
        public List<String> getWailaHead(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
            IWailaConfigHandler config) {
            return currenttip;
        }

        @Override
        public List<String> getWailaBody(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
            IWailaConfigHandler config) {
            Data data = new Data();

            NBTTagCompound tag = accessor.getNBTData();
            if (tag != null && tag.hasKey(NBT_KIND)) {
                data.kind = tag.getInteger(NBT_KIND);
                data.stored = tag.getInteger(NBT_STORED);
                data.capacity = tag.getInteger(NBT_CAPACITY);
                data.tier = tag.getInteger(NBT_TIER);
                data.output = tag.getInteger(NBT_OUTPUT);
                data.progressTicks = tag.getInteger(NBT_PROGRESS);
                data.operationLength = tag.getInteger(NBT_OPERATION_LENGTH);
            } else {
                // Client-only Waila (serverPresent == false): Waila never asked the server, so read the
                // client tile entity. IC2 syncs enough of these fields for the GUI to work.
                readTile(accessor.getTileEntity(), data);
            }

            if (data.kind == KIND_NONE) {
                return currenttip;
            }

            if (config.getConfig(CONFIG_ENERGY) && data.capacity > 0) {
                String label = StatCollector
                    .translateToLocal(data.kind == KIND_MACHINE ? "ic2tweaks.waila.buffer" : "ic2tweaks.waila.energy");
                int stored = Math.min(data.stored, data.capacity);
                currenttip.add(
                    bar(
                        String.format("%s %s / %s EU", label, format(stored), format(data.capacity)),
                        ENERGY_BAR_TOP,
                        ENERGY_BAR_BOTTOM,
                        (double) stored / (double) data.capacity));
            }

            if (data.kind == KIND_MACHINE && config.getConfig(CONFIG_PROGRESS)
                && data.operationLength > 0
                && data.progressTicks > 0) {
                int progress = Math.min(data.progressTicks, data.operationLength);
                currenttip.add(
                    bar(
                        String.format(
                            "%s %.1fs / %.1fs",
                            StatCollector.translateToLocal("ic2tweaks.waila.progress"),
                            progress / 20.0D,
                            data.operationLength / 20.0D),
                        PROGRESS_BAR_TOP,
                        PROGRESS_BAR_BOTTOM,
                        (double) progress / (double) data.operationLength));
            }

            if (config.getConfig(CONFIG_TIER) && data.tier > 0) {
                currenttip.add(
                    String.format(
                        "%s%s\u00a7f%s\u00a7r (%s EU/t)",
                        StatCollector.translateToLocal("ic2tweaks.waila.tier"),
                        TAB + ALIGNRIGHT,
                        tierName(data.tier),
                        format(tierVoltage(data.tier))));
            }

            if (data.kind == KIND_STORAGE && config.getConfig(CONFIG_OUTPUT) && data.output > 0) {
                currenttip.add(
                    String.format(
                        "%s%s\u00a7f%s\u00a7r EU/t",
                        StatCollector.translateToLocal("ic2tweaks.waila.output"),
                        TAB + ALIGNRIGHT,
                        format(data.output)));
            }

            return currenttip;
        }

        @Override
        public List<String> getWailaTail(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
            IWailaConfigHandler config) {
            return currenttip;
        }

        @Override
        public NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity te, NBTTagCompound tag, World world, int x,
            int y, int z) {
            if (tag == null) {
                tag = new NBTTagCompound();
            }

            Data data = new Data();
            readTile(te, data);

            if (data.kind != KIND_NONE) {
                tag.setInteger(NBT_KIND, data.kind);
                tag.setInteger(NBT_STORED, data.stored);
                tag.setInteger(NBT_CAPACITY, data.capacity);
                tag.setInteger(NBT_TIER, data.tier);
                tag.setInteger(NBT_OUTPUT, data.output);
                tag.setInteger(NBT_PROGRESS, data.progressTicks);
                tag.setInteger(NBT_OPERATION_LENGTH, data.operationLength);
            }

            return tag;
        }
    }

    private static void readTile(TileEntity te, Data data) {
        if (te instanceof IEnergyStorage) {
            IEnergyStorage storage = (IEnergyStorage) te;
            data.kind = KIND_STORAGE;
            data.stored = storage.getStored();
            data.capacity = storage.getCapacity();
            data.output = storage.getOutput();
            data.tier = tierOf(te);
        } else if (te instanceof TileEntityElectricMachine) {
            TileEntityElectricMachine machine = (TileEntityElectricMachine) te;
            data.kind = KIND_MACHINE;
            data.stored = clampEnergy(machine.energy);
            data.capacity = machine.maxEnergy;
            data.tier = machine.getSinkTier();

            // Standard machines (macerator, compressor, electric furnace, ...) expose a 0..1 progress
            // ratio and the current operation length (which changes with overclockers).
            if (te instanceof TileEntityStandardMachine) {
                TileEntityStandardMachine standard = (TileEntityStandardMachine) te;
                int length = standard.operationLength;
                if (length > 0) {
                    data.operationLength = length;
                    data.progressTicks = Math.round(standard.getProgress() * length);
                }
            }
        }
    }

    private static int tierOf(TileEntity te) {
        if (te instanceof IEnergySink) {
            int tier = ((IEnergySink) te).getSinkTier();
            if (tier > 0) {
                return tier;
            }
        }
        if (te instanceof IEnergySource) {
            return ((IEnergySource) te).getSourceTier();
        }
        return 0;
    }

    private static int clampEnergy(double energy) {
        if (energy <= 0.0D) {
            return 0;
        }
        if (energy >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) energy;
    }

    private static String format(int value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    /** Builds a Waila gradient bar (the same renderer used by the vanilla furnace). */
    private static String bar(String text, int topColor, int bottomColor, double progress) {
        double clamped = Math.max(0.0D, Math.min(1.0D, progress));
        return SpecialChars.getRenderString(
            "waila.bar",
            text,
            String.valueOf(topColor),
            String.valueOf(bottomColor),
            String.valueOf(clamped));
    }

    private static String tierName(int tier) {
        switch (tier) {
            case 1:
                return "LV";
            case 2:
                return "MV";
            case 3:
                return "HV";
            case 4:
                return "EV";
            case 5:
                return "IV";
            default:
                return "T" + tier;
        }
    }

    /**
     * Rated voltage for a tier. Uses IC2's own table when available and falls back to 32 * 4^(tier-1).
     */
    private static int tierVoltage(int tier) {
        if (tier <= 0) {
            return 0;
        }

        IEnergyNet net = EnergyNet.instance;
        if (net != null) {
            try {
                double voltage = net.getPowerFromTier(tier);
                if (voltage > 0.0D) {
                    return (int) Math.min(Integer.MAX_VALUE, Math.round(voltage));
                }
            } catch (Throwable ignored) {
                // Fall through to the local table.
            }
        }

        if (tier >= 16) {
            return Integer.MAX_VALUE;
        }
        long voltage = 32L << (2 * (tier - 1));
        return (int) Math.min(Integer.MAX_VALUE, voltage);
    }

    private static final class Data {

        int kind = KIND_NONE;
        int stored;
        int capacity;
        int tier;
        int output;
        int progressTicks;
        int operationLength;
    }
}
