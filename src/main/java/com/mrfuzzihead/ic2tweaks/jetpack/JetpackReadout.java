package com.mrfuzzihead.ic2tweaks.jetpack;

import java.util.Locale;

/**
 * Formatting/maths shared by the jetpack HUD and tooltip. Kept free of Minecraft and client classes so the
 * pure parts are unit-tested.
 */
public final class JetpackReadout {

    private JetpackReadout() {}

    /** @return the clamped charge percentage (0-100), or 0 when the capacity is unknown. */
    public static int percent(double charge, double maxCharge) {
        if (maxCharge <= 0.0D) {
            return 0;
        }
        double percent = charge / maxCharge * 100.0D;
        if (percent <= 0.0D) {
            return 0;
        }
        if (percent >= 100.0D) {
            return 100;
        }
        return (int) Math.round(percent);
    }

    /** @return {@code value} with locale-independent thousands separators, for HUD/tooltip text. */
    public static String amount(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    /** @return horizontal speed in metres per second (blocks/tick * 20), with one decimal. */
    public static String speed(double motionX, double motionZ) {
        return String.format(Locale.ROOT, "%.1f", Math.hypot(motionX, motionZ) * 20.0D);
    }

    /** @return the block Y as an integer string. */
    public static String altitude(double posY) {
        return Integer.toString((int) Math.floor(posY));
    }

    /** @return whether {@code percent} is at/below {@code threshold}; a threshold of 0 disables it. */
    public static boolean lowFuel(int percent, int threshold) {
        return threshold > 0 && percent <= threshold;
    }
}
