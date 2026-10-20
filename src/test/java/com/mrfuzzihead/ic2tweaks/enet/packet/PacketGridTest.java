package com.mrfuzzihead.ic2tweaks.enet.packet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * Behavioural tests for the pure packet calculator: path discovery, demand caps, rejection, loss
 * accounting, event-path gating, melt/strip/shock/explosion decisions and suppression.
 */
class PacketGridTest {

    @Test
    void picksLowestLossRoute() {
        PacketGraph.Builder builder = PacketGraph.builder();
        int source = builder.source(0);
        int high = builder.conductor(1, 1000.0D, 9001.0D);
        int low = builder.conductor(2, 1000.0D, 9001.0D);
        int sink = builder.sink(3);
        builder.link(source, high, 0.4D);
        builder.link(high, sink, 0.4D);
        builder.link(source, low, 0.1D);
        builder.link(low, sink, 0.1D);

        PacketGrid grid = new PacketGrid(builder.build(), false);
        grid.rebuild();

        List<PacketPath> paths = grid.sourcePaths(source);
        assertEquals(1, paths.size());
        assertEquals(0.2D, paths.get(0).loss, 1e-9D);
        assertArrayEquals(new int[] { low }, paths.get(0).conductors);
    }

    @Test
    void directPathHasNoConductors() {
        PacketGraph.Builder builder = PacketGraph.builder();
        int source = builder.source(0);
        int sink = builder.sink(1);
        builder.link(source, sink, 0.0D);

        PacketGrid grid = new PacketGrid(builder.build(), false);
        grid.rebuild();

        PacketPath path = grid.sourcePaths(source)
            .get(0);
        assertEquals(0.0D, path.loss, 1e-9D);
        assertEquals(0, path.conductors.length);
        assertTrue(Double.isInfinite(path.minEffectEnergy));
    }

    @Test
    void deliversLossAdjustedPacketAndDrawsSource() {
        PacketGraph graph = line(0.1D, 0.1D, 1000.0D, 9001.0D);
        PacketGrid grid = new PacketGrid(graph, false);
        grid.rebuild();
        PacketInputs inputs = new PacketInputs(3);
        inputs.setSource(0, 32.0D, 1);
        inputs.setSink(2, 1000.0D, 1);
        Recorder recorder = new Recorder();

        PacketTickResult result = grid.tick(inputs, recorder, new Random(0), false);

        assertTrue(result.isEmpty());
        assertEquals(1, recorder.injections.size());
        assertEquals(31.8D, recorder.injections.get(0).amount, 1e-9D);
        assertEquals(1, recorder.injections.get(0).tier);
        assertEquals(1, recorder.draws.size());
        assertEquals(32.0D, recorder.draws.get(0), 1e-9D);
        assertEquals(0.0D, inputs.sourceOffer[0], 1e-9D);

        PacketPath path = grid.sourcePaths(0)
            .get(0);
        assertEquals(31.8D, path.energySupplied(), 1e-9D);
        assertEquals(32.0D, path.maxPacketConducted(), 1e-9D);
    }

    @Test
    void demandCapLeavesRemainderWithSource() {
        PacketGraph graph = line(0.1D, 0.1D, 1000.0D, 9001.0D);
        PacketGrid grid = new PacketGrid(graph, false);
        grid.rebuild();
        PacketInputs inputs = new PacketInputs(3);
        inputs.setSource(0, 32.0D, 1);
        inputs.setSink(2, 10.0D, 1);
        Recorder recorder = new Recorder();

        grid.tick(inputs, recorder, new Random(0), false);

        assertEquals(1, recorder.injections.size());
        assertEquals(10.0D, recorder.injections.get(0).amount, 1e-9D);
        assertEquals(
            10.0D,
            grid.sourcePaths(0)
                .get(0)
                .energySupplied(),
            1e-9D);
        assertEquals(10.2D, recorder.draws.get(0), 1e-9D);
        assertEquals(21.8D, inputs.sourceOffer[0], 1e-9D);
    }

    @Test
    void fullRejectionDeliversNothingAndDrawsNothing() {
        PacketGraph graph = line(0.1D, 0.1D, 1000.0D, 9001.0D);
        PacketGrid grid = new PacketGrid(graph, false);
        grid.rebuild();
        PacketInputs inputs = new PacketInputs(3);
        inputs.setSource(0, 32.0D, 1);
        inputs.setSink(2, 1000.0D, 1);
        Recorder recorder = new Recorder();
        recorder.mode = Mode.REJECT_ALL;

        PacketTickResult result = grid.tick(inputs, recorder, new Random(0), false);

        assertTrue(result.isEmpty());
        assertEquals(1, recorder.injections.size());
        assertEquals(31.8D, recorder.injections.get(0).amount, 1e-9D);
        assertTrue(recorder.draws.isEmpty());
        assertEquals(32.0D, inputs.sourceOffer[0], 1e-9D);
        assertEquals(
            0.0D,
            grid.sourcePaths(0)
                .get(0)
                .energySupplied(),
            1e-9D);
    }

