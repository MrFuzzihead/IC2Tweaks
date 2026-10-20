# Packet-Model Spike — porting IC2 1.12.2's path/packet energy calculator to 1.7.10

**Branch:** `feature-ENetRefactor`
**Status:** design spike, no runtime code yet
**Goal:** close gap 1 (per-packet vs per-node aggregate) and the parity gaps that fall out of it.
**Evidence base:** the 1.12.2 calculator was decompiled from `industrialcraft-2-2.8.222-ex112.jar`
(CurseForge project 242638, file 3838713); the 1.7.10 side from `ic2-242638-2353971`
(`industrialcraft-2:2.2.828-experimental`). All method/constant references below are from those jars.

---

## 1. Why this is the right next step

Today's implementation runs the overload rules on IC2 1.7.10's **per-node** solved values
(`max(|currentIn|,|currentOut|) · V`, or the `injectEnergy` amount). 1.12.2 runs them on
**per-source, per-path packet sizes** (`EnergyPath.maxPacketConducted`). Every remaining fidelity gap
except the genuinely absent API (`IMultiEnergySource`) traces back to that one difference:

| Gap | Under the packet model |
|---|---|
| 1 per-packet vs per-node | **closed** by the port |
| 4 `conductorOverload` suppression | **closed** — the path knows the cables *and* the sink |
| 5 demand-capped packets | **closed** — `min(injectAmount, sinkDemand)` is inherent |
| 6 shock accumulation | **closed exactly** — max per conductor per path, summed across paths |
| 11 `NodeStats` staleness | **closed** — stats read from the current run's paths |
| 12 effects in a separate tick pass | **closed** — effects run inside the distribution pass |
| 13 high-tier starvation | **closed** — the packet model never derives sink resistance from tier |
| 15 `accepted` vs `amount` | **closed** — uses `maxPacketConducted` as 1.12.2 does |
| 2 insulation stripping | already parity (1.12.2's `getInsulationBreakdownEnergy()` is also 9001) |
| 7 `ExplosionIC2.Type.Electrical` | still unavailable (1.7.10 has no electrical type) |
| 9 config key location | still cosmetic |
| 16 multi-packet sources | still unavailable (1.7.10 API has no `IMultiEnergySource`) |

It also removes the need to force `useLinearTransferModel`, and with it the whole "global linear-model
side effect" (gap 10) and the tier-starvation quirk in the same stroke.

---

## 2. What the 1.12.2 packet system actually is (verified, not assumed)

### 2.1 Structure

```
ic2/core/energy/grid/           framework: EnergyNetLocal, Grid, Node, NodeLink, Tile,
                                GridUpdater, ChangeHandler, IEnergyCalculator, EnergyNetSettings
ic2/core/energy/leg/            the packet calculator: EnergyCalculatorLeg, EnergyPath
```

`EnergyNetGlobal.getCalculator()` is pluggable. In 2.8.222 the *only* calculator is
`EnergyCalculatorLeg` — there is no `EnergyCalculatorUnified` in the 1.12.2 release line. (The
unified/rewritten calculators are later IC2 versions; the "modern calculator dropped suppression"
note in `ENET_OVERLOAD_ANALYSIS.md` §12 refers to those, not to 2.8.222.)

### 2.2 Tick lifecycle

* Topology changes are queued as `GridChange`s; `GridUpdater` applies them (on IC2's thread pool) and
  calls `IEnergyCalculator.handleGridChange(grid)` for each dirty grid. `EnergyCalculatorLeg` rebuilds
  a per-source **path cache** there (`updateCache`).
* `EnergyNetLocal.onTickEnd()` → `GridUpdater.startTransferCalc()`:
  1. `runSyncStep(enet)`: for every source tile, `amount = min(getOfferedEnergy(), powerFromTier(tier) × packetCount)`;
     `tile.setSourceData(amount, packetCount)`.
  2. For every grid, `runSyncStep(grid)` → `runCalculation(grid, data)`, which **runs synchronously on
     the calling (server) thread** and returns `false` (the "async step" path is unused by the Leg
     calculator).
  3. That same call distributes energy *and* applies all cable/machine/shock effects.

So in 1.12.2, distribution, `injectEnergy`, `drawEnergy` and effects all happen on the server thread in
one pass. Only the topology/path-cache work is off-thread, and `awaitCompletion()` gates the tick.

### 2.3 Distribution (`runCalculation` → `distribute` → `emit`)

* Active sources: source nodes with `tile.amount > 0` and a path map. Active sinks: sink nodes with
  `getDemandedEnergy() > 0`, tracked as a mutable demand budget (`activeSinks`).
* Sources are visited starting at a random offset (re-shuffled every 4th tick:
  `shufflePaths = (world time & 3) != 0`), so competing sources share sinks fairly.
* `distribute` drains the tile's offer as paths are used:
  `used = totalOffer − max(0, offer); tile.setAmount(offer); source.drawEnergy(used)`.
* `emit(path, offer)` is the heart of the model:

```java
double injectAmount = offer - path.loss;                 // loss is per-path, not per-node
if (injectAmount <= 0) return 0;
MutableDouble sinkDemand = data.activeSinks.get(path.target);
if (sinkDemand == null) return 0;                        // sink already satisfied
double amount = Math.min(injectAmount, sinkDemand);      // demand cap
double rejected = sink.injectEnergy(path.targetDirection, amount,
                                    EnergyNet.instance.getTierFromPower(amount));
if (rejected >= amount) return 0;
double effectiveAmount = Math.max(0, amount - rejected + path.loss);
path.energySupplied += amount - rejected;
path.maxPacketConducted = Math.max(effectiveAmount, path.maxPacketConducted);
if (effectiveAmount > path.minEffectEnergy
        || amount > EnergyNet.instance.getPowerFromTier(sink.getSinkTier())) {
    data.eventPaths.add(path);                           // candidate for effects
}
if (amount >= sinkDemand.doubleValue() || rejected > 0) {
    data.activeSinks.remove(path.target);
}
return effectiveAmount;                                  // this much offer is consumed
```

* `distributeMultiple` repeats this with packets of `min(offer, powerFromTier(sourceTier))` for
  `IMultiEnergySource` tiles; without that interface (1.7.10) every source is single-packet.
* A machine that **rejects** the packet does not consume the offer (returns 0), so it neither explodes
  later nor drains the source.

### 2.4 Effects (`applyCableEffects`), same pass, same order

For every event path with `target = path.maxPacketConducted`:

1. **Cable** — if `target > minConductorBreakdownEnergy || target > minInsulationBreakdownEnergy`
   (`min*` are minima across the path's conductors), then per conductor:
   `target > getConductorBreakdownEnergy()` → melt (`removeConductor()`);
   else `target > getInsulationBreakdownEnergy()` → strip (`removeInsulation()`; no-op in both versions).
2. **Shock** — if `target > minInsulationEnergyAbsorption`, per conductor with
   `target > getInsulationEnergyAbsorption()`: per-sub-tile, per-entity, keep the **max**
   `(int)(target − absorption)`; then **sum across paths**; damage = `ceil(sum / 64)` once per entity.
3. **Machine explosion** — if **`!conductorOverload`** and
   `target > powerFromTier(sink.getSinkTier())`: record the sink tile with the max `target`; later
   `explodeTile`: `IOverloadHandler.onOverload(tier)` on main tile *and* the real TE, then
   `IExplosionPowerOverride` (veto/resize), `world.setBlockToAir(pos)`, `ExplosionIC2(2.5f, 0.75f,
   Type.Electrical)`.

This is where gap 4 gets closed for free: suppression is a property of the path object.

### 2.5 Path cache (`updateCache`)

Per grid, per source: a Dijkstra over `Node.links` (`NodeLink.loss` weights) that stops early once
every sink has a path (`paths.size() == sinkCount`) and composes with paths already computed for
**other sources** it reaches (source→source chaining). That composition subtracts the connecting link's
loss and drops the junction conductor from the composed conductor list (`reconstructPath` excludes its
destination node); both quirks are replicated verbatim and pinned by a test. Each `EnergyPath`
precomputes the conductor
node list, summed loss, `targetDirection`, minima of the three conductor thresholds, and the path's
bounding box (used for shock entity search). `EnergyNetSettings.roundLossDown` (`misc/roundEnetLoss`)
floors losses when enabled; grids with > 2048 sources use an `ArrayDeque` instead of a priority queue.

### 2.6 Stats

`getNodeStats(tile)` sums `path.energySupplied` over the paths touching the tile's nodes
(`energySupplied` excludes path loss) and reports `voltage = getTierFromPower(maxPacketConducted)`,
i.e. a **tier number**, not volts. That is the 1.12.2 contract; EU-reader-style mods see different
numbers under it.

### 2.7 Constants that differ from 1.7.10

| Quantity | 1.7.10 | 1.12.2 (2.8.222) |
|---|---|---|
| `Node.getInnerLoss()` source/sink | `0.4` | `0.002` |
| `EnergyNetLocal.nonConductorResistance` | `0.2` | `0.001` (`EnergyNetSettings`) |
| `getPowerFromTier` | `8 << 2t` (t<14) | same |
| cable breakdown | `capacity + 1` | same (`TileEntityCable` unchanged) |
| insulation breakdown | `9001` | `9001` |
| `misc/roundEnetLoss` | absent | present (`roundLossDown`) |
| `IMultiEnergySource` | absent | present |

**Correction to §11.2 of `ENET_OVERLOAD_ANALYSIS.md`:** 1.12.2's source/sink inner loss is **0.002**,
not 0.4. The shape of the loss is identical (link loss = mean of the endpoints' inner losses, and
`emit` charges `amount − rejected + path.loss`), but a direct BatBox→BatBox run loses ≈2.5 EU in
1.12.2, not ≈495. The 495 figure is correct for 1.7.10's constants and stays correct for us unless we
choose to port 1.12.2's constant too.

---

## 3. Architecture options

### Option A — replace `Grid.calculateDistribution`/`calculateEffects` inside the async solve
Rejected. 1.7.10's solve runs on the IC2 thread pool; the packet model needs `sink.injectEnergy` /
`source.drawEnergy` / world effects, which must not run off-thread (machine buffers are mutable game
state and `EnergyNetLocal.removeTileEntity` throws unless `locked == false`). It would also fight the
`optimize()` contraction layer, which the packet model does not want.

### Option B — a packet tick in the `processChanges()` window (recommended)
Keep 1.7.10's graph (`Grid`/`Node`/`NodeLink`/`Tile`) and topology management; keep the packet
calculator's data path completely out of IC2's linear solver. Run path-cache rebuilds on the main
thread when a grid is dirty and distribution + effects synchronously in the already-existing
unlocked window. This is the smallest change that is still faithful:

* `processChanges()` is the one place IC2 allows tile removal (`locked == false`) and is already where
  sinks are injected and sources drawn — i.e. exactly 1.12.2's `startTransferCalc` moment.
* The raw graph is a superset of what the calculator needs: links carry `loss`, conductors carry
  breakdown/absorption, tiles carry positions and the TE, and node identity is stable.
* No changes to world save format, no API break for `IEnergySink`/`IEnergySource` (they keep being
  called exactly as the API intends).

### Option C — shadow calculator for effects only
Rejected (already assessed in the analysis doc): it duplicates the model, cannot recover true packet
caps, disagrees with actual delivery, and costs O(sources) extra solves per tick.

### Option D — port the whole 1.12.2 grid framework (`GridUpdater`, `ChangeHandler`, queued changes)
Rejected for this spike: it replaces the enet's topology engine too, which is the least valuable and
most compatibility-sensitive part. Revisit only if Option B proves structurally impossible.

---

## 4. Recommended design (Option B)

### 4.1 Config

| Key (`energynet` category) | Default | Meaning |
|---|---|---|
| `enablePacketModel` | `false` during the spike, `true` at release | master switch for the packet calculator; off = today's behavior |
| `packetModelRoundLossDown` | `false` | mirrors `misc/roundEnetLoss` |
| existing `enableEnetExplosions`/`Meltdown`/`Shocks` | unchanged | still gate effects |

### 4.2 Class placement (the package-access problem)

`Grid`, `Node`, `NodeLink`, `Tile`, `Change` are **package-private** in `ic2.core.energy`, and the
packet model touches them heavily. Two mechanisms, combined:

1. **Algorithm classes live in `ic2.core.energy`** (same package as IC2's classes):
   `src/main/java/ic2/core/energy/Ic2TweaksPacketModel.java` etc. Java package-private access is
   granted by package *name*, across jars, in the same runtime classloader — which is how all 1.7.10
   FML mods are loaded. The IC2 jar is **not sealed** (verified: no `Sealed: true` in its manifest),
   so package splitting is legal. Public entry points take only public types (`EnergyNetLocal`,
   `TileEntity`, `World`, our mod classes); everything else stays internal.
2. **Mixins stay in `com.mrfuzzihead.ic2tweaks.mixins.late.ic2`** (the configured `mixinsPackage`) and
   bridge the two remaining *private* members as raw types:
   `Grid.nodes` (`@Shadow @Final private Map nodes;` — the erased descriptor matches, the shadow does
   not need to name `Node`), and, if needed, `EnergyNetLocal.registeredTiles`. Bridges call the
   IC2-package model with `Object`/`Collection<?>` arguments; the model casts internally.

Fallback if package splitting ever becomes a problem: an access transformer to widen the handful of
members, or `@Invoker` bridges. Not needed on 1.7.10.

### 4.3 Objects

| Class (in `ic2.core.energy`) | Port of | Notes |
|---|---|---|
| `Ic2TweaksPacketModel` | `EnergyCalculatorLeg` (static facade) | `tick(EnergyNetLocal)`, `markDirty(Object grid)`, `getNodeStats(EnergyNetLocal, TileEntity)`, `getTotalEnergyEmitted/Sunken(...)`; holds the per-grid data map |
| `Ic2TweaksPacketGridData` | `EnergyCalculatorLeg.GridData` | `active`, `energySourceToEnergyPathMap`, `activeSources`, `activeSinks`, `eventPaths`, `pathCache`, `currentCalcId`, plus our `dirty` flag and a per-tick `Map<Tile, double[]>{amount, packetCount}` |
| `Ic2TweaksPacketPath` | `EnergyPath` | source/target nodes, conductor list, loss, targetDirection, threshold minima, bounding box, `energySupplied`, `maxPacketConducted`, `lastCalcId` |
| `Ic2TweaksPacketDistributor` | `updateCache`/`runCalculation`/`distribute`/`emit` | graph walk + distribution; no world access except the constructor-scanned tile index |
| `Ic2TweaksPacketEffects` | `applyCableEffects`/`explodeTile` | melt/strip/shock/explode; uses our `IOverloadHandler`/`IExplosionPowerOverride` |
| `Ic2TweaksPacketMath` (mod package) | pure helpers | tier/power, threshold and shock math; unit-testable without IC2 |

The mod package keeps `EnetCableData`, `MixinTileEntityCable` and the tooltip (both models use them),
and `EnetOverloadConfig` gains the new keys.

### 4.4 Mixins

| Mixin | Hook | Purpose |
|---|---|---|
| `MixinGridPacketModel` | `@Inject(method = "invalidate", at = @At("HEAD"))` | mark the grid dirty on every add/remove/merge |
| `MixinGridPacketModel` | `@Inject(method = "startCalculation", at = @At("HEAD"), cancellable = true)` | packet mode: cancel (no upfront `drawEnergy`, no async solve, no `Change`s) |
| `MixinEnergyNetLocalPacketModel` | `@Inject(method = "processChanges", at = @At("HEAD"), cancellable = true)` | packet mode: run the packet tick and cancel the legacy body |
| `MixinEnergyNetLocalPacketModel` | `@Inject(method = "getNodeStats", at = @At("HEAD"), cancellable = true)` | packet-mode stats from path data |
| `MixinEnergyNetLocalPacketModel` | same for `getTotalEnergyEmitted`/`getTotalEnergySunken` | keep the API truthful in packet mode |
| `MixinEnergyNetLocalOverload` (existing) | unchanged | legacy mode only; the `processChanges` cancel prevents it firing in packet mode |
| `EnetCableEffects` (existing) | runtime guard | return early when `enablePacketModel` (no double effects) |
| `MixinEnergyNetLocalLinearModel` (existing) | `setApplyIf`/runtime guard | in packet mode, stop forcing `useLinearTransferModel` (removes gap 10) |

All new mixins are `setApplyIf(() -> EnetOverloadConfig.enabled && EnetOverloadConfig.packetModel)`.

### 4.5 Tick sequence (packet mode)

```
onTickEnd()                                  [server thread]
├─ locked = true
├─ for each grid: finishCalculation()        → no-op (calculation == null)
│                 updateStats()              → stale but unused (stats are overridden)
├─ locked = false
├─ processChanges()  ── mixin cancels and runs Ic2TweaksPacketModel.tick(enet)
│   1. drop data for grids no longer present
│   2. for dirty grids: rebuild path cache (Dijkstra, main thread)
│   3. gather source offers: min(getOfferedEnergy(), powerFromTier(sourceTier))
│   4. per grid, in shuffled order: distribute() → emit() → injectEnergy/drawEnergy
│   5. applyCableEffects(): melt / strip / shock / explode (world mutation is legal here)
├─ pending adds are applied                       [unchanged IC2 code]
├─ locked = true
│    prepareCalculation()                    → still runs (sets legacy node amounts; wasteful but harmless)
│    startCalculation()  ── mixin cancels (no draw, no async solve)
└─ locked = false
```

*Everything* the packet model mutates runs on the server thread, in the same window where IC2 itself
injects and draws.

Optional refinement (Phase 3): take over `onTickEnd` at `HEAD` and re-implement its ~15 lines, dropping
the redundant `finishCalculation`/`updateStats`/`prepareCalculation` calls as well. Not needed for a
correct spike.

### 4.6 Cache invalidation

`Grid.invalidate()` is called by `Grid.add/remove/merge`, i.e. on every topology change. The dirty flag
is consumed at the start of the next packet tick. Grids created during the tick get data on their first
tick. Grids that disappear have their data pruned by comparing against `EnergyNetLocal.grids`. No NBT,
no persisted state, no client state.

---

## 5. Fidelity mapping (1.7.10 → 1.12.2 port)

| 1.12.2 construct | 1.7.10 equivalent | Notes |
|---|---|---|
| `Grid.getNodes()` | `Grid.nodes` (private, raw-map shadow) | values are `Node` |
| `Node.getType()` | `node.nodeType` (field) | `Source`/`Sink`/`Conductor` |
| `Node.getTile().getMainTile()` | `node.tile.entity` | main TE; sub-tiles in `tile.positions` |
| `Node.getLinks()`, `getLinkTo` | `node.links`, `Node.getConnectionTo` | adjacency |
| `NodeLink.getLoss()` | `NodeLink.loss` | both are `(innerA + innerB)/2` |
| `Tile.getSubTiles()` | `Tile.positions` (`IMetaDelegate`) | same concept |
| `Tile.isDisabled()` | `entity.isInvalid()` / missing from grid | removed tiles leave the grid immediately |
| `Tile.getAmount()/setAmount/setSourceData` | per-tick side map (or `@Unique` fields) | 1.7.10 `Tile` has no source state |
| `EnergyNet.instance.getTierFromPower/powerFromTier` | identical public API | |
| `IMultiEnergySource` | absent | `packetCount = 1`; gap 16 stays open |
| `IOverloadHandler`, `IExplosionPowerOverride` | our API interfaces | already exist |
| `ExplosionIC2(..., Type.Electrical)` | `ExplosionIC2(world, null, x+.5, y+.5, z+.5, power, 0.75f)` | no electrical type in 1.7.10 |
| `world.getEntitiesWithinAABB` equivalents (1.12.2 `func_72872_a`) | `world.getEntitiesWithinAABB` | shock box from path bounding box, expanded by 1 |
| `MainConfig misc/enetCableMeltdown`, `enableEnetExplosions` | our config toggles | keep ours |
| `misc/roundEnetLoss` | new `packetModelRoundLossDown` | default false |
| `world.rand`, `world time` (shuffle every 4th tick) | same, server thread | deterministic per world seed/time |

Decisions to make before Phase 2:

1. **Loss constants.** Keep 1.7.10's `0.4`/`0.2` (no change to current losses; packet model only
   changes *allocation*) or port 1.12.2's `0.002`/`0.001` (closer to 1.12.2 feel, but changes energy
   economics for every setup). **Recommendation: keep 1.7.10 constants in the spike, document the
   divergence, make it a separate config later.**
2. **`NodeStats.voltage` semantics.** 1.12.2 reports the tier. Porting that exactly is faithful but
   changes what EU-reader mods display; reporting actual volts is compatible-ish but invents behavior.
   **Recommendation: port the tier exactly, document it, offer a compat flag if requested.**
3. **Gap 4 policy.** 2.8.222 suppresses the sink explosion when a conductor on the path overloads;
   post-1.12.2 calculators do not. Since the target is "1.12.2", **recommendation: implement the
   suppression** (and keep the current both-effects behavior only in legacy mode).

---

## 6. Pitfalls

1. **Threading / `locked` rules are non-negotiable.** Distribution, injection, drawing and every world
   mutation run on the server thread inside the `processChanges` window. Path-cache rebuilds are also
   main-thread in the spike; if they ever move to the pool they must be joined before use (1.12.2's
   `awaitCompletion`) and must not touch world state.
2. **`injectEnergy` may be called several times per tick per sink** (once per source path), each with
   `voltage = getTierFromPower(amount)`. That is 1.12.2 behavior, but it breaks mods with side effects
   in `injectEnergy` (counters, logs, state machines). Document; this is the single most visible
   downstream behavior change.
3. **Energy conservation.** The port must be exact: `used` drawn equals delivered + path loss, and
   balls rejected by a sink are not consumed. Add debug invariants and tests before trusting it.
4. **Topology mutation during distribution.** Effects are collected per pass and applied *after* the
   distribution loop; conductors may disappear under us (`removeConductor` → unload event →
   `Grid.remove`). Iterate copies, re-check `isInvalid()`/membership before touching a tile.
5. **Multi-grid source tiles.** Replacing 1.7.10's `shareCount` with 1.12.2's
   "offer is a mutable per-tile amount decremented by each grid's distribution" keeps totals conserved,
   but which grid gets energy first depends on `EnergyNetLocal.grids` (a `HashSet`) iteration order.
   1.12.2 shuffles its grid list (`shuffleGrids`) exactly for this reason — port that too.
6. **Source offer cap.** 1.12.2 caps a source's offer at `powerFromTier(tier)` per packet. A 1.7.10
   source offering more than its tier's power is throttled; that is correct for 1.12.2 but is a
   delivery change (check IC2's own generators and storage blocks first; an MFE at tier 3 offers 512 and
   an MFSU at tier 4 offers 2048, aligned).
7. **Cache staleness.** One missed invalidation routes energy through a stale path. `Grid.invalidate`
   is the only mutation entry point (`add`/`remove`/`merge` all call it) — verify with a debug
   validator that compares a cached path's links against the live graph and a "rebuild every tick"
   debug switch.
8. **`NodeStats` semantic change** (tier, not volts) and `energySupplied` excluding loss vs
   `maxPacketConducted` including it. Make sure the overload decision and the stats agree with 1.12.2,
   and spell out the difference for mod authors.
9. **Explosion semantics.** 1.12.2 calls `world.setBlockToAir(pos)` *then* spawns
   `ExplosionIC2` with `0.75f`; it checks `IOverloadHandler`/`IExplosionPowerOverride` on both the
   main tile and the real TE. `IC2.explodeMachineAt` would additionally `destroy` and drop items —
   don't use it in packet mode. Blast power/rate stay 2.5/0.75.
10. **`IMetaDelegate` machines** exist on both sides; explode/iterate each sub-tile position, and
    remember `getDemandedEnergy`/`injectEnergy` live on the main TE.
11. **Sinks that lie.** The model trusts `getDemandedEnergy` for the cap. A sink that demands 0 but
    accepts, or demands a lie, gets nothing (same as 1.12.2). Document rather than special-case.
12. **Other enet mixins / optimizers** (StellarCore-style, GTNH patches): our `processChanges` HEAD
    cancel suppresses their hooks into that method. Gate with `IMixinConfigPlugin` (or `setApplyIf`)
    when a known conflict is detected, default the feature off until validated, and list conflicts in
    the README.
13. **Patched IC2 forks.** The target is `2.2.828-experimental`. If `Grid`/`EnergyNetLocal` shape
    differs, injections can fail. Prefer `require = 1` for the structural hooks (fail loudly), and a
    version guard that disables packet mode with a log line on unknown builds.
14. **Performance.** Dijkstra per source runs on topology change; a large GTNH-style base can change
    topology often (chunk load/unload). Distribution is O(paths) per tick with a heap-free path list.
    Measure with a 1k-cable / 100-source setup; compare tick time against legacy mode. The >2048-source
    `ArrayDeque` fallback (non-priority BFS) exists in 1.12.2 for a reason — port it or bound the
    Dijkstra work.
15. **Randomness / desync.** Everything runs server-side; the authoritative draw is
    `IC2.platform.isSimulating()`. The `shufflePaths` rotation uses `world.rand`/world time — keep it
    server-side and out of client code.
16. **No save-format impact.** All new state is runtime only; loading an old world with packet mode on
    and off again must be lossless (validate).
17. **Unit testing.** Mixins can't be loaded in unit tests, so keep the decision math pure
    (`Ic2TweaksPacketMath`) and, if practical, define a tiny `PacketGraph` adapter so the distributor's
    path discovery/emit scheduling can be tested against a hand-built fake graph. This is the highest
    value test asset of the whole refactor.

---

## 7. Downstream impact on mods that depend on IC2

This is a *global delivery rewrite*, so the blast radius is every IC2 energy participant. Categorised:

### 7.1 Implementors of `IEnergySink`
* Receive demand-capped amounts (`min(offer − loss, remaining demand)`), possibly **multiple times per
  tick** (once per source path) instead of one aggregate injection.
* The `voltage` argument becomes `getTierFromPower(amount)` — a real per-packet tier, often lower than
  1.7.10's tier-derived value.
* Partial acceptance is honoured correctly (1.7.10's base machines are all-or-nothing, but addon sinks
  with partial semantics now participate).
* A sink whose `getDemandedEnergy()` doesn't reflect real capacity will be fed wrongly — same class of
  bug as 1.12.2. Affects AE2's Energy Acceptor, NuclearCraft, GT converters, PowerConverters,
  chargepads, teleporters, anything with a custom sink.
* Base IC2 machines are clamped to remaining capacity, which incidentally fixes the 1.7.10
  `injectEnergy` overflow (`energy += amount` past `maxEnergy`).

### 7.2 Implementors of `IEnergySource`
* `drawEnergy(used)` is called once per grid distribution with the energy actually used; no more
  upfront-draw-then-refund pattern (`drawEnergy` is never called with a negative amount in packet
  mode).
* `getOfferedEnergy()` is capped at one packet of `powerFromTier(sourceTier)` per tick — sources that
  offer more than their tier allows are throttled (1.12.2 semantics).
* Direction callbacks (`emitsEnergyTo`/`acceptsEnergyFrom`) are unchanged — the topology still comes
  from 1.7.10's graph builder.

### 7.3 Implementors of `IEnergyConductor`
* Curtain effects now use per-path packet size, so a cable shared by two 32 EU/t paths no longer sees
  64 and falsely melts. `getConductorBreakdownEnergy`/`getInsulation*`/`removeConductor`/
  `removeInsulation` are respected per conductor.
* Custom cables (GT, Advanced Power Management, etc.) inherit the new behavior with no API changes.

### 7.4 Readers of `NodeStats` / `EnergyNet` totals
* `getNodeStats().getVoltage()` becomes the max packet's **tier** (1.12.2 contract), not volts;
  `energyIn`/`energyOut` come from `path.energySupplied` (delivered energy, excludes loss).
* `getTotalEnergyEmitted/Sunken` are overridden in packet mode to stay consistent; EU-reader/Waila/OC
  integrations will show different numbers and should be called out in the release notes.

### 7.5 Mods that mix into the enet, or replace it
* Any mod that mixins `Grid.calculateDistribution`/`EnergyNetLocal.processChanges`/`Grid.calculate`
  (optimizers, "fast energy net" patches) will conflict or be bypassed. This is the main hard
  incompatibility class; detect and document.
* Mods that replace `EnergyNet.instance` wholesale make the underlying `EnergyNetLocal` unreachable;
  packet mode should detect this and disable with a warning (legacy behavior remains).

### 7.6 Mods relying on the current (non-lossy / linear) default
* Installing IC2Tweaks already changes delivery today by forcing `useLinearTransferModel`; packet mode
  changes it further (demand caps, per-path loss, source throttling, packet effects). Packs that tune
  around 1.7.10's original no-loss distribution will see different machine throughput, especially
  storage charging and multi-source junction behavior. The README already carries this warning; the
  packet model must extend it with the per-packet details and the `NodeStats` change.

### 7.7 IC2's own blocks
* Storage blocks (source+sink on one tile) exercise the source→source chaining in `updateCache`;
  transformer blocks exercise packet conversion (sink accepts a high-tier packet, source emits at its
  own tier); chargepads/teleporters exercise repeated injection. These are the best in-game canaries.
* Transformer upgrades keep making `getSinkTier()` higher; with the packet model that no longer
  starves the machine (gap 13 fixed) — a behavior *improvement* users will notice immediately
  (previously, ≥6 upgrades on an MFE made a machine draw almost nothing).

---

## 8. Phased implementation plan

**Phase 1 — pure core + tests (no runtime change, flag off)**
* Port the path discovery + emit math against a small `PacketGraph` adapter into `Ic2TweaksPacketMath`/
  `Ic2TweaksPacketDistributor`.
* Unit tests: min-loss path selection, source→source chaining, demand caps, rejection, loss accounting,
  `effectiveAmount`/`maxPacketConducted`, event-path gating, shock max/sum, suppression decision.
* Exit: tests green; `enablePacketModel=false` behavior byte-identical.

**Phase 2 — runtime integration**
* IC2-package adapter over `Grid`/`Node`/`Tile`; raw-map shadow mixins; `invalidate` dirty hook;
  `startCalculation` cancel; `processChanges` takeover; stats overrides; effects port; legacy passes
  disabled in packet mode.
* Exit: dedicated-server smoke test, no mixin errors; legacy mode still works.

**Phase 3 — validation + hardening**
* In-game checklist (§9), perf measurement, conflict detection/logging, docs; decide the open questions
  in §5.
* Exit: checklist green, perf within budget, docs updated.

**Phase 4 — default flip + cleanup**
* Decide whether packet mode becomes the default; retire `EnetCableEffects`/`EnetConductorRegistry`/
  `MixinEnergyNetLocalOverload` and the linear-model override (or keep them as an explicit legacy
  fallback for one release).

---

## 9. Validation checklist (packet mode)

* [ ] 2× BatBox → one LV machine: **no explosion** (the headline gap-1 fix).
* [ ] MFSU → copper cable → LV machine: cable melts **and the machine does not explode** (gap 4).
* [ ] MFSU → glass fibre → LV machine: glass fibre survives, machine explodes.
* [ ] MFSU direct → LV machine: machine explodes, no cable involved.
* [ ] Junction: two 32 EU/t sources into one copper cable feeding one LV machine — cable does not
      melt (per-path packets; legacy mode would aggregate).
* [ ] Demand cap: a nearly full machine receives only its remaining demand; the rest stays with the
      source.
* [ ] Rejection: a full machine causes no draw/no explosion.
* [ ] Storage: BatBox→BatBox and MFSU→MFE charge with packet loss; energy in ≈ energy out + loss.
* [ ] Transformer: 512 into the HV side, 128 out the other side; no false explosion.
* [ ] Transformer upgrades: a machine with many upgrades still charges and runs (gap 13).
* [ ] Multi-grid source: a storage block feeding two separate networks splits its offer, no dupe.
* [ ] Shock: two paths near one entity sum their shock energy once per tick; a long single run doesn't
      multiply by length.
* [ ] Cable strip path is inert (`getInsulationBreakdownEnergy()` 9001 / no-op), matching 1.12.2.
* [ ] EU reader/`getNodeStats`: shows packet-tier semantics (documented, not a bug).
* [ ] `enablePacketModel=false`: full legacy regression (explosions, meltdown, shocks, tooltips).
* [ ] Save/reload, chunk unload/reload, dedicated server, two players: no stale paths, no crash,
      effects sync.
* [ ] Perf: 1,000 cables / 100 sources; tick time comparable to legacy; no GC spikes on chunk load.
* [ ] Conflict smoke test with a known enet-mixin mod loaded (if available) — either clean disable or
      documented incompatibility.

---

## 10. Decisions (confirmed)

| # | Decision | Outcome |
|---|---|---|
| 1 | Loss constants | Keep 1.7.10 `0.4`/`0.2` for now; make it configurable and plan for 1.12.2 `0.002`/`0.001` to become the eventual default |
| 2 | `NodeStats.voltage` | Rated volts by default; config to opt into raw tier; revisit later |
| 3 | Gap 4 suppression | Match 2.8.222: suppress the sink explosion when a conductor on that path overloads |
| 4 | Rollout | `enablePacketModel=false` through Phases 1–3, `true` at Phase 4, key retained for at least one release |
| 5 | Gap 16 multi-packet | Implement `packetCount`/`distributeMultiple` internally now; publish an opt-in API later (Phase 4) |

The analysis below records the options and reasoning behind these outcomes.

### 10.1 Loss constants — keep 1.7.10 (`0.4`/`0.2`) or port 1.12.2 (`0.002`/`0.001`)?

**Facts.** A link's loss is the mean of its endpoints' `getInnerLoss()`:

| Link | 1.7.10 | 1.12.2 (2.8.222) |
|---|---|---|
| machine ↔ machine | `(0.4+0.4)/2 = 0.4` | `(0.002+0.002)/2 = 0.002` |
| machine ↔ cable | `(0.4+0.2)/2 = 0.3` (copper) | `(0.002+0.2)/2 = 0.101` |
| cable ↔ cable | `(0.2+0.2)/2 = 0.2` | `0.2` (identical) |

Cable conduction loss is the *same constant in both versions* — only links touching a machine differ. 1.7.10
defines `nonConductorResistance = 0.2` but never reads it in the enet; 1.12.2 derives its 0.002 inner loss
from `EnergyNetSettings.nonConductorResistance = 0.001`. Practical effect: long runs are dominated by cable
loss and barely change (MFSU + 10 copper hops: 2.4 vs 2.002); short/direct paths change a lot
(BatBox→BatBox: 0.4 EU/t ≈ 1.25 %, ~495 EU per full transfer, vs 0.002 ≈ 0.006 %, ~2.5 EU).

* **Option A — keep 1.7.10 constants.** Isolates the refactor: only *allocation* semantics change, so any
  in-game difference is attributable to the packet model, not to balance. Preserves the loss behavior users
  of the current forcing mode already see, and keeps the documented 495 EU example true. Downstream: nothing
  new beyond the packet model itself.
* **Option B — port 1.12.2 constants.** Machine hops become effectively free, matching 1.12.2's economy and
  making direct connections lossless-feeling. Cost: a second delivery change stacked on the first, the old
  numbers become wrong, and the change must be scoped to the packet-model path loss only — `Node.getInnerLoss()`
  is also read by the legacy linear solver, so a blanket mixin would silently change legacy mode too.
* **Recommendation: keep 1.7.10 constants for Phases 1–3.** If validation says machine-adjacent loss feels
  wrong versus 1.12.2, add `packetModelMachineLoss` (0.4 default, 0.002 optional) applied **only when building
  packet paths**, leaving legacy mode untouched. A/B it in-game before deciding; do not couple the choice to
  the packet-model landing.

### 10.2 `NodeStats.voltage` — 1.12.2 tier vs 1.7.10 volts

**Facts.** 1.12.2 returns `getTierFromPower(maxPacketConducted)` — a tier *index* (1, 2, 3…), not volts.
Consumers we found:

* IC2 1.7.10 itself: `ic2.core.item.tool.ContainerMeter` (the EU meter GUI) calls `NodeStats.getVoltage()`
  and renders it next to `EnergyIn`/`EnergyOut`; `TileEntityCableDetector` and the `BlockCable` comparator
  use `getEnergyIn()` only (unaffected by the voltage choice).
* IC2 1.12.2's own `ContainerMeter` reads the same `getVoltage()`, so the tier convention is what the 1.12.2
  UI was built against.
* External: WAILA plugins, energy-meter addons, OpenComputers/pack-script integrations.
* Observed on the production pack in **legacy** mode: BatBox/CESU/MFE/MFSU read 25.7 / 102.5 / 409.8 / 1.6 kV
  (solved voltages, roughly 80% of tier). With packet mode active the same setups report 32 / 128 / 512 / 2048 V
  by default, or 1 / 2 / 3 / 4 with `packetModelTierVoltage=true`.

* **Option A — exact tier.** Full parity, zero mapping logic. But in 1.7.10 the field is labelled voltage and
  the stock EU meter would show `4` for an MFSU — a user-visible regression in IC2's own UI, and every
  1.7.10-era addon that reads it was written for volts.
* **Option B — rated volts** (`getPowerFromTier(getTierFromPower(max))` → 32/128/512…). Preserves 1.7.10 UI
  meaning; the packet model's *logic* does not consume `NodeStats` at all (effects read path data), so this is
  purely a display/compat surface. Diverges from 1.12.2 by exactly this one field.
* **Option C — config switch** (`packetModelTierVoltage`, default one of the above). Cheap, but two semantics
  in the wild makes support and docs worse.
* **Recommendation: rated volts by default, with a config to opt into raw tier.** Revised from the earlier
  "exact tier" lean: `getEnergyIn/Out` becoming packet-based is a strict improvement, but showing tier
  indexes in 1.7.10's voltage field breaks stock UI and addons for no functional gain. Document the one-field
  deviation; expose `packetModelTierVoltage=true` for strict parity.

### 10.3 Gap 4 — `conductorOverload` suppression

2.8.222 skips the sink explosion when a conductor on the same path overloads; post-1.12.2 calculators removed
that. With the packet model the check is a field on the path (cheap); with the legacy per-node pass it was
gap 4 (hard).

* **Suppression on** = the cable acts as a fuse: MFSU → copper → LV machine melts the copper and the machine
  survives. Per-path, so a machine fed by two paths can still explode through the path whose cable held.
* **Suppression off** (today's legacy behavior) = both melt and explode; the machine can die even though its
  feed already melted, which reads as a double punishment/random.
* **Recommendation: match 2.8.222 and suppress.** It is the stated target, it is nearly free in the packet
  model, and it makes cable choice meaningful as protection. Note the divergence from modern IC2 in the docs;
  no config (keep the surface small). Validate the visual clarity of "cable melts, machine lives" in-game.

### 10.4 `enablePacketModel` default and rollout

Forge only applies a config default when the key is absent, so a later default flip silently changes behavior
for every existing install — the rollout is a planned event, not a code detail.

* Off through Phases 1–3: the branch/PR lands without changing anyone's active behavior; both code paths stay
  live and testable; the packet model gets validated on real saves before it is authoritative.
* Flip to `true` at Phase 4 when the §9 checklist is green (packet model proves itself against the legacy
  mode in the same world). Keep the key as a documented fallback for at least one release; retire
  `EnetCableEffects`/`EnetConductorRegistry`/`MixinEnergyNetLocalOverload` after that (or keep them behind the
  key for one more cycle if pack authors need it).
* Release notes must call out the delivery changes (multi-injection, demand caps, source throttling) and the
  `NodeStats` deviation from §10.2; `/ic2tweaks status` should print the active model.
* **Config migration:** once `enablePacketModel=false` exists in a user's config, changing the code default
  does not flip them (Forge keeps the stored value). Phase 4 needs a migration strategy — e.g. a tri-state
  `packetModelMode=auto|legacy|packet` (new key, defaults to `auto` → packet) or a config version bump — so
  the new default reaches existing installs while an explicit legacy choice is preserved.
* When packet mode is on, stop forcing `useLinearTransferModel` (gap 10); when off, keep today's behavior
  exactly.
* **Recommendation: off now, on at Phase 4, key retained for one release.**

### 10.5 Gap 16 — our own multi-packet source interface?

1.7.10 has no `IMultiEnergySource`, and every 1.7.10 source is one packet per tick by API contract. Without
it, an over-offering source is throttled to `powerFromTier(tier)` per tick (pitfall 6), which is 1.12.2's
behavior for single-packet sources.

* **Leave closed**: simplest, no new API promise; the port still reads a `packetCount` internally, fixed at 1.
* **Expose our own opt-in interface** (e.g. `com.mrfuzzihead.ic2tweaks.api.IMultiPacketSource` with
  `sendMultiplePackets()` / `getPacketAmount()`): a few lines to wire `distributeMultiple`; lets future ports
  of 1.12.2 machines source several packets per tick; opt-in only, so no existing mod changes behavior.
  Downsides: a new public API to support, and pack authors could use it to exceed tier limits by design.
* **Recommendation: implement the internal `packetCount`/`distributeMultiple` support during the port (keeps
  the port faithful to 1.12.2), but do not publish the interface until Phase 4.** Decide then whether any mod
  wants it; if not, it stays closed with the internal hook dormant.

---

## 11. Compatibility reconnaissance (TODO — before any packet-model code is pushed)

Our hooks overlap the few IC2 internals another mod is most likely to touch: `EnergyNetLocal.processChanges`,
`Grid.invalidate`/`startCalculation`, `EnergyNetLocal.getNodeStats`/`getTotalEnergyEmitted|Sunken`,
`TileEntityCable`, and package injection into `ic2.core.energy`. Static reconnaissance of the production pack
is therefore a release gate.

### 11.1 Sources

* Primary: `C:\Users\Duncan\AppData\Roaming\PrismLauncher\instances\FuzzCraft-Legacy\minecraft\mods`
  (234 jars, `industrialcraft-2-2.2.828-experimental.jar`, `hodgepodge-2.7.216.jar`).
* Secondary: `...\ModrinthApp\profiles\FuzzCraft\mods`, `...\ModrinthApp\profiles\Alone_ Modded World\mods`.
* Re-run after every pack update; new mods (and new versions of Hodgepodge-style bugfix mods) can add mixins at
  any time.

### 11.2 Method (repeatable)

1. Enumerate jars; skip IC2 itself.
2. For each jar, extract all entries to a temp dir and grep **every** file (classes, JSON, cfg) for
   `ic2[/.]core[/.](energy|block/wiring|coremod)`. Constant pools expose mixin target class refs, ASM string
   constants and AT targets, so this catches mixins, coremods and ATs alike.
3. Separately list jars that *ship classes* under `ic2/core/` — package injectors, the same trick this mod may
   use for `ic2.core.energy`.
4. Read `*mixins*.json` and `*_at.cfg` for any `ic2` reference (belt and braces; some tools keep targets in
   JSON rather than bytecode).
5. Controls: step 2 must hit on the IC2 jar (positive) and miss on a known-unrelated jar (negative).
6. Record every hit in a `COMPAT_RECON.md` matrix: mod, mechanism (mixin/coremod/AT/package injection/API),
   IC2 classes touched, overlap with our hooks, risk, mitigation, test status.

Notes from the first run: naive `unzip` wildcards do not recurse (`*` does not cross `/`), so use extract-all or
an offline Python `zipfile` scan (`namelist()` + `read()`) — full extraction of all 233 jars exceeded a 15-minute
budget and must be run as a batch job, not on the fly.

### 11.3 First-pass findings (IC2-named and known-IC2 jars only)

* **Hodgepodge 2.7.216** — mixes into IC2 armor (hazmat/jetpack/nano/quantum/solar helmet/static boots),
  night-vision, water kinetic generator, crops, the nuclear reactor and its chambers, tin cans, cells, and IC2
  localization/resource loading. **No references to `ic2/core/energy` or `ic2/core/block/wiring`** — no hook
  overlap with us. Its reactor/source mixins change *how much* energy is offered, which the packet model
  handles like any other source (throttling per §10.5 / pitfall 6).
* **IC2NuclearControl 2.7.17**, **Ic2ExpReactorPlanner** — IC2 API only, no internal references.
* **IC2 addons in the pack** (`AdvancedSolarPanel`, `GraviSuite`/`gravisuiteneo`, `advancedmachines`,
  `CompactSolars`, `EnergyControl`, `SolarFlux`) — API consumers, i.e. subject to the delivery-change matrix in
  §7, not to mixin conflicts. `advancedmachines` and the solar mods are the best in-game canaries (sinks that
  charge slowly, sources that offer at their tier).

### 11.4 Risk taxonomy and mitigation

| Risk | Signal | Mitigation |
|---|---|---|
| Mixin/ASM collides with our exact hooks | refs to `ic2/core/energy/…` or `processChanges` in bytecode/JSON | `IMixinConfigPlugin` conflict list, `setApplyIf` gating, log-and-disable, document |
| Package injection into `ic2/core/*` | classes shipped under `ic2/core/` | detect at scan time; same-package split is additive unless names collide |
| AT widening IC2 internals | `*_at.cfg` referencing `ic2` | scan; ATs from other mods do not conflict with ours |
| Enet replacement (`EnergyNet.instance = …`) | class refs to `ic2/api/energy/EnergyNet` + custom `IEnergyNet` impl | detect at runtime (`EnergyNet.instance` type), disable packet mode with a warning |
| API consumers (sinks/sources/conductors/stats) | `ic2/api/energy` refs | §7 matrix; in-game canaries; release notes |

### 11.5 TODO

* [x] Write the offline batch scanner (`tools/scan_ic2_refs.py`) and run it over the primary pack and both
      secondary packs; raw JSON in `build/recon/ic2-scan.json`, review artifact in `COMPAT_RECON.md`.
* [x] Initial triage of the high/modifying hits (EnergyControl, Angelica, Galacticraft, Hodgepodge).
* [ ] Confirm EnergyControl's storage/cable hook behavior in-game while packet mode is on (its `IC2Hooks`
      class is the only real `ic2/core/block/wiring` modification in the pack).
* [ ] Inspect Hodgepodge's reactor/chamber mixins for source-output changes that interact with the packet
      model's throttling.
* [ ] Add the `IMixinConfigPlugin` conflict list once the runtime behaviour is known (Phase 2/3).
* [ ] Re-run after any packet-model code lands and again at Phase 4 before the default flip.

### 11.6 Baseline result (2026-10-19 snapshot)

| Pack | Jars | critical | high | medium | api |
|---|---:|---:|---:|---:|---:|
| FuzzCraft-Legacy | 233 | 0 | 1 | 26 | 10 |
| FuzzCraft-Modrinth | 20 | 0 | 0 | 0 | 0 |
| Alone-Modded-World | 26 | 0 | 0 | 0 | 0 |

The two Modrinth profiles are 1.20 packs and contain no IC2 references. The primary pack's hits were triaged:

* **EnergyControl** (high) — `com.zuxelus.energycontrol.hooks.IC2Hooks` (HookLib transformer) references
  `ic2/core/block/wiring` storage/MFSU classes. It does not touch the enet; its energy display/upgrade
  behaviour is a storage canary during validation.
* **Angelica** (medium after triage) — the `TargetedMod` enum lists `ic2.core.coremod.IC2core` (target /
  exclusion list) and `MixinRenderLiquidCell` is client-side fluid-cell rendering. No enet or cabling.
* **Galacticraft** (medium) — `BlockEnclosed`/`CompatibilityManager` reference `TileEntityCable` as plain
  classes; no transformer hook.
* **Hodgepodge** (medium) — IC2 armor, crops, reactor/chamber and water-kinetic mixins; no enet or cabling.
  Its reactor mixins change source offers, making it a delivery canary.
* **AFSU, advancedmachines, IC2NuclearControl, Waila, OpenPeripheralIntegration, NotEnoughResources, …**
  (medium) — plain use/extension of IC2 machine and storage internals; delivery-change canaries.
* **API-only** (10 jars) — no internals; subject to the §7 delivery changes.

**Conclusion:** no mod in this pack statically targets the packet-model seams (`ic2.core.energy.Grid`,
`EnergyNetLocal.processChanges`, `getNodeStats`). The remaining risk is ASM that matches obfuscated names at
runtime and future pack updates; re-run the scanner before the Phase 4 default flip.

---

## 12. Implementation status (Phases 1–2)

### 12.1 Phase 1 — pure core

Phase 1 was pure Java with no runtime behavior change; Phase 2 below wires it into the live net behind a
default-off flag.

| Area | Class | Port of |
|---|---|---|
| Graph snapshot | `enet/packet/PacketGraph` | `Grid`/`Node`/`NodeLink`/`Tile`, reduced to the fields the calculator needs |
| Path | `enet/packet/PacketPath` | `EnergyPath` (loss, conductor list, threshold minima, bounding box, per-tick counters) |
| Math | `enet/packet/PacketMath` | `EnergyNet.getPowerFromTier`/`getTierFromPower`, `emit`'s effective amount, shock damage |
| Calculator | `enet/packet/PacketGrid` | `updateCache` (Dijkstra per source + source-to-source composition + `roundLossDown`), `runCalculation`/`distribute`/`distributeSingle`/`distributeMultiple`/`emit`, `applyCableEffects` decisions, `getNodeStats` |
| Adapter boundary | `PacketInputs`, `PacketInjectionHandler`, `PacketTickResult`, `PacketNodeStats` | per-tick source/sink data, inject/draw callbacks, effect decisions |

Decisions encoded here:

* **Loss constants** come from the graph the adapter builds (Phase 2 will use 1.7.10's `0.4`/`0.2`
  values, configurable later per §10.1).
* **Gap 4 suppression** is implemented in `PacketGrid.applyEffects` (a path with a conductor overload can
  never add its sink to `sinksToExplode`).
* **Multi-packet** distribution is implemented and `PacketInputs.sourcePackets` defaults to 1; the public
  opt-in API is deferred per §10.5.
* **Stats** expose `maxPacket` + tier; the Phase-2 adapter maps that to rated volts (or raw tier when the
  config asks for it) per §10.2.

Tests: 20 new (`PacketMathTest` 5, `PacketGridTest` 15)
covering tier/power boundaries, effective-packet accounting, min-loss path selection, direct paths,
loss-adjusted delivery, demand caps, full/partial rejection, melt/shock/suppression, explosion without
conductor overload, insulation strip, per-path suppression with two sources, multi-packet spreading,
node stats, `roundLossDown`, the source-to-source composition quirk, and an inactive grid.

The literal 1.12.2 source-to-source composition (`loss -= link.getLoss()`, junction conductor omitted) is
replicated and pinned by a test; see §2.5.

### 12.2 Phase 2 — live adapter and gated mixins

Delivered on this branch, still **default-off** (`enablePacketModel=false`):

* **`ic2/core/energy/Ic2TweaksPacketModel`** — builds the pure graph from a live `Grid` (nodes, links,
  conductor thresholds, sub-tile bounds), attaches per-grid state through the `Grid` mixin, caps and shares
  source offers through `PacketOfferLedger`, supplies sink demand/tier, maps handler directions and
  `injectEnergy`/`drawEnergy`, applies melt/strip/explode/shock decisions, and serves
  `getNodeStats`/`getTotalEnergyEmitted`/`getTotalEnergySunken` from the packet paths.
* **`MixinGridPacketModel`** — `invalidate` drops cached state; `startCalculation` and
  `prepareCalculation` are cancelled while packet mode is active (no upfront source draw, no async linear
  solve, no duplicate getter calls); exposes `PacketGridStateHolder` (raw node map + state) to the adapter.
* **`MixinEnergyNetLocalPacketModel`** — replaces `processChanges` with the packet tick (the only
  `locked == false` window), clears the now-unused legacy `changes`/`removedTiles`, and overrides the three
  stat methods.
* **Config** — `enablePacketModel`, `packetModelRoundLossDown`, `packetModelTierVoltage`; legacy mixins are
  gated off in packet mode, `EnetCableEffects`/`EnetOverload` keep a runtime guard, and
  `EnetOverload.explodePacketTile` implements 1.12.2's per-sub-tile explosion/handler order.
* **Tests** — seven new (source-offer cap, `NodeStats` voltage mapping, cross-grid offer ledger, two-source
  non-aggregation, two-path cable max-not-sum, and a real `Grid`/`Tile`/`Node` graph-mapping test against
  fake IC2 tiles), **38 total**, `./gradlew build` green.
* **Dedicated-server smoke** — `enablePacketModel=true`: GTNHMixins logs the legacy mixins as not loaded and
  both packet mixins as loaded, startup logs `Energy-net model: packet (1.12.2-style)`, server reaches
  `Done` with no mixin or tick errors. `false`: the inverse (packet mixins skipped, legacy loaded).

Behavior notes for validation:

* State is rebuilt on the first tick after any `Grid.invalidate`; a grid removed by another grid's effects
  is skipped via an `EnergyNetLocal.grids` membership check.
* Source offers are capped once per tile (`min(offered, powerFromTier(tier) × packets)`) and shared across
  the grids the tile feeds; the adapter draws only the used amount.
* `getNodeStats` is read-only and returns zeros until the first packet tick (no lazy rebuild on the stats
  path); client-side stats remain zero, as before.
* EnergyControl is the only pack mod that modifies cabling/storage internals (see §11.6); its storage
  display is the first in-game canary.
* The 1.7.10 loss constants still come from the live graph; §10.1 remains pending.

### 12.3 Phase 3 canaries (packet mode, dedicated server)

Run with `JAVA_TOOL_OPTIONS="-Dic2tweaks.canary=true" ./gradlew runServer` while packet mode is on. The
developer harness (`dev/CanaryHarness`, inactive unless that system property is set) builds four IC2 rigs at
`y=160` with storage blocks and cables, waits 80 ticks, logs `[canary] PASS/FAIL` and stops the server.

Result (2026-10-19): **ALL PASS**.

| Canary | Observed |
|---|---|
| MFSU → LV sink directly | sink exploded (`air`) |
| MFSU → copper cable → LV sink | copper cable melted; sink **survived** (2.8.222 suppression) |
| 2×BatBox → one LV sink | no explosion; stats `in=63.2`, `voltage=32.0` |
| 2×BatBox → one tin cable (breakdown 33) → LV sink | no melt; cable stats `in/out=62.8`, `voltage=32.0` |
| Cow at the overloaded tin cable | shock damage accumulated; health 0 |

The aggregation canaries that motivated gap 1 are therefore validated in-game: two 32 EU/t packets do not
add up to 64 for explosion or melt checks, and a melting cable suppresses the sink explosion.

Still manual because they need specific mod/pack setups: transformer packet conversion, a machine with many
transformer upgrades (tier-starvation fix), EnergyControl's storage display, and Hodgepodge's reactor source
changes.

### 12.4 Phase 4 gate (manual, before the default flip)

The calculation layer is covered by the 38 tests plus the §12.3 canaries. Before changing the default, run
this short pack-level pass with `enablePacketModel=true` and compare against `false` where noted. First
confirm the model is actually active (startup log `Energy-net model: packet`, or `/ic2tweaks status`); in
legacy mode the voltages are solved values (roughly 80% of tier) and many-upgrade machines still starve,
which is expected, not a packet-model result.

* [ ] **Transformer**: MFE → HV transformer → MV machine (or MFSU → EV transformer → HV); correct throughput
      and no false explosion; also test step-up mode if the pack uses it.
* [ ] **Upgraded machine**: 5–6 transformer upgrades fed by an MFSU; it charges and runs (the gap-13 fix).
* [ ] **Hodgepodge reactor (deferred)**: compare reactor EU/t with packet mode on/off when it is practical.
      Hodgepodge's reactor mixins can make a source offer more than one tier packet; 1.12.2's single-packet
      cap would throttle it. Decide whether to exempt/represent multi-packet sources before flipping the
      default. EnergyControl is a dead mod and is out of scope.
* [ ] **EU meter / `NodeStats`**: rated volts (32/128/512), plausible energy in/out.
* [ ] **Save/reload and chunk unload/reload**: no stale paths, dupes or crashes.
* [ ] **Two networks on one storage block**: one storage tile with two *independent* cable runs (not
      connected to each other) feeding two sinks; total delivered should not exceed the shared per-tick offer.
* [ ] Performance sanity on a real base (optional).
* [ ] **Fallback**: set `enablePacketModel=false`, restart, legacy behavior intact.

Phase 4 code (default flip, legacy retirement) can be prepared now, but the default should not flip until this
list is green.

---

## 13. Residual gaps after the packet model (and the aggregation question)

### 13.1 Does aggregation still happen?

With `enablePacketModel=true` and the Phase-2 adapter in place, **no fault threshold uses a sum across sources
or paths**:

* `PacketGrid.emit` records `path.maxPacketConducted = max(effectiveAmount, …)` for each source→sink path.
* `PacketGrid.applyEffects` evaluates each event path at that path's `maxPacketConducted`; a cable on several
  paths uses the **max**, not the sum. A junction carrying two 32 EU/t packets does not melt a 33-breakdown
  cable.
* Machine explosions collect `sink -> max offending packet` and compare it to the sink tier; multiple paths to
  one machine do not add up.

So the 2×BatBox→LV false explosion and the junction false melt disappear once packet mode is active;
`PacketGridTest.twoSourcesDoNotAggregateForExplosion` and `cableOnTwoPathsUsesMaxNotSum` pin exactly those
cases. Physical
energy delivered by several paths still adds in the machine's buffer (that is real energy, not an overload
trigger), and a cable's actual throughput still sums physically; only the *fault checks* are per packet,
exactly like 1.12.2.

Aggregation can still appear if:

1. `enablePacketModel=false` (the legacy per-node mode, before Phase 4 or as a deliberate fallback).
2. A mod bypasses or replaces IC2's `EnergyNet`, so our calculator is not driving it.
3. The adapter forgets 1.12.2's source-offer cap
   (`min(getOfferedEnergy(), powerFromTier(tier) × packets)`), letting an over-offering single-packet source
   push a too-large packet down one path.
4. The source is multi-packet: each chunk is evaluated independently and `maxPacketConducted` is the largest
   chunk, not the sum.
5. Multiple independent grids are merged by mistake; per-grid calculators and per-tile offer sharing keep
   them separate.

Validation canaries: 2×BatBox→LV (no explosion), MFSU→copper→LV (cable melts, machine survives), a junction
fed by two 32 EU/t sources (cable does not melt at 64), and a cable that is a member of two paths.

### 13.2 Residual gaps vs 1.12.2

| # | Gap | Impact | Disposition |
|---|---|---|---|
| 1 | Absorption policy is 1.7.10-adapted (insulated cables shock only while melting) | Shock only; melt/explosion thresholds are exact | Deliberate (§10, gap 14); revisit if 2x/3x insulation becomes usable |
| 2 | Loss constants stay 1.7.10 `0.4`/`0.2` initially | Delivered energy on machine-adjacent hops; no fault-check impact | Config planned; eventual 1.12.2 defaults (§10.1) |
| 3 | `NodeStats.voltage` reports rated volts, not the 1.12.2 tier index | Display/API only; effects read path data | Deliberate (§10.2); config for strict tier |
| 4 | No `ExplosionIC2.Type.Electrical` in 1.7.10 | Blast visuals/type only | Unavailable; plain blast is the closest match |
| 5 | No public multi-packet source API yet | Third-party multi-packet sources cannot opt in | Internal support exists; API deferred to Phase 4 (§10.5) |
| 6 | Config surface differs (separate meltdown/explosion/shock toggles; no IC2.ini keys) | Pack tuning, not simulation | Superset; documented (gap 9) |
| 7 | Topology/lifecycle timing differs (1.7.10 applies changes synchronously; 1.12.2 queues them) | Possible one-tick routing difference after place/break | Adapter rebuilds on `invalidate`; validate chunk reload/place-break cases |
| 8 | Literal 1.12.2 source→source composition quirks (subtracted link, omitted junction conductor) | Could under-report loss/effects on that one junction | Replicated and pinned (`PacketGridTest`); revisit if correctness should beat parity |
| 9 | `getNodeStats`/`getTotalEnergy*` semantics are packet-based and overridden | External meters see delivered energy and rated volts | Matches §10.2; document for mod authors |
| 10 | `IOverloadHandler`/`IExplosionPowerOverride` are our own interfaces | 1.12.2 mods cannot implement them by binary name | Semantic parity; document the API |
| 11 | `explodeMachine` achievement is not issued | Minor cosmetic/advancement difference | Add in Phase 2/3 if desired |
| 12 | Entity shock AABB/sub-tile semantics are adapter-side | Damage can differ at box edges | Replicate 1.12.2 per-sub-tile/bbox logic and validate |
| 13 | `Tile.isDisabled`/removal timing differs (1.7.10 removes immediately) | Mid-tick removal ordering | Collect effects, apply after distribution, re-check validity |
| 14 | Performance profile differs (Dijkstra on topology change, per-path tick work) | No functional gap; big bases need measurement | Phase 3 perf test (1k cables / 100 sources) |
| 15 | Mods bypassing IC2's enet (e.g. custom `IEnergyNet`) are outside the calculator | Out of scope, same as 1.12.2 | Detect and disable/warn (conflict plugin) |
| 16 | Target is 2.8.222, not later unified calculators | Deliberate | 2.8.222 is the reference for gap 1/4 |
| 17 | Insulation stripping is a no-op | None | Parity: 1.12.2 also has 9001/no-op |

**Bottom line:** after Phase 4 the only *rule* deviation that can still bite is the deliberate shock-absorption
policy (item 1); loss constants, `NodeStats` voltage and the multi-packet API are config/API decisions, and the
remaining rows are lifecycle/visual/edge-case differences. The aggregation false positives that motivated gap 1
are gone when packet mode is active.
