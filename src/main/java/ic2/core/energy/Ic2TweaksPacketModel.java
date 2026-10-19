package ic2.core.energy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.mrfuzzihead.ic2tweaks.enet.EnetOverload;
import com.mrfuzzihead.ic2tweaks.enet.EnetOverloadConfig;
import com.mrfuzzihead.ic2tweaks.enet.packet.PacketGraph;
import com.mrfuzzihead.ic2tweaks.enet.packet.PacketGrid;
import com.mrfuzzihead.ic2tweaks.enet.packet.PacketGridStateHolder;
import com.mrfuzzihead.ic2tweaks.enet.packet.PacketInjectionHandler;
import com.mrfuzzihead.ic2tweaks.enet.packet.PacketInputs;
import com.mrfuzzihead.ic2tweaks.enet.packet.PacketMath;
import com.mrfuzzihead.ic2tweaks.enet.packet.PacketNodeStats;
import com.mrfuzzihead.ic2tweaks.enet.packet.PacketOfferLedger;
import com.mrfuzzihead.ic2tweaks.enet.packet.PacketPath;
import com.mrfuzzihead.ic2tweaks.enet.packet.PacketTickResult;

import ic2.api.energy.NodeStats;
import ic2.api.energy.tile.IEnergyConductor;
import ic2.api.energy.tile.IEnergySink;
import ic2.api.energy.tile.IEnergySource;
import ic2.core.IC2DamageSource;

/**
 * Live adapter between IC2 1.7.10's energy net and the pure packet model.
 * <p>
 * Lives in {@code ic2.core.energy} on purpose: {@code Grid}, {@code Node}, {@code NodeLink} and
 * {@code Tile} are package-private. The two Phase-2 mixins (in the mod's mixin package) call the public
 * methods below; everything else is internal. The packet state is attached to each {@code Grid} through
 * {@link PacketGridStateHolder}, so its lifetime follows the grid and there is no static world leak.
 * <p>
 * Everything here runs on the server thread inside {@code EnergyNetLocal.processChanges()}
 * ({@code locked == false}), the only window where IC2 allows removing tiles and the same window IC2
 * itself uses to inject/draw energy.
 */
public final class Ic2TweaksPacketModel {

    private Ic2TweaksPacketModel() {}

    public static boolean isActive() {
        return EnetOverloadConfig.enabled && EnetOverloadConfig.packetModel;
    }

    /** Replaces the legacy {@code processChanges} body while packet mode is active. */
    public static void tick(Object enetObject) {
        if (!isActive() || !(enetObject instanceof EnergyNetLocal)) {
            return;
        }
        EnergyNetLocal energyNet = (EnergyNetLocal) enetObject;
        List<Grid> grids = new ArrayList<>(energyNet.grids);

        // 1.12.2's runSyncStep(enet): cap every source tile once, shared across all grids it feeds.
        PacketOfferLedger<Tile> offers = new PacketOfferLedger<>();
        for (Grid grid : grids) {
            GridState state = state(grid);
            if (state == null) {
                continue;
            }
            for (int node : state.sourceNodes) {
                Tile tile = state.tile(node);
                if (tile == null || offers.contains(tile) || !(tile.entity instanceof IEnergySource)) {
                    continue;
                }
                IEnergySource source = (IEnergySource) tile.entity;
                offers.seed(tile, source.getOfferedEnergy(), source.getSourceTier(), 1);
            }
        }

        for (Grid grid : grids) {
            // An earlier grid's effects may have removed this grid from the energy net.
            if (!energyNet.grids.contains(grid)) {
                continue;
            }
            GridState state = state(grid);
            if (state == null) {
                continue;
            }
            World world = state.world();
            if (world == null || world.isRemote) {
                continue;
            }

            PacketInputs inputs = new PacketInputs(state.graph.nodeCount());
            for (int node : state.sourceNodes) {
                Tile tile = state.tile(node);
                if (tile == null || !(tile.entity instanceof IEnergySource)) {
                    continue;
                }
                IEnergySource source = (IEnergySource) tile.entity;
                inputs.setSource(node, offers.remaining(tile), source.getSourceTier(), 1);
            }
            for (int node : state.sinkNodes) {
                Tile tile = state.tile(node);
                if (tile == null || !(tile.entity instanceof IEnergySink)) {
                    continue;
                }
                IEnergySink sink = (IEnergySink) tile.entity;
                inputs.setSink(node, sink.getDemandedEnergy(), sink.getSinkTier());
            }

            boolean shufflePaths = (world.getTotalWorldTime() & 3L) != 0L;
            PacketTickResult result = state.model.tick(inputs, new Handler(state), world.rand, shufflePaths);

            for (int node : state.sourceNodes) {
                Tile tile = state.tile(node);
                if (tile != null) {
                    offers.update(tile, inputs.sourceOffer[node]);
                }
            }

            applyEffects(world, state, result);
        }
    }

