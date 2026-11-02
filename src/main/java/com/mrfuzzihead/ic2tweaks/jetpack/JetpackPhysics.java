package com.mrfuzzihead.ic2tweaks.jetpack;

/**
 * Pure jetpack flight maths, kept free of Minecraft/IC2 types so it can be unit tested without booting a
 * game and reused by the client and server handlers.
 * <p>
 * The stock numbers mirror IC2 2.2.828's {@code ItemArmorJetpack.useJetpack}; the configured numbers
 * mirror the Phase 1 flight model. The tick order is important and is reproduced by
 * {@link #moveAndApplyAirPhysics(Motion)}: the armour tick runs before movement, movement uses the current
 * motion, then Minecraft applies gravity and drag for the next tick.
 */
public final class JetpackPhysics {

    /** Minecraft 1.7.10 horizontal air drag, applied after movement. */
    public static final double AIR_DRAG_HORIZONTAL = 0.91D;

    /** Minecraft 1.7.10 gravity for players, applied after movement. */
    public static final double AIR_GRAVITY = 0.08D;

    /** Minecraft 1.7.10 vertical air drag, applied after gravity. */
    public static final double AIR_DRAG_VERTICAL = 0.98D;

    /** Vanilla air control impulse while a movement key is held ({@code jumpMovementFactor}). */
    public static final double VANILLA_AIR_CONTROL_IMPULSE = 0.02D;

    /** Ticks per second. */
    public static final double TICKS_PER_SECOND = 20.0D;

    /** Blocks per tick to metres per second. */
    public static final double BLOCKS_PER_TICK_TO_METERS_PER_SECOND = 20.0D;

    /** Jetpack fuel/energy type. */
    public enum Kind {
        GAS,
        ELECTRIC
    }

    /** Jetpack flight mode. */
    public enum Mode {
        NORMAL,
        HOVER
    }

    /** Mutable motion vector, in blocks per tick. */
    public static final class Motion {

        public double x;
        public double y;
        public double z;

        public Motion() {}

