package com.mrfuzzihead.ic2tweaks.jetpack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;

import net.minecraftforge.common.config.Configuration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mrfuzzihead.ic2tweaks.jetpack.JetpackConfig.TravellersGearSlot;

import cpw.mods.fml.relauncher.FMLInjectionData;

/**
 * Verifies the Phase 0 jetpack configuration: defaults, file round-trips, slot parsing and Forge's
 * min/max clamping. The config is static, so every test starts from a fresh file to keep tests
 * independent.
 */
class JetpackConfigTest {

    @TempDir
    Path tempDir;

    /**
     * Forge's {@code Configuration} resolves its base path through {@code FMLInjectionData}. Plain JUnit
     * has no FML launcher, so provide a dummy home directory before the first config is created. This only
     * affects the test JVM.
     */
    @BeforeAll
    static void provideForgeHome() throws ReflectiveOperationException {
        Field field = FMLInjectionData.class.getDeclaredField("minecraftHome");
        field.setAccessible(true);
        if (field.get(null) == null) {
            field.set(null, new File(".").getAbsoluteFile());
        }
    }

    private int fileCounter;
    private File currentFile;

    @BeforeEach
    void resetToDefaults() {
        currentFile = freshFile();
        JetpackConfig.synchronizeConfiguration(currentFile);
    }

    @Test
    void defaultsMatchTheDesign() {
        assertTrue(JetpackConfig.enabled);
        assertFalse(JetpackConfig.tweakAddonJetpacks);
        assertFalse(JetpackConfig.tweakQuantumJetpack);

        assertEquals(0.32F, JetpackConfig.horizontalSpeed, 1.0E-6F);
        assertEquals(0.05F, JetpackConfig.horizontalAccel, 1.0E-6F);
        assertEquals(0.08F, JetpackConfig.horizontalBrake, 1.0E-6F);
        assertEquals(0.80F, JetpackConfig.strafeFactor, 1.0E-6F);
        assertEquals(0.60F, JetpackConfig.backwardFactor, 1.0E-6F);
        assertEquals(1.60F, JetpackConfig.boostMultiplier, 1.0E-6F);
        assertEquals(2.50F, JetpackConfig.boostFuelMultiplier, 1.0E-6F);

        assertEquals(0.60F, JetpackConfig.ascentSpeed, 1.0E-6F);
        assertEquals(0.20F, JetpackConfig.hoverRise, 1.0E-6F);
        assertEquals(0.20F, JetpackConfig.hoverSink, 1.0E-6F);
        assertEquals(0.0F, JetpackConfig.hoverIdle, 1.0E-6F);
        assertEquals(0.50F, JetpackConfig.electricHoverFactor, 1.0E-6F);
        assertEquals(0.70F, JetpackConfig.electricPowerFactor, 1.0E-6F);

        assertEquals(2, JetpackConfig.gasFuelPerTick);
        assertEquals(1, JetpackConfig.gasHoverFuelPerTick);
        assertEquals(8, JetpackConfig.electricEuPerTick);
        assertEquals(7, JetpackConfig.electricHoverEuPerTick);
        assertEquals(8, JetpackConfig.quantumEuPerTick);
        assertEquals(10, JetpackConfig.quantumHoverEuPerTick);
        assertEquals(0.20F, JetpackConfig.gasLowFuelThreshold, 1.0E-6F);
        assertEquals(0.05F, JetpackConfig.electricLowFuelThreshold, 1.0E-6F);

        assertEquals(25, JetpackConfig.altitudeFadeBlocks);
        assertEquals(1.28F, JetpackConfig.electricAltitudeFactor, 1.0E-6F);

        assertFalse(JetpackConfig.allowInWater);
        assertFalse(JetpackConfig.allowInLava);
        assertTrue(JetpackConfig.creativeNoDrain);
        assertFalse(JetpackConfig.fallProtection);
        assertTrue(JetpackConfig.stripJetpackArmorDisplay);

        assertTrue(JetpackConfig.hudEnabled);
        assertEquals(JetpackConfig.HudCorner.TOP_LEFT, JetpackConfig.hudCorner);
        assertTrue(JetpackConfig.hudShowSpeed);
        assertTrue(JetpackConfig.hudShowAltitude);
        assertEquals(10, JetpackConfig.hudLowFuelPercent);
        assertTrue(JetpackConfig.tooltipsEnabled);
        assertTrue(JetpackConfig.particlesEnabled);
        assertEquals(2, JetpackConfig.particleIntervalTicks);
        assertTrue(JetpackConfig.chargepadChargesBaubles);
        assertEquals(0.75F, JetpackConfig.particleYOffset, 1.0E-6F);

        assertArrayEquals(new String[] { "cape", "wings", "body", "belt" }, JetpackConfig.baubleSlotTypes);
        assertEquals(TravellersGearSlot.CLOAK, JetpackConfig.travellersGearSlot);
        assertEquals(1.0F, JetpackConfig.slotFuelMultiplier, 1.0E-6F);
        assertTrue(JetpackConfig.renderWornJetpack);
        assertEquals(0, JetpackConfig.syncIntervalTicks);

        assertTrue(JetpackConfig.isBaubleSlotEnabled());
        assertTrue(JetpackConfig.hasBaubleSlotType("CAPE"));
        assertTrue(JetpackConfig.hasBaubleSlotType("WINGS"));
        assertFalse(JetpackConfig.hasBaubleSlotType("ring"));
        assertFalse(JetpackConfig.hasBaubleSlotType(null));
        assertEquals("cape,wings,body,belt", JetpackConfig.baubleSlotTypesCsv());
    }

