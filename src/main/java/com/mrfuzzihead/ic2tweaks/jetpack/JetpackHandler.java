package com.mrfuzzihead.ic2tweaks.jetpack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.event.world.WorldEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import ic2.api.item.IElectricItem;
import ic2.core.IC2;
import ic2.core.item.armor.ItemArmorJetpack;
import ic2.core.util.StackUtil;

/**
 * Owns the jetpack tick once the stock {@code ItemArmorJetpack.onArmorTick} is cancelled by
 * {@code MixinItemArmorJetpack}.
 * <p>
 * This runs at {@code PlayerTickEvent.START}, the same point as the stock armour tick relative to movement.
 * Applying the motion at END (after movement) worked, but the limb swing/camera view use the post-movement
 * delta; running before movement avoids the small arm/camera jitter at take-off. The client applies the
 * motion (1.7.10 player movement is client-authoritative) and owns the audio; both sides account
 * fuel/charge, fall distance and the hover mode. The per-side state maps avoid sharing mutable state between
 * the client and integrated-server threads.
 */
public final class JetpackHandler {

    private final Map<EntityPlayer, JetpackState> clientStates = new WeakHashMap<>();
    private final Map<EntityPlayer, JetpackState> serverStates = new WeakHashMap<>();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        EntityPlayer player = event.player;
        if (player == null || !JetpackConfig.enabled) {
            return;
        }

        boolean client = player.worldObj.isRemote;
        if (client && player != IC2.platform.getPlayerInstance()) {
            return;
        }

        JetpackState state = stateFor(client ? clientStates : serverStates, player);
        ItemStack stack = JetpackWorn.get(player);
        if (stack == null) {
            state.stopAudio();
            state.seededStack = null;
            state.thrusting = false;
            return;
        }

        if (!JetpackRules.canOperate(
            player.isDead,
            player.isRiding(),
            player.capabilities.isFlying,
            player.isInWater(),
            player.handleLavaMovement(),
            JetpackConfig.allowInWater,
            JetpackConfig.allowInLava)) {
            state.stopAudio();
            state.thrusting = false;
            return;
        }

        // Seed the locally held mode when the worn stack changes. The NBT remains the persistent copy, but
        // reading it every tick would keep the client toggle vulnerable to a delayed/lost slot sync.
        if (state.seededStack != stack) {
            state.hoverMode = stack.hasTagCompound() && stack.getTagCompound()
                .getBoolean("hoverMode");
            state.seededStack = stack;
            state.comboDown = false;
        }

        if (state.toggleCooldown > 0) {
            --state.toggleCooldown;
        }

        boolean jump = IC2.keyboard.isJumpKeyDown(player);
        boolean modeSwitch = IC2.keyboard.isModeSwitchKeyDown(player);
        boolean combo = jump && modeSwitch;
        // Edge-triggered so holding the combo only toggles once; the cooldown only guards very fast presses.
        if (combo && !state.comboDown && state.toggleCooldown == 0) {
            state.toggleCooldown = 10;
            state.hoverMode = !state.hoverMode;
            StackUtil.getOrCreateNbtData(stack)
                .setBoolean("hoverMode", state.hoverMode);
            StackUtil.getOrCreateNbtData(stack)
                .setByte("toggleTimer", (byte) 10);
            if (!client) {
                IC2.platform.messagePlayer(
                    player,
                    StatCollector.translateToLocal(
                        state.hoverMode ? "ic2tweaks.jetpack.hover.on" : "ic2tweaks.jetpack.hover.off"));
            }
        }
        state.comboDown = combo;

        ItemArmorJetpack item = (ItemArmorJetpack) stack.getItem();
        JetpackPhysics.Kind kind = item instanceof IElectricItem ? JetpackPhysics.Kind.ELECTRIC
            : JetpackPhysics.Kind.GAS;
        double charge = item.getCharge(stack);
        double maxCharge = item.getMaxCharge(stack);
        if (charge <= 0.0D) {
            state.stopAudio();
            state.thrusting = false;
            return;
        }

        // Optional safety net: a charged jetpack keeps resetting fall distance even when not thrusting.
        if (JetpackConfig.fallProtection && !player.onGround) {
            player.fallDistance = 0.0F;
        }

        boolean hover = state.hoverMode;
        boolean engaged = jump || hover;
        if (!engaged || !JetpackRules.shouldThrust(jump, hover, player.onGround)) {
            state.stopAudio();
            state.thrusting = false;
            return;
        }