    @Test
    void partialRejectionAccountsForAcceptedAmount() {
        PacketGraph graph = line(0.1D, 0.1D, 1000.0D, 9001.0D);
        PacketGrid grid = new PacketGrid(graph, false);
        grid.rebuild();
        PacketInputs inputs = new PacketInputs(3);
        inputs.setSource(0, 32.0D, 1);
        inputs.setSink(2, 1000.0D, 1);
        Recorder recorder = new Recorder();
        recorder.mode = Mode.PARTIAL;
        recorder.partialRejection = 5.0D;

        grid.tick(inputs, recorder, new Random(0), false);

        assertEquals(27.0D, recorder.draws.get(0), 1e-9D);
        assertEquals(5.0D, inputs.sourceOffer[0], 1e-9D);
        assertEquals(
            26.8D,
            grid.sourcePaths(0)
                .get(0)
                .energySupplied(),
            1e-9D);
        assertEquals(
            27.0D,
            grid.sourcePaths(0)
                .get(0)
                .maxPacketConducted(),
            1e-9D);
    }

    @Test
    void meltsShocksAndSuppressesExplosion() {
        PacketGraph graph = line(0.1D, 0.1D, 33.0D, 5.0D);
        PacketGrid grid = new PacketGrid(graph, false);
        grid.rebuild();
        PacketInputs inputs = new PacketInputs(3);
        inputs.setSource(0, 64.0D, 1);
        inputs.setSink(2, 1000.0D, 1);
        Recorder recorder = new Recorder();

        PacketTickResult result = grid.tick(inputs, recorder, new Random(0), false);

        assertTrue(result.conductorsToRemove.contains(1));
        assertTrue(result.conductorsToStrip.isEmpty());
        assertTrue(result.sinksToExplode.isEmpty());
        assertEquals(1, result.shockContributions.size());
        assertEquals(1, result.shockContributions.get(0).conductorNode);
        assertEquals(59, result.shockContributions.get(0).energy);
        assertEquals(
            64.0D,
            grid.sourcePaths(0)
                .get(0)
                .maxPacketConducted(),
            1e-9D);
    }

    @Test
    void explodesSinkWhenNoConductorOverloads() {
        PacketGraph graph = line(0.1D, 0.1D, 1000.0D, 9001.0D);
        PacketGrid grid = new PacketGrid(graph, false);
        grid.rebuild();
        PacketInputs inputs = new PacketInputs(3);
        inputs.setSource(0, 64.0D, 1);
        inputs.setSink(2, 1000.0D, 1);
        Recorder recorder = new Recorder();

        PacketTickResult result = grid.tick(inputs, recorder, new Random(0), false);

        assertTrue(result.conductorsToRemove.isEmpty());
        assertTrue(result.conductorsToStrip.isEmpty());
        assertTrue(result.shockContributions.isEmpty());
        assertEquals(64.0D, result.sinksToExplode.get(2), 1e-9D);
    }

    @Test
    void stripsInsulationWhenOnlyInsulationBreaks() {
        PacketGraph.Builder builder = PacketGraph.builder();
        int source = builder.source(0);
        int conductor = builder.conductor(1, 1000.0D, 40.0D, 9001.0D, 0, 0, 0, 0, 0, 0);
        int sink = builder.sink(2);
        builder.link(source, conductor, 0.1D);
        builder.link(conductor, sink, 0.1D);

        PacketGrid grid = new PacketGrid(builder.build(), false);
        grid.rebuild();
        PacketInputs inputs = new PacketInputs(3);
        inputs.setSource(0, 64.0D, 1);
        inputs.setSink(2, 1000.0D, 1);

        PacketTickResult result = grid.tick(inputs, new Recorder(), new Random(0), false);

        assertTrue(result.conductorsToRemove.isEmpty());
        assertTrue(result.conductorsToStrip.contains(conductor));
        assertTrue(result.sinksToExplode.isEmpty());
    }

