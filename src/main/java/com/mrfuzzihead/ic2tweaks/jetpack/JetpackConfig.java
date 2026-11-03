package com.mrfuzzihead.ic2tweaks.jetpack;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import net.minecraftforge.common.config.Configuration;

/**
 * Configuration for the IC2 jetpack tweaks.
 * <p>
 * Like {@code WrenchConfig} and {@code EnetOverloadConfig}, this is read from the late-mixin loader (before
 * {@code FMLPreInitializationEvent}) because the jetpack mixins will be gated by {@link #enabled} and the
 * slot options. Phase 0 only exposes and validates these values; the flight implementation lands in
 * Phase 1.
 */
public final class JetpackConfig {

    /** Config category used for the jetpack settings. Forge lower-cases category names. */
    public static final String CATEGORY = "jetpack";

    /**
     * Master switch. When {@code false}, none of the jetpack mixins are applied and IC2's stock jetpack
     * behaviour is left untouched. Requires a restart, because the mixins are selected at startup.
     */
    public static boolean enabled = true;

    /**
     * Also apply the tweaks to subclasses of IC2's jetpack items (addon jetpacks). Off by default so an
     * addon that overrides {@code useJetpack} keeps its own behaviour.
     */
    public static boolean tweakAddonJetpacks = false;

    /** Apply the new flight model to the quantum suit's integrated jetpack. Off by default. */
    public static boolean tweakQuantumJetpack = false;

    // ---------------------------------------------------------------------------------------------
    // Horizontal flight (blocks per tick; 20 ticks = 1 second, so 0.32 b/t = 6.4 m/s)
    // ---------------------------------------------------------------------------------------------

    /** Target horizontal speed while a movement key is held. 0.32 b/t is about 6.4 m/s. */
    public static float horizontalSpeed = 0.32F;

    /** How quickly the horizontal velocity approaches the target, in blocks/tick per tick. */
    public static float horizontalAccel = 0.05F;

    /** How quickly the horizontal velocity is bled off when no movement key is held. */
    public static float horizontalBrake = 0.08F;

    /** Multiplier for sideways (strafe) input. 0.8 keeps strafing useful but slower than forward. */
    public static float strafeFactor = 0.8F;

    /** Multiplier for backwards input. */
    public static float backwardFactor = 0.6F;

    /** Horizontal/vertical speed multiplier while IC2's boost key is held. */
    public static float boostMultiplier = 1.6F;

    /** Fuel/energy cost multiplier while IC2's boost key is held. */
    public static float boostFuelMultiplier = 2.5F;

    // ---------------------------------------------------------------------------------------------
    // Vertical flight (blocks per tick)
    // ---------------------------------------------------------------------------------------------

    /** Maximum normal (non-hover) ascent speed. IC2's stock value is 0.6 b/t (12 m/s). */
    public static float ascentSpeed = 0.6F;

    /** Hover-mode ascent speed while the jump key is held. */
    public static float hoverRise = 0.2F;

    /** Hover-mode descent speed while the sneak key is held. */
    public static float hoverSink = 0.2F;

    /** Hover-mode idle vertical speed. 0 holds altitude; a small negative value sinks slowly. */
    public static float hoverIdle = 0.0F;

    /**
     * Scales {@link #hoverRise} and {@link #hoverSink} for the electric jetpack. The balanced default is
     * 1.0 so the electric jetpack hovers at the same speed as gas; set 0.5 for IC2's stock 0.1 hover speed.
     */
    public static float electricHoverFactor = 1.0F;

    /**
     * Normal-mode lift/horizontal multiplier for the electric jetpack. The balanced default is 1.0 (gas
     * parity); set 0.7 for IC2's stock electric lift.
     */
    public static float electricPowerFactor = 1.0F;

    // ---------------------------------------------------------------------------------------------
    // Fuel / energy
    // ---------------------------------------------------------------------------------------------

    /** Biogas consumed per tick by the gas jetpack in normal mode. IC2's stock value is 2 mB/t. */
    public static int gasFuelPerTick = 2;

    /** Biogas consumed per tick by the gas jetpack in hover mode. IC2's stock value is 1 mB/t. */
    public static int gasHoverFuelPerTick = 1;