    /** Packet-model replacement for {@code EnergyNetLocal.getNodeStats(TileEntity)}. */
    public static NodeStats getNodeStats(Object enetObject, Map registeredTiles, TileEntity te) {
        Tile tile = tileFor(registeredTiles, te);
        if (tile == null) {
            return new NodeStats(0.0D, 0.0D, 0.0D);
        }

        double energyIn = 0.0D;
        double energyOut = 0.0D;
        double maxPacket = 0.0D;
        for (Node node : tile.nodes) {
            Grid grid = node.getGrid();
            if (grid == null) {
                continue;
            }
            GridState state = stateOrNull(grid);
            if (state == null) {
                continue;
            }
            Integer nodeId = state.idByNode.get(node);
            if (nodeId == null) {
                continue;
            }
            PacketNodeStats stats = state.model.nodeStats(nodeId);
            energyIn += stats.energyIn;
            energyOut += stats.energyOut;
            maxPacket = Math.max(maxPacket, stats.maxPacket);
        }

        int tier = PacketMath.tierFromPower(maxPacket);
        double voltage = PacketMath.nodeStatsVoltage(tier, EnetOverloadConfig.packetModelTierVoltage);
        return new NodeStats(energyIn, energyOut, voltage);
    }

    public static double getTotalEnergyEmitted(Object enetObject, Map registeredTiles, TileEntity te) {
        return getNodeStats(enetObject, registeredTiles, te).getEnergyOut();
    }

    public static double getTotalEnergySunken(Object enetObject, Map registeredTiles, TileEntity te) {
        return getNodeStats(enetObject, registeredTiles, te).getEnergyIn();
    }

    private static void applyEffects(World world, GridState state, PacketTickResult result) {
        if (result == null || result.isEmpty()) {
            return;
        }

        if (EnetOverloadConfig.cableMeltdown) {
            Set<Tile> toRemove = Collections.newSetFromMap(new IdentityHashMap<Tile, Boolean>());
            for (int node : result.conductorsToRemove) {
                Tile tile = state.tile(node);
                if (tile != null) {
                    toRemove.add(tile);
                }
            }
            Set<Tile> toStrip = Collections.newSetFromMap(new IdentityHashMap<Tile, Boolean>());
            for (int node : result.conductorsToStrip) {
                Tile tile = state.tile(node);
                if (tile != null && !toRemove.contains(tile)) {
                    toStrip.add(tile);
                }
            }
            for (Tile tile : toRemove) {
                if (!tile.entity.isInvalid() && tile.entity instanceof IEnergyConductor) {
                    ((IEnergyConductor) tile.entity).removeConductor();
                }
            }
            for (Tile tile : toStrip) {
                if (!tile.entity.isInvalid() && tile.entity instanceof IEnergyConductor) {
                    ((IEnergyConductor) tile.entity).removeInsulation();
                }
            }
        }

        if (EnetOverloadConfig.explosions) {
            for (Map.Entry<Integer, Double> entry : result.sinksToExplode.entrySet()) {
                Tile tile = state.tileById(entry.getKey());
                if (tile != null) {
                    EnetOverload.explodePacketTile(tile.entity, tile.positions, entry.getValue());
                }
            }
        }

        if (EnetOverloadConfig.shocks) {
            applyShocks(world, state, result);
        }
    }

