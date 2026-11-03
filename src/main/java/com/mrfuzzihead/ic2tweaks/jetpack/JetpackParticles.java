package com.mrfuzzihead.ic2tweaks.jetpack;

import java.util.Random;

import net.minecraft.entity.player.EntityPlayer;

/**
 * Client-side exhaust particles for the jetpack. This is deliberately common code: it only ever runs on
 * the client (the handler calls it behind {@code worldObj.isRemote}) and uses vanilla particles, so no
 * client-only classes need to be loaded on a dedicated server.
 */
public final class JetpackParticles {

    private JetpackParticles() {}

    public static void spawn(EntityPlayer player, boolean electric, boolean boost) {
        if (!JetpackConfig.enabled || !JetpackConfig.particlesEnabled) {
            return;
        }
        int interval = JetpackConfig.particleIntervalTicks;
        if (player == null || !player.worldObj.isRemote || interval <= 0 || player.ticksExisted % interval != 0) {
            return;
        }

        Random rand = player.worldObj.rand;
        double yaw = Math.toRadians(player.rotationYaw);
        // The player's look vector is (-sin(yaw), cos(yaw)); the nozzles sit behind and either side.
        double backX = -Math.sin(yaw) * 0.35D;
        double backZ = Math.cos(yaw) * 0.35D;
        double sideX = Math.cos(yaw) * 0.18D;
        double sideZ = Math.sin(yaw) * 0.18D;
        double baseY = player.boundingBox.minY + JetpackConfig.particleYOffset - (player.isSneaking() ? 0.15D : 0.0D);
        String particle = electric ? "flame" : (boost ? "largesmoke" : "smoke");

        for (int side = -1; side <= 1; side += 2) {
            double x = player.posX + backX + sideX * side + (rand.nextDouble() - 0.5D) * 0.06D;
            double y = baseY + (rand.nextDouble() - 0.5D) * 0.06D;
            double z = player.posZ + backZ + sideZ * side + (rand.nextDouble() - 0.5D) * 0.06D;
            player.worldObj.spawnParticle(particle, x, y, z, 0.0D, -0.05D - rand.nextDouble() * 0.03D, 0.0D);
            if (boost) {
                player.worldObj.spawnParticle(
                    particle,
                    x + (rand.nextDouble() - 0.5D) * 0.04D,
                    y - 0.04D,
                    z + (rand.nextDouble() - 0.5D) * 0.04D,
                    0.0D,
                    -0.08D,
                    0.0D);
            }
        }
    }
}