    /**
     * EU consumed per tick by the electric jetpack in normal mode. The balanced default is 4 EU/t (half
     * the gas endurance, but the electric jetpack is rechargeable); IC2's stock value is 8.
     */
    public static int electricEuPerTick = 4;

    /**
     * EU consumed per tick by the electric jetpack in hover mode. The balanced default is 2 EU/t; IC2's
     * stock value is 7.
     */
    public static int electricHoverEuPerTick = 2;

    /** EU consumed per tick by the quantum chest in normal mode. IC2's stock value is 8 EU/t. */
    public static int quantumEuPerTick = 8;

    /** EU consumed per tick by the quantum chest in hover mode. IC2's stock value is 10 EU/t. */
    public static int quantumHoverEuPerTick = 10;

    /** Charge fraction below which the jetpack's lift starts to fade (gas stock: 0.20). */
    public static float gasLowFuelThreshold = 0.20F;

    /** Charge fraction below which the jetpack's lift starts to fade (electric stock: 0.05). */
    public static float electricLowFuelThreshold = 0.05F;

    // ---------------------------------------------------------------------------------------------
    // Altitude
    // ---------------------------------------------------------------------------------------------

    /**
     * Number of blocks over which lift fades out below the ceiling. 0 (the default) disables the fade and
     * the arbitrary electric ceiling entirely; set 25 for IC2's stock fade.
     */
    public static int altitudeFadeBlocks = 0;

    /**
     * The electric jetpack's ceiling is {@code worldHeight / electricAltitudeFactor} when
     * {@link #altitudeFadeBlocks} is positive. IC2's stock value is 1.28, which gives y=200 in a 256-high
     * world; it is inert while the fade is disabled.
     */
    public static float electricAltitudeFactor = 1.28F;

    // ---------------------------------------------------------------------------------------------
    // Safety / edge cases
    // ---------------------------------------------------------------------------------------------

    /** Allow thrust while in water. Stock IC2 allows it; the default disables it. */
    public static boolean allowInWater = false;

    /** Allow thrust while in lava. Stock IC2 allows it; the default disables it. */
    public static boolean allowInLava = false;

    /** Do not consume fuel/energy while the player is in creative mode. */
    public static boolean creativeNoDrain = true;

    /**
     * Keep resetting fall distance while a charged jetpack is worn, even when not thrusting. Off by
     * default so running out of fuel still lets the player fall (stock behaviour).
     */
    public static boolean fallProtection = false;

    /**
     * Strip the jetpack's misleading HUD armour value. The jetpack's {@code ISpecialArmor} absorption is 0,
     * but it is an {@code ItemArmor} with the diamond chestplate material, so vanilla's
     * {@code InventoryPlayer.getTotalArmorValue()} displays 8 points. When on, the HUD uses
     * {@code ISpecialArmor.getArmorDisplay()} (via the Forge hook) instead. Phase 1.
     */
    public static boolean stripJetpackArmorDisplay = true;

    // ---------------------------------------------------------------------------------------------
    // HUD, tooltips and particles
    // ---------------------------------------------------------------------------------------------

    /** Show the jetpack HUD while a jetpack is worn. */
    public static boolean hudEnabled = true;

    /** Screen corner for the jetpack HUD. */
    public static HudCorner hudCorner = HudCorner.TOP_LEFT;

    /** Show the horizontal speed on the jetpack HUD. */
    public static boolean hudShowSpeed = true;

    /** Show the player's altitude on the jetpack HUD. */
    public static boolean hudShowAltitude = true;

    /** Fuel percentage at or below which the HUD shows a low-fuel warning. 0 disables the warning. */
    public static int hudLowFuelPercent = 10;

    /** Add the jetpack status/controls lines to the item tooltip. */
    public static boolean tooltipsEnabled = true;

    /** Let chargepads charge an electric jetpack worn in a Baubles/TravellersGear slot. */
    public static boolean chargepadChargesBaubles = true;

    /** Show exhaust particles while the jetpack is thrusting. */
    public static boolean particlesEnabled = true;

