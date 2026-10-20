package com.mrfuzzihead.ic2tweaks.enet.packet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

/**
 * Per-grid port of IC2 Experimental 1.12.2's {@code EnergyCalculatorLeg}: the path cache
 * ({@code updateCache}), the per-tick distribution ({@code runCalculation}/{@code distribute}/
 * {@code emit}) and the effects decisions ({@code applyCableEffects}).
 * <p>
 * Everything here is pure Java: the live game is reached only through {@link PacketInjectionHandler}
 * (sink injection and source draw). The Phase-2 adapter owns one instance per IC2 {@code Grid}, rebuilds
 * it when the topology changes ({@code Grid.invalidate}) and calls {@link #tick} inside the energy-net
 * tick's unlocked window.
 */
public final class PacketGrid {

    /** 1.12.2 switches from a priority queue to a FIFO queue above this many sources. */
    private static final int BFS_SOURCE_THRESHOLD = 2048;

    private final PacketGraph graph;
    private final boolean roundLossDown;

    private final Map<Integer, List<PacketPath>> sourcePaths = new HashMap<>();
    private final Map<Integer, List<PacketPath>> pathCache = new HashMap<>();
    private final List<Integer> activeSources = new ArrayList<>();
    private final Map<Integer, Double> activeSinks = new HashMap<>();
    private final Set<PacketPath> eventPaths = Collections.newSetFromMap(new IdentityHashMap<PacketPath, Boolean>());

    private int currentCalcId = -1;
    private boolean active;

    public PacketGrid(PacketGraph graph, boolean roundLossDown) {
        this.graph = graph;
        this.roundLossDown = roundLossDown;
    }

    public PacketGraph graph() {
        return graph;
    }

    public boolean isActive() {
        return active;
    }

    public int currentCalcId() {
        return currentCalcId;
    }

    public List<PacketPath> sourcePaths(int source) {
        List<PacketPath> paths = sourcePaths.get(source);
        return paths == null ? Collections.<PacketPath>emptyList() : Collections.unmodifiableList(paths);
    }

