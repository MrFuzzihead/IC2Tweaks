package com.mrfuzzihead.ic2tweaks.jetpack;

/**
 * Pure gating rules for the jetpack tick. Kept separate from {@link JetpackHandler} so the edge cases
 * (hover on the ground, riding, water/lava, empty fuel) can be unit tested without Minecraft.
 */
public final class JetpackRules {

    private JetpackRules() {}

    /**
     * @return whether the jetpack may run at all this tick. Riding, death and creative flight disable it,
     *         as do water/lava unless the corresponding config allows them.
     */
    public static boolean canOperate(boolean dead, boolean riding, boolean creativeFlying, boolean inWater,
        boolean inLava, boolean allowWater, boolean allowLava) {
        if (dead || riding || creativeFlying) {
            return false;
        }
        if (inWater && !allowWater) {
            return false;
        }
        return !inLava || allowLava;
    }

    /**
     * @return whether thrust should be applied/sounded this tick. Jumping always thrusts (including the
     *         take-off tick on the ground); hover mode only thrusts while airborne, so a hovering player
     *         standing on the ground no longer runs the loop sound or burns fuel.
     */
    public static boolean shouldThrust(boolean jump, boolean hover, boolean onGround) {
        return jump || (hover && !onGround);
    }

    /**
     * @return whether the jetpack has enough charge for this tick's cost. Checking before applying thrust
     *         fixes the stock free-thrust tick when only 1 mB / 1 EU is left.
     */
    public static boolean canConsume(double charge, int cost) {
        return cost <= 0 || charge >= cost;
    }
}
