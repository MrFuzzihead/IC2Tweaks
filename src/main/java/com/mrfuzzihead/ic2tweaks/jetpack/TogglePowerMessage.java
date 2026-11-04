package com.mrfuzzihead.ic2tweaks.jetpack;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import ic2.core.IC2;
import io.netty.buffer.ByteBuf;

/** Client -> server power-switch toggle for the dedicated key. */
public final class TogglePowerMessage implements IMessage {

    public TogglePowerMessage() {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static final class Handler implements IMessageHandler<TogglePowerMessage, IMessage> {

        @Override
        public IMessage onMessage(TogglePowerMessage message, MessageContext ctx) {
            if (!JetpackConfig.enabled || !JetpackConfig.dedicatedPowerKey) {
                return null;
            }
            EntityPlayer player = ctx.getServerHandler().playerEntity;
            if (player == null) {
                return null;
            }
            ItemStack stack = JetpackWorn.get(player);
            if (!JetpackItem.isSupported(stack)) {
                return null;
            }
            boolean off = !JetpackItem.isPowerOff(stack);
            JetpackItem.setPowerOff(stack, off);
            IC2.platform.messagePlayer(
                player,
                StatCollector.translateToLocal(off ? "ic2tweaks.jetpack.power.off" : "ic2tweaks.jetpack.power.on"));
            return null;
        }
    }
}
