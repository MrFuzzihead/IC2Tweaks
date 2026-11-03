package com.mrfuzzihead.ic2tweaks.jetpack;

/**
 * Client GUI state shared with the common handler. Updated by the client tick handler; stays false on a
 * dedicated server. Used only when {@code pauseInGui} is enabled.
 */
public final class JetpackGuiState {

    public static volatile boolean guiOpen;

    private JetpackGuiState() {}
}