    /**
     * Rebuilds the per-source path cache. Port of {@code EnergyCalculatorLeg.updateCache}: a Dijkstra
     * per source, stopping once every sink has a path, with 1.12.2's source-to-source composition for
     * paths that run through another source.
     */
    public void rebuild() {
        sourcePaths.clear();
        pathCache.clear();
        activeSources.clear();
        activeSinks.clear();
        eventPaths.clear();
        currentCalcId = -1;
        active = false;

        List<Integer> sources = new ArrayList<>();
        int sinkCount = 0;
        for (int node = 0; node < graph.nodeCount(); node++) {
            int type = graph.type(node);
            if (type == PacketGraph.SOURCE) {
                sources.add(node);
            } else if (type == PacketGraph.SINK) {
                sinkCount++;
            }
        }
        if (graph.nodeCount() < 2 || sources.isEmpty() || sinkCount == 0) {
            return;
        }

        boolean usePriorityQueue = sources.size() <= BFS_SOURCE_THRESHOLD;
        for (int source : sources) {
            final Map<Integer, Double> distance = new HashMap<>();
            final Map<Integer, Integer> previous = new HashMap<>();
            Queue<Integer> queue = usePriorityQueue
                ? new PriorityQueue<Integer>(Math.max(1, graph.nodeCount()), (a, b) -> {
                    int comparison = Double.compare(distance.get(a), distance.get(b));
                    return comparison != 0 ? comparison : Integer.compare(a, b);
                })
                : new ArrayDeque<Integer>();
            Map<Integer, PacketPath> paths = new LinkedHashMap<>();

            distance.put(source, 0.0D);
            queue.add(source);

            search: while (true) {
                Integer polled = queue.poll();
                if (polled == null) {
                    break;
                }
                int node = polled;

                if (graph.type(node) == PacketGraph.SINK) {
                    double loss = distance.get(node);
                    PacketPath existing = paths.get(graph.tileId(node));
                    if (existing != null && existing.loss <= loss) {
                        continue;
                    }
                    if (roundLossDown) {
                        loss = Math.floor(loss);
                    }
                    paths.put(
                        graph.tileId(node),
                        new PacketPath(graph, source, node, toArray(reconstruct(source, node, previous)), loss));
                    if (paths.size() != sinkCount) {
                        continue;
                    }
                    break;
                }

                if (graph.type(node) != PacketGraph.CONDUCTOR && node != source) {
                    continue;
                }

                double loss = distance.get(node);
                for (int index = graph.neighborStart(node); index < graph.neighborEnd(node); index++) {
                    int neighbor = graph.neighbor(node, index);
                    double linkLoss = graph.linkLoss(index);

                    if (graph.type(neighbor) == PacketGraph.SOURCE) {
                        List<PacketPath> neighborPaths = sourcePaths.get(neighbor);
                        if (neighborPaths == null) {
                            continue;
                        }
                        if (neighborPaths.isEmpty()) {
                            continue search;
                        }
                        // IC2 1.12.2 composes with an already-solved source and subtracts the link
                        // loss here. Replicated verbatim (see PACKET_MODEL_SPIKE.md §2.5).
                        loss -= linkLoss;
                        List<Integer> pathToHere = null;
                        for (PacketPath composed : neighborPaths) {
                            double composedLoss = loss + composed.loss;
                            PacketPath existing = paths.get(graph.tileId(composed.target));
                            if (existing != null && existing.loss <= composedLoss) {
                                continue;
                            }
                            if (roundLossDown) {
                                composedLoss = Math.floor(composedLoss);
                            }
                            if (pathToHere == null) {
                                pathToHere = reconstruct(source, node, previous);
                            }
                            paths.put(
                                graph.tileId(composed.target),
                                new PacketPath(
                                    graph,
                                    source,
                                    composed.target,
                                    concat(pathToHere, composed.conductors),
                                    composedLoss));
                        }
                        continue search;
                    }

                    double newLoss = loss + linkLoss;
                    Double previousLoss = distance.get(neighbor);
                    if (previousLoss != null && !(previousLoss > newLoss)) {
                        continue;
                    }
                    if (previousLoss != null) {
                        queue.remove(neighbor);
                    }
                    distance.put(neighbor, newLoss);
                    previous.put(neighbor, node);
                    queue.add(neighbor);
                }
            }

            if (!paths.isEmpty()) {
                sourcePaths.put(source, new ArrayList<>(paths.values()));
            }
        }

        active = !sourcePaths.isEmpty();
    }

    /**
     * Runs one distribution tick. Port of {@code EnergyCalculatorLeg.runCalculation}: collects active
     * sources/sinks, visits sources from a random offset, distributes each packet, and returns the
     * effects decisions for this tick.
     * <p>
     * {@code inputs.sourceOffer} is mutated (the adapter shares one instance across all grids, matching
     * 1.12.2's per-tile remaining amount).
     */
    public PacketTickResult tick(PacketInputs inputs, PacketInjectionHandler handler, Random random,
        boolean shufflePaths) {
        if (!active) {
            return PacketTickResult.empty();
        }

        activeSources.clear();
        activeSinks.clear();
        int calcId = ++currentCalcId;

        for (int node = 0; node < graph.nodeCount(); node++) {
            int type = graph.type(node);
            if (type == PacketGraph.SOURCE) {
                if (sourcePaths.containsKey(node) && inputs.sourceOffer[node] > 0.0D) {
                    activeSources.add(node);
                }
            } else if (type == PacketGraph.SINK && inputs.sinkDemand[node] > 0.0D) {
                activeSinks.put(node, inputs.sinkDemand[node]);
            }
        }

        if (activeSources.isEmpty() || activeSinks.isEmpty()) {
            return PacketTickResult.empty();
        }

        int offset = activeSources.size() > 1 ? random.nextInt(activeSources.size()) : 0;
        for (int i = offset; i < activeSources.size() && !activeSinks.isEmpty(); i++) {
            distribute(activeSources.get(i), inputs, handler, calcId, shufflePaths, random);
        }
        for (int i = 0; i < offset && !activeSinks.isEmpty(); i++) {
            distribute(activeSources.get(i), inputs, handler, calcId, shufflePaths, random);
        }

        return applyEffects(inputs);
    }