        public Motion(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public void set(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public double horizontalSpeed() {
            return Math.sqrt(x * x + z * z);
        }

        public double speed() {
            return Math.sqrt(x * x + y * y + z * z);
        }
    }

    /** The motion actually used by {@code moveEntity} for one tick. */
    public static final class MoveResult {

        public final double usedX;
        public final double usedY;
        public final double usedZ;

        public MoveResult(double usedX, double usedY, double usedZ) {
            this.usedX = usedX;
            this.usedY = usedY;
            this.usedZ = usedZ;
        }
    }

    private JetpackPhysics() {}

    // ---------------------------------------------------------------------------------------------
    // Stock IC2 behaviour (kept for parity tests and the config-disabled fallback)
    // ---------------------------------------------------------------------------------------------

    /**
     * The horizontal impulse {@code Entity.moveFlying(strafe, forward, friction)} adds when the input
     * direction is normalised. If the input magnitude is at least 1, the added magnitude saturates at
     * {@code friction}; below 1 it is {@code magnitude * friction}.
     */
    public static double moveFlyingImpulse(double strafe, double forward, double friction) {
        if (!Double.isFinite(strafe) || !Double.isFinite(forward)) {
            return 0.0D;
        }
        double squared = strafe * strafe + forward * forward;
        if (squared < 1.0E-4D || !(friction > 0.0D) || !Double.isFinite(friction)) {
            return 0.0D;
        }
        double magnitude = Math.sqrt(squared);
        double normalizer = Math.max(1.0D, magnitude);
        return magnitude * (friction / normalizer);
    }

    /** IC2's stock forward-thrust factor: 0.15 normal, 1.0 hover, +0.15 for the electric jetpack. */
    public static double stockHeightFactor(Mode mode, Kind kind) {
        double factor = mode == Mode.HOVER ? 1.0D : 0.15D;
        if (kind == Kind.ELECTRIC) {
            factor += 0.15D;
        }
        return factor;
    }

    /** IC2's stock {@code moveFlying(0, 0.4 * power * heightFactor * 2.0, 0.02)} impulse. */
    public static double stockForwardImpulse(Mode mode, Kind kind, double power) {
        return moveFlyingImpulse(0.0D, 0.4D * Math.max(0.0D, power) * stockHeightFactor(mode, kind) * 2.0D, 0.02D);
    }

    /** Terminal speed for a constant per-tick impulse and a per-tick drag factor. */
    public static double terminalSpeed(double impulsePerTick, double drag) {
        if (!(impulsePerTick > 0.0D) || !Double.isFinite(impulsePerTick) || !Double.isFinite(drag)) {
            return 0.0D;
        }
        double remaining = 1.0D - drag;
        if (remaining <= 0.0D) {
            return Double.POSITIVE_INFINITY;
        }
        return impulsePerTick / remaining;
    }

    /** IC2's stock jetpack-only forward terminal speed in blocks per tick. */
    public static double stockForwardSpeed(Mode mode, Kind kind, double power) {
        return terminalSpeed(stockForwardImpulse(mode, kind, power), AIR_DRAG_HORIZONTAL);
    }

    /**
     * IC2's stock vertical thrust and hover clamp. The {@code power} fade is applied to the additive lift
     * first; the hover clamp then overrides it (including the stock quirk that hover rise does not fade at
     * the altitude ceiling).
     */
    public static void applyStockVertical(Motion motion, Kind kind, boolean hover, boolean jump, boolean sneak,
        double power) {
        double safePower = power > 0.0D && !Double.isNaN(power) ? power : 0.0D;
        double previous = motion.y;
        motion.y = Math.min(motion.y + safePower * 0.2D, 0.6D);
        if (hover) {
            double cap = 0.0D;
            if (jump) {
                cap = kind == Kind.ELECTRIC ? 0.1D : 0.2D;
            }
            if (sneak) {
                cap = kind == Kind.ELECTRIC ? -0.1D : -0.2D;
            }
            if (motion.y > cap) {
                motion.y = cap;
                if (previous > motion.y) {
                    motion.y = previous;
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Configured (Phase 1) behaviour
    // ---------------------------------------------------------------------------------------------

    /**
     * Blends the horizontal velocity toward a yaw-relative target while a movement key is held, or brakes
     * when no key is held. The change per tick is capped by {@code accel}, so knockback/explosions are
     * never overwritten instantly.
     *
     * @param forward        forward input in [-1, 1] (positive is forward)
     * @param strafe         strafe input in [-1, 1] (positive is left, matching {@code moveFlying})
     * @param yawDegrees     the player's yaw
     * @param maxSpeed       target speed in blocks per tick (before the strafe/back factors)
     * @param accel          maximum velocity change per tick
     * @param brake          velocity change per tick when there is no input
     * @param strafeFactor   multiplier applied to strafe input
     * @param backwardFactor multiplier applied to negative forward input
     * @param boost          speed multiplier (1.0 for none)
     */
    public static void applyHorizontal(Motion motion, double forward, double strafe, double yawDegrees, double maxSpeed,
        double accel, double brake, double strafeFactor, double backwardFactor, double boost) {
        if (!Double.isFinite(forward) || !Double.isFinite(strafe) || !Double.isFinite(yawDegrees)) {
            brake(motion, brake);
            return;
        }
        double scaledForward = forward < 0.0D ? forward * backwardFactor : forward;
        double scaledStrafe = strafe * strafeFactor;
        double length = Math.sqrt(scaledForward * scaledForward + scaledStrafe * scaledStrafe);
        if (length < 1.0E-4D || !(maxSpeed > 0.0D) || !(accel > 0.0D)) {
            brake(motion, brake);
            return;
        }

        double speed = maxSpeed * Math.max(1.0D, boost) * Math.min(1.0D, length);
        double directionForward = scaledForward / length;
        double directionStrafe = scaledStrafe / length;

        double yaw = Math.toRadians(yawDegrees);
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double leftX = Math.cos(yaw);
        double leftZ = Math.sin(yaw);

        double targetX = (forwardX * directionForward + leftX * directionStrafe) * speed;
        double targetZ = (forwardZ * directionForward + leftZ * directionStrafe) * speed;
        double deltaX = targetX - motion.x;
        double deltaZ = targetZ - motion.z;
        double delta = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        if (delta > accel) {
            deltaX *= accel / delta;
            deltaZ *= accel / delta;
        }
        motion.x += deltaX;
        motion.z += deltaZ;
    }

    /**
     * Vertical thrust for the configured model. Normal mode climbs at {@code power * 0.2} up to
     * {@code ascentSpeed}. Hover mode clamps to {@code hoverRise} (jump), {@code -hoverSink} (sneak) or
     * {@code hoverIdle}; an upward clamp is scaled by {@code hoverPower} so low fuel and the altitude
     * ceiling also affect hover (unlike stock IC2, where holding jump keeps rising at the ceiling).
     * <p>
     * The additive lift uses {@code power} (which includes the electric jetpack's stock 0.7 factor), while
     * the hover clamp uses {@code hoverPower} (without that factor) so the configured electric hover speed
     * still matches IC2's stock 0.1.
     */
    public static void applyConfiguredVertical(Motion motion, boolean hover, boolean jump, boolean sneak, double power,
        double hoverPower, double ascentSpeed, double hoverRise, double hoverSink, double hoverIdle) {
        double safePower = clamp01(power);
        double safeHoverPower = clamp01(hoverPower);
        double previous = motion.y;
        double lift = safePower * 0.2D;
        if (!hover) {
            motion.y = Math.min(motion.y + lift, Math.max(0.0D, ascentSpeed));
            return;
        }

        double additiveCap = Math.max(0.6D, hoverRise);
        motion.y = Math.min(motion.y + lift, additiveCap);
        double cap = jump ? hoverRise : sneak ? -hoverSink : hoverIdle;
        if (cap > 0.0D) {
            cap *= safeHoverPower;
        }
        if (motion.y > cap) {
            motion.y = cap;
            if (previous > motion.y) {
                motion.y = previous;
            }
        }
    }

    private static double clamp01(double value) {
        if (!(value > 0.0D) || Double.isNaN(value)) {
            return 0.0D;
        }
        return Math.min(1.0D, value);
    }

    /** Applies the configured horizontal brake. */
    public static void brake(Motion motion, double brake) {
        if (!(brake > 0.0D)) {
            return;
        }
        double speed = motion.horizontalSpeed();
        if (speed <= 1.0E-9D) {
            return;
        }
        double factor = Math.min(speed, brake) / speed;
        motion.x *= 1.0D - factor;
        motion.z *= 1.0D - factor;
    }

    // ---------------------------------------------------------------------------------------------
    // Fuel, low-charge and altitude helpers
    // ---------------------------------------------------------------------------------------------

    /**
     * IC2's low-charge lift fade: full power above {@code threshold}, proportional below it, zero when the
     * charge is empty.
     */
    public static double lowFuelPower(double charge, double maxCharge, double threshold) {
        if (!(charge > 0.0D) || !(maxCharge > 0.0D) || Double.isNaN(charge) || Double.isNaN(maxCharge)) {
            return 0.0D;
        }
        if (!(threshold > 0.0D)) {
            return 1.0D;
        }
        double ratio = charge / maxCharge;
        if (ratio >= 1.0D) {
            return 1.0D;
        }
        return ratio <= threshold ? ratio / threshold : 1.0D;
    }

    /**
     * IC2's altitude fade. The ceiling is {@code worldHeight} for gas and
     * {@code worldHeight / electricAltitudeFactor} for electric; lift fades linearly over the last
     * {@code fadeBlocks} blocks.
     *
     * @return a factor in [0, 1]
     */
    public static double altitudeFactor(double posY, int worldHeight, int fadeBlocks, Kind kind,
        double electricAltitudeFactor) {
        if (worldHeight <= 0 || Double.isNaN(posY)) {
            return 0.0D;
        }
        double ceiling = worldHeight;
        if (kind == Kind.ELECTRIC) {
            double factor = Double.isFinite(electricAltitudeFactor) ? Math.max(1.0D, electricAltitudeFactor) : 1.0D;
            ceiling /= factor;
        }
        double fade = Math.max(1.0D, fadeBlocks);
        if (posY >= ceiling) {
            return 0.0D;
        }
        if (posY <= ceiling - fade) {
            return 1.0D;
        }
        return (ceiling - posY) / fade;
    }

    /**
     * Fuel/energy consumed this tick. The stock costs are gas 2 (normal) / 1 (hover) mB and electric 8 /
     * 7 EU. Boost multiplies the base cost and rounds up.
     */
    public static int fuelCost(Kind kind, boolean hover, boolean boost, int gasNormal, int gasHover, int euNormal,
        int euHover, double boostFuelMultiplier) {
        int base = kind == Kind.ELECTRIC ? (hover ? euHover : euNormal) : (hover ? gasHover : gasNormal);
        base = Math.max(0, base);
        if (!boost) {
            return base;
        }
        double multiplier = Math.max(1.0D, boostFuelMultiplier);
        return Math.max(base, (int) Math.ceil(base * multiplier));
    }

    /**
     * @return seconds of flight for a charge and a per-tick cost; {@code Infinity} if the cost is zero and
     *         the charge is positive, 0 if the charge is empty.
     */
    public static double secondsOfFlight(double charge, int costPerTick) {
        if (!(charge > 0.0D)) {
            return 0.0D;
        }
        if (costPerTick <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return charge / costPerTick / TICKS_PER_SECOND;
    }

    /** Converts blocks per tick to metres per second. */
    public static double metersPerSecond(double blocksPerTick) {
        return blocksPerTick * BLOCKS_PER_TICK_TO_METERS_PER_SECOND;
    }

    /**
     * Applies Minecraft's post-movement air physics: gravity then vertical drag, and horizontal drag. The
     * returned result is the motion that was used by {@code moveEntity} for this tick.
     */
    public static MoveResult moveAndApplyAirPhysics(Motion motion) {
        MoveResult used = new MoveResult(motion.x, motion.y, motion.z);
        motion.x *= AIR_DRAG_HORIZONTAL;
        motion.z *= AIR_DRAG_HORIZONTAL;
        motion.y = (motion.y - AIR_GRAVITY) * AIR_DRAG_VERTICAL;
        return used;
    }
}
