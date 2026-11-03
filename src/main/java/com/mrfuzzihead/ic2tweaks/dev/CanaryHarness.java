package com.mrfuzzihead.ic2tweaks.dev;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.mojang.authlib.GameProfile;
import com.mrfuzzihead.ic2tweaks.IC2Tweaks;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackConfig;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackHandler;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackItem;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackPhysics;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackPhysics.Kind;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackPhysics.Mode;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackPhysics.Motion;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackRules;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackWorn;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import ic2.api.energy.EnergyNet;
import ic2.api.energy.NodeStats;
import ic2.api.energy.tile.IEnergySink;
import ic2.api.energy.tile.IEnergySource;
import ic2.api.item.ElectricItem;
import ic2.core.Ic2Items;
import ic2.core.block.wiring.TileEntityChargepadBlock;
import ic2.core.block.wiring.TileEntityElectricBlock;
import ic2.core.block.wiring.TileEntityTransformer;
import ic2.core.item.armor.ItemArmorJetpack;
import ic2.core.item.armor.ItemArmorQuantumSuit;
import ic2.core.util.StackUtil;

/**
 * Temporary Phase-3 canary harness. Enabled with {@code -Dic2tweaks.canary=true}; builds four IC2 rigs in
 * the overworld, lets the energy net tick, logs PASS/FAIL and stops the server. Not part of normal play.
 */
public final class CanaryHarness {

    private static final int Y = 160;
    private static final int WAIT_TICKS = 80;