    /** Ticks between exhaust particle emissions. 0 disables them (the same as particlesEnabled=false). */
    public static int particleIntervalTicks = 2;

    /** Enable a soft landing when falling near the ground with a charged jetpack. */
    public static boolean landingAssist = true;

    /** Height above the ground at which landing assist starts, in blocks. */
    public static float landingAssistDistance = 3.0F;

    /** Maximum downward speed while landing assist is active, in blocks per tick. */
    public static float landingAssistSpeed = 0.08F;

    /** How much upward hover velocity carries over after releasing jump. 0 stops immediately. */
    public static float hoverMomentum = 0.0F;

    /** Stop jetpack thrust (including hover) while a GUI is open. Client-side pause. */
    public static boolean pauseInGui = false;

    /** Enable the dedicated hover key binding (default H, configurable in Controls). */
    public static boolean dedicatedHoverKey = true;

    /** Right-click a biogas container to refill a worn gas jetpack. */
    public static boolean easyGasRefuel = true;

    /** Height above the player's feet at which exhaust particles spawn. Lower this if they look too high. */
    public static float particleYOffset = 0.75F;

    // ---------------------------------------------------------------------------------------------
    // Slot integration
    // ---------------------------------------------------------------------------------------------

    /** Default Baubles slot types. {@code cape}/{@code wings}/{@code body} need Baubles-Expanded. */
    private static final String DEFAULT_BAUBLE_SLOT_TYPES = "cape,wings,body,belt";

    /** The pre-wings default; migrated in {@link #synchronizeConfiguration(File)}. */
    private static final String LEGACY_BAUBLE_SLOT_TYPES = "cape,body,belt";

    /**
     * Baubles slot types the jetpack is allowed in, as a comma-separated list. "cape", "wings" and "body"
     * require Baubles-Expanded (and an assigned slot of that type); "belt" works with original Baubles. The
     * item reports all listed types, so it fits whichever of those slots the pack actually has. Use OFF to
     * disable the Baubles integration. Requires a restart.
     */
    public static String[] baubleSlotTypes = { "cape", "wings", "body", "belt" };

    /** TravellersGear slot the jetpack is allowed in. Requires a restart. */
    public static TravellersGearSlot travellersGearSlot = TravellersGearSlot.CLOAK;

    /**
     * Extra fuel/energy multiplier while the jetpack is worn in a Baubles/TravellersGear slot instead of
     * the chest slot. 1.0 disables the surcharge.
     */
    public static float slotFuelMultiplier = 1.0F;

    /** Render the jetpack on the player's back when it is worn in a Baubles/TravellersGear slot. */
    public static boolean renderWornJetpack = true;

    /**
     * Server-to-client fuel/charge sync interval in ticks. 0 means "never": both sides simulate the
     * consumption, matching how IC2 (and Hodgepodge's fixIc2ArmorLag) already behaves. Set a positive
     * value to periodically re-sync the authoritative stack for GUIs.
     */
    public static int syncIntervalTicks = 0;

    /** Screen corners supported by the jetpack HUD. */
    public enum HudCorner {

        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT;

        public static HudCorner fromString(String value) {
            if (value == null) {
                return TOP_LEFT;
            }
            try {
                return valueOf(
                    value.trim()
                        .toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return TOP_LEFT;
            }
        }
    }

    /** Slots supported by the TravellersGear integration. */
    public enum TravellersGearSlot {

        OFF(-1),
        CLOAK(0),
        SHOULDERS(1);

        /** TravellersGear's slot index, or -1 when disabled. */
        public final int index;

        TravellersGearSlot(int index) {
            this.index = index;
        }

        public static TravellersGearSlot fromString(String value) {
            if (value == null) {
                return CLOAK;
            }
            try {
                return valueOf(
                    value.trim()
                        .toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return CLOAK;
            }
        }
    }

    private JetpackConfig() {}

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        enabled = configuration.getBoolean(
            "enableJetpackTweaks",
            CATEGORY,
            true,
            "Master switch for the jetpack tweaks. When false, IC2's stock jetpack behaviour is left "
                + "untouched. Requires a restart.");

