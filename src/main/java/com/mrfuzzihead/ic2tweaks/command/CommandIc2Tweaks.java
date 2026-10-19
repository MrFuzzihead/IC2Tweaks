package com.mrfuzzihead.ic2tweaks.command;

import java.lang.reflect.Method;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import com.mrfuzzihead.ic2tweaks.Tags;
import com.mrfuzzihead.ic2tweaks.enet.EnetConductorRegistry;
import com.mrfuzzihead.ic2tweaks.enet.EnetOverloadConfig;

import ic2.core.energy.EnergyNetLocal;
import ic2.core.init.MainConfig;
import ic2.core.util.ConfigUtil;

/**
 * In-game diagnostic for the energy-net overload feature. Reports whether the mixins are actually
 * applied and whether the linear transfer model was forced, so a test session can confirm the feature
 * is live without reading the log.
 */
public class CommandIc2Tweaks extends CommandBase {

    private final EnetConductorRegistry registry;

    public CommandIc2Tweaks(EnetConductorRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String getCommandName() {
        return "ic2tweaks";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/ic2tweaks status";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length > 0 && !"status".equalsIgnoreCase(args[0])) {
            send(sender, EnumChatFormatting.RED + getCommandUsage(sender));
            return;
        }

        boolean rawLinear = ConfigUtil.getBool(MainConfig.get(), "misc/useLinearTransferModel");
        boolean linear = EnergyNetLocal.useLinearTransferModel;
        boolean mixinApplied = hasIc2TweaksMember(EnergyNetLocal.class);

        send(sender, EnumChatFormatting.GOLD + "IC2Tweaks " + Tags.VERSION);
        send(sender, "mixin active: " + colour(mixinApplied) + mixinApplied);
        send(sender, "enableEnetOverload: " + colour(EnetOverloadConfig.enabled) + EnetOverloadConfig.enabled);
        send(
            sender,
            "useLinearTransferModel: " + colour(
                linear) + linear + EnumChatFormatting.GRAY + " (IC2.ini: " + rawLinear + ")");
        send(
            sender,
            "explosions=" + EnetOverloadConfig.explosions
                + " cableMeltdown="
                + EnetOverloadConfig.cableMeltdown
                + " shocks="
                + EnetOverloadConfig.shocks
                + " demandCapped="
                + EnetOverloadConfig.demandCapped);
        send(sender, "conductors registered in this world: " + conductorCount(sender));
    }

    private int conductorCount(ICommandSender sender) {
        World world = sender.getEntityWorld();
        if (world == null) {
            return -1;
        }
        return registry.getConductors(world)
            .size();
    }

    private static boolean hasIc2TweaksMember(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName()
                .contains("ic2tweaks")) {
                return true;
            }
        }
        return false;
    }

    private static EnumChatFormatting colour(boolean value) {
        return value ? EnumChatFormatting.GREEN : EnumChatFormatting.RED;
    }

    private static void send(ICommandSender sender, String message) {
        sender.addChatMessage(new ChatComponentText(message));
    }
}