    /** Packet-model replacement for {@code getNodeStats}: sums the current tick's paths by node role. */
    public PacketNodeStats nodeStats(int node) {
        double energyIn = 0.0D;
        double energyOut = 0.0D;
        double maxPacket = 0.0D;
        int type = graph.type(node);

        for (PacketPath path : pathsFor(node)) {
            if (path.lastCalcId != currentCalcId) {
                continue;
            }
            maxPacket = Math.max(maxPacket, path.maxPacketConducted);
            if (type == PacketGraph.SOURCE) {
                energyOut += path.energySupplied;
            } else if (type == PacketGraph.SINK) {
                energyIn += path.energySupplied;
            } else {
                energyIn += path.energySupplied;
                energyOut += path.energySupplied;
            }
        }
        return new PacketNodeStats(energyIn, energyOut, maxPacket);
    }

    private void distribute(int source, PacketInputs inputs, PacketInjectionHandler handler, int calcId,
        boolean shufflePaths, Random random) {
        int packetCount = inputs.sourcePackets[source];
        if (packetCount <= 0) {
            return;
        }
        List<PacketPath> paths = sourcePaths.get(source);
        if (paths == null || paths.isEmpty()) {
            return;
        }
        double totalOffer = inputs.sourceOffer[source];
        if (!(totalOffer > 0.0D)) {
            return;
        }

        int pathOffset = paths.size() > 1 && shufflePaths ? random.nextInt(paths.size()) : 0;
        double offer = packetCount == 1 ? distributeSingle(totalOffer, paths, pathOffset, inputs, handler, calcId)
            : distributeMultiple(totalOffer, source, paths, pathOffset, inputs, handler, calcId, packetCount);

        double used = totalOffer - Math.max(0.0D, offer);
        if (used > 0.0D) {
            // 1.12.2 stores the remainder on the tile so other grids see the reduced offer.
            inputs.sourceOffer[source] = offer;
            handler.draw(source, used);
        }
    }

    private double distributeMultiple(double offer, int source, List<PacketPath> paths, int pathOffset,
        PacketInputs inputs, PacketInjectionHandler handler, int calcId, int packetCount) {
        double power = PacketMath.powerFromTier(inputs.sourceTier[source]);
        do {
            double packetOffer = Math.min(offer, power);
            double used = packetOffer - distributeSingle(packetOffer, paths, pathOffset, inputs, handler, calcId);
            if (used <= 0.0D) {
                break;
            }
            offer -= used;
            packetCount--;
        } while (packetCount > 0 && offer > 0.0D);
        return offer;
    }

    private double distributeSingle(double offer, List<PacketPath> paths, int pathOffset, PacketInputs inputs,
        PacketInjectionHandler handler, int calcId) {
        for (int i = pathOffset; i < paths.size(); i++) {
            offer -= emit(paths.get(i), offer, inputs, handler, calcId);
            if (offer <= 0.0D) {
                break;
            }
        }
        for (int i = 0; i < pathOffset && offer > 0.0D; i++) {
            offer -= emit(paths.get(i), offer, inputs, handler, calcId);
        }
        return offer;
    }

    /** Port of {@code EnergyCalculatorLeg.emit}. */
    private double emit(PacketPath path, double offer, PacketInputs inputs, PacketInjectionHandler handler,
        int calcId) {
        double injectAmount = offer - path.loss;
        if (injectAmount <= 0.0D) {
            return 0.0D;
        }

        Double sinkDemand = activeSinks.get(path.target);
        if (sinkDemand == null) {
            return 0.0D;
        }

        double amount = Math.min(injectAmount, sinkDemand);
        int tier = PacketMath.tierFromPower(amount);
        double rejected = handler.inject(path, amount, tier);
        if (rejected >= amount) {
            return 0.0D;
        }

        double effectiveAmount = PacketMath.effectiveAmount(amount, rejected, path.loss);
        if (path.lastCalcId != calcId) {
            path.lastCalcId = calcId;
            path.energySupplied = 0.0D;
            path.maxPacketConducted = 0.0D;
        }
        path.energySupplied += amount - rejected;
        path.maxPacketConducted = Math.max(effectiveAmount, path.maxPacketConducted);

        if (effectiveAmount > path.minEffectEnergy || amount > PacketMath.powerFromTier(inputs.sinkTier[path.target])) {
            eventPaths.add(path);
        }
        if (amount >= sinkDemand || rejected > 0.0D) {
            activeSinks.remove(path.target);
        }
        return effectiveAmount;
    }

