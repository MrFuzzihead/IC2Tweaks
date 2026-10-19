package com.mrfuzzihead.ic2tweaks.enet.packet;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable graph snapshot for the packet model. The adapter (Phase 2) builds one from an IC2
 * {@code Grid} on every topology change; the calculator only ever reads it.
 * <p>
 * Nodes are dense {@code int} ids. Links are undirected and carry the loss IC2's {@code NodeLink}
 * carries (the mean of the two endpoints' inner losses). Conductor nodes additionally carry the three
 * thresholds the effects pass needs, plus their covered block volume.
 */
public final class PacketGraph {

    public static final int SOURCE = 1;
    public static final int SINK = 2;
    public static final int CONDUCTOR = 3;

    private final int nodeCount;
    private final int[] type;
    private final int[] tileId;
    private final double[] conductorBreakdown;
    private final double[] insulationBreakdown;
    private final double[] insulationAbsorption;
    private final int[] minX;
    private final int[] minY;
    private final int[] minZ;
    private final int[] maxX;
    private final int[] maxY;
    private final int[] maxZ;
    private final int[] adjacencyStart;
    private final int[] adjacencyNode;
    private final double[] adjacencyLoss;

    private PacketGraph(Builder builder) {
        this.nodeCount = builder.types.size();
        this.type = new int[nodeCount];
        this.tileId = new int[nodeCount];
        this.conductorBreakdown = new double[nodeCount];
        this.insulationBreakdown = new double[nodeCount];
        this.insulationAbsorption = new double[nodeCount];
        this.minX = new int[nodeCount];
        this.minY = new int[nodeCount];
        this.minZ = new int[nodeCount];
        this.maxX = new int[nodeCount];
        this.maxY = new int[nodeCount];
        this.maxZ = new int[nodeCount];

        for (int i = 0; i < nodeCount; i++) {
            NodeDefinition node = builder.types.get(i);
            type[i] = node.type;
            tileId[i] = node.tileId;
            conductorBreakdown[i] = node.conductorBreakdown;
            insulationBreakdown[i] = node.insulationBreakdown;
            insulationAbsorption[i] = node.insulationAbsorption;
            minX[i] = node.minX;
            minY[i] = node.minY;
            minZ[i] = node.minZ;
            maxX[i] = node.maxX;
            maxY[i] = node.maxY;
            maxZ[i] = node.maxZ;
        }

        int[] degree = new int[nodeCount];
        for (LinkDefinition link : builder.links) {
            degree[link.a]++;
            degree[link.b]++;
        }
        this.adjacencyStart = new int[nodeCount + 1];
        for (int i = 0; i < nodeCount; i++) {
            adjacencyStart[i + 1] = adjacencyStart[i] + degree[i];
        }
        this.adjacencyNode = new int[adjacencyStart[nodeCount]];
        this.adjacencyLoss = new double[adjacencyStart[nodeCount]];
        int[] cursor = adjacencyStart.clone();
        for (LinkDefinition link : builder.links) {
            adjacencyNode[cursor[link.a]] = link.b;
            adjacencyLoss[cursor[link.a]++] = link.loss;
            adjacencyNode[cursor[link.b]] = link.a;
            adjacencyLoss[cursor[link.b]++] = link.loss;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public int nodeCount() {
        return nodeCount;
    }

    public int type(int node) {
        return type[node];
    }

    public int tileId(int node) {
        return tileId[node];
    }

    public double conductorBreakdown(int node) {
        return conductorBreakdown[node];
    }

    public double insulationBreakdown(int node) {
        return insulationBreakdown[node];
    }

    public double insulationAbsorption(int node) {
        return insulationAbsorption[node];
    }

    public int minX(int node) {
        return minX[node];
    }

    public int minY(int node) {
        return minY[node];
    }

    public int minZ(int node) {
        return minZ[node];
    }

    public int maxX(int node) {
        return maxX[node];
    }

    public int maxY(int node) {
        return maxY[node];
    }

    public int maxZ(int node) {
        return maxZ[node];
    }

    public int neighborStart(int node) {
        return adjacencyStart[node];
    }

    public int neighborEnd(int node) {
        return adjacencyStart[node + 1];
    }

    public int neighbor(int node, int index) {
        return adjacencyNode[index];
    }

    public double linkLoss(int index) {
        return adjacencyLoss[index];
    }

    private static final class NodeDefinition {

        final int type;
        final int tileId;
        final double conductorBreakdown;
        final double insulationBreakdown;
        final double insulationAbsorption;
        final int minX;
        final int minY;
        final int minZ;
        final int maxX;
        final int maxY;
        final int maxZ;

        NodeDefinition(int type, int tileId, double conductorBreakdown, double insulationBreakdown,
            double insulationAbsorption, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.type = type;
            this.tileId = tileId;
            this.conductorBreakdown = conductorBreakdown;
            this.insulationBreakdown = insulationBreakdown;
            this.insulationAbsorption = insulationAbsorption;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }
    }

    private static final class LinkDefinition {

        final int a;
        final int b;
        final double loss;

        LinkDefinition(int a, int b, double loss) {
            this.a = a;
            this.b = b;
            this.loss = loss;
        }
    }

    /** Mutable builder; not thread safe. */
    public static final class Builder {

        private final List<NodeDefinition> types = new ArrayList<>();
        private final List<LinkDefinition> links = new ArrayList<>();

        private Builder() {}

        public int source(int tileId) {
            return add(SOURCE, tileId, 0.0D, 0.0D, 0.0D, 0, 0, 0, 0, 0, 0);
        }

        public int sink(int tileId) {
            return add(SINK, tileId, 0.0D, 0.0D, 0.0D, 0, 0, 0, 0, 0, 0);
        }

        /** Conductor with the 1.7.10 defaults: 9001 insulation breakdown, no block volume. */
        public int conductor(int tileId, double conductorBreakdown, double insulationAbsorption) {
            return conductor(tileId, conductorBreakdown, 9001.0D, insulationAbsorption, 0, 0, 0, 0, 0, 0);
        }

        public int conductor(int tileId, double conductorBreakdown, double insulationBreakdown,
            double insulationAbsorption, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            return add(
                CONDUCTOR,
                tileId,
                conductorBreakdown,
                insulationBreakdown,
                insulationAbsorption,
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ);
        }

        public Builder link(int a, int b, double loss) {
            if (a == b) {
                throw new IllegalArgumentException("self link on node " + a);
            }
            if (!(loss >= 0.0D) || Double.isNaN(loss) || Double.isInfinite(loss)) {
                throw new IllegalArgumentException("invalid link loss " + loss);
            }
            links.add(new LinkDefinition(a, b, loss));
            return this;
        }

        public PacketGraph build() {
            for (LinkDefinition link : links) {
                if (link.a < 0 || link.a >= types.size() || link.b < 0 || link.b >= types.size()) {
                    throw new IllegalStateException("link endpoint out of range: " + link.a + " -> " + link.b);
                }
            }
            return new PacketGraph(this);
        }

        private int add(int type, int tileId, double conductorBreakdown, double insulationBreakdown,
            double insulationAbsorption, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            types.add(
                new NodeDefinition(
                    type,
                    tileId,
                    conductorBreakdown,
                    insulationBreakdown,
                    insulationAbsorption,
                    minX,
                    minY,
                    minZ,
                    maxX,
                    maxY,
                    maxZ));
            return types.size() - 1;
        }
    }
}
