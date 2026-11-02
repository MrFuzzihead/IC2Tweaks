package com.mrfuzzihead.ic2tweaks.jetpack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mrfuzzihead.ic2tweaks.jetpack.JetpackPhysics.Kind;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackPhysics.Mode;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackPhysics.Motion;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackPhysics.MoveResult;

/**
 * Pins the stock IC2 numbers and the Phase 1 flight model. The stock table is the measured behaviour of
 * IC2 2.2.828; the configured tests pin the target/blend/brake behaviour so the Phase 1 handler cannot
 * silently change it.
 */
class JetpackPhysicsTest {

    private static final double EPS = 1.0E-9D;
    private static final double GAS_POWER = 1.0D;
    private static final double ELECTRIC_POWER = 0.7D;

    // ---------------------------------------------------------------------------------------------
    // Stock IC2 behaviour
    // ---------------------------------------------------------------------------------------------

    @Test
    void stockHorizontalImpulsesMatchIc2() {
        // moveFlying(0, 0.4 * power * heightFactor * 2, 0.02)
        assertEquals(0.0024D, JetpackPhysics.stockForwardImpulse(Mode.NORMAL, Kind.GAS, GAS_POWER), EPS);
        assertEquals(0.016D, JetpackPhysics.stockForwardImpulse(Mode.HOVER, Kind.GAS, GAS_POWER), EPS);
        assertEquals(0.00336D, JetpackPhysics.stockForwardImpulse(Mode.NORMAL, Kind.ELECTRIC, ELECTRIC_POWER), EPS);
        assertEquals(0.01288D, JetpackPhysics.stockForwardImpulse(Mode.HOVER, Kind.ELECTRIC, ELECTRIC_POWER), EPS);

        // Below 1 the impulse scales with the input; at or above 1 it saturates at the friction.
        assertEquals(0.0024D, JetpackPhysics.moveFlyingImpulse(0.0D, 0.12D, 0.02D), EPS);
        assertEquals(0.02D, JetpackPhysics.moveFlyingImpulse(0.0D, 2.8D, 0.02D), EPS);
        assertEquals(0.0D, JetpackPhysics.moveFlyingImpulse(0.0D, 0.0D, 0.02D), 0.0D);
    }

    @Test
    void stockHorizontalTerminalSpeedsMatchTheMeasurement() {
        // 0.0024 / 0.09 = 0.0266667 b/t = 0.5333 m/s
        assertEquals(0.0266666667D, JetpackPhysics.stockForwardSpeed(Mode.NORMAL, Kind.GAS, GAS_POWER), EPS);
        // 0.016 / 0.09 = 0.1777778 b/t = 3.5556 m/s
        assertEquals(0.1777777778D, JetpackPhysics.stockForwardSpeed(Mode.HOVER, Kind.GAS, GAS_POWER), EPS);
        // 0.00336 / 0.09 = 0.0373333 b/t = 0.7467 m/s
        assertEquals(0.0373333333D, JetpackPhysics.stockForwardSpeed(Mode.NORMAL, Kind.ELECTRIC, ELECTRIC_POWER), EPS);
        // 0.01288 / 0.09 = 0.1431111 b/t = 2.8622 m/s
        assertEquals(0.1431111111D, JetpackPhysics.stockForwardSpeed(Mode.HOVER, Kind.ELECTRIC, ELECTRIC_POWER), EPS);

        // Vanilla air control alone: 0.02 / 0.09 = 0.2222 b/t = 4.4444 m/s.
        assertEquals(
            4.4444444444D,
            JetpackPhysics
                .metersPerSecond(JetpackPhysics.terminalSpeed(JetpackPhysics.VANILLA_AIR_CONTROL_IMPULSE, 0.91D)),
            EPS);
    }

    @Test
    void stockNormalVerticalRampsToTheHardCap() {
        List<Double> moves = stockVerticalMoves(Kind.GAS, false, true, false, GAS_POWER, 6);
        assertSequence(new double[] { 0.2D, 0.3176D, 0.432848D, 0.54579104D, 0.6D, 0.6D }, moves);
    }