    /** Port of {@code EnergyCalculatorLeg.applyCableEffects}, minus the world application. */
    private PacketTickResult applyEffects(PacketInputs inputs) {
        if (eventPaths.isEmpty()) {
            return PacketTickResult.empty();
        }

        Set<Integer> conductorsToRemove = new HashSet<>();
        Set<Integer> conductorsToStrip = new HashSet<>();
        Map<Integer, Double> sinksToExplode = new HashMap<>();
        List<PacketTickResult.ShockContribution> shocks = new ArrayList<>();

        for (PacketPath path : eventPaths) {
            double packet = path.maxPacketConducted;
            boolean conductorOverload = packet > path.minConductorBreakdown || packet > path.minInsulationBreakdown;

            if (conductorOverload) {
                for (int conductor : path.conductors) {
                    if (packet > graph.conductorBreakdown(conductor)) {
                        conductorsToRemove.add(conductor);
                    } else if (packet > graph.insulationBreakdown(conductor)) {
                        conductorsToStrip.add(conductor);
                    }
                }
            }

            if (packet > path.minInsulationAbsorption) {
                for (int conductor : path.conductors) {
                    double absorption = graph.insulationAbsorption(conductor);
                    if (packet > absorption) {
                        int energy = (int) (packet - absorption);
                        if (energy > 0) {
                            shocks.add(new PacketTickResult.ShockContribution(path, conductor, energy));
                        }
                    }
                }
            }

            if (!conductorOverload && packet > PacketMath.powerFromTier(inputs.sinkTier[path.target])) {
                int tile = graph.tileId(path.target);
                Double previous = sinksToExplode.get(tile);
                if (previous == null || previous < packet) {
                    sinksToExplode.put(tile, packet);
                }
            }
        }

        // 1.12.2 strips only conductors that are not being removed.
        conductorsToStrip.removeAll(conductorsToRemove);
        eventPaths.clear();
        return new PacketTickResult(conductorsToRemove, conductorsToStrip, sinksToExplode, shocks);
    }

    private List<PacketPath> pathsFor(int node) {
        if (graph.type(node) == PacketGraph.SOURCE) {
            List<PacketPath> paths = sourcePaths.get(node);
            return paths == null ? Collections.<PacketPath>emptyList() : paths;
        }

        List<PacketPath> cached = pathCache.get(node);
        if (cached != null) {
            return cached;
        }

        List<PacketPath> result = new ArrayList<>();
        for (List<PacketPath> paths : sourcePaths.values()) {
            for (PacketPath path : paths) {
                if (graph.type(node) == PacketGraph.SINK) {
                    if (path.target == node) {
                        result.add(path);
                    }
                } else if (path.hasConductor(node)) {
                    result.add(path);
                }
            }
        }
        pathCache.put(node, result);
        return result;
    }

    private static List<Integer> reconstruct(int source, int target, Map<Integer, Integer> previous) {
        List<Integer> route = new ArrayList<>();
        Integer node = target;
        while (true) {
            node = previous.get(node);
            if (node == null) {
                throw new IllegalStateException("broken path from " + source + " to " + target);
            }
            if (node == source) {
                break;
            }
            route.add(node);
        }
        Collections.reverse(route);
        return route;
    }

    private static int[] toArray(List<Integer> nodes) {
        int[] result = new int[nodes.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = nodes.get(i);
        }
        return result;
    }

    private static int[] concat(List<Integer> prefix, int[] suffix) {
        int[] result = new int[prefix.size() + suffix.length];
        int index = 0;
        for (int node : prefix) {
            result[index++] = node;
        }
        System.arraycopy(suffix, 0, result, index, suffix.length);
        return result;
    }
}