        tweakAddonJetpacks = configuration.getBoolean(
            "tweakAddonJetpacks",
            CATEGORY,
            false,
            "Also apply the tweaks to subclasses of IC2's jetpack items. Off by default so addon jetpacks "
                + "that override useJetpack() keep their own behaviour.");

        tweakQuantumJetpack = configuration.getBoolean(
            "tweakQuantumJetpack",
            CATEGORY,
            false,
            "Apply the new flight model to the quantum suit's integrated jetpack. Off by default; the quantum "
                + "suit has very different fuel numbers (10,000,000 EU).");

        horizontalSpeed = configuration.getFloat(
            "horizontalSpeed",
            CATEGORY,
            0.32F,
            0.0F,
            2.0F,
            "Target horizontal speed while a movement key is held, in blocks per tick. 0.32 b/t is about "
                + "6.4 m/s (sprint is ~5.6 m/s). Set 0 to disable horizontal thrust.");

        horizontalAccel = configuration.getFloat(
            "horizontalAccel",
            CATEGORY,
            0.05F,
            0.001F,
            1.0F,
            "How quickly the horizontal velocity approaches the target speed, in blocks/tick per tick. "
                + "0.05 reaches 0.32 b/t in about 7 ticks.");

        horizontalBrake = configuration.getFloat(
            "horizontalBrake",
            CATEGORY,
            0.08F,
            0.001F,
            1.0F,
            "How quickly horizontal velocity is bled off when no movement key is held, in blocks/tick per "
                + "tick. 0.08 stops 0.32 b/t in about 4 ticks.");

        strafeFactor = configuration.getFloat(
            "strafeFactor",
            CATEGORY,
            0.80F,
            0.0F,
            1.0F,
            "Multiplier for sideways (strafe) input. 0.8 keeps strafing useful but slower than forward.");

        backwardFactor = configuration
            .getFloat("backwardFactor", CATEGORY, 0.60F, 0.0F, 1.0F, "Multiplier for backwards input.");

        boostMultiplier = configuration.getFloat(
            "boostMultiplier",
            CATEGORY,
            1.60F,
            1.0F,
            5.0F,
            "Speed multiplier while IC2's boost key is held (default LCTRL).");

        boostFuelMultiplier = configuration.getFloat(
            "boostFuelMultiplier",
            CATEGORY,
            2.50F,
            1.0F,
            10.0F,
            "Fuel/energy cost multiplier while IC2's boost key is held.");

        ascentSpeed = configuration.getFloat(
            "ascentSpeed",
            CATEGORY,
            0.60F,
            0.05F,
            2.0F,
            "Maximum normal (non-hover) ascent speed in blocks per tick. IC2's stock value is 0.6 b/t " + "(12 m/s).");

        hoverRise = configuration.getFloat(
            "hoverRise",
            CATEGORY,
            0.20F,
            0.0F,
            1.0F,
            "Hover-mode ascent speed while the jump key is held, in blocks per tick.");

        hoverSink = configuration.getFloat(
            "hoverSink",
            CATEGORY,
            0.20F,
            0.0F,
            1.0F,
            "Hover-mode descent speed while the sneak key is held, in blocks per tick.");

        hoverIdle = configuration.getFloat(
            "hoverIdle",
            CATEGORY,
            0.0F,
            -0.2F,
            0.2F,
            "Hover-mode idle vertical speed in blocks per tick. 0 holds altitude; a small negative value "
                + "sinks slowly.");

        electricHoverFactor = configuration.getFloat(
            "electricHoverFactor",
            CATEGORY,
            1.0F,
            0.0F,
            1.0F,
            "Scales hoverRise and hoverSink for the electric jetpack. The balanced default 1.0 matches gas; "
                + "set 0.5 for IC2's stock electric hover speed.");

        electricPowerFactor = configuration.getFloat(
            "electricPowerFactor",
            CATEGORY,
            1.0F,
            0.0F,
            2.0F,
            "Normal-mode lift/horizontal multiplier for the electric jetpack. The balanced default 1.0 gives "
                + "gas parity; set 0.7 for IC2's stock electric lift.");