    @Test
    void suppressionIsPerPath() {
        PacketGraph.Builder builder = PacketGraph.builder();
        int sourceA = builder.source(0);
        int sourceB = builder.source(1);
        int cableA = builder.conductor(2, 33.0D, 9001.0D);
        int cableB = builder.conductor(3, 1000.0D, 9001.0D);
        int sink = builder.sink(4);
        builder.link(sourceA, cableA, 0.1D);
        builder.link(cableA, sink, 0.1D);
        builder.link(sourceB, cableB, 0.1D);
        builder.link(cableB, sink, 0.1D);

        PacketGrid grid = new PacketGrid(builder.build(), false);
        grid.rebuild();
        PacketInputs inputs = new PacketInputs(5);
        inputs.setSource(sourceA, 64.0D, 1);
        inputs.setSource(sourceB, 128.0D, 3);
        inputs.setSink(sink, 10000.0D, 1);
        Recorder recorder = new Recorder();

        PacketTickResult result = grid.tick(inputs, recorder, new Random(0), false);

        assertEquals(2, recorder.injections.size());
        assertTrue(result.conductorsToRemove.contains(cableA));
        assertFalse(result.conductorsToRemove.contains(cableB));
        assertEquals(128.0D, result.sinksToExplode.get(4), 1e-9D);
    }

    @Test
    void multiPacketSpreadsAcrossSinks() {
        PacketGraph.Builder builder = PacketGraph.builder();
        int source = builder.source(0);
        int sinkA = builder.sink(1);
        int sinkB = builder.sink(2);
        builder.link(source, sinkA, 0.2D);
        builder.link(source, sinkB, 0.2D);

        PacketGrid grid = new PacketGrid(builder.build(), false);
        grid.rebuild();
        PacketInputs inputs = new PacketInputs(3);
        inputs.setSource(source, 64.0D, 1, 2);
        inputs.setSink(sinkA, 10.0D, 1);
        inputs.setSink(sinkB, 10.0D, 1);
        Recorder recorder = new Recorder();

        grid.tick(inputs, recorder, new Random(0), false);

        assertEquals(2, recorder.injections.size());
        assertEquals(10.0D, recorder.injections.get(0).amount, 1e-9D);
        assertEquals(10.0D, recorder.injections.get(1).amount, 1e-9D);
        assertEquals(20.4D, recorder.draws.get(0), 1e-9D);
        assertEquals(43.6D, inputs.sourceOffer[source], 1e-9D);
    }

    @Test
    void nodeStatsFollowThePaths() {
        PacketGraph graph = line(0.1D, 0.1D, 1000.0D, 9001.0D);
        PacketGrid grid = new PacketGrid(graph, false);
        grid.rebuild();
        PacketInputs inputs = new PacketInputs(3);
        inputs.setSource(0, 32.0D, 1);
        inputs.setSink(2, 1000.0D, 1);

        grid.tick(inputs, new Recorder(), new Random(0), false);

        PacketNodeStats source = grid.nodeStats(0);
        assertEquals(0.0D, source.energyIn, 1e-9D);
        assertEquals(31.8D, source.energyOut, 1e-9D);
        assertEquals(32.0D, source.maxPacket, 1e-9D);
        assertEquals(1, source.tier);

        PacketNodeStats sink = grid.nodeStats(2);
        assertEquals(31.8D, sink.energyIn, 1e-9D);
        assertEquals(0.0D, sink.energyOut, 1e-9D);

        PacketNodeStats conductor = grid.nodeStats(1);
        assertEquals(31.8D, conductor.energyIn, 1e-9D);
        assertEquals(31.8D, conductor.energyOut, 1e-9D);
    }

    @Test
    void roundLossDownFloorsPathLoss() {
        PacketGraph graph = line(0.7D, 0.7D, 1000.0D, 9001.0D);
        PacketGrid grid = new PacketGrid(graph, true);
        grid.rebuild();
        PacketInputs inputs = new PacketInputs(3);
        inputs.setSource(0, 32.0D, 1);
        inputs.setSink(2, 1000.0D, 1);
        Recorder recorder = new Recorder();

        grid.tick(inputs, recorder, new Random(0), false);

        assertEquals(
            1.0D,
            grid.sourcePaths(0)
                .get(0).loss,
            1e-9D);
        assertEquals(31.0D, recorder.injections.get(0).amount, 1e-9D);
        assertEquals(32.0D, recorder.draws.get(0), 1e-9D);
    }

    @Test
    void sourceWithoutSinkIsInactive() {
        PacketGraph.Builder builder = PacketGraph.builder();
        int source = builder.source(0);

        PacketGrid grid = new PacketGrid(builder.build(), false);
        grid.rebuild();

        assertFalse(grid.isActive());
        PacketInputs inputs = new PacketInputs(1);
        inputs.setSource(source, 32.0D, 1);
        assertTrue(
            grid.tick(inputs, new Recorder(), new Random(0), false)
                .isEmpty());
    }

