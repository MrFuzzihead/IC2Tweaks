package com.mrfuzzihead.ic2tweaks.jetpack.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderPlayerEvent;

import org.lwjgl.opengl.GL11;

import com.mrfuzzihead.ic2tweaks.jetpack.JetpackConfig;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackWorn;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Draws a jetpack on the player's back when it is worn in a Baubles/TravellersGear slot. The chest slot is
 * skipped because vanilla armour rendering already draws the item's texture there.
 * <p>
 * This class references client-only types and is only instantiated when
 * {@code FMLCommonHandler.getSide().isClient()} in {@code IC2Tweaks.preInit}.
 */
public final class JetpackRenderer {

    private static final ResourceLocation TEXTURE = new ResourceLocation("ic2", "textures/armor/jetpack_1.png");

    private final ModelJetpack model = new ModelJetpack();

    @SubscribeEvent
    public void onRenderPlayer(RenderPlayerEvent.Specials.Post event) {
        if (!JetpackConfig.enabled || !JetpackConfig.renderWornJetpack) {
            return;
        }
        EntityPlayer player = event.entityPlayer;
        if (player.isInvisible()) {
            return;
        }
        ItemStack stack = JetpackWorn.get(player);
        if (stack == null || JetpackWorn.isChestSlot(player, stack)) {
            return;
        }

        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(TEXTURE);

        ModelBiped main = event.renderer.modelBipedMain;
        model.isSneak = main.isSneak;
        model.isRiding = main.isRiding;
        model.isChild = main.isChild;
        model.aimedBow = main.aimedBow;
        model.heldItemRight = main.heldItemRight;
        model.heldItemLeft = main.heldItemLeft;

        float partial = event.partialRenderTick;
        float limbSwing = player.limbSwing - player.limbSwingAmount * (1.0F - partial);
        float limbSwingAmount = player.prevLimbSwingAmount
            + (player.limbSwingAmount - player.prevLimbSwingAmount) * partial;
        float age = player.ticksExisted + partial;
        float headYaw = (player.prevRotationYawHead + (player.rotationYawHead - player.prevRotationYawHead) * partial)
            - (player.prevRenderYawOffset + (player.renderYawOffset - player.prevRenderYawOffset) * partial);
        float headPitch = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partial;

        GL11.glPushMatrix();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        model.render(player, limbSwing, limbSwingAmount, age, headYaw, headPitch, 0.0625F);
        GL11.glPopMatrix();
    }
}