    @Test
    void valuesRoundTripThroughTheFile() {
        Configuration configuration = new Configuration(currentFile);
        configuration.get(JetpackConfig.CATEGORY, "enableJetpackTweaks", "true")
            .set(false);
        configuration.get(JetpackConfig.CATEGORY, "horizontalSpeed", "0.32")
            .set("0.42");
        configuration.get(JetpackConfig.CATEGORY, "gasFuelPerTick", "2")
            .set("5");
        configuration.get(JetpackConfig.CATEGORY, "electricHoverEuPerTick", "7")
            .set("3");
        configuration.get(JetpackConfig.CATEGORY, "quantumEuPerTick", "8")
            .set("4");
        configuration.get(JetpackConfig.CATEGORY, "quantumHoverEuPerTick", "10")
            .set("6");
        configuration.get(JetpackConfig.CATEGORY, "chargepadChargesBaubles", "true")
            .set(false);
        configuration.get(JetpackConfig.CATEGORY, "electricPowerFactor", "0.7")
            .set("1.0");
        configuration.get(JetpackConfig.CATEGORY, "baubleSlotTypes", "cape,wings,body,belt")
            .set("OFF");
        configuration.get(JetpackConfig.CATEGORY, "travellersGearSlot", "CLOAK")
            .set("SHOULDERS");
        configuration.get(JetpackConfig.CATEGORY, "slotFuelMultiplier", "1.0")
            .set("2.0");
        configuration.get(JetpackConfig.CATEGORY, "syncIntervalTicks", "0")
            .set("40");
        configuration.get(JetpackConfig.CATEGORY, "stripJetpackArmorDisplay", "true")
            .set(false);
        configuration.get(JetpackConfig.CATEGORY, "hudCorner", "TOP_LEFT")
            .set("BOTTOM_RIGHT");
        configuration.get(JetpackConfig.CATEGORY, "hudLowFuelPercent", "10")
            .set("0");
        configuration.get(JetpackConfig.CATEGORY, "particlesEnabled", "true")
            .set(false);
        configuration.get(JetpackConfig.CATEGORY, "particleIntervalTicks", "2")
            .set("5");
        configuration.get(JetpackConfig.CATEGORY, "particleYOffset", "0.75")
            .set("0.60");
        configuration.save();

        JetpackConfig.synchronizeConfiguration(currentFile);

        assertFalse(JetpackConfig.enabled);
        assertEquals(0.42F, JetpackConfig.horizontalSpeed, 1.0E-6F);
        assertEquals(5, JetpackConfig.gasFuelPerTick);
        assertEquals(3, JetpackConfig.electricHoverEuPerTick);
        assertEquals(4, JetpackConfig.quantumEuPerTick);
        assertEquals(6, JetpackConfig.quantumHoverEuPerTick);
        assertFalse(JetpackConfig.chargepadChargesBaubles);
        assertEquals(1.0F, JetpackConfig.electricPowerFactor, 1.0E-6F);
        assertEquals(0, JetpackConfig.baubleSlotTypes.length);
        assertFalse(JetpackConfig.isBaubleSlotEnabled());
        assertEquals(TravellersGearSlot.SHOULDERS, JetpackConfig.travellersGearSlot);
        assertEquals(1, JetpackConfig.travellersGearSlot.index);
        assertEquals(2.0F, JetpackConfig.slotFuelMultiplier, 1.0E-6F);
        assertEquals(40, JetpackConfig.syncIntervalTicks);
        assertFalse(JetpackConfig.stripJetpackArmorDisplay);
        assertEquals(JetpackConfig.HudCorner.BOTTOM_RIGHT, JetpackConfig.hudCorner);
        assertEquals(0, JetpackConfig.hudLowFuelPercent);
        assertFalse(JetpackConfig.particlesEnabled);
        assertEquals(5, JetpackConfig.particleIntervalTicks);
        assertEquals(0.60F, JetpackConfig.particleYOffset, 1.0E-6F);
    }