    @Test
    void stockHoverHoldsAltitude() {
        Motion motion = new Motion();
        for (int i = 0; i < 10; i++) {
            JetpackPhysics.applyStockVertical(motion, Kind.GAS, true, false, false, GAS_POWER);
            assertEquals(0.0D, JetpackPhysics.moveAndApplyAirPhysics(motion).usedY, EPS);
        }
        // The pre-movement motion is zeroed by the hover clamp; gravity only accumulates in the
        // end-of-tick motion, which the next clamp zeroes again.
        assertEquals(-0.0784D, motion.y, EPS);
    }

    @Test
    void stockHoverJumpRisesAtTheConfiguredCap() {
        assertSequence(
            new double[] { 0.2D, 0.2D, 0.2D },
            stockVerticalMoves(Kind.GAS, true, true, false, GAS_POWER, 3));
        assertSequence(
            new double[] { 0.1D, 0.1D, 0.1D },
            stockVerticalMoves(Kind.ELECTRIC, true, true, false, ELECTRIC_POWER, 3));
    }

    @Test
    void stockHoverSneakDescendsAfterOneTick() {
        // Gas: the stock carry-over keeps the first tick at rest, then it accelerates to -0.2.
        assertSequence(
            new double[] { 0.0D, -0.0784D, -0.155232D, -0.2D, -0.2D },
            stockVerticalMoves(Kind.GAS, true, false, true, GAS_POWER, 5));
        // Electric uses 0.1 instead of 0.2.
        assertSequence(
            new double[] { 0.0D, -0.0784D, -0.1D, -0.1D, -0.1D },
            stockVerticalMoves(Kind.ELECTRIC, true, false, true, ELECTRIC_POWER, 5));
    }

    @Test
    void stockLowFuelPowerFadesBelowTheThreshold() {
        assertEquals(1.0D, JetpackPhysics.lowFuelPower(6000.0D, 30000.0D, 0.2D), EPS);
        assertEquals(0.5D, JetpackPhysics.lowFuelPower(3000.0D, 30000.0D, 0.2D), EPS);
        assertEquals(0.0D, JetpackPhysics.lowFuelPower(0.0D, 30000.0D, 0.2D), EPS);
        assertEquals(1.0D, JetpackPhysics.lowFuelPower(30000.0D, 30000.0D, 0.2D), EPS);
        assertEquals(1.0D, JetpackPhysics.lowFuelPower(60000.0D, 30000.0D, 0.2D), EPS);
        assertEquals(1.0D, JetpackPhysics.lowFuelPower(1.0D, 30000.0D, 0.0D), EPS);
        assertEquals(0.0D, JetpackPhysics.lowFuelPower(1.0D, 0.0D, 0.2D), EPS);
    }

    @Test
    void stockAltitudeFadeUsesTheStockCeilings() {
        // Gas: ceiling 256, fade starts at 231.
        assertEquals(1.0D, JetpackPhysics.altitudeFactor(231.0D, 256, 25, Kind.GAS, 1.28D), EPS);
        assertEquals(0.5D, JetpackPhysics.altitudeFactor(243.5D, 256, 25, Kind.GAS, 1.28D), EPS);
        assertEquals(0.0D, JetpackPhysics.altitudeFactor(256.0D, 256, 25, Kind.GAS, 1.28D), EPS);
        assertEquals(1.0D, JetpackPhysics.altitudeFactor(-5.0D, 256, 25, Kind.GAS, 1.28D), EPS);

        // Electric: ceiling 256 / 1.28 = 200, fade starts at 175.
        assertEquals(1.0D, JetpackPhysics.altitudeFactor(175.0D, 256, 25, Kind.ELECTRIC, 1.28D), EPS);
        assertEquals(0.5D, JetpackPhysics.altitudeFactor(187.5D, 256, 25, Kind.ELECTRIC, 1.28D), EPS);
        assertEquals(0.0D, JetpackPhysics.altitudeFactor(200.0D, 256, 25, Kind.ELECTRIC, 1.28D), EPS);
        assertEquals(0.0D, JetpackPhysics.altitudeFactor(0.0D, 0, 25, Kind.GAS, 1.28D), EPS);
        // A broken factor must not produce NaN.
        assertEquals(1.0D, JetpackPhysics.altitudeFactor(175.0D, 256, 25, Kind.ELECTRIC, Double.NaN), EPS);
    }

