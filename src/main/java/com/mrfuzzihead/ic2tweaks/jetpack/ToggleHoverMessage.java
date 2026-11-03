package com.mrfuzzihead.ic2tweaks.jetpack;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import ic2.core.IC2;
import ic2.core.util.StackUtil;
import io.netty.buffer.ByteBuf;

/** Client -> server hover toggle for the dedicated key. The client also toggles its local NBT immediately. */
public final class ToggleHoverMessage implements IMessage {

    public ToggleHoverMessage() {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static final class Handler implements IMessageHandler<ToggleHoverMessage, IMessage> {

        @Override
        public IMessage onMessage(ToggleHoverMessage message, MessageContext ctx) {
            if (!JetpackConfig.enabled || !JetpackConfig.dedicatedHoverKey) {
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
            NBTTagCompound tag = StackUtil.getOrCreateNbtData(stack);
            boolean hover = !tag.getBoolean("hoverMode");
            tag.setBoolean("hoverMode", hover);
            tag.setByte("toggleTimer", (byte) 10);
            IC2.platform.messagePlayer(
                player,
                StatCollector.translateToLocal(hover ? "ic2tweaks.jetpack.hover.on" : "ic2tweaks.jetpack.hover.off"));
            return null;
        }
    }
}