        gasFuelPerTick = configuration.getInt(
            "gasFuelPerTick",
            CATEGORY,
            2,
            0,
            100,
            "Biogas consumed per tick by the gas jetpack in normal mode. IC2's stock value is 2 mB/t.");

        gasHoverFuelPerTick = configuration.getInt(
            "gasHoverFuelPerTick",
            CATEGORY,
            1,
            0,
            100,
            "Biogas consumed per tick by the gas jetpack in hover mode. IC2's stock value is 1 mB/t.");

        electricEuPerTick = configuration.getInt(
            "electricEuPerTick",
            CATEGORY,
            4,
            0,
            10000,
            "EU consumed per tick by the electric jetpack in normal mode. The balanced default is 4 EU/t; "
                + "IC2's stock value is 8.");

        electricHoverEuPerTick = configuration.getInt(
            "electricHoverEuPerTick",
            CATEGORY,
            2,
            0,
            10000,
            "EU consumed per tick by the electric jetpack in hover mode. The balanced default is 2 EU/t; "
                + "IC2's stock value is 7.");

        quantumEuPerTick = configuration.getInt(
            "quantumEuPerTick",
            CATEGORY,
            8,
            0,
            100000,
            "EU consumed per tick by the quantum chest in normal mode. IC2's stock value is 8 EU/t.");

        quantumHoverEuPerTick = configuration.getInt(
            "quantumHoverEuPerTick",
            CATEGORY,
            10,
            0,
            100000,
            "EU consumed per tick by the quantum chest in hover mode. IC2's stock value is 10 EU/t.");

        gasLowFuelThreshold = configuration.getFloat(
            "gasLowFuelThreshold",
            CATEGORY,
            0.20F,
            0.0F,
            1.0F,
            "Charge fraction below which the gas jetpack's lift starts to fade. IC2's stock value is 0.20.");

        electricLowFuelThreshold = configuration.getFloat(
            "electricLowFuelThreshold",
            CATEGORY,
            0.05F,
            0.0F,
            1.0F,
            "Charge fraction below which the electric jetpack's lift starts to fade. IC2's stock value is " + "0.05.");

        altitudeFadeBlocks = configuration.getInt(
            "altitudeFadeBlocks",
            CATEGORY,
            0,
            0,
            256,
            "Number of blocks over which lift fades out below the ceiling. 0 (default) removes the arbitrary "
                + "ceiling; set 25 for IC2's stock fade.");

        electricAltitudeFactor = configuration.getFloat(
            "electricAltitudeFactor",
            CATEGORY,
            1.28F,
            1.0F,
            10.0F,
            "The electric jetpack's ceiling is worldHeight / this value. IC2's stock value is 1.28, which "
                + "gives y=200 in a 256-high world.");

        allowInWater = configuration.getBoolean(
            "allowInWater",
            CATEGORY,
            false,
            "Allow jetpack thrust while in water. Stock IC2 allows it; the default disables it.");

        allowInLava = configuration.getBoolean(
            "allowInLava",
            CATEGORY,
            false,
            "Allow jetpack thrust while in lava. Stock IC2 allows it; the default disables it.");

        creativeNoDrain = configuration.getBoolean(
            "creativeNoDrain",
            CATEGORY,
            true,
            "Do not consume fuel or energy while the player is in creative mode.");

        fallProtection = configuration.getBoolean(
            "fallProtection",
            CATEGORY,
            false,
            "Keep resetting fall distance while a charged jetpack is worn, even when not thrusting. Off by "
                + "default so running out of fuel still lets the player fall.");

        stripJetpackArmorDisplay = configuration.getBoolean(
            "stripJetpackArmorDisplay",
            CATEGORY,
            true,
            "The jetpack's ISpecialArmor absorption is 0, but as a diamond-material chestplate it makes the "
                + "vanilla HUD display 8 armour points. When true, the HUD uses the Forge ISpecialArmor "
                + "display value instead (0 for the jetpack). Requires a restart.");

        hudEnabled = configuration.getBoolean(
            "hudEnabled",
            CATEGORY,
            true,
            "Show the jetpack HUD (fuel, mode, speed, altitude) while a jetpack is worn.");

