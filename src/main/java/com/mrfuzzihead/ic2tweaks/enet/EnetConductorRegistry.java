package com.mrfuzzihead.ic2tweaks.enet;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import ic2.api.energy.event.EnergyTileLoadEvent;
import ic2.api.energy.event.EnergyTileUnloadEvent;
import ic2.api.energy.tile.IEnergyConductor;

/**
 * Tracks the {@link IEnergyConductor} tiles currently registered with IC2's energy net so the cable
 * pass can query their public {@code NodeStats} each tick without touching IC2 internals.
 */
public final class EnetConductorRegistry {

    private final Map<World, Set<TileEntity>> conductors = new WeakHashMap<>();

    @SubscribeEvent
    public void onEnergyTileLoad(EnergyTileLoadEvent event) {
        if (!(event.energyTile instanceof IEnergyConductor) || !(event.energyTile instanceof TileEntity)) {
            return;
        }
        Set<TileEntity> set = conductors.get(event.world);
        if (set == null) {
            set = new HashSet<>();
            conductors.put(event.world, set);
        }
        set.add((TileEntity) event.energyTile);
    }

    @SubscribeEvent
    public void onEnergyTileUnload(EnergyTileUnloadEvent event) {
        Set<TileEntity> set = conductors.get(event.world);
        if (set != null) {
            set.remove(event.energyTile);
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        conductors.remove(event.world);
    }

    public Set<TileEntity> getConductors(World world) {
        Set<TileEntity> set = conductors.get(world);
        return set == null ? Collections.<TileEntity>emptySet() : set;
    }
}