    @Test
    void hudCornerParsingFallsBackToTopLeft() {
        assertEquals(JetpackConfig.HudCorner.TOP_LEFT, JetpackConfig.HudCorner.fromString(null));
        assertEquals(JetpackConfig.HudCorner.TOP_LEFT, JetpackConfig.HudCorner.fromString("banana"));
        assertEquals(JetpackConfig.HudCorner.TOP_RIGHT, JetpackConfig.HudCorner.fromString(" top_right "));
        assertEquals(JetpackConfig.HudCorner.BOTTOM_LEFT, JetpackConfig.HudCorner.fromString("BOTTOM_LEFT"));
        assertEquals(JetpackConfig.HudCorner.BOTTOM_RIGHT, JetpackConfig.HudCorner.fromString("bottom_right"));
    }

    @Test
    void baubleSlotTypesAreTrimmedLowerCasedAndDeduplicated() {
        setString("baubleSlotTypes", " Cape, body ,BELT, cape ");
        JetpackConfig.synchronizeConfiguration(currentFile);
        assertArrayEquals(new String[] { "cape", "body", "belt" }, JetpackConfig.baubleSlotTypes);
        assertEquals("cape,body,belt", JetpackConfig.baubleSlotTypesCsv());

        for (String disabled : new String[] { "OFF", "off", "", "  ", " , " }) {
            setString("baubleSlotTypes", disabled);
            JetpackConfig.synchronizeConfiguration(currentFile);
            assertEquals(0, JetpackConfig.baubleSlotTypes.length, "value: [" + disabled + "]");
            assertFalse(JetpackConfig.isBaubleSlotEnabled());
        }

        setString("baubleSlotTypes", "wings");
        JetpackConfig.synchronizeConfiguration(currentFile);
        assertArrayEquals(new String[] { "wings" }, JetpackConfig.baubleSlotTypes);
        assertTrue(JetpackConfig.hasBaubleSlotType("WINGS"));
    }

    @Test
    void oldSlotDefaultIsMigratedToIncludeWings() {
        setString("baubleSlotTypes", "cape,body,belt");
        JetpackConfig.synchronizeConfiguration(currentFile);
        assertArrayEquals(new String[] { "cape", "wings", "body", "belt" }, JetpackConfig.baubleSlotTypes);
        assertEquals("cape,wings,body,belt", JetpackConfig.baubleSlotTypesCsv());
    }

    @Test
    void travellersGearSlotFallsBackToCloakAndReportsItsIndex() {
        assertEquals(0, TravellersGearSlot.CLOAK.index);
        assertEquals(1, TravellersGearSlot.SHOULDERS.index);
        assertEquals(-1, TravellersGearSlot.OFF.index);
        assertEquals(TravellersGearSlot.CLOAK, TravellersGearSlot.fromString(null));
        assertEquals(TravellersGearSlot.CLOAK, TravellersGearSlot.fromString("banana"));
        assertEquals(TravellersGearSlot.OFF, TravellersGearSlot.fromString(" off "));
        assertEquals(TravellersGearSlot.SHOULDERS, TravellersGearSlot.fromString(" shoulders "));

        setString("travellersGearSlot", "banana");
        JetpackConfig.synchronizeConfiguration(currentFile);
        assertEquals(TravellersGearSlot.CLOAK, JetpackConfig.travellersGearSlot);

        setString("travellersGearSlot", "OFF");
        JetpackConfig.synchronizeConfiguration(currentFile);
        assertEquals(TravellersGearSlot.OFF, JetpackConfig.travellersGearSlot);
        assertEquals(-1, JetpackConfig.travellersGearSlot.index);
    }

    @Test
    void numericValuesAreClampedToTheirRanges() {
        setString("horizontalSpeed", "99");
        setString("gasFuelPerTick", "-5");
        setString("electricEuPerTick", "99999");
        setString("electricPowerFactor", "99");
        setString("hoverIdle", "5");
        setString("altitudeFadeBlocks", "0");
        setString("syncIntervalTicks", "5000");
        setString("slotFuelMultiplier", "0");
        JetpackConfig.synchronizeConfiguration(currentFile);

        assertEquals(2.0F, JetpackConfig.horizontalSpeed, 1.0E-6F);
        assertEquals(0, JetpackConfig.gasFuelPerTick);
        assertEquals(10000, JetpackConfig.electricEuPerTick);
        assertEquals(2.0F, JetpackConfig.electricPowerFactor, 1.0E-6F);
        assertEquals(0.2F, JetpackConfig.hoverIdle, 1.0E-6F);
        assertEquals(1, JetpackConfig.altitudeFadeBlocks);
        assertEquals(1200, JetpackConfig.syncIntervalTicks);
        assertEquals(0.1F, JetpackConfig.slotFuelMultiplier, 1.0E-6F);
    }

    private File freshFile() {
        return tempDir.resolve("ic2tweaks-" + (fileCounter++) + ".cfg")
            .toFile();
    }

    private void setString(String key, String value) {
        Configuration configuration = new Configuration(currentFile);
        configuration.get(JetpackConfig.CATEGORY, key, "")
            .set(value);
        configuration.save();
    }
}