        hudCorner = HudCorner.fromString(
            configuration.getString(
                "hudCorner",
                CATEGORY,
                "TOP_LEFT",
                "Screen corner for the jetpack HUD: TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT or BOTTOM_RIGHT."));

        hudShowSpeed = configuration
            .getBoolean("hudShowSpeed", CATEGORY, true, "Show the horizontal speed on the jetpack HUD.");

        hudShowAltitude = configuration
            .getBoolean("hudShowAltitude", CATEGORY, true, "Show the player's altitude on the jetpack HUD.");

        hudLowFuelPercent = configuration.getInt(
            "hudLowFuelPercent",
            CATEGORY,
            10,
            0,
            100,
            "Fuel percentage at or below which the HUD shows a low-fuel warning. 0 disables the warning.");

        tooltipsEnabled = configuration.getBoolean(
            "tooltipsEnabled",
            CATEGORY,
            true,
            "Add the jetpack status and controls lines to the item tooltip.");

        chargepadChargesBaubles = configuration.getBoolean(
            "chargepadChargesBaubles",
            CATEGORY,
            true,
            "Let IC2 chargepads charge an electric jetpack worn in a Baubles/TravellersGear slot. Requires "
                + "a restart.");

        particlesEnabled = configuration
            .getBoolean("particlesEnabled", CATEGORY, true, "Show exhaust particles while the jetpack is thrusting.");

        particleIntervalTicks = configuration.getInt(
            "particleIntervalTicks",
            CATEGORY,
            2,
            0,
            20,
            "Ticks between exhaust particle emissions. 0 disables them.");

        landingAssist = configuration.getBoolean(
            "landingAssist",
            CATEGORY,
            true,
            "Enable a soft landing when falling near the ground with a charged jetpack.");

        landingAssistDistance = configuration.getFloat(
            "landingAssistDistance",
            CATEGORY,
            3.0F,
            0.5F,
            16.0F,
            "Height above the ground at which landing assist starts, in blocks.");

        landingAssistSpeed = configuration.getFloat(
            "landingAssistSpeed",
            CATEGORY,
            0.08F,
            0.01F,
            0.5F,
            "Maximum downward speed while landing assist is active, in blocks per tick.");

        hoverMomentum = configuration.getFloat(
            "hoverMomentum",
            CATEGORY,
            0.0F,
            0.0F,
            1.0F,
            "How much upward hover velocity carries over after releasing jump. 0 stops immediately; 1 "
                + "preserves the old floaty behaviour.");

        pauseInGui = configuration.getBoolean(
            "pauseInGui",
            CATEGORY,
            false,
            "Stop jetpack thrust (including hover) while a GUI is open. This is a client-side pause; the "
                + "server may still simulate fuel when syncIntervalTicks is 0.");

        dedicatedHoverKey = configuration.getBoolean(
            "dedicatedHoverKey",
            CATEGORY,
            true,
            "Enable the dedicated hover key binding (default H, configurable in Controls).");

        easyGasRefuel = configuration.getBoolean(
            "easyGasRefuel",
            CATEGORY,
            true,
            "Right-click a biogas container to refill a worn gas jetpack.");

        particleYOffset = configuration.getFloat(
            "particleYOffset",
            CATEGORY,
            0.75F,
            0.0F,
            2.0F,
            "Height above the player's feet at which exhaust particles spawn. Lower this if the particles "
                + "look too high on the player model.");

        String rawBaubleSlotTypes = configuration.getString(
            "baubleSlotTypes",
            CATEGORY,
            DEFAULT_BAUBLE_SLOT_TYPES,
            "Comma-separated Baubles slot types the jetpack is allowed in. \"cape\", \"wings\" and \"body\" "
                + "require Baubles-Expanded (and an assigned slot of that type); \"belt\" works with original "
                + "Baubles. The item reports all listed types, so it fits whichever slot the pack has. "
                + "Use OFF to disable the Baubles integration. The old default (cape,body,belt) is upgraded "
                + "to include wings. Requires a restart.");
        if (LEGACY_BAUBLE_SLOT_TYPES.equalsIgnoreCase(rawBaubleSlotTypes.trim())) {
            rawBaubleSlotTypes = DEFAULT_BAUBLE_SLOT_TYPES;
            configuration.get(CATEGORY, "baubleSlotTypes", DEFAULT_BAUBLE_SLOT_TYPES)
                .set(rawBaubleSlotTypes);
        }
        baubleSlotTypes = parseSlotTypes(rawBaubleSlotTypes);