    // ---------------------------------------------------------------------------------------------
    // Configured (Phase 1) behaviour
    // ---------------------------------------------------------------------------------------------

    @Test
    void configuredHorizontalReachesTheTargetAndBrakes() {
        Motion motion = new Motion();
        JetpackPhysics.applyHorizontal(motion, 1.0D, 0.0D, 0.0D, 0.30D, 1.0D, 0.08D, 0.8D, 0.6D, 1.0D);
        assertEquals(0.30D, motion.z, EPS);
        assertEquals(0.0D, motion.x, EPS);

        // Acceleration is capped per tick.
        Motion slow = new Motion();
        JetpackPhysics.applyHorizontal(slow, 1.0D, 0.0D, 0.0D, 0.30D, 0.05D, 0.08D, 0.8D, 0.6D, 1.0D);
        assertEquals(0.05D, slow.z, EPS);

        // No input brakes at the configured rate.
        Motion braking = new Motion(0.0D, 0.0D, 0.30D);
        JetpackPhysics.applyHorizontal(braking, 0.0D, 0.0D, 0.0D, 0.30D, 0.05D, 0.08D, 0.8D, 0.6D, 1.0D);
        assertEquals(0.22D, braking.z, EPS);

        // No input and no brake leaves the motion untouched.
        Motion coasting = new Motion(0.0D, 0.0D, 0.30D);
        JetpackPhysics.applyHorizontal(coasting, 0.0D, 0.0D, 0.0D, 0.30D, 0.05D, 0.0D, 0.8D, 0.6D, 1.0D);
        assertEquals(0.30D, coasting.z, EPS);
    }

    @Test
    void configuredHorizontalUsesYawAndDirectionalFactors() {
        // Yaw 0 faces +Z; left strafe is +X.
        assertEquals(0.30D, horizontal(1.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D).z, EPS);
        assertEquals(0.30D, horizontal(0.0D, 1.0D, 0.0D, 1.0D, 1.0D, 1.0D).x, EPS);
        assertEquals(-0.30D, horizontal(0.0D, -1.0D, 0.0D, 1.0D, 1.0D, 1.0D).x, EPS);
        assertEquals(-0.30D, horizontal(1.0D, 0.0D, 90.0D, 1.0D, 1.0D, 1.0D).x, EPS);
        assertEquals(-0.30D, horizontal(1.0D, 0.0D, 180.0D, 1.0D, 1.0D, 1.0D).z, EPS);
        assertEquals(0.30D, horizontal(1.0D, 0.0D, 270.0D, 1.0D, 1.0D, 1.0D).x, EPS);

        // Backwards is slower, strafing uses its own factor.
        assertEquals(-0.18D, horizontal(-1.0D, 0.0D, 0.0D, 1.0D, 0.6D, 1.0D).z, EPS);
        assertEquals(0.24D, horizontal(0.0D, 1.0D, 0.0D, 0.8D, 1.0D, 1.0D).x, EPS);

        // Diagonals are normalised (no 1.414x speed boost).
        Motion diagonal = horizontal(1.0D, 1.0D, 0.0D, 1.0D, 1.0D, 1.0D);
        assertEquals(0.30D, diagonal.horizontalSpeed(), EPS);

        // Boost scales the target.
        assertEquals(0.48D, horizontal(1.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.6D).z, EPS);
    }