    /**
     * Port of the shock half of 1.12.2's {@code applyCableEffects}: per path, take the max contribution
     * among the conductors within an entity's reach, sum the path maxima per entity, then apply
     * {@code ceil(sum / 64)} once.
     */
    private static void applyShocks(World world, GridState state, PacketTickResult result) {
        List<PacketTickResult.ShockContribution> contributions = result.shockContributions;
        if (contributions.isEmpty()) {
            return;
        }

        Map<PacketPath, List<PacketTickResult.ShockContribution>> byPath = new LinkedHashMap<>();
        for (PacketTickResult.ShockContribution contribution : contributions) {
            List<PacketTickResult.ShockContribution> pathContributions = byPath.get(contribution.path);
            if (pathContributions == null) {
                pathContributions = new ArrayList<>();
                byPath.put(contribution.path, pathContributions);
            }
            pathContributions.add(contribution);
        }

        Map<EntityLivingBase, Integer> total = new IdentityHashMap<>();
        for (Map.Entry<PacketPath, List<PacketTickResult.ShockContribution>> entry : byPath.entrySet()) {
            PacketPath path = entry.getKey();
            AxisAlignedBB pathBox = AxisAlignedBB.getBoundingBox(
                path.minX - 1.0D,
                path.minY - 1.0D,
                path.minZ - 1.0D,
                path.maxX + 2.0D,
                path.maxY + 2.0D,
                path.maxZ + 2.0D);
            List<EntityLivingBase> entities = world.getEntitiesWithinAABB(EntityLivingBase.class, pathBox);
            if (entities.isEmpty()) {
                continue;
            }

            Map<EntityLivingBase, Integer> pathMax = new IdentityHashMap<>();
            for (PacketTickResult.ShockContribution contribution : entry.getValue()) {
                Tile tile = state.tile(contribution.conductorNode);
                if (tile == null) {
                    continue;
                }
                for (TileEntity subTile : tile.positions) {
                    AxisAlignedBB conductorBox = AxisAlignedBB.getBoundingBox(
                        subTile.xCoord - 1.0D,
                        subTile.yCoord - 1.0D,
                        subTile.zCoord - 1.0D,
                        subTile.xCoord + 2.0D,
                        subTile.yCoord + 2.0D,
                        subTile.zCoord + 2.0D);
                    for (EntityLivingBase entity : entities) {
                        if (entity.boundingBox == null || !entity.boundingBox.intersectsWith(conductorBox)) {
                            continue;
                        }
                        Integer previous = pathMax.get(entity);
                        if (previous == null || previous < contribution.energy) {
                            pathMax.put(entity, contribution.energy);
                        }
                    }
                }
            }

            for (Map.Entry<EntityLivingBase, Integer> pathEntry : pathMax.entrySet()) {
                Integer previous = total.get(pathEntry.getKey());
                total
                    .put(pathEntry.getKey(), previous == null ? pathEntry.getValue() : previous + pathEntry.getValue());
            }
        }

        for (Map.Entry<EntityLivingBase, Integer> entry : total.entrySet()) {
            EntityLivingBase entity = entry.getKey();
            if (entity == null || entity.isDead) {
                continue;
            }
            int damage = PacketMath.shockDamageFromEnergy(entry.getValue());
            if (damage > 0) {
                entity.attackEntityFrom(IC2DamageSource.electricity, damage);
            }
        }
    }

    private static Tile tileFor(Map registeredTiles, TileEntity te) {
        if (te == null || registeredTiles == null) {
            return null;
        }
        return (Tile) registeredTiles.get(new ChunkCoordinates(te.xCoord, te.yCoord, te.zCoord));
    }

    private static GridState state(Grid grid) {
        if (grid == null) {
            return null;
        }
        PacketGridStateHolder holder = (PacketGridStateHolder) grid;
        Object raw = holder.ic2tweaks$getPacketState();
        if (raw instanceof GridState) {
            return (GridState) raw;
        }
        GridState built = new GridState(holder.ic2tweaks$rawNodes());
        holder.ic2tweaks$setPacketState(built);
        return built;
    }

    private static GridState stateOrNull(Grid grid) {
        Object raw = ((PacketGridStateHolder) grid).ic2tweaks$getPacketState();
        return raw instanceof GridState ? (GridState) raw : null;
    }

    /** One live {@code EnergyNetLocal} injection/draw pair for a grid. */
    private static final class Handler implements PacketInjectionHandler {

        private final GridState state;

        Handler(GridState state) {
            this.state = state;
        }

        @Override
        public double inject(PacketPath path, double amount, int tier) {
            Tile target = state.tile(path.target);
            if (target == null || !(target.entity instanceof IEnergySink)) {
                return amount;
            }
            ForgeDirection direction = directionFrom(target, path);
            return ((IEnergySink) target.entity).injectEnergy(direction, amount, tier);
        }

        @Override
        public void draw(int sourceNode, double amount) {
            Tile tile = state.tile(sourceNode);
            if (tile != null && tile.entity instanceof IEnergySource) {
                ((IEnergySource) tile.entity).drawEnergy(amount);
            }
        }

