package com.mrfuzzihead.ic2tweaks.compat.waila;

/**
 * Tiny, Waila-free holder so the rest of IC2Tweaks can report whether the optional Waila plugin was
 * actually registered without loading any {@code mcp.mobius.waila} class. This class deliberately has
 * no Waila imports; loading it is always safe even when Waila is not installed.
 */
public final class WailaIntegrationState {

    /**
     * Waila IMC callback method. This is a compile-time constant, so referencing it does not load the
     * Waila-dependent {@code WailaCompat} class.
     */
    public static final String REGISTRATION_METHOD = "com.mrfuzzihead.ic2tweaks.compat.waila.WailaCompat.register";

    /** Set by {@link WailaCompat#register} when Waila calls the IMC registration callback. */
    public static boolean registered;

    private WailaIntegrationState() {}
}