    @Test
    void configuredVerticalMatchesTheStockDefaultsAndFadesHoverRise() {
        // Default configured hover values reproduce the stock gas behaviour.
        assertSequence(
            new double[] { 0.0D, 0.0D, 0.0D },
            configuredVerticalMoves(true, false, false, 1.0D, 0.6D, 0.2D, 0.2D, 0.0D, 3));
        assertSequence(
            new double[] { 0.2D, 0.2D, 0.2D },
            configuredVerticalMoves(true, true, false, 1.0D, 0.6D, 0.2D, 0.2D, 0.0D, 3));
        assertSequence(
            new double[] { 0.0D, -0.0784D, -0.155232D, -0.2D },
            configuredVerticalMoves(true, false, true, 1.0D, 0.6D, 0.2D, 0.2D, 0.0D, 4));

        // Low power scales the hover rise (unlike stock IC2, which keeps the full 0.2).
        assertSequence(
            new double[] { 0.1D, 0.1D, 0.1D },
            configuredVerticalMoves(true, true, false, 0.5D, 0.6D, 0.2D, 0.2D, 0.0D, 3));
        assertSequence(
            new double[] { 0.0D, -0.0784D, -0.155232D },
            configuredVerticalMoves(true, true, false, 0.0D, 0.6D, 0.2D, 0.2D, 0.0D, 3));

        // Normal mode uses ascentSpeed as the cap.
        assertSequence(
            new double[] { 0.2D, 0.3D, 0.3D },
            configuredVerticalMoves(false, true, false, 1.0D, 0.3D, 0.2D, 0.2D, 0.0D, 3));

        // Positive hoverIdle rises slowly; the clamp is applied before movement.
        assertSequence(
            new double[] { 0.05D, 0.05D, 0.05D },
            configuredVerticalMoves(true, false, false, 1.0D, 0.6D, 0.2D, 0.2D, 0.05D, 3));

        // Electric stock parity: normal power 0.7, hover power 1.0, and the hover speeds already scaled by
        // electricHoverFactor (0.1). The normal ascent first tick is 0.14 instead of 0.2.
        assertSequence(
            new double[] { 0.1D, 0.1D },
            configuredVerticalMoves(true, true, false, 0.7D, 1.0D, 0.6D, 0.1D, 0.1D, 0.0D, 2));
        assertSequence(
            new double[] { 0.14D },
            configuredVerticalMoves(false, true, false, 0.7D, 1.0D, 0.6D, 0.2D, 0.2D, 0.0D, 1));
    }

    @Test
    void fuelAccountingMatchesTheStockCostsAndBoost() {
        assertEquals(2, JetpackPhysics.fuelCost(Kind.GAS, false, false, 2, 1, 8, 7, 2.5D));
        assertEquals(1, JetpackPhysics.fuelCost(Kind.GAS, true, false, 2, 1, 8, 7, 2.5D));
        assertEquals(8, JetpackPhysics.fuelCost(Kind.ELECTRIC, false, false, 2, 1, 8, 7, 2.5D));
        assertEquals(7, JetpackPhysics.fuelCost(Kind.ELECTRIC, true, false, 2, 1, 8, 7, 2.5D));
        assertEquals(5, JetpackPhysics.fuelCost(Kind.GAS, false, true, 2, 1, 8, 7, 2.5D));
        assertEquals(18, JetpackPhysics.fuelCost(Kind.ELECTRIC, true, true, 2, 1, 8, 7, 2.5D));
        assertEquals(0, JetpackPhysics.fuelCost(Kind.GAS, false, false, -1, -1, -1, -1, 2.5D));

        // Stock endurance: 30,000 mB / 2 mB/t = 12.5 min; 30,000 EU / 8 EU/t = 3.125 min.
        assertEquals(750.0D, JetpackPhysics.secondsOfFlight(30000.0D, 2), EPS);
        assertEquals(1500.0D, JetpackPhysics.secondsOfFlight(30000.0D, 1), EPS);
        assertEquals(187.5D, JetpackPhysics.secondsOfFlight(30000.0D, 8), EPS);
        assertEquals(30000.0D / 7.0D / 20.0D, JetpackPhysics.secondsOfFlight(30000.0D, 7), EPS);
        assertEquals(0.0D, JetpackPhysics.secondsOfFlight(0.0D, 2), EPS);
        assertEquals(Double.POSITIVE_INFINITY, JetpackPhysics.secondsOfFlight(30000.0D, 0), EPS);
    }

    @Test
    void airPhysicsTickOrderMatchesMinecraft() {
        Motion motion = new Motion(0.3D, 0.2D, -0.1D);
        MoveResult used = JetpackPhysics.moveAndApplyAirPhysics(motion);
        // Movement uses the pre-gravity motion.
        assertEquals(0.3D, used.usedX, EPS);
        assertEquals(0.2D, used.usedY, EPS);
        assertEquals(-0.1D, used.usedZ, EPS);
        // Then gravity, vertical drag and horizontal drag are applied for the next tick.
        assertEquals(0.273D, motion.x, EPS);
        assertEquals(0.1176D, motion.y, EPS);
        assertEquals(-0.091D, motion.z, EPS);
    }