        /** 1.12.2's {@code EnergyPath.targetDirection}: from the sink toward the previous node. */
        private ForgeDirection directionFrom(Tile target, PacketPath path) {
            int previousNode = path.conductors.length == 0 ? path.source : path.conductors[path.conductors.length - 1];
            Tile previous = state.tile(previousNode);
            if (previous == null) {
                return ForgeDirection.UNKNOWN;
            }
            for (TileEntity targetPart : target.positions) {
                for (TileEntity previousPart : previous.positions) {
                    int deltaX = previousPart.xCoord - targetPart.xCoord;
                    int deltaY = previousPart.yCoord - targetPart.yCoord;
                    int deltaZ = previousPart.zCoord - targetPart.zCoord;
                    for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
                        if (direction.offsetX == deltaX && direction.offsetY == deltaY && direction.offsetZ == deltaZ) {
                            return direction;
                        }
                    }
                }
            }
            return ForgeDirection.UNKNOWN;
        }
    }

    /** Per-grid graph, id maps and packet cache, attached to the IC2 {@code Grid}. */
    static final class GridState {

        final PacketGraph graph;
        final PacketGrid model;
        final List<Node> nodesById = new ArrayList<>();
        final Map<Node, Integer> idByNode = new IdentityHashMap<>();
        final List<Tile> tilesById = new ArrayList<>();
        final Map<Tile, Integer> tileIds = new IdentityHashMap<>();
        final List<Integer> sourceNodes = new ArrayList<>();
        final List<Integer> sinkNodes = new ArrayList<>();
        private final World world;

        GridState(Map rawNodes) {
            List<Node> nodes = new ArrayList<>();
            for (Object value : rawNodes.values()) {
                nodes.add((Node) value);
            }
            Collections.sort(nodes, (left, right) -> Integer.compare(left.uid, right.uid));

            PacketGraph.Builder builder = PacketGraph.builder();
            for (Node node : nodes) {
                int nodeId = nodesById.size();
                nodesById.add(node);
                idByNode.put(node, nodeId);

                Tile tile = node.tile;
                Integer tileId = tileIds.get(tile);
                if (tileId == null) {
                    tileId = tilesById.size();
                    tileIds.put(tile, tileId);
                    tilesById.add(tile);
                }

                if (node.nodeType == NodeType.Source) {
                    builder.source(tileId);
                    sourceNodes.add(nodeId);
                } else if (node.nodeType == NodeType.Sink) {
                    builder.sink(tileId);
                    sinkNodes.add(nodeId);
                } else if (node.nodeType == NodeType.Conductor) {
                    IEnergyConductor conductor = (IEnergyConductor) tile.entity;
                    int minX = Integer.MAX_VALUE;
                    int minY = Integer.MAX_VALUE;
                    int minZ = Integer.MAX_VALUE;
                    int maxX = Integer.MIN_VALUE;
                    int maxY = Integer.MIN_VALUE;
                    int maxZ = Integer.MIN_VALUE;
                    for (TileEntity subTile : tile.positions) {
                        minX = Math.min(minX, subTile.xCoord);
                        minY = Math.min(minY, subTile.yCoord);
                        minZ = Math.min(minZ, subTile.zCoord);
                        maxX = Math.max(maxX, subTile.xCoord);
                        maxY = Math.max(maxY, subTile.yCoord);
                        maxZ = Math.max(maxZ, subTile.zCoord);
                    }
                    builder.conductor(
                        tileId,
                        conductor.getConductorBreakdownEnergy(),
                        conductor.getInsulationBreakdownEnergy(),
                        conductor.getInsulationEnergyAbsorption(),
                        minX,
                        minY,
                        minZ,
                        maxX,
                        maxY,
                        maxZ);
                }
            }

            for (Node node : nodes) {
                for (NodeLink link : node.links) {
                    if (link.nodeA != node) {
                        continue;
                    }
                    Integer from = idByNode.get(node);
                    Integer to = idByNode.get(link.nodeB);
                    if (from != null && to != null) {
                        builder.link(from, to, link.loss);
                    }
                }
            }

            this.graph = builder.build();
            this.model = new PacketGrid(graph, EnetOverloadConfig.packetModelRoundLossDown);
            this.model.rebuild();
            this.world = worldOf(tilesById);
        }

        Tile tile(int nodeId) {
            if (nodeId < 0 || nodeId >= nodesById.size()) {
                return null;
            }
            return nodesById.get(nodeId).tile;
        }

        Tile tileById(int tileId) {
            if (tileId < 0 || tileId >= tilesById.size()) {
                return null;
            }
            return tilesById.get(tileId);
        }

        World world() {
            return world;
        }

        private static World worldOf(List<Tile> tiles) {
            for (Tile tile : tiles) {
                World world = tile.entity.getWorldObj();
                if (world != null) {
                    return world;
                }
            }
            return null;
        }
    }
}
