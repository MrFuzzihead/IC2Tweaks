package com.mrfuzzihead.ic2tweaks.jetpack.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import com.mrfuzzihead.ic2tweaks.jetpack.JetpackConfig;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackReadout;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackWorn;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import ic2.core.item.armor.ItemArmorJetpack;

/**
 * Compact jetpack HUD: charge percentage, mode, speed and altitude. It is registered only on the client
 * and only draws while a jetpack is worn in any supported slot. The pure formatting lives in
 * {@link JetpackReadout} so it can be unit-tested without a client.
 */
public final class JetpackHudOverlay {

    private static final int LINE_HEIGHT = 10;
    private static final int MARGIN = 4;
    private static final int BOTTOM_OFFSET = 40;
    private static final int BACKGROUND = 0x66000000;

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.HOTBAR || !JetpackConfig.enabled
            || !JetpackConfig.hudEnabled) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        if (player == null || mc.fontRenderer == null || mc.gameSettings.hideGUI || mc.currentScreen != null) {
            return;
        }
        ItemStack stack = JetpackWorn.get(player);
        if (stack == null) {
            return;
        }

        ItemArmorJetpack item = (ItemArmorJetpack) stack.getItem();
        int percent = JetpackReadout.percent(item.getCharge(stack), item.getMaxCharge(stack));
        boolean warn = JetpackConfig.hudLowFuelPercent > 0;
        boolean low = JetpackReadout.lowFuel(percent, JetpackConfig.hudLowFuelPercent);
        boolean empty = warn && percent == 0;
        boolean hover = stack.hasTagCompound() && stack.getTagCompound()
            .getBoolean("hoverMode");

        String warning = !warn ? ""
            : empty ? " " + StatCollector.translateToLocal("ic2tweaks.jetpack.hud.empty")
                : low ? " " + StatCollector.translateToLocal("ic2tweaks.jetpack.hud.low") : "";
        String line1 = StatCollector.translateToLocalFormatted("ic2tweaks.jetpack.hud.fuel", percent) + warning;

        List<String> parts = new ArrayList<>();
        parts.add(
            StatCollector
                .translateToLocal(hover ? "ic2tweaks.jetpack.hud.mode.hover" : "ic2tweaks.jetpack.hud.mode.normal"));
        if (JetpackConfig.hudShowSpeed) {
            parts.add(
                StatCollector.translateToLocalFormatted(
                    "ic2tweaks.jetpack.hud.speed",
                    JetpackReadout.speed(player.motionX, player.motionZ)));
        }
        if (JetpackConfig.hudShowAltitude) {
            parts.add(
                StatCollector
                    .translateToLocalFormatted("ic2tweaks.jetpack.hud.altitude", JetpackReadout.altitude(player.posY)));
        }
        String line2 = String.join(" | ", parts);

        List<String> lines = new ArrayList<>();
        lines.add(line1);
        if (!line2.isEmpty()) {
            lines.add(line2);
        }

        int width = 0;
        for (String line : lines) {
            width = Math.max(width, mc.fontRenderer.getStringWidth(line));
        }

        ScaledResolution resolution = event.resolution;
        if (resolution == null) {
            return;
        }
        int screenWidth = resolution.getScaledWidth();
        int screenHeight = resolution.getScaledHeight();
        int x = MARGIN;
        int y = MARGIN;
        switch (JetpackConfig.hudCorner) {
            case TOP_RIGHT:
                x = screenWidth - width - MARGIN;
                break;
            case BOTTOM_LEFT:
                y = screenHeight - BOTTOM_OFFSET - lines.size() * LINE_HEIGHT;
                break;
            case BOTTOM_RIGHT:
                x = screenWidth - width - MARGIN;
                y = screenHeight - BOTTOM_OFFSET - lines.size() * LINE_HEIGHT;
                break;
            case TOP_LEFT:
            default:
                break;
        }

        Gui.drawRect(x - 3, y - 3, x + width + 3, y + lines.size() * LINE_HEIGHT + 1, BACKGROUND);
        mc.fontRenderer.drawStringWithShadow(line1, x, y, warn && (low || empty) ? 0xFF5555 : 0xFFFFFF);
        if (lines.size() > 1) {
            mc.fontRenderer.drawStringWithShadow(line2, x, y + LINE_HEIGHT, hover ? 0x55FFFF : 0xAAAAAA);
        }
    }
}