    @Test
    void degenerateInputsAreHarmless() {
        assertEquals(0.0D, JetpackPhysics.moveFlyingImpulse(Double.NaN, 0.0D, 0.02D), 0.0D);
        assertEquals(0.0D, JetpackPhysics.moveFlyingImpulse(0.0D, 0.0D, 0.0D), 0.0D);
        assertEquals(0.0D, JetpackPhysics.stockForwardImpulse(Mode.NORMAL, Kind.GAS, Double.NaN), 0.0D);
        assertEquals(0.0D, JetpackPhysics.terminalSpeed(0.0D, 0.91D), 0.0D);
        assertEquals(0.0D, JetpackPhysics.terminalSpeed(0.02D, Double.NaN), 0.0D);
        assertEquals(Double.POSITIVE_INFINITY, JetpackPhysics.terminalSpeed(0.02D, 1.0D), 0.0D);

        Motion motion = new Motion(0.1D, 0.0D, 0.1D);
        JetpackPhysics.applyHorizontal(motion, Double.NaN, 0.0D, 0.0D, 0.3D, 0.05D, 0.0D, 0.8D, 0.6D, 1.0D);
        assertEquals(0.1D, motion.x, EPS);
        assertEquals(0.1D, motion.z, EPS);

        JetpackPhysics.applyHorizontal(motion, 1.0D, 0.0D, 0.0D, 0.0D, 0.05D, 0.0D, 0.8D, 0.6D, 1.0D);
        assertEquals(0.1D, motion.x, EPS);
        assertEquals(0.1D, motion.z, EPS);
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private static Motion horizontal(double forward, double strafe, double yaw, double strafeFactor,
        double backwardFactor, double boost) {
        Motion motion = new Motion();
        JetpackPhysics
            .applyHorizontal(motion, forward, strafe, yaw, 0.30D, 1.0D, 0.0D, strafeFactor, backwardFactor, boost);
        return motion;
    }

    private static List<Double> stockVerticalMoves(Kind kind, boolean hover, boolean jump, boolean sneak, double power,
        int ticks) {
        Motion motion = new Motion();
        List<Double> moves = new ArrayList<>(ticks);
        for (int i = 0; i < ticks; i++) {
            JetpackPhysics.applyStockVertical(motion, kind, hover, jump, sneak, power);
            moves.add(JetpackPhysics.moveAndApplyAirPhysics(motion).usedY);
        }
        return moves;
    }

    private static List<Double> configuredVerticalMoves(boolean hover, boolean jump, boolean sneak, double power,
        double ascentSpeed, double hoverRise, double hoverSink, double hoverIdle, int ticks) {
        return configuredVerticalMoves(
            hover,
            jump,
            sneak,
            power,
            power,
            ascentSpeed,
            hoverRise,
            hoverSink,
            hoverIdle,
            ticks);
    }

    private static List<Double> configuredVerticalMoves(boolean hover, boolean jump, boolean sneak, double power,
        double hoverPower, double ascentSpeed, double hoverRise, double hoverSink, double hoverIdle, int ticks) {
        Motion motion = new Motion();
        List<Double> moves = new ArrayList<>(ticks);
        for (int i = 0; i < ticks; i++) {
            JetpackPhysics.applyConfiguredVertical(
                motion,
                hover,
                jump,
                sneak,
                power,
                hoverPower,
                ascentSpeed,
                hoverRise,
                hoverSink,
                hoverIdle);
            moves.add(JetpackPhysics.moveAndApplyAirPhysics(motion).usedY);
        }
        return moves;
    }

    private static void assertSequence(double[] expected, List<Double> actual) {
        assertEquals(expected.length, actual.size(), "tick count");
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual.get(i), EPS, "tick " + i);
        }
        assertTrue(actual.size() > 0, "expected at least one tick");
    }
}
