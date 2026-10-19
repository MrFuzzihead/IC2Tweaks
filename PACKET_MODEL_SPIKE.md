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
**other sources** it reaches (source→source chaining). Each `EnergyPath` precomputes the conductor
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
   delivery change (check IC2's own generators and storage blocks first; an MFSU at tier 3 offers 512,
   aligned).
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
  (previously, ≥6 upgrades on an MFSU made a machine draw almost nothing).

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

## 10. Open decisions

1. Loss constants: keep 1.7.10 (`0.4`/`0.2`) or port 1.12.2 (`0.002`/`0.001` + `roundEnetLoss`)?
2. `NodeStats.voltage`: tier (faithful) vs volts (compat shim)?
3. Gap 4 suppression: match 2.8.222 (suppress) — recommended — or modern IC2 (no suppression)?
4. When does `enablePacketModel` flip to `true` by default, and do we keep a legacy fallback release?
5. Do we expose an `IMultiEnergySource`-like interface of our own for multi-packet 1.7.10 sources
   (gap 16), or leave it closed?
