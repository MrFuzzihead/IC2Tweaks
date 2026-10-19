package ic2.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mrfuzzihead.ic2tweaks.enet.packet.PacketGraph;
import com.mrfuzzihead.ic2tweaks.enet.packet.PacketInjectionHandler;
import com.mrfuzzihead.ic2tweaks.enet.packet.PacketInputs;
import com.mrfuzzihead.ic2tweaks.enet.packet.PacketPath;
import com.mrfuzzihead.ic2tweaks.enet.packet.PacketTickResult;

import ic2.api.energy.tile.IEnergyConductor;
import ic2.api.energy.tile.IEnergySink;
import ic2.api.energy.tile.IEnergySource;
import ic2.core.init.MainConfig;
import ic2.core.util.Config;

/**
 * Exercises the live adapter's graph build with real IC2 {@code Grid}/{@code Tile}/{@code Node} objects and
 * fake tiles, without a running game. This is the layer directly above the pure packet calculator: it
 * checks node/tile/link mapping, 1.7.10 loss values, source draw, sink injection and the per-path effect
 * decisions.
 */
class Ic2TweaksPacketModelTest {

    @BeforeAll
    static void initializeIc2Config() throws Exception {
        // EnergyNetLocal's static initializer reads MainConfig; unit tests have no IC2 preInit.
        Config config = new Config("ic2tweaks-test");
        config.set("misc/useLinearTransferModel", Boolean.FALSE);
        Field field = MainConfig.class.getDeclaredField("config");
        field.setAccessible(true);
        field.set(null, config);
    }

    @Test
    void mapsARealGridIntoThePacketModel() {
        EnergyNetLocal energyNet = new EnergyNetLocal();
        FakeSource sourceTileEntity = place(new FakeSource(), 0, 64, 0);
        FakeConductor cableTileEntity = place(new FakeConductor(), 1, 64, 0);
        FakeSink sinkTileEntity = place(new FakeSink(), 2, 64, 0);

        Tile sourceTile = new Tile(energyNet, sourceTileEntity);
        Tile cableTile = new Tile(energyNet, cableTileEntity);
        Tile sinkTile = new Tile(energyNet, sinkTileEntity);
        Node source = sourceTile.nodes.get(0);
        Node cable = cableTile.nodes.get(0);
        Node sink = sinkTile.nodes.get(0);

        // Grid.add() would drag in IC2's EJML-backed solver; the packet model only needs nodes + links.
        link(source, cable, 0.3D);
        link(cable, sink, 0.3D);

        Map<Integer, Node> rawNodes = new HashMap<>();
        for (Tile tile : Arrays.asList(sourceTile, cableTile, sinkTile)) {
            for (Node node : tile.nodes) {
                rawNodes.put(node.uid, node);
            }
        }

        Ic2TweaksPacketModel.GridState state = new Ic2TweaksPacketModel.GridState(rawNodes);

        assertEquals(3, state.graph.nodeCount());
        assertEquals(1, state.sourceNodes.size());
        assertEquals(1, state.sinkNodes.size());
        assertEquals(PacketGraph.CONDUCTOR, state.graph.type(conductorNode(state)));

        List<PacketPath> paths = state.model.sourcePaths(state.sourceNodes.get(0));
        assertEquals(1, paths.size());
        // 1.7.10 inner losses: source/sink 0.4, copper 0.2 -> (0.3 + 0.3) path loss
        assertEquals(0.6D, paths.get(0).loss, 1e-9D);

        PacketInputs inputs = new PacketInputs(state.graph.nodeCount());
        inputs.setSource(state.sourceNodes.get(0), 32.0D, 1);
        inputs.setSink(state.sinkNodes.get(0), 1000.0D, 1);
        PacketInjectionHandler handler = new PacketInjectionHandler() {

            @Override
            public double inject(PacketPath path, double amount, int tier) {
                return sinkTileEntity.injectEnergy(ForgeDirection.UNKNOWN, amount, tier);
            }

            @Override
            public void draw(int sourceNode, double amount) {
                sourceTileEntity.drawEnergy(amount);
            }
        };

        PacketTickResult result = state.model.tick(inputs, handler, new Random(0), false);

        assertEquals(32.0D, sourceTileEntity.drawn, 1e-9D);
        assertEquals(31.4D, sinkTileEntity.injected, 1e-9D);
        assertTrue(result.conductorsToRemove.isEmpty());
        assertTrue(result.sinksToExplode.isEmpty());
        // 32 effective - 5 absorption = 27 shock energy on the cable's path
        assertEquals(1, result.shockContributions.size());
        assertEquals(27, result.shockContributions.get(0).energy);
        assertFalse(cableTileEntity.removed);
    }

    private static void link(Node a, Node b, double loss) {
        NodeLink link = new NodeLink(a, b, loss);
        a.links.add(link);
        b.links.add(link);
    }

    private static int conductorNode(Ic2TweaksPacketModel.GridState state) {
        for (int node = 0; node < state.graph.nodeCount(); node++) {
            if (state.graph.type(node) != PacketGraph.SOURCE && state.graph.type(node) != PacketGraph.SINK) {
                return node;
            }
        }
        return -1;
    }

    private static <T extends TileEntity> T place(T tile, int x, int y, int z) {
        tile.xCoord = x;
        tile.yCoord = y;
        tile.zCoord = z;
        return tile;
    }

    private static final class FakeSource extends TileEntity implements IEnergySource {

        double drawn;

        @Override
        public double getOfferedEnergy() {
            return 32.0D;
        }

        @Override
        public void drawEnergy(double amount) {
            drawn += amount;
        }

        @Override
        public int getSourceTier() {
            return 1;
        }

        @Override
        public boolean emitsEnergyTo(TileEntity receiver, ForgeDirection direction) {
            return true;
        }
    }

    private static final class FakeSink extends TileEntity implements IEnergySink {

        double injected;

        @Override
        public double getDemandedEnergy() {
            return 1000.0D;
        }

        @Override
        public int getSinkTier() {
            return 1;
        }

        @Override
        public double injectEnergy(ForgeDirection directionFrom, double amount, double voltage) {
            injected += amount;
            return 0.0D;
        }

        @Override
        public boolean acceptsEnergyFrom(TileEntity emitter, ForgeDirection direction) {
            return true;
        }
    }

    private static final class FakeConductor extends TileEntity implements IEnergyConductor {

        boolean removed;

        @Override
        public double getConductionLoss() {
            return 0.2D;
        }

        @Override
        public double getInsulationEnergyAbsorption() {
            return 5.0D;
        }

        @Override
        public double getInsulationBreakdownEnergy() {
            return 9001.0D;
        }

        @Override
        public double getConductorBreakdownEnergy() {
            return 33.0D;
        }

        @Override
        public void removeInsulation() {}

        @Override
        public void removeConductor() {
            removed = true;
        }

        @Override
        public boolean emitsEnergyTo(TileEntity receiver, ForgeDirection direction) {
            return true;
        }

        @Override
        public boolean acceptsEnergyFrom(TileEntity emitter, ForgeDirection direction) {
            return true;
        }
    }
}