    private final List<String> failures = new ArrayList<>();
    private World world;
    private int ticks;
    private boolean started;
    private boolean reported;
    private EntityCow shockCow;
    private EntityPlayer jetpackPlayer;
    private ItemStack jetpackStack;
    private double jetpackChargeBefore;
    private double jetpackChargeAfter;
    private EntityPlayer baublesPlayer;
    private double baublesChargeBefore;
    private double baublesChargeAfter;
    private EntityPlayer travellersGearPlayer;
    private double travellersGearChargeBefore;
    private double travellersGearChargeAfter;
    private EntityPlayer quantumPlayer;
    private ItemStack quantumStack;
    private double quantumChargeBefore;
    private double quantumChargeAfter;
    private EntityPlayer chargepadPlayer;
    private ItemStack chargepadStack;
    private double chargepadChargeBefore;
    private double chargepadChargeAfter;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.worldServerForDimension(0) == null) {
            return;
        }
        if (!started) {
            world = server.worldServerForDimension(0);
            buildRigs();
            started = true;
            return;
        }
        if (reported) {
            return;
        }
        if (++ticks < WAIT_TICKS) {
            return;
        }
        reported = true;
        report();
        server.initiateShutdown();
    }

    private void buildRigs() {
        // Clear the harness area so repeated runs start from a clean slate.
        for (int x = -2; x <= 3; x++) {
            for (int z = -1; z <= 26; z++) {
                world.setBlockToAir(x, Y, z);
            }
        }

        // 1. MFSU -> BatBox directly: over-voltage explodes the sink.
        TileEntity mfsu1 = place(Ic2Items.mfsUnit, 0, 0, ForgeDirection.EAST);
        TileEntity sink1 = place(Ic2Items.batBox, 1, 0, ForgeDirection.DOWN);
        charge(mfsu1, 1_000_000.0D, 3);

        // 2. MFSU -> copper cable -> BatBox: the cable acts as a fuse; the sink survives.
        TileEntity mfsu2 = place(Ic2Items.mfsUnit, 0, 6, ForgeDirection.EAST);
        TileEntity cable2 = place(Ic2Items.copperCableBlock, 1, 6);
        TileEntity sink2 = place(Ic2Items.batBox, 2, 6, ForgeDirection.DOWN);
        charge(mfsu2, 1_000_000.0D, 3);

        // 3. Two full BatBoxes -> one empty BatBox directly: two 32 EU/t packets, no explosion.
        TileEntity source3a = place(Ic2Items.batBox, -1, 12, ForgeDirection.EAST);
        TileEntity sink3 = place(Ic2Items.batBox, 0, 12, ForgeDirection.DOWN);
        TileEntity source3b = place(Ic2Items.batBox, 1, 12, ForgeDirection.WEST);
        charge(source3a, 40_000.0D, 1);
        charge(source3b, 40_000.0D, 1);

        // 4. Two full BatBoxes through one tin cable (breakdown 33) to one sink: per-path max, no melt.
        TileEntity source4a = place(Ic2Items.batBox, -1, 18, ForgeDirection.EAST);
        TileEntity cable4 = place(Ic2Items.tinCableBlock, 0, 18);
        TileEntity source4b = place(Ic2Items.batBox, 1, 18, ForgeDirection.WEST);
        TileEntity sink4 = place(Ic2Items.batBox, 0, 19, ForgeDirection.DOWN);
        charge(source4a, 40_000.0D, 1);
        charge(source4b, 40_000.0D, 1);

        // 5. MFE -> MV transformer (step-down) with two independent output grids: the transformer is a
        // multi-grid source; the ledger must share its 128 EU/t offer and the grid order must shuffle.
        TileEntity mfe5 = place(Ic2Items.mfeUnit, 0, 24, ForgeDirection.EAST);
        TileEntity transformer5 = place(Ic2Items.mvTransformer, 1, 24, ForgeDirection.WEST);
        TileEntity sink5a = place(Ic2Items.cesuUnit, 2, 24, ForgeDirection.DOWN);
        TileEntity sink5b = place(Ic2Items.cesuUnit, 1, 25, ForgeDirection.DOWN);
        charge(mfe5, 1_000_000.0D, 3);

        shockCow = new EntityCow(world);
        shockCow.setPosition(0.5D, Y, 18.5D);
        world.spawnEntityInWorld(shockCow);

        buildJetpackProbe();

        if (mfsu1 == null || sink1 == null
            || mfsu2 == null
            || cable2 == null
            || sink2 == null
            || source3a == null
            || sink3 == null
            || source3b == null
            || source4a == null
            || cable4 == null
            || source4b == null
            || sink4 == null) {
            failures.add("rig placement returned a null tile entity");
            IC2Tweaks.LOG.error("[canary] placement failed; see above");
        }
    }

    private void buildJetpackProbe() {
        try {
            jetpackStack = makeJetpack();
            jetpackPlayer = makePlayer("ic2tweaks_canary");
            jetpackPlayer.inventory.armorInventory[2] = jetpackStack;
            jetpackChargeBefore = charge(jetpackStack);

            // Tick the real player object directly. The handler must drain hover fuel and reset fall
            // distance; this exercises the mixin + handler, not just the pure maths.
            tick(jetpackPlayer, 10);
            jetpackChargeAfter = charge(jetpackStack);
        } catch (Exception e) {
            failures.add("jetpack probe: " + e);
            IC2Tweaks.LOG.error("[canary] jetpack probe failed", e);
        }
        buildBaublesProbe();
        buildTravellersGearProbe();
        buildQuantumProbe();
        buildChargepadProbe();
    }

    private void buildChargepadProbe() {
        if (!JetpackConfig.chargepadChargesBaubles || !Loader.isModLoaded("Baubles")) {
            return;
        }
        try {
            TileEntity tile = place(Ic2Items.ChargepadbatBox, 3, 25);
            if (!(tile instanceof TileEntityChargepadBlock)) {
                failures.add("chargepad probe: tile is " + tile);
                return;
            }
            TileEntityChargepadBlock pad = (TileEntityChargepadBlock) tile;
            pad.onLoaded();
            charge(pad, 100_000.0D, 1);

            chargepadStack = Ic2Items.electricJetpack.copy();
            chargepadPlayer = makePlayer("ic2tweaks_chargepad");
            IInventory inventory = (IInventory) Class.forName("baubles.api.BaublesApi")
                .getMethod("getBaubles", EntityPlayer.class)
                .invoke(null, chargepadPlayer);
            if (inventory == null) {
                failures.add("chargepad probe: baubles inventory is null");
                return;
            }
            inventory.setInventorySlotContents(3, chargepadStack);
            chargepadChargeBefore = JetpackItem.charge(chargepadStack);
            pad.playerstandsat(chargepadPlayer);
            for (int i = 0; i < 40; i++) {
                pad.updateEntity();
            }
            chargepadChargeAfter = JetpackItem.charge(chargepadStack);
        } catch (Exception e) {
            failures.add("chargepad probe: " + e);
            IC2Tweaks.LOG.error("[canary] chargepad probe failed", e);
        }
    }

    private void buildQuantumProbe() {
        if (!JetpackConfig.tweakQuantumJetpack) {
            return;
        }
        try {
            quantumStack = Ic2Items.quantumBodyarmor.copy();
            ElectricItem.manager.charge(quantumStack, 100_000.0D, 4, true, false);
            StackUtil.getOrCreateNbtData(quantumStack)
                .setBoolean("jetpack", true);
            StackUtil.getOrCreateNbtData(quantumStack)
                .setBoolean("hoverMode", true);
            quantumPlayer = makePlayer("ic2tweaks_quantum");
            quantumPlayer.inventory.armorInventory[2] = quantumStack;
            quantumChargeBefore = JetpackItem.charge(quantumStack);
            JetpackHandler handler = new JetpackHandler();
            TickEvent.PlayerTickEvent event = new TickEvent.PlayerTickEvent(TickEvent.Phase.START, quantumPlayer);
            for (int i = 0; i < 10; i++) {
                quantumPlayer.setPosition(6.5D, Y + 40.0D, 0.5D);
                quantumPlayer.motionY = 0.0D;
                quantumPlayer.onGround = false;
                handler.onPlayerTick(event);
            }
            quantumChargeAfter = JetpackItem.charge(quantumStack);
        } catch (Exception e) {
            failures.add("quantum probe: " + e);
            IC2Tweaks.LOG.error("[canary] quantum probe failed", e);
        }
    }

    private void buildBaublesProbe() {
        if (!Loader.isModLoaded("Baubles")) {
            return;
        }
        try {
            ItemStack stack = makeJetpack();
            baublesPlayer = makePlayer("ic2tweaks_baubles");
            IInventory inventory = (IInventory) Class.forName("baubles.api.BaublesApi")
                .getMethod("getBaubles", EntityPlayer.class)
                .invoke(null, baublesPlayer);
            if (inventory == null) {
                failures.add("baubles probe: inventory is null");
                return;
            }
            inventory.setInventorySlotContents(3, stack);
            baublesChargeBefore = charge(stack);
            tick(baublesPlayer, 10);
            baublesChargeAfter = charge(stack);
        } catch (Exception e) {
            failures.add("baubles probe: " + e);
            IC2Tweaks.LOG.error("[canary] baubles probe failed", e);
        }
    }

    private void buildTravellersGearProbe() {
        if (!Loader.isModLoaded("TravellersGear")) {
            return;
        }
        try {
            ItemStack stack = makeJetpack();
            travellersGearPlayer = makePlayer("ic2tweaks_tg");
            Class<?> api = Class.forName("travellersgear.api.TravellersGearAPI");
            ItemStack[] inventory = (ItemStack[]) api.getMethod("getExtendedInventory", EntityPlayer.class)
                .invoke(null, travellersGearPlayer);
            if (inventory == null || inventory.length == 0) {
                failures.add("travellersgear probe: inventory is null");
                return;
            }
            inventory[0] = stack;
            api.getMethod("setExtendedInventory", EntityPlayer.class, ItemStack[].class)
                .invoke(null, travellersGearPlayer, (Object) inventory);
            travellersGearChargeBefore = charge(stack);
            tick(travellersGearPlayer, 10);
            travellersGearChargeAfter = charge(stack);
        } catch (Exception e) {
            failures.add("travellersgear probe: " + e);
            IC2Tweaks.LOG.error("[canary] travellersgear probe failed", e);
        }
    }

    private CanaryPlayer makePlayer(String name) {
        return new CanaryPlayer(world, new GameProfile(UUID.randomUUID(), name));
    }

    private static ItemStack makeJetpack() {
        ItemStack stack = Ic2Items.jetpack.copy();
        ((ItemArmorJetpack) stack.getItem()).filltank(stack);
        StackUtil.getOrCreateNbtData(stack)
            .setBoolean("hoverMode", true);
        return stack;
    }

    private void tick(EntityPlayer player, int ticks) {
        for (int i = 0; i < ticks; i++) {
            player.setPosition(6.5D, Y + 40.0D, 0.5D);
            player.motionY = 0.0D;
            player.onGround = false;
            player.onUpdate();
        }
    }

    private static double charge(ItemStack stack) {
        return ((ItemArmorJetpack) stack.getItem()).getCharge(stack);
    }

    private static final class CanaryPlayer extends EntityPlayer {

        CanaryPlayer(World world, GameProfile profile) {
            super(world, profile);
        }

        @Override
        public boolean canCommandSenderUseCommand(int permissionLevel, String command) {
            return true;
        }

        @Override
        public void addChatMessage(IChatComponent component) {}

        @Override
        public ChunkCoordinates getPlayerCoordinates() {
            return new ChunkCoordinates(
                MathHelper.floor_double(posX),
                MathHelper.floor_double(posY),
                MathHelper.floor_double(posZ));
        }
    }

    private TileEntity place(ItemStack stack, int x, int z) {
        Block block = Block.getBlockFromItem(stack.getItem());
        world.setBlock(x, Y, z, block, stack.getItemDamage(), 3);
        return world.getTileEntity(x, Y, z);
    }

    private TileEntity place(ItemStack stack, int x, int z, ForgeDirection facing) {
        TileEntity tile = place(stack, x, z);
        if (tile instanceof TileEntityTransformer) {
            ((TileEntityTransformer) tile).setFacing((short) facing.ordinal());
        } else if (tile instanceof TileEntityElectricBlock) {
            ((TileEntityElectricBlock) tile).setFacing((short) facing.ordinal());
        }
        return tile;
    }

    private static void charge(TileEntity tile, double amount, int tier) {
        if (tile instanceof IEnergySink) {
            ((IEnergySink) tile).injectEnergy(ForgeDirection.UNKNOWN, amount, tier);
        }
    }

    private void report() {
        reportJetpackPhysics();
        logStorage("S3 source A", world.getTileEntity(-1, Y, 12));
        logStorage("S3 source B", world.getTileEntity(1, Y, 12));
        logStorage("S3 sink", world.getTileEntity(0, Y, 12));
        logStorage("S4 source A", world.getTileEntity(-1, Y, 18));
        logStorage("S4 source B", world.getTileEntity(1, Y, 18));
        logStorage("S4 sink", world.getTileEntity(0, Y, 19));
        logStorage("S5 MFE", world.getTileEntity(0, Y, 24));
        logStorage("S5 transformer", world.getTileEntity(1, Y, 24));
        logStorage("S5 CESU A", world.getTileEntity(2, Y, 24));
        logStorage("S5 CESU B", world.getTileEntity(1, Y, 25));

        check("direct MFSU -> LV sink explodes", world.isAirBlock(1, Y, 0), blockName(1, 0));
        check("copper cable on MFSU path melts", world.isAirBlock(1, Y, 6), blockName(1, 6));
        check(
            "melting cable suppresses sink explosion",
            !world.isAirBlock(2, Y, 6) && world.getTileEntity(2, Y, 6) != null,
            blockName(2, 6));
        check(
            "two 32 EU/t sources do not explode the LV sink",
            !world.isAirBlock(0, Y, 12) && world.getTileEntity(0, Y, 12) != null,
            blockName(0, 12));
        check("tin cable on two 32 EU/t paths does not melt", !world.isAirBlock(0, Y, 18), blockName(0, 18));
        check(
            "junction sink survives",
            !world.isAirBlock(0, Y, 19) && world.getTileEntity(0, Y, 19) != null,
            blockName(0, 19));
        check(
            "shock damages an entity near the overloaded tin cable",
            shockCow != null && (shockCow.isDead || shockCow.getHealth() < shockCow.getMaxHealth()),
            "health=" + (shockCow == null ? "null" : shockCow.getHealth()));

        if (EnergyNet.instance != null) {
            TileEntity sink3 = world.getTileEntity(0, Y, 12);
            if (sink3 != null) {
                NodeStats stats = EnergyNet.instance.getNodeStats(sink3);
                IC2Tweaks.LOG.info(
                    "[canary] two-source sink stats: in={} out={} voltage={}",
                    stats.getEnergyIn(),
                    stats.getEnergyOut(),
                    stats.getVoltage());
                check(
                    "two-source sink received both packets",
                    stats.getEnergyIn() > 60.0D && stats.getEnergyIn() < 64.5D,
                    "in=" + stats.getEnergyIn());
                check("packet stats report rated voltage", stats.getVoltage() == 32.0D, "v=" + stats.getVoltage());
            }

            TileEntity source3 = world.getTileEntity(-1, Y, 12);
            if (source3 != null) {
                NodeStats stats = EnergyNet.instance.getNodeStats(source3);
                IC2Tweaks.LOG.info(
                    "[canary] two-source storage stats: in={} out={} voltage={}",
                    stats.getEnergyIn(),
                    stats.getEnergyOut(),
                    stats.getVoltage());
                check(
                    "source storage reports out + rated voltage",
                    stats.getEnergyOut() > 30.0D && stats.getVoltage() == 32.0D,
                    "out=" + stats.getEnergyOut() + " v=" + stats.getVoltage());
            }

            TileEntity cable4 = world.getTileEntity(0, Y, 18);
            if (cable4 != null) {
                NodeStats stats = EnergyNet.instance.getNodeStats(cable4);
                IC2Tweaks.LOG.info(
                    "[canary] junction cable stats: in={} out={} voltage={}",
                    stats.getEnergyIn(),
                    stats.getEnergyOut(),
                    stats.getVoltage());
                check(
                    "junction cable carried both packets without melting",
                    stats.getEnergyIn() > 60.0D && stats.getEnergyIn() < 64.5D,
                    "in=" + stats.getEnergyIn());
            }
        }

        TileEntity cesuA = world.getTileEntity(2, Y, 24);
        TileEntity cesuB = world.getTileEntity(1, Y, 25);
        double energyA = cesuA instanceof TileEntityElectricBlock ? ((TileEntityElectricBlock) cesuA).energy : -1.0D;
        double energyB = cesuB instanceof TileEntityElectricBlock ? ((TileEntityElectricBlock) cesuB).energy : -1.0D;
        IC2Tweaks.LOG.info("[canary] multi-grid transformer sinks: A={} B={}", energyA, energyB);
        check(
            "multi-grid transformer feeds both output grids",
            energyA > 0.0D && energyB > 0.0D,
            "A=" + energyA + " B=" + energyB);

        if (failures.isEmpty()) {
            IC2Tweaks.LOG.info("[canary] RESULT: ALL PASS");
        } else {
            IC2Tweaks.LOG.error("[canary] RESULT: {} FAILURE(S): {}", failures.size(), failures);
        }
    }

    private void reportJetpackPhysics() {
        double gasNormal = JetpackPhysics
            .metersPerSecond(JetpackPhysics.stockForwardSpeed(Mode.NORMAL, Kind.GAS, 1.0D));
        double gasHover = JetpackPhysics.metersPerSecond(JetpackPhysics.stockForwardSpeed(Mode.HOVER, Kind.GAS, 1.0D));
        double electricNormal = JetpackPhysics
            .metersPerSecond(JetpackPhysics.stockForwardSpeed(Mode.NORMAL, Kind.ELECTRIC, 0.7D));
        double electricHover = JetpackPhysics
            .metersPerSecond(JetpackPhysics.stockForwardSpeed(Mode.HOVER, Kind.ELECTRIC, 0.7D));
        check("jetpack stock gas normal horizontal speed", approx(gasNormal, 0.533333D), "m/s=" + gasNormal);
        check("jetpack stock gas hover horizontal speed", approx(gasHover, 3.555556D), "m/s=" + gasHover);
        check(
            "jetpack stock electric normal horizontal speed",
            approx(electricNormal, 0.746667D),
            "m/s=" + electricNormal);
        check(
            "jetpack stock electric hover horizontal speed",
            approx(electricHover, 2.862222D),
            "m/s=" + electricHover);

        Motion motion = new Motion();
        JetpackPhysics.applyHorizontal(motion, 1.0D, 0.0D, 0.0D, 0.32D, 1.0D, 0.08D, 0.8D, 0.6D, 1.0D);
        check("jetpack configured horizontal target", approx(motion.z, 0.32D), "z=" + motion.z);
        check(
            "jetpack stock gas endurance",
            approx(JetpackPhysics.secondsOfFlight(30000.0D, 2), 750.0D),
            "seconds=" + JetpackPhysics.secondsOfFlight(30000.0D, 2));

        check(
            "jetpack hover idle on the ground does not thrust",
            !JetpackRules.shouldThrust(false, true, true),
            "hover\nonGround");
        check("jetpack jump on the ground thrusts", JetpackRules.shouldThrust(true, false, true), "jump\nonGround");
        check(
            "jetpack riding blocks the jetpack",
            !JetpackRules.canOperate(false, true, false, false, false, false, false),
            "riding");
        check("jetpack fuel boundary blocks thrust", !JetpackRules.canConsume(1.0D, 2), "charge=1 cost=2");
        check(
            "jetpack tick mixin applied",
            hasIc2TweaksMethod(ItemArmorJetpack.class, "ic2tweaks$jetpackTickReplaced"),
            "ItemArmorJetpack");
        check(
            "jetpack armor HUD mixin applied",
            hasIc2TweaksMethod(EntityPlayer.class, "ic2tweaks$armorHudMixinApplied"),
            "EntityPlayer");
        if (JetpackConfig.tweakQuantumJetpack) {
            check(
                "jetpack quantum mixin applied",
                hasIc2TweaksMethod(ItemArmorQuantumSuit.class, "ic2tweaks$quantumJetpackMixinApplied"),
                "ItemArmorQuantumSuit");
            if (quantumPlayer != null) {
                check(
                    "quantum chest wins over a bauble jetpack",
                    JetpackWorn.get(quantumPlayer) == quantumStack,
                    "resolver");
                double drained = quantumChargeBefore - quantumChargeAfter;
                check("quantum handler drains hover EU", drained >= 80.0D && drained <= 120.0D, "drained=" + drained);
            }
        }
        if (JetpackConfig.chargepadChargesBaubles) {
            check(
                "jetpack chargepad mixin applied",
                hasIc2TweaksMethod(TileEntityChargepadBlock.class, "ic2tweaks$chargepadMixinApplied"),
                "TileEntityChargepadBlock");
        }

        if (jetpackPlayer == null) {
            check("jetpack handler real-player probe", false, "player=null");
        } else {
            double drained = jetpackChargeBefore - jetpackChargeAfter;
            check(
                "jetpack handler drains hover fuel for a real player",
                drained >= 8.0D && drained <= 10.0D,
                "drained=" + drained);
            check(
                "jetpack handler keeps a real player's fall distance reset",
                jetpackPlayer.fallDistance == 0.0F,
                "fall=" + jetpackPlayer.fallDistance);
        }
        if (Loader.isModLoaded("Baubles")) {
            check(
                "jetpack bauble mixin applied",
                hasIc2TweaksMethod(ItemArmorJetpack.class, "ic2tweaks$baubleMixinApplied")
                    || hasIc2TweaksMethod(ItemArmorJetpack.class, "ic2tweaks$baubleExpandedMixinApplied"),
                "Baubles");
            if (jetpackStack != null
                && hasIc2TweaksMethod(ItemArmorJetpack.class, "ic2tweaks$baubleExpandedMixinApplied")) {
                try {
                    String[] types = (String[]) ItemArmorJetpack.class.getMethod("getBaubleTypes", ItemStack.class)
                        .invoke(jetpackStack.getItem(), jetpackStack);
                    check(
                        "jetpack exposes the wings bauble type",
                        java.util.Arrays.asList(types)
                            .contains("wings"),
                        java.util.Arrays.toString(types));
                } catch (Exception e) {
                    failures.add("jetpack bauble types: " + e);
                    IC2Tweaks.LOG.error("[canary] bauble types probe failed", e);
                }
            }
        }
        if (Loader.isModLoaded("TravellersGear")) {
            check(
                "jetpack travellersgear mixin applied",
                hasIc2TweaksMethod(ItemArmorJetpack.class, "ic2tweaks$travellersGearMixinApplied"),
                "TravellersGear");
        }
        if (chargepadPlayer != null) {
            double gained = chargepadChargeAfter - chargepadChargeBefore;
            check("chargepad charges a Baubles-slot electric jetpack", gained > 0.0D, "gained=" + gained);
        }
        if (baublesPlayer != null) {
            check(
                "jetpack resolver drains a Baubles-slot jetpack",
                baublesChargeBefore - baublesChargeAfter >= 8.0D,
                "drained=" + (baublesChargeBefore - baublesChargeAfter));
        }
        if (travellersGearPlayer != null) {
            check(
                "jetpack resolver drains a TravellersGear-slot jetpack",
                travellersGearChargeBefore - travellersGearChargeAfter >= 8.0D,
                "drained=" + (travellersGearChargeBefore - travellersGearChargeAfter));
        }
    }

    private static boolean hasIc2TweaksMethod(Class<?> clazz, String name) {
        try {
            clazz.getDeclaredMethod(name);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static boolean approx(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0E-5D;
    }

    private void logStorage(String name, TileEntity tile) {
        if (tile instanceof TileEntityElectricBlock) {
            TileEntityElectricBlock storage = (TileEntityElectricBlock) tile;
            double offered = tile instanceof IEnergySource ? ((IEnergySource) tile).getOfferedEnergy() : -1.0D;
            double demanded = tile instanceof IEnergySink ? ((IEnergySink) tile).getDemandedEnergy() : -1.0D;
            IC2Tweaks.LOG
                .info("[canary] {}: energy={} offered={} demanded={}", name, storage.energy, offered, demanded);
        } else if (tile != null) {
            IC2Tweaks.LOG.info(
                "[canary] {}: {}",
                name,
                tile.getClass()
                    .getSimpleName());
        } else {
            IC2Tweaks.LOG.info("[canary] {}: null", name);
        }
    }

    private void check(String name, boolean passed, String detail) {
        if (passed) {
            IC2Tweaks.LOG.info("[canary] PASS {} ({})", name, detail);
        } else {
            failures.add(name);
            IC2Tweaks.LOG.error("[canary] FAIL {} ({})", name, detail);
        }
    }

    private String blockName(int x, int z) {
        Block block = world.getBlock(x, Y, z);
        return String.valueOf(Block.blockRegistry.getNameForObject(block)) + " meta=" + world.getBlockMetadata(x, Y, z);
    }
}
