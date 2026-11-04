package com.mrfuzzihead.ic2tweaks.jetpack;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Direction maths for the exhaust nozzles; a sign error previously put the plume inside the player. */
class JetpackParticlesTest {

    private static final double EPSILON = 1.0E-9D;

    @Test
    void backOffsetIsOppositeTheLookVector() {
        // yaw 0 faces +Z, so the back is -Z.
        assertEquals(0.0D, JetpackParticles.backX(0.0D, 0.45D), EPSILON);
        assertEquals(-0.45D, JetpackParticles.backZ(0.0D, 0.45D), EPSILON);

        // yaw 90 faces -X, so the back is +X.
        assertEquals(0.45D, JetpackParticles.backX(90.0D, 0.45D), EPSILON);
        assertEquals(0.0D, JetpackParticles.backZ(90.0D, 0.45D), EPSILON);

        // yaw 180 faces -Z, so the back is +Z.
        assertEquals(0.0D, JetpackParticles.backX(180.0D, 0.45D), EPSILON);
        assertEquals(0.45D, JetpackParticles.backZ(180.0D, 0.45D), EPSILON);
    }

    @Test
    void sideOffsetIsPerpendicularToTheBackOffset() {
        assertEquals(0.18D, JetpackParticles.sideX(0.0D, 0.18D), EPSILON);
        assertEquals(0.0D, JetpackParticles.sideZ(0.0D, 0.18D), EPSILON);
        assertEquals(0.0D, JetpackParticles.sideX(90.0D, 0.18D), EPSILON);
        assertEquals(0.18D, JetpackParticles.sideZ(90.0D, 0.18D), EPSILON);
    }
}
