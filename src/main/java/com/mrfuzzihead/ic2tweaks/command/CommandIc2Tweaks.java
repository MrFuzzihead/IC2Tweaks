package com.mrfuzzihead.ic2tweaks.command;

import java.lang.reflect.Method;
import java.util.Locale;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import com.mrfuzzihead.ic2tweaks.Tags;
import com.mrfuzzihead.ic2tweaks.WrenchConfig;
import com.mrfuzzihead.ic2tweaks.compat.waila.WailaIntegrationState;
import com.mrfuzzihead.ic2tweaks.enet.EnetConductorRegistry;
import com.mrfuzzihead.ic2tweaks.enet.EnetOverloadConfig;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackConfig;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackPhysics;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackPhysics.Kind;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackPhysics.Mode;

import ic2.core.energy.EnergyNetLocal;
import ic2.core.init.MainConfig;
import ic2.core.item.armor.ItemArmorJetpack;
import ic2.core.item.tool.ItemToolWrench;
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
        return "/ic2tweaks status|jetpack";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length > 0 && "jetpack".equalsIgnoreCase(args[0])) {
            reportJetpack(sender);
            return;
        }
        if (args.length > 0 && !"status".equalsIgnoreCase(args[0])) {
            send(sender, EnumChatFormatting.RED + getCommandUsage(sender));
            return;
        }

        boolean rawLinear = ConfigUtil.getBool(MainConfig.get(), "misc/useLinearTransferModel");
        boolean linear = EnergyNetLocal.useLinearTransferModel;
        boolean mixinApplied = hasIc2TweaksMember(EnergyNetLocal.class);
        boolean wrenchMixinApplied = hasIc2TweaksMember(ItemToolWrench.class);

        send(sender, EnumChatFormatting.GOLD + "IC2Tweaks " + Tags.VERSION);
        send(sender, "mixin active: " + colour(mixinApplied) + mixinApplied);
        send(
            sender,
            "wrench mixin active: " + colour(wrenchMixinApplied)
                + wrenchMixinApplied
                + EnumChatFormatting.GRAY
                + " (losslessWrench="
                + WrenchConfig.lossless
                + ")");
        if (WrenchConfig.lossless) {
            send(
                sender,
                "lossless wrench cost: " + WrenchConfig.manualDurabilityCost
                    + " durability / "
                    + WrenchConfig.electricEnergyCost
                    + " EU");
        }
        send(sender, "enableEnetOverload: " + colour(EnetOverloadConfig.enabled) + EnetOverloadConfig.enabled);
        send(
            sender,
            "energy model: " + colour(EnetOverloadConfig.packetModel)
                + (EnetOverloadConfig.packetModel ? "packet (1.12.2)" : "legacy per-node"));
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
        if (EnetOverloadConfig.packetModel) {
            send(
                sender,
                "packetModel: roundLossDown=" + EnetOverloadConfig.packetModelRoundLossDown
                    + " tierVoltage="
                    + EnetOverloadConfig.packetModelTierVoltage);
        }
        send(sender, "conductors registered in this world: " + conductorCount(sender));
        send(
            sender,
            "waila plugin: "
                + (WailaIntegrationState.registered ? "registered" : "not registered (Waila absent or not loaded)"));
    }

    private void reportJetpack(ICommandSender sender) {
        send(sender, EnumChatFormatting.GOLD + "IC2Tweaks " + Tags.VERSION + " - jetpack");
        boolean tickMixin = hasMethod(ItemArmorJetpack.class, "ic2tweaks$jetpackTickReplaced");
        boolean hudMixin = hasMethod(EntityPlayer.class, "ic2tweaks$armorHudMixinApplied");
        boolean baubleMixin = hasMethod(ItemArmorJetpack.class, "ic2tweaks$baubleMixinApplied")
            || hasMethod(ItemArmorJetpack.class, "ic2tweaks$baubleExpandedMixinApplied");
        boolean travellersGearMixin = hasMethod(ItemArmorJetpack.class, "ic2tweaks$travellersGearMixinApplied");
        send(
            sender,
            "tick mixin: " + colour(tickMixin) + tickMixin + EnumChatFormatting.GRAY + " (stock onArmorTick replaced)");
        send(
            sender,
            "armor HUD mixin: " + colour(hudMixin)
                + hudMixin
                + EnumChatFormatting.GRAY
                + " (ISpecialArmor display="
                + JetpackConfig.stripJetpackArmorDisplay
                + ")");
        send(
            sender,
            "baubles mixin: " + colour(baubleMixin)
                + baubleMixin
                + EnumChatFormatting.GRAY
                + " (slots "
                + JetpackConfig.baubleSlotTypesCsv()
                + ")");
        send(
            sender,
            "travellersgear mixin: " + colour(travellersGearMixin)
                + travellersGearMixin
                + EnumChatFormatting.GRAY
                + " (slot "
                + JetpackConfig.travellersGearSlot
                + ")");
        send(
            sender,
            "enabled: " + colour(JetpackConfig.enabled)
                + JetpackConfig.enabled
                + EnumChatFormatting.GRAY
                + " ("
                + (tickMixin ? "active" : "stock IC2 behaviour")
                + ")");
        send(
            sender,
            String.format(
                Locale.ROOT,
                "horizontal: %.2f b/t (%.2f m/s), accel %.3f, brake %.3f, strafe %.2f, back %.2f",
                JetpackConfig.horizontalSpeed,
                JetpackPhysics.metersPerSecond(JetpackConfig.horizontalSpeed),
                JetpackConfig.horizontalAccel,
                JetpackConfig.horizontalBrake,
                JetpackConfig.strafeFactor,
                JetpackConfig.backwardFactor));
        send(
            sender,
            String.format(
                Locale.ROOT,
                "boost: %.2fx speed, %.2fx fuel; ascent %.2f b/t; hover +%.2f/-%.2f/%.2f (electric hover x%.2f, power x%.2f)",
                JetpackConfig.boostMultiplier,
                JetpackConfig.boostFuelMultiplier,
                JetpackConfig.ascentSpeed,
                JetpackConfig.hoverRise,
                JetpackConfig.hoverSink,
                JetpackConfig.hoverIdle,
                JetpackConfig.electricHoverFactor,
                JetpackConfig.electricPowerFactor));
        send(
            sender,
            String.format(
                Locale.ROOT,
                "fuel: gas %d/%d mB/t, electric %d/%d EU/t, slot multiplier %.2fx",
                JetpackConfig.gasFuelPerTick,
                JetpackConfig.gasHoverFuelPerTick,
                JetpackConfig.electricEuPerTick,
                JetpackConfig.electricHoverEuPerTick,
                JetpackConfig.slotFuelMultiplier));
        send(
            sender,
            String.format(
                Locale.ROOT,
                "stock horizontal terminal: gas %.2f/%.2f m/s, electric %.2f/%.2f m/s (normal/hover)",
                stockSpeed(Mode.NORMAL, Kind.GAS, 1.0D),
                stockSpeed(Mode.HOVER, Kind.GAS, 1.0D),
                stockSpeed(Mode.NORMAL, Kind.ELECTRIC, 0.7D),
                stockSpeed(Mode.HOVER, Kind.ELECTRIC, 0.7D)));
        send(
            sender,
            String.format(
                Locale.ROOT,
                "stock endurance: gas %.1f min, electric %.1f min",
                JetpackPhysics.secondsOfFlight(30000.0D, JetpackConfig.gasFuelPerTick) / 60.0D,
                JetpackPhysics.secondsOfFlight(30000.0D, JetpackConfig.electricEuPerTick) / 60.0D));
        send(
            sender,
            "slots: baubles [" + JetpackConfig.baubleSlotTypesCsv()
                + "]"
                + (JetpackConfig.isBaubleSlotEnabled() ? "" : " (off)")
                + ", TG "
                + JetpackConfig.travellersGearSlot
                + ", render "
                + JetpackConfig.renderWornJetpack);
        send(
            sender,
            "UI: HUD " + JetpackConfig.hudEnabled
                + " (corner "
                + JetpackConfig.hudCorner
                + ", speed "
                + JetpackConfig.hudShowSpeed
                + ", altitude "
                + JetpackConfig.hudShowAltitude
                + ", low-fuel "
                + JetpackConfig.hudLowFuelPercent
                + "%), tooltips "
                + JetpackConfig.tooltipsEnabled
                + ", particles "
                + JetpackConfig.particlesEnabled
                + " every "
                + JetpackConfig.particleIntervalTicks
                + "t");
    }

    private static double stockSpeed(Mode mode, Kind kind, double power) {
        return JetpackPhysics.metersPerSecond(JetpackPhysics.stockForwardSpeed(mode, kind, power));
    }

    private int conductorCount(ICommandSender sender) {
        World world = sender.getEntityWorld();
        if (world == null) {
            return -1;
        }
        return registry.getConductors(world)
            .size();
    }

    private static boolean hasMethod(Class<?> clazz, String name) {
        try {
            clazz.getDeclaredMethod(name);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
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
