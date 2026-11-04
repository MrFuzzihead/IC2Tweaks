package com.mrfuzzihead.ic2tweaks.jetpack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Pure formatting/maths for the Phase 3 HUD and tooltip. */
class JetpackReadoutTest {

    @Test
    void percentIsClampedAndRounded() {
        assertEquals(0, JetpackReadout.percent(0.0D, 30_000.0D));
        assertEquals(50, JetpackReadout.percent(15_000.0D, 30_000.0D));
        assertEquals(100, JetpackReadout.percent(30_000.0D, 30_000.0D));
        assertEquals(0, JetpackReadout.percent(-5.0D, 30_000.0D));
        assertEquals(100, JetpackReadout.percent(40_000.0D, 30_000.0D));
        assertEquals(0, JetpackReadout.percent(10.0D, 0.0D));
        assertEquals(33, JetpackReadout.percent(1.0D, 3.0D));
        assertEquals(67, JetpackReadout.percent(2.0D, 3.0D));
    }

    @Test
    void amountUsesGrouping() {
        assertEquals("0", JetpackReadout.amount(0L));
        assertEquals("12,345", JetpackReadout.amount(12_345L));
        assertEquals("1,234,567", JetpackReadout.amount(1_234_567L));
    }

    @Test
    void speedUsesHorizontalMotionAtTwentyTicksPerSecond() {
        assertEquals("6.4", JetpackReadout.speed(0.32D, 0.0D));
        assertEquals("5.0", JetpackReadout.speed(0.15D, 0.20D));
        assertEquals("0.0", JetpackReadout.speed(0.0D, 0.0D));
        assertEquals("6.4", JetpackReadout.speed(0.0D, -0.32D));
    }

    @Test
    void altitudeFloorsTheBlockY() {
        assertEquals("64", JetpackReadout.altitude(64.9D));
        assertEquals("64", JetpackReadout.altitude(64.0D));
        assertEquals("-1", JetpackReadout.altitude(-0.1D));
    }

    @Test
    void lowFuelHonoursTheThresholdAndCanBeDisabled() {
        assertTrue(JetpackReadout.lowFuel(10, 10));
        assertTrue(JetpackReadout.lowFuel(9, 10));
        assertFalse(JetpackReadout.lowFuel(11, 10));
        assertFalse(JetpackReadout.lowFuel(0, 0));
        assertFalse(JetpackReadout.lowFuel(5, 0));
    }
}