    @Test
    void sourceToSourceCompositionMatches1212() {
        // B is solved first (lower node id); A's Dijkstra then composes B's path to the sink.
        PacketGraph.Builder builder = PacketGraph.builder();
        int sourceB = builder.source(0);
        int sourceA = builder.source(1);
        int conductor = builder.conductor(2, 1000.0D, 9001.0D);
        int sink = builder.sink(3);
        builder.link(sourceA, conductor, 0.1D);
        builder.link(conductor, sourceB, 0.1D);
        builder.link(sourceB, sink, 0.1D);

        PacketGrid grid = new PacketGrid(builder.build(), false);
        grid.rebuild();

        List<PacketPath> composed = grid.sourcePaths(sourceA);
        assertEquals(1, composed.size());
        // 1.12.2 subtracts the composing link (so the loss is A-C + B-sink, not A-C + C-B + B-sink)
        // and its reconstructPath excludes the destination node, so the junction conductor C is not
        // included in the composed conductor list. Both quirks are replicated verbatim; see §2.5.
        assertEquals(0.1D, composed.get(0).loss, 1e-9D);
        assertEquals(0, composed.get(0).conductors.length);
        assertEquals(
            0.1D,
            grid.sourcePaths(sourceB)
                .get(0).loss,
            1e-9D);
    }

    @Test
    void twoSourcesDoNotAggregateForExplosion() {
        PacketGraph.Builder builder = PacketGraph.builder();
        int sourceA = builder.source(0);
        int sourceB = builder.source(1);
        int sink = builder.sink(2);
        builder.link(sourceA, sink, 0.2D);
        builder.link(sourceB, sink, 0.2D);

        PacketGrid grid = new PacketGrid(builder.build(), false);
        grid.rebuild();
        PacketInputs inputs = new PacketInputs(3);
        inputs.setSource(sourceA, 32.0D, 1);
        inputs.setSource(sourceB, 32.0D, 1);
        inputs.setSink(sink, 1000.0D, 1);
        Recorder recorder = new Recorder();

        PacketTickResult result = grid.tick(inputs, recorder, new Random(0), false);

        // Two 32 EU/t packets reach the same tier-1 sink; 64 aggregated would explode, 32 per packet must not.
        assertEquals(2, recorder.injections.size());
        assertTrue(result.sinksToExplode.isEmpty());
    }

    @Test
    void cableOnTwoPathsUsesMaxNotSum() {
        PacketGraph.Builder builder = PacketGraph.builder();
        int sourceA = builder.source(0);
        int sourceB = builder.source(1);
        int cable = builder.conductor(2, 33.0D, 5.0D);
        int sinkA = builder.sink(3);
        int sinkB = builder.sink(4);
        builder.link(sourceA, cable, 0.1D);
        builder.link(cable, sinkA, 0.1D);
        builder.link(sourceB, cable, 0.1D);
        builder.link(cable, sinkB, 0.1D);

        PacketGrid grid = new PacketGrid(builder.build(), false);
        grid.rebuild();
        PacketInputs inputs = new PacketInputs(5);
        inputs.setSource(sourceA, 32.0D, 1);
        inputs.setSource(sourceB, 32.0D, 1);
        inputs.setSink(sinkA, 1000.0D, 1);
        inputs.setSink(sinkB, 1000.0D, 1);
        Recorder recorder = new Recorder();

        PacketTickResult result = grid.tick(inputs, recorder, new Random(0), false);

        // Both sources' packets traverse the same 33-breakdown cable; each path sees 32, not the 64 sum.
        assertTrue(result.conductorsToRemove.isEmpty());
        assertTrue(result.sinksToExplode.isEmpty());
        assertEquals(2, result.shockContributions.size());
        assertEquals(27, result.shockContributions.get(0).energy);
        assertEquals(27, result.shockContributions.get(1).energy);
    }

    private static PacketGraph line(double firstLoss, double secondLoss, double breakdown, double absorption) {
        PacketGraph.Builder builder = PacketGraph.builder();
        int source = builder.source(0);
        int conductor = builder.conductor(1, breakdown, absorption);
        int sink = builder.sink(2);
        builder.link(source, conductor, firstLoss);
        builder.link(conductor, sink, secondLoss);
        return builder.build();
    }

    private enum Mode {
        ACCEPT,
        REJECT_ALL,
        PARTIAL
    }

    private static final class Injection {

        final int target;
        final double amount;
        final int tier;

        Injection(int target, double amount, int tier) {
            this.target = target;
            this.amount = amount;
            this.tier = tier;
        }
    }

    private static final class Recorder implements PacketInjectionHandler {

        final List<Injection> injections = new ArrayList<>();
        final List<Double> draws = new ArrayList<>();
        Mode mode = Mode.ACCEPT;
        double partialRejection;

        @Override
        public double inject(PacketPath path, double amount, int tier) {
            injections.add(new Injection(path.target, amount, tier));
            if (mode == Mode.REJECT_ALL) {
                return amount;
            }
            if (mode == Mode.PARTIAL) {
                return partialRejection;
            }
            return 0.0D;
        }

        @Override
        public void draw(int sourceNode, double amount) {
            draws.add(amount);
        }
    }
}
