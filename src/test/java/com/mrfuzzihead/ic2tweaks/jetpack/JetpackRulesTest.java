package com.mrfuzzihead.ic2tweaks.jetpack;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pins the Phase 1 gating rules, especially the hover-on-the-ground bug (no thrust/sound) and the
 * drain-before-thrust boundary.
 */
class JetpackRulesTest {

    @Test
    void hoverOnTheGroundDoesNotThrustButJumpingDoes() {
        assertFalse(JetpackRules.shouldThrust(false, true, true), "hover idle on the ground");
        assertTrue(JetpackRules.shouldThrust(true, true, true), "hover take-off with jump");
        assertTrue(JetpackRules.shouldThrust(true, false, true), "normal jump on the ground");
        assertTrue(JetpackRules.shouldThrust(false, true, false), "hover airborne");
        assertTrue(JetpackRules.shouldThrust(true, false, false), "normal airborne jump");
        assertFalse(JetpackRules.shouldThrust(false, false, false), "no input airborne");
    }

    @Test
    void ridingDeathAndCreativeFlightDisableTheJetpack() {
        assertTrue(JetpackRules.canOperate(false, false, false, false, false, false, false));
        assertFalse(JetpackRules.canOperate(true, false, false, false, false, false, false));
        assertFalse(JetpackRules.canOperate(false, true, false, false, false, false, false));
        assertFalse(JetpackRules.canOperate(false, false, true, false, false, false, false));
        assertFalse(JetpackRules.canOperate(false, false, false, true, false, false, false));
        assertFalse(JetpackRules.canOperate(false, false, false, false, true, false, false));
        assertTrue(JetpackRules.canOperate(false, false, false, true, false, true, false));
        assertTrue(JetpackRules.canOperate(false, false, false, false, true, false, true));
    }

    @Test
    void landingAssistOnlyAppliesToAFallingPlayer() {
        assertTrue(JetpackRules.shouldLandingAssist(-0.1D, false, false, false, false, false));
        assertFalse(JetpackRules.shouldLandingAssist(0.1D, false, false, false, false, false));
        assertFalse(JetpackRules.shouldLandingAssist(-0.1D, true, false, false, false, false));
        assertFalse(JetpackRules.shouldLandingAssist(-0.1D, false, true, false, false, false));
        assertFalse(JetpackRules.shouldLandingAssist(-0.1D, false, false, true, false, false));
        assertFalse(JetpackRules.shouldLandingAssist(-0.1D, false, false, false, true, false));
        assertFalse(JetpackRules.shouldLandingAssist(-0.1D, false, false, false, false, true));
    }

    @Test
    void landingRangeIsBounded() {
        assertTrue(JetpackRules.withinLandingRange(10.0D, 8, 3.0D));
        assertTrue(JetpackRules.withinLandingRange(8.0D, 8, 3.0D));
        assertFalse(JetpackRules.withinLandingRange(10.0D, 8, 1.0D));
        assertFalse(JetpackRules.withinLandingRange(7.0D, 8, 3.0D));
        assertFalse(JetpackRules.withinLandingRange(10.0D, 8, -1.0D));
    }

    @Test
    void fuelIsCheckedBeforeThrust() {
        assertTrue(JetpackRules.canConsume(10.0D, 2));
        assertTrue(JetpackRules.canConsume(2.0D, 2));
        assertFalse(JetpackRules.canConsume(1.0D, 2));
        assertFalse(JetpackRules.canConsume(0.0D, 1));
        assertTrue(JetpackRules.canConsume(0.0D, 0));
        assertTrue(JetpackRules.canConsume(10.0D, 0));
    }
}
