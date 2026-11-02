package com.mrfuzzihead.ic2tweaks.jetpack;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import ic2.core.IC2;
import ic2.core.audio.AudioSource;
import ic2.core.audio.PositionSpec;

/**
 * Per-player, per-side transient jetpack state. The client and server each keep their own instance (see
 * {@link JetpackHandler}), so hover toggles, audio and counters never leak across the two threads.
 */
final class JetpackState {

    /** Scratch motion vector reused each tick; avoids allocating a vector per tick. */
    final JetpackPhysics.Motion motion = new JetpackPhysics.Motion();

    private AudioSource audioSource;
    private boolean audioOn;

    /** Locally held hover mode, seeded from the stack's NBT when the worn stack changes. */
    boolean hoverMode;
    ItemStack seededStack;

    /** Local debounce for the hover toggle; the edge detector prevents re-toggling while the keys are held. */
    int toggleCooldown;
    boolean comboDown;

    /** Server-only counter for the optional low-frequency charge/NBT sync. */
    int syncCounter;

    /** Whether thrust was applied last tick; available for display/telemetry. */
    boolean thrusting;

    void stopAudio() {
        if (audioSource != null) {
            audioSource.remove();
            audioSource = null;
        }
        audioOn = false;
    }

    void updateAudio(EntityPlayer player, boolean used) {
        if (used != audioOn) {
            if (used) {
                if (audioSource == null) {
                    audioSource = IC2.audioManager.createSource(
                        player,
                        PositionSpec.Backpack,
                        "Tools/Jetpack/JetpackLoop.ogg",
                        true,
                        false,
                        IC2.audioManager.getDefaultVolume());
                }
                if (audioSource != null) {
                    audioSource.play();
                }
            } else if (audioSource != null) {
                audioSource.remove();
                audioSource = null;
            }
            audioOn = used;
        }
        if (audioSource != null) {
            audioSource.updatePosition();
        }
    }
}