        double lowFuel = JetpackPhysics.lowFuelPower(
            charge,
            maxCharge,
            kind == JetpackPhysics.Kind.ELECTRIC ? JetpackConfig.electricLowFuelThreshold
                : JetpackConfig.gasLowFuelThreshold);
        double altitude = JetpackPhysics.altitudeFactor(
            player.posY,
            IC2.getWorldHeight(player.worldObj),
            JetpackConfig.altitudeFadeBlocks,
            kind,
            JetpackConfig.electricAltitudeFactor);
        double powerFactor = kind == JetpackPhysics.Kind.ELECTRIC ? JetpackConfig.electricPowerFactor : 1.0D;
        double normalPower = lowFuel * altitude * powerFactor;
        double hoverPower = lowFuel * altitude;
        boolean boost = IC2.keyboard.isBoostKeyDown(player);
        double speedBoost = boost ? JetpackConfig.boostMultiplier : 1.0D;
        double hoverScale = kind == JetpackPhysics.Kind.ELECTRIC ? JetpackConfig.electricHoverFactor : 1.0D;
        boolean sneak = player.isSneaking();

        int cost = JetpackPhysics.fuelCost(
            kind,
            hover,
            boost,
            JetpackConfig.gasFuelPerTick,
            JetpackConfig.gasHoverFuelPerTick,
            JetpackConfig.electricEuPerTick,
            JetpackConfig.electricHoverEuPerTick,
            JetpackConfig.boostFuelMultiplier);
        if (!JetpackWorn.isChestSlot(player, stack)) {
            cost = (int) Math.ceil(cost * JetpackConfig.slotFuelMultiplier);
        }

        boolean drain = !(player.capabilities.isCreativeMode && JetpackConfig.creativeNoDrain);
        if (drain) {
            // Check before applying thrust: the stock code ignored use()'s result and got a free tick at
            // the 1 mB / 1 EU boundary.
            if (!JetpackRules.canConsume(charge, cost)) {
                state.stopAudio();
                state.thrusting = false;
                return;
            }
            item.use(stack, cost);
            if (!client && JetpackConfig.syncIntervalTicks > 0 && JetpackWorn.isChestSlot(player, stack)) {
                if (++state.syncCounter >= JetpackConfig.syncIntervalTicks) {
                    player.inventoryContainer.detectAndSendChanges();
                    state.syncCounter = 0;
                }
            }
        }

        if (client) {
            state.motion.set(player.motionX, player.motionY, player.motionZ);
            JetpackPhysics.applyHorizontal(
                state.motion,
                player.moveForward,
                player.moveStrafing,
                player.rotationYaw,
                JetpackConfig.horizontalSpeed * lowFuel * powerFactor * speedBoost,
                JetpackConfig.horizontalAccel,
                JetpackConfig.horizontalBrake,
                JetpackConfig.strafeFactor,
                JetpackConfig.backwardFactor,
                1.0D);
            JetpackPhysics.applyConfiguredVertical(
                state.motion,
                hover,
                jump,
                sneak,
                normalPower,
                hoverPower,
                JetpackConfig.ascentSpeed * speedBoost,
                JetpackConfig.hoverRise * hoverScale * speedBoost,
                JetpackConfig.hoverSink * hoverScale,
                JetpackConfig.hoverIdle);
            player.motionX = state.motion.x;
            player.motionY = state.motion.y;
            player.motionZ = state.motion.z;
        }

        player.fallDistance = 0.0F;
        player.distanceWalkedModified = 0.0F;
        IC2.platform.resetPlayerInAirTime(player);

        if (client) {
            state.updateAudio(player, true);
            JetpackParticles.spawn(player, kind == JetpackPhysics.Kind.ELECTRIC, boost);
        }
        state.thrusting = true;
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        serverStates.remove(event.player);
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerChangedDimensionEvent event) {
        JetpackState state = clientStates.get(event.player);
        if (state != null) {
            state.stopAudio();
        }
    }

    @SubscribeEvent
    public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        stopAll(clientStates);
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world.isRemote) {
            stopAll(clientStates);
        } else {
            serverStates.clear();
        }
    }

    private static JetpackState stateFor(Map<EntityPlayer, JetpackState> states, EntityPlayer player) {
        JetpackState state = states.get(player);
        if (state == null) {
            state = new JetpackState();
            states.put(player, state);
        }
        return state;
    }

    private static void stopAll(Map<EntityPlayer, JetpackState> states) {
        // Snapshot and clear before stopping: audio/world teardown can otherwise mutate the WeakHashMap
        // (or let GC expunge weak keys) while we are iterating it, which crashed on world unload.
        List<JetpackState> snapshot = new ArrayList<>(states.values());
        states.clear();
        for (JetpackState state : snapshot) {
            state.stopAudio();
        }
    }
}