        travellersGearSlot = TravellersGearSlot.fromString(
            configuration.getString(
                "travellersGearSlot",
                CATEGORY,
                "CLOAK",
                "TravellersGear slot the jetpack is allowed in: OFF, CLOAK or SHOULDERS. CLOAK is the back "
                    + "slot (it conflicts with TravellersGear cloaks); SHOULDERS conflicts with pauldrons. "
                    + "Requires a restart."));

        slotFuelMultiplier = configuration.getFloat(
            "slotFuelMultiplier",
            CATEGORY,
            1.0F,
            0.1F,
            10.0F,
            "Extra fuel/energy multiplier while the jetpack is worn in a Baubles/TravellersGear slot instead "
                + "of the chest slot. 1.0 disables the surcharge.");

        renderWornJetpack = configuration.getBoolean(
            "renderWornJetpack",
            CATEGORY,
            true,
            "Render the jetpack on the player's back when it is worn in a Baubles/TravellersGear slot. "
                + "Requires a restart.");

        syncIntervalTicks = configuration.getInt(
            "syncIntervalTicks",
            CATEGORY,
            0,
            0,
            1200,
            "Server-to-client fuel/charge sync interval in ticks. 0 means never: both sides simulate the "
                + "consumption (IC2 and Hodgepodge's fixIc2ArmorLag already work this way). Set a positive "
                + "value to periodically re-sync the authoritative stack for GUIs.");

        // Migrate the old stock defaults to the balanced/no-ceiling defaults. A user who deliberately
        // kept the full stock quartet (8/7/0.7/0.5) gets the new balance; partial tuning is preserved.
        boolean legacyElectric = electricEuPerTick == 8 && electricHoverEuPerTick == 7
            && Math.abs(electricPowerFactor - 0.7F) < 1.0E-6F
            && Math.abs(electricHoverFactor - 0.5F) < 1.0E-6F;
        if (legacyElectric) {
            electricEuPerTick = 4;
            electricHoverEuPerTick = 2;
            electricPowerFactor = 1.0F;
            electricHoverFactor = 1.0F;
            configuration.get(CATEGORY, "electricEuPerTick", 4)
                .set(4);
            configuration.get(CATEGORY, "electricHoverEuPerTick", 2)
                .set(2);
            configuration.get(CATEGORY, "electricPowerFactor", 1.0F)
                .set(1.0F);
            configuration.get(CATEGORY, "electricHoverFactor", 1.0F)
                .set(1.0F);
        }
        if (altitudeFadeBlocks == 25) {
            altitudeFadeBlocks = 0;
            configuration.get(CATEGORY, "altitudeFadeBlocks", 0)
                .set(0);
        }

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    /** @return whether the Baubles integration is enabled (at least one slot type). */
    public static boolean isBaubleSlotEnabled() {
        return baubleSlotTypes.length > 0;
    }

    /** @return whether {@code type} is one of the configured Baubles slot types (case-insensitive). */
    public static boolean hasBaubleSlotType(String type) {
        if (type == null) {
            return false;
        }
        for (String configured : baubleSlotTypes) {
            if (configured.equalsIgnoreCase(type)) {
                return true;
            }
        }
        return false;
    }

    /** @return the configured Baubles slot types as a comma-separated string, for logging. */
    public static String baubleSlotTypesCsv() {
        return String.join(",", baubleSlotTypes);
    }

    private static String[] parseSlotTypes(String raw) {
        if (raw == null) {
            return new String[0];
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || "off".equalsIgnoreCase(trimmed)) {
            return new String[0];
        }
        Set<String> types = new LinkedHashSet<>();
        for (String part : trimmed.split(",")) {
            String type = part.trim()
                .toLowerCase(Locale.ROOT);
            if (!type.isEmpty()) {
                types.add(type);
            }
        }
        return types.toArray(new String[0]);
    }
}
