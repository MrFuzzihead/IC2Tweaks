# IC2 Voltage / Packet Overload System — 1.7.10 Implementation Analysis

**Subject:** adding the IC2 Experimental 1.12.2 voltage/packet overload system to 1.7.10 IC2
**Target:** `IC2Tweaks` (`com.mrfuzzihead.ic2tweaks`), a standalone IC2 core-tweak mod for 1.7.10 Forge. The design was originally prototyped in `AdvancedMachines-Legacy` and has been moved here; the feature is **global** (all IC2 machines and cables), not tied to any machine addon.
**Decision recorded here:** implement **Option A — mixin into the IC2 energy net** and port the 1.12.2 "effects pass". No code has been changed yet; this is the design/findings document.

---

## 1. Why Option A (and not the alternatives)

| Option | What it does | Verdict |
|---|---|---|
| **A. Mixin the energy net** (`EnergyNetLocal` / `Grid` / `Node`) | Global enforcement for every machine + cable, using the net's own solved data | **chosen** |
| B. Mixin `TileEntityElectricMachine` | Global *machine* explosions only; no cable meltdown/shock; same model caveats as C | rejected (less complete) |
| C. Override `injectEnergy` in a machine addon's five TEs | that addon only, machine-local math | rejected (scope) |
| D. Contribute back to IC2 | — | impossible: the 1.7.10 IC2 source is unmaintained/closed |

Reasons: (1) we cannot patch IC2; (2) it must apply to all IC2 machines/addons, not just the five advanced machines; (3) faithfulness to the 1.12.2 mechanism is preferred over making each machine do its own calculation.

The good news: **1.7.10 already contains almost all of the machinery.** The new graph/`Node`/`Tile`/tier model, `Tile.maxCurrent`, `Node.getMaxCurrent()`, `IEnergyConductor.removeConductor()`, `getConductorBreakdownEnergy()`, `IC2DamageSource.electricity` and `IC2.explodeMachineAt()` all exist. What was never backported is the *effects pass* that consumes them — exactly what Option A adds.

---

## 2. Reference behaviour: IC2 Experimental 1.12.2

The 1.12.2 energy calculator (`ic2.core.energy.leg.EnergyCalculatorLeg`, the default packet/path calculator) does two things per tick:

### 2.1 During distribution (`emit`)

For each source → sink path:

```java
amount = Math.min(injectAmount, sinkDemand);
rejected = sink.injectEnergy(path.targetDirection, amount, EnergyNet.instance.getTierFromPower(amount));
effectiveAmount = Math.max(0, amount - rejected + path.loss);
path.energySupplied += amount - rejected;
path.maxPacketConducted = Math.max(effectiveAmount, path.maxPacketConducted);
if (effectiveAmount > path.minEffectEnergy
        || amount > EnergyNet.instance.getPowerFromTier(sink.getSinkTier())) {
    data.eventPaths.add(path);
}
if (amount >= sinkDemand || rejected > 0) data.activeSinks.remove(path.target);
```

Key point: the overload quantity is **`path.maxPacketConducted`** — the *per-path, per-packet* energy, not the sum of all sources.

### 2.2 After distribution (`applyCableEffects`)

Gate: `misc/enetCableMeltdown` (default **true**). For each event path with `amount = path.maxPacketConducted`:

1. **Cable meltdown / insulation loss** — `conductorOverload = amount > path.minConductorBreakdownEnergy || amount > path.minInsulationBreakdownEnergy`.
   - `amount > conductor.getConductorBreakdownEnergy()` → `cablesToRemove` → `removeConductor()`
   - else `amount > conductor.getInsulationBreakdownEnergy()` → `cablesToStrip` → `removeInsulation()`
2. **Shock** — if `amount > path.minInsulationEnergyAbsorption`, for each conductor with `amount > conductor.getInsulationEnergyAbsorption()`, entities near the conductor take `(amount - absorption) / 64` damage (accumulated per entity).
3. **Machine explosion** — `if (!conductorOverload && amount > EnergyNet.instance.getPowerFromTier(sink.getSinkTier()))` → record the sink tile with the maximum `amount`.

Then sinks are passed to `explodeTile(world, tile, maxPower)`:

```java
if (!MainConfig.get().get("misc/enableEnetExplosions").getBool()) return;      // default true
if (te instanceof IOverloadHandler
        && ((IOverloadHandler) te).onOverload(tier)) return;                    // graceful
float power = 2.5f;
if (te instanceof IExplosionPowerOverride) {
    IExplosionPowerOverride o = (IExplosionPowerOverride) te;
    if (!o.shouldExplode()) return;
    power = o.getExplosionPower(tier, power);
}
world.setBlockToAir(pos);
new ExplosionIC2(world, null, x + .5, y + .5, z + .5, power, 0.75f, ExplosionIC2.Type.Electrical).doExplosion();
```

The two graceful-hook interfaces (not present anywhere in 1.7.10):

```java
public interface IOverloadHandler { boolean onOverload(int tier); }
public interface IExplosionPowerOverride {
    boolean shouldExplode();
    float getExplosionPower(int tier, float defaultPower);
}
```

Config defaults in 1.12.2: `misc/enableEnetExplosions = true`, `misc/enetCableMeltdown = true`.

---

## 3. What 1.7.10 (the exact dependency) already provides

The build resolves `net.industrial-craft:industrialcraft-2:2.2.828-experimental` to
`curse.maven:ic2-242638:2353971` (its `mcmod.info` says `2.2.827-experimental`). Decompiled for this analysis.

### 3.1 Machines

- `TileEntityElectricMachine.injectEnergy(ForgeDirection dir, double amount, double voltage)` **ignores `voltage`** entirely; it adds `amount` if the buffer has room and returns `amount` if full (all-or-nothing).
- `getSinkTier()` returns the live `tier`, which **does include transformer upgrades** (`TileEntityStandardMachine.setOverclockRates()` → `setTier(applyModifier(defaultTier, upgradeSlot.extraTier, 1.0))`). So a tier check respects upgrades for free.
- `EnergyNetLocal.processChanges()` calls `sink.injectEnergy(change.dir, change.getAmount(), change.getVoltage())`.

### 3.2 The data carrier

`ic2.core.energy.Change` is package-private: `Node node`, `ForgeDirection dir`, `double amount`, `double voltage`.

### 3.3 Cables (the dead layer)

- `TileEntityCable.getConductorBreakdownEnergy() = getMaxCapacity(cableType) + 1`. Breakdown thresholds: tin **33**, copper **129**, gold **513**, HV **2049**, glass fibre **8193**.
- `TileEntityCable.getInsulationBreakdownEnergy()` returns **9001**, and `removeInsulation()` is a **no-op** → the insulation-strip half of 1.12.2 is vestigial in 1.7.10.
- `TileEntityCable.getInsulationEnergyAbsorption()` returns small values (5, 6, …) per cable type.
- `Tile.maxCurrent = ((IEnergyConductor) te).getConductorBreakdownEnergy()` (or `Double.MAX_VALUE`), exposed as `Node.getMaxCurrent()`. **It is never read.**
- `TileEntityCable.removeConductor()` exists (sets block to air + fires the fizz network event) and is **never called**.

### 3.4 Helpers already present

- `IC2.explodeMachineAt(World, int, int, int, boolean noDrop)` → `new ExplosionIC2(world, null, x+.5, y+.5, z+.5, 2.5f, 0.75f)` + `destroy(x,y,z,noDrop)` + `doExplosion()`. `noDrop=true` = block value 3 (no drop); `false` = value 1 (75% explosion drop rate).
- `IC2DamageSource.electricity` exists (shock damage source).
- `Node.getAmount()`, `getVoltage()`, `getTier()`, `getMaxCurrent()`, `getInnerLoss()`; `Node.currentIn/currentOut`; `Tile.entity`, `Tile.positions`.

### 3.5 Missing in 1.7.10

- No `IOverloadHandler`, no `IExplosionPowerOverride`, no `enableEnetExplosions`, no `enetCableMeltdown`.
- Nothing ever calls `removeConductor()`, `removeInsulation()`, `Node.getMaxCurrent()`, or `IC2.explodeMachineAt()`.

---

## 4. The two 1.7.10 energy models (`useLinearTransferModel`)

`config/IC2.ini`, `[misc] useLinearTransferModel`, default **false** (verified in a generated config):

```
; Use the new highly experimental current + voltage energy net model with energy loss.
; Only set this to true if you know what you are doing.
useLinearTransferModel = false
```

It is read **once** into `EnergyNetLocal.useLinearTransferModel` (`public static final`) at class-load, so changing it requires a restart. This setting determines *which half* of the 1.12.2 system is even meaningful.

### 4.1 `false` — default "energy distribution" model

- sources inject `getOfferedEnergy()` as ideal currents (no internal resistance);
- each sink is a `1Ω` load to ground (`+1` on the matrix diagonal);
- `NodeLink.loss` is only a conductance weight (`1/loss`).

The solved node value is **not a voltage** — it is the energy allocated to that node. For a sink `amount == voltage == V`. Consequences:

- cable length does not reduce delivered energy (loss only decides the split between multiple sinks);
- `Node.currentIn/currentOut` are derived from allocation potentials and are dimensionally meaningless (an MFSU→copper cable→LV machine yields `currentIn ≈ 512`, `currentIn * voltage` in the hundreds of thousands);
- multiple same-tier sources **aggregate**: two BatBoxes (2×32) feeding one LV machine produce a single `Change` of 64.

### 4.2 `true` — physical voltage/current model

- source = voltage source: open-circuit `getPowerFromTier(sourceTier)`, internal resistance `R_src = V_tier² / (4 · offered)`;
- sink resistance `R_sink = getPowerFromTier(sinkTier)` (32Ω tier 1, 128Ω tier 2, …);
- solved node value is a real voltage; `amount = V² / R_sink`; losses are real (`I²R`).

`getPowerFromTier(tier) = tier < 14 ? (8 << (2*tier)) : 8 * 4^tier` → 32 / 128 / 512 / 2048 for tiers 1–4.

Worked examples (one cable hop, `loss = 0.2`):

| Setup | Linear result |
|---|---|
| MFSU (512 EU/t, tier 3) → LV machine (tier 1) | `V ≈ 102V`, `amount ≈ 327 EU/t` → machine explodes and copper cable melts |
| 2× BatBox (32 EU/t each, tier 1) → one LV machine | node ≈ 26V, `amount ≈ 21 EU/t` → nothing happens (correct per-packet) |
| long tier-1 run | voltage sags, delivered energy drops (the "with energy loss" part) |

### 4.3 How the model ties into Option A

| | `useLinearTransferModel=false` (default) | `useLinearTransferModel=true` |
|---|---|---|
| Machine explosion | yes — `amount > tierPower` on the aggregate allocation | yes — physically correct |
| Multiple same-tier sources | **false positive** (64 > 32 for 2×BatBox) | no false positive |
| Cable meltdown | not meaningful (fake currents) — must be skipped | yes: `max(\|currentIn\|,\|currentOut\|) · \|voltage\| > getConductorBreakdownEnergy()` |
| Shock | not meaningful — must be skipped | yes, via `getInsulationEnergyAbsorption()` |
| Fidelity to 1.12.2 | partial | closest achievable |

Handling:

1. Read `EnergyNetLocal.useLinearTransferModel` at runtime in the effects pass.
2. Run machine explosions in both models; document the aggregate/multi-source caveat for the default model.
3. Run cable meltdown + shocks **only** when the linear model is on; log a one-time warning if the configs are enabled while it is off.
4. Document that full 1.12.2 behaviour requires `misc/useLinearTransferModel=true`, and that this changes the whole energy simulation for every mod.
5. Optional: a `forceLinearTransferModel` mod option implemented as an `@Redirect` of the `ConfigUtil.getBool(...)` call in `EnergyNetLocal.<clinit>` (off by default, log loudly).

### 4.4 Fidelity gap that remains even in the linear model

1.7.10 solves one V/I network per grid, so conductor and sink quantities are **per-node aggregates**, not 1.12.2's per-path `maxPacketConducted`. A cable carrying two 128 EU/t packets sees ~256 and can melt where 1.12.2 would not. This is inherent to the 1.7.10 model and should be documented.

---

## 5. Option A design

### 5.1 Tick pipeline and hook constraints (verified)

`EnergyNetGlobal.onTickEnd(world)` → `WorldData.get(world).energyNet.onTickEnd()`; one `EnergyNetLocal` per world. Inside:

| Step | `locked` | Notes |
|---|---|---|
| `finishCalculation()` per grid | `true` | joins the **async** calculation from last tick; emits one `Change(node, dir, amount, voltage)` per source/sink |
| `updateStats()` per grid | `true` | `currentIn/currentOut/voltage` → `NodeStats` |
| **`processChanges()`** | **`false`** | calls `sink.injectEnergy(...)`, refunds unused source energy, clears changes |
| `prepareCalculation()` / `startCalculation()` per grid | `true` | starts next tick's async `Grid.calculate()` on IC2's thread pool |

Rules this imposes:

- **Effects must run in/after `processChanges()`.** `EnergyNetLocal.removeTileEntity()` throws `IllegalStateException("removeTileEntity isn't allowed from this context")` while `locked == true`. `finishCalculation()`/`updateStats()` are locked out; `processChanges()` is the only legal main-thread window, and IC2 already tolerates removals there (`removedTiles` + redistribution).
- **Never mutate the world in `calculateEffects()`** — it runs off-thread on the pool. Collect pure data only.
- All needed data is final by `processChanges()` (the future was joined in step 1).

### 5.2 Effect mapping

| 1.12.2 effect | 1.12.2 test | 1.7.10 data | Default model? |
|---|---|---|---|
| Machine explosion | `maxPacketConducted > getPowerFromTier(sinkTier)` | `amount - returned` from the `injectEnergy` call vs `EnergyNet.instance.getPowerFromTier(sink.getSinkTier())` | yes, aggregate |
| Cable meltdown | `amount > getConductorBreakdownEnergy()` → `removeConductor()` | `max(\|currentIn\|,\|currentOut\|) · \|voltage\| > node.getMaxCurrent()` | linear only |
| Insulation strip | `amount > getInsulationBreakdownEnergy()` → `removeInsulation()` | dead in 1.7.10 (9001 / no-op) | n/a |
| Shock | `amount > getInsulationEnergyAbsorption()` → `(amount-absorption)/64` | same threshold, `IC2DamageSource.electricity` | linear only |
| Graceful cancel | `IOverloadHandler.onOverload(tier)` | define an own interface, `instanceof` before exploding | either |
| Veto / resize | `IExplosionPowerOverride` | define an own interface | either |
| Config gates | `enableEnetExplosions`, `enetCableMeltdown` | own config gates (runtime flags) | either |

### 5.3 Mixin sketch

Package access is the structural constraint: `Change`, `Node`, `Tile`, `Grid`, `NodeLink` are **package-private** in `ic2.core.energy`.

- **Simplest:** put the mixins in `ic2.core.energy` (`src/main/java/ic2/core/energy/…`) and set `mixinsPackage = ic2.core.energy`. Direct access, no shadows/invokers.
- **GTNH-conventional:** keep mixins in the mod's package and use `@Mixin(targets = "ic2.core.energy.…")` + `@Invoker`/`@Shadow` for the members you need (more boilerplate, more shadow-name fragility).

Sink capture can be done without touching package-private types at all:

```java
@Mixin(value = EnergyNetLocal.class, remap = false)
public abstract class MixinEnergyNetLocal {
    @Unique private final List<OverloadCandidate> am$overloaded = new ArrayList<>();

    // processChanges(): sink.injectEnergy(change.dir, change.getAmount(), change.getVoltage())
    @Redirect(method = "processChanges",
              at = @At(value = "INVOKE",
                       target = "Lic2/api/energy/tile/IEnergySink;injectEnergy(Lnet/minecraftforge/common/util/ForgeDirection;DD)D"))
    private double am$captureInjection(IEnergySink sink, ForgeDirection dir, double amount, double voltage) {
        double returned = sink.injectEnergy(dir, amount, voltage);
        am$overloaded.add(new OverloadCandidate(sink, amount, amount - returned, voltage));
        return returned;
    }

    @Inject(method = "processChanges", at = @At("RETURN"))
    private void am$applyOverloads(CallbackInfo ci) {
        EnetOverload.apply(am$overloaded);   // explosions + (linear-only) cable pass
        am$overloaded.clear();
    }
}
```

Notes:
- `amount - returned` mirrors 1.12.2's `effectiveAmount = amount - rejected (+loss)`: a full machine that **rejects** the packet does not explode. 1.7.10 base machines are all-or-nothing, so this is exact enough.
- Cable/shock need a second mixin on `Grid`/`Node` scanning nodes whose `voltage` is not `NaN` (inactive nodes are `NaN`/0), checking `max(|currentIn|,|currentOut|) · |voltage|` against `node.getMaxCurrent()` / the cable's `getInsulationEnergyAbsorption()`.
- Application: verify `world.getTileEntity(x,y,z) == te` before destroying (the net may have removed it in the same pass); dedupe per tile with the max amount; machine → `IC2.explodeMachineAt(world, x, y, z, true)` (`true` = no drop, matching 1.12.2's `setBlockToAir`; `false` = 75% drop); cable → `((IEnergyConductor) te).removeConductor()`; shock → `attackEntityFrom(IC2DamageSource.electricity, damage)`.
- Divergence to accept: 1.12.2 suppresses the sink explosion when a cable on the *path* overloaded (`!conductorOverload`). 1.7.10 has no paths, so both fire; cannot correlate.

### 5.4 Config

Add to `ic2tweaks.cfg` (mixins apply before Forge configs, so gate at runtime with a static holder populated in `preInit`; default the holder to "disabled" so the pre-config window is inert):

| Key | Meaning | Suggested default |
|---|---|---|
| `enableEnetExplosions` | machines explode on over-tier packets | mirror 1.12.2 (`true`) for a fresh release; `false` is safer for existing worlds |
| `enableEnetCableMeltdown` | cables/conductors melt on overload | `true` (only active with the linear model) |
| `enableEnetShocks` | electric shock damage | `true` (optional; not a separate 1.12.2 key) |
| `forceLinearTransferModel` | redirect IC2's `useLinearTransferModel` read to true | `false` |

Because the feature is global, the README must state plainly that installing the mod changes **every** IC2 machine and cable, not just one addon's machines.

### 5.5 Build wiring

- `gradle.properties`: `usesMixins = true`, `mixinsPackage = <chosen package>`, optional `mixinPlugin`.
- The GTNH buildscript then generates the mixin JSON (`compatibilityLevel: JAVA_8`, refmap) and auto-adds **UniMixins** as a required runtime dependency (and to the Modrinth/Curse relations).
- `@Mixin(..., remap = false)` — IC2 is not obfuscated.
- Optional `IMixinConfigPlugin.shouldApplyMixin()` for IC2-version/class-shape gating (fail soft rather than crash on a patched IC2 fork).

### 5.6 Optional graceful hooks

Define mod-local equivalents of `IOverloadHandler` / `IExplosionPowerOverride` and `instanceof`-check them before the default explosion, so other mods/GT machines can opt out or resize the blast.

---

## 6. Risks

- **Private internals / version fragility:** `processChanges`, `Grid.nodes`, `Node.currentIn/...` are not API. Pin the mixin to the supported IC2 build; consider a version guard.
- **GTNH patched IC2 forks** are the main compatibility risk for a public release.
- **Other enet mixins** (StellarCore-style optimizers) can conflict; keep injections narrow (`HEAD`/`RETURN` of `processChanges`, `RETURN` of a node/stat method) and never overwrite whole methods.
- **Threading/`locked` rules** from §5.1 are non-negotiable.
- **Testing:** mixins only apply under the FML launchwrapper, so unit tests cannot exercise them. Keep all decisions (`amount > tierPower`, `throughput > breakdown`, damage math) in a plain, unit-testable class and keep the mixins as thin adapters.

---

## 7. Phased plan

1. **Phase 1 — infrastructure + machine explosions.** `usesMixins`, config holder, `MixinEnergyNetLocal` sink capture, `IC2.explodeMachineAt`, pure decision math + unit tests, README. Works in both energy models (aggregate in the default one).
2. **Phase 2 — cables + shock.** `Grid`/`Node` scan, `removeConductor()`, `IC2DamageSource.electricity`, gated on `useLinearTransferModel`, one-time warning when disabled.
3. **Phase 3 — polish.** Optional overload/power-override interfaces, `forceLinearTransferModel`, in-game QA, documentation.

---

## 8. Verification / QA checklist

Unit-testable (no game): tier-power threshold, accepted-vs-rejected packet, `getPowerFromTier` mapping, conductor throughput vs breakdown, shock damage math, config gating.

In-game (dev client + dedicated server):
- MFSU → LV machine directly → machine explodes.
- MFSU → copper cable → LV machine → cable melts (linear model), machine explodes.
- 2× BatBox → LV machine → nothing (linear model); check the documented default-model aggregate behaviour.
- Transformer upgrade installed → 128 EU/t accepted, 512 still explodes.
- MV source → MV machine → nothing.
- Glass fibre / HV cable thresholds (pipe a large packet through them).
- Wrench/break a machine or cable in the same tick as an overload.
- Shock damage near an overloaded cable.
- Dedicated server: no client-only access, no crash, no lost EU accounting.
- With `enableEnetExplosions=false` / `enableEnetCableMeltdown=false`: no effects, no overhead.

---

## Appendix — reference values (1.7.10, `ic2-242638-2353971`)

- `getPowerFromTier(tier)`: `8 << (2*tier)` for `tier < 14`, else `8 * 4^tier` → 32 / 128 / 512 / 2048 / 8192 for tiers 1–5.
- `TileEntityCable.getConductorBreakdownEnergy() = getMaxCapacity(type) + 1`: tin 33, copper 129, gold 513, HV 2049, glass fibre 8193.
- `getInsulationBreakdownEnergy() = 9001`; `removeInsulation()` is a no-op.
- `getInsulationEnergyAbsorption()`: small per-type values (5, 6, …).
- `getConductionLoss()`: per cable type (0.2, 0.3, 0.45, 0.5, …).
- `IC2.explodeMachineAt(..., noDrop)` = `ExplosionIC2(2.5f, 0.75f)` at block centre; `noDrop=true` → no machine drop, `false` → 75% drop.
- `EnergyNetLocal.useLinearTransferModel = ConfigUtil.getBool(MainConfig.get(), "misc/useLinearTransferModel")`, default `false`, `public static final`, read once.

---

## 9. Implementation status (Option A) and feature gaps

### 9.1 What was implemented

| Piece | Where | Notes |
|---|---|---|
| GTNH late-mixin infrastructure | `com.mrfuzzihead.ic2tweaks.mixins` (`Mixins`, `TargetMods`, `LateMixinsLoader`), `mixins.ic2tweaks.json`, `mixins.ic2tweaks.late.json` | late-only (IC2 is a mod); `usesMixins = true`, `mixinsPackage = mixins` |
| Master toggle + sub-toggles | `EnetOverloadConfig` (`energynet` category in `ic2tweaks.cfg`) | `enableEnetOverload` defaults to `true`; read before Forge preInit so `setApplyIf` sees the real file |
| Linear-model override | `MixinEnergyNetLocalLinearModel` | `<clinit>` `@Inject` + `@Mutable @Shadow @Final useLinearTransferModel`, hard-sets `true` and ignores `IC2.ini`; only applied when the master toggle is on |
| Machine explosions | `MixinEnergyNetLocalOverload` + `EnetOverload` | `@Redirect` on `IEnergySink.injectEnergy` inside `processChanges`, applied at `RETURN` (`locked == false`); uses `amount - returned` so a full machine that rejects the packet does not explode |
| Cable meltdown + shocks | `EnetConductorRegistry` + `EnetCableEffects` | public-API per-world tick pass over registered `IEnergyConductor`s; `NodeStats` throughput vs `getConductorBreakdownEnergy()`; meltdown and shock are computed in the same pass, so a melting cable still shocks; shock energy is accumulated per entity and applied once per tick (gap 6 closed) |
| Cable loss tooltip | `MixinItemCable` + `EnetCableData` | appends `Loss: N EU/block` to `ItemCable`'s tooltip next to IC2's packet-size line; gated by the master toggle |
| Pure decisions + tests | `EnetOverloadMath`, `EnetCableData`, `EnetOverloadMathTest`, `EnetCableDataTest` | strict `>`, NaN/infinite/non-positive safe; cable loss table pinned to the IC2 build |
| Runtime smoke test | `./gradlew runServer` with the master toggle on | mixins load (`Loading [mixins.ic2tweaks.late.json] ...`), the override log fires, server reaches `Done`, no mixin/injection errors |

### 9.2 Feature gaps vs IC2 Experimental 1.12.2

Summary of what is missing, whether it can be closed, and how hard it would be. "Mixin" here means
adding/modifying a mixin in this mod; "plain" means ordinary mod code.

(Statuses in this table are from the first implementation round. §12 recalculates them after the
round-1 fixes, reclassifies gaps 2 and 4, and adds the absorption policy as gap 14.)

| # | Gap | Closable? | Approach | Difficulty | Risk |
|---|-----|-----------|----------|------------|------|
| 1 | Per-packet vs per-node aggregate | No (practically) | Rewrite the distribution model (port `EnergyCalculatorLeg`) | Infeasible / very high | Extreme |
| 2 | Insulation stripping | Partially, semantics unclear | Mixin `TileEntityCable.getInsulationBreakdownEnergy()` / `removeInsulation()` | Medium-hard | Medium |
| 3 | Graceful overload hooks | Yes | Own `IOverloadHandler` / `IExplosionPowerOverride` interfaces, `instanceof` before exploding | **Easy** | Low |
| 4 | `conductorOverload` suppression | Approximate only | Per-grid correlation between the machine and cable passes (deep enet mixin) | Hard | High |
| 5 | Demand-capped packets | Partially | Clamp `amount` to `getDemandedEnergy()` in the existing `@Redirect` | Trivial code / high balance risk | High |
| 6 | Shock accumulation/details | **Closed** | Per-entity accumulation in `EnetCableEffects` (max per tick) | Easy | Low |
| 7 | `ExplosionIC2.Type.Electrical` | Partially | Construct `ExplosionIC2` directly instead of `IC2.explodeMachineAt` | Easy-medium | Low |
| 8 | Cables require the linear model | N/A (dependency) | Already forced + guarded | — | — |
| 9 | Config keys live in `ic2tweaks.cfg` | Cosmetic | Read/inject IC2.ini keys via mixin | Easy | Low |
| 10 | Global linear-model side effect | Inherent | — | — | — |
| 11 | `NodeStats` one-tick staleness | Yes | Mixin `Node.updateStats`/read `currentIn`/`currentOut` directly | Medium (package access) | Medium |
| 12 | Cable pass is a separate tick pass (ordering) | Partially | Move it into the `processChanges` `RETURN` hook | Medium | Medium |
| 13 | High-tier machines unpowerable on the linear model | Partially | Clamp the linear-model sink resistance (mixin `Grid`/`Node`) or cap transformer upgrades | Medium | Medium |

#### 1. Per-packet vs per-node aggregate — *inherent, not worth closing*
1.12.2's `EnergyCalculatorLeg` distributes per source and records `path.maxPacketConducted`; 1.7.10's
`Grid` solves a single admittance matrix and produces one voltage/current per node. Per-source packet
sizes cannot be recovered from that solution. Closing it means replacing IC2's distribution with a
path-based one (essentially porting `EnergyCalculatorLeg`) via `@Overwrite`/`@Inject` on
`Grid.calculateDistribution`/`calculateEffects` — a rewrite of the simulation, not a tweak.

#### 2. Insulation stripping — *semantically unclear*
1.7.10 hard-codes `getInsulationBreakdownEnergy() == 9001` and `removeInsulation()` is a no-op, so the
branch can never fire. Closing it would need `@Mixin(TileEntityCable.class)` to give the cable a real
threshold and a real `removeInsulation()` implementation; 1.7.10's `foamed`/`tryRemoveInsulation()` is
not the same concept as 1.12.2 rubber insulation, so this would be inventing behaviour. Not recommended.

#### 3. Graceful overload hooks — *easy, recommended*
Define mod-local `IOverloadHandler` (`boolean onOverload(int tier)`) and `IExplosionPowerOverride`
(`boolean shouldExplode()`, `float getExplosionPower(int tier, float defaultPower)`) in an API package and
check them in `EnetOverload` in 1.12.2's order (config → overload handler → power override → explode).
No mixin is needed. Caveat: other mods cannot implement 1.12.2's interfaces because those classes do not
exist in 1.7.10, so only mods that opt into our interface are honoured.

#### 4. `conductorOverload` suppression — *hard, low value*
1.12.2 skips the sink explosion when a cable on the same path overloaded. The machine pass only sees an
`IEnergySink` (from the `@Redirect` handler), not the `Node`/`Grid`, so there is no path correlation. An
approximation would need per-grid state shared between the two passes: capture the node/grid in the enet
mixin (or `@Mixin` `Node`/`Grid`, which requires placing mixins in `ic2.core.energy`), record melted
conductors per grid, then skip machine explosions on that grid. Doing both effects is physically
defensible, so this is not worth the fragility.

#### 5. Demand-capped packets — *trivial code, high balance risk*
1.12.2's `emit` caps the packet at `getDemandedEnergy()` before injecting; 1.7.10's linear solve ignores
demand and the base `injectEnergy` is all-or-nothing. Clamping `amount` to `sink.getDemandedEnergy()`
inside the existing `@Redirect` would match 1.12.2, but it changes how much energy every IC2 machine
receives and how much is returned to sources — a global energy-delivery change, not just an overload
rule. Leave it unless there is a separate, clearly-labelled opt-in.

#### 6. Shock accumulation/details — *closed*
1.12.2 accumulates shock energy per entity across the conductors of a path (max per conductor) and sums
across paths, applying `ceil(energy / 64)` once. `EnetCableEffects` now keeps the maximum across all
conductors per entity and applies damage once per world tick. 1.7.10 has no path data, so treating the
tick as one path is exact for a single circuit and conservative for several independent circuits near
the same entity.

#### 7. `ExplosionIC2.Type.Electrical` — *partial*
1.7.10's `ExplosionIC2` has no `Type` parameter and `IC2.explodeMachineAt` uses the plain 2.5f / 0.75f
constructor. If a different blast is wanted, construct `ExplosionIC2` directly in `EnetOverload`
(public `(World, Entity, x, y, z, power, dropRate)` constructor) and call `destroy`/`doExplosion`.
There is no electrical type to match exactly.

#### 8. Cables require the linear model — *dependency, already handled*
The master switch forces `useLinearTransferModel`; if the override mixin ever fails to apply,
`EnetCableEffects` warns once and skips cables/shocks.

#### 9. Config key location — *cosmetic*
Our keys live in `ic2tweaks.cfg` (`energynet`). Mirroring IC2.ini's `misc/enetExplosions` /
`misc/enetCableMeltdown` would mean inventing keys 1.7.10 IC2 does not have; not worth it.

#### 10. Global linear-model side effect — *inherent*
Enabling the master switch also switches the whole energy net to IC2's experimental lossy model. That is
the price of having physical voltage/current data at all.

#### 11. `NodeStats` one-tick staleness — *medium, low value*
`EnergyNetLocal.getNodeStats` returns `lastNodeStats`, refreshed in `Grid.updateStats()` only when a
calculation ran, so values can persist one extra tick after a grid goes idle. Reading
`Node.currentIn`/`currentOut`/`voltage` directly would remove the lag but needs package-private access
(mixins in `ic2.core.energy` or `@Accessor`/`@Invoker` shims).

#### 12. Cable pass ordering — *medium, low value*
Machine explosions run in `processChanges` `RETURN`; the cable pass runs in `WorldTickEvent.END`. 1.12.2
applies both in one post-injection pass. Moving the cable pass into the same hook is possible but needs
the world inside `EnergyNetLocal` (no world field; get it from a registered conductor or shadow
`WorldData`). Low value.

#### 13. High-tier machines are unpowerable on the linear model — *IC2 quirk exposed by forcing the model*
Discovered in-game: an IC2 machine (e.g. a Macerator) with 64 transformer upgrades receives no power.

- `InvSlotUpgrade.onChanged()` computes `extraTier += IUpgradeItem.getExtraTier(stack) * stackSize`;
  64 transformer upgrades in one stack give `extraTier = 64`.
- `TileEntityStandardMachine.setOverclockRates()` calls `setTier(defaultTier + extraTier)`, so
  `getSinkTier()` returns 65.
- `Grid.prepareCalculation()` copies that onto the node, but `Node.setTier()` clamps any tier above 20
  down to 20 (a deliberate overflow guard).
- The linear model's `Grid.populateNetworkMatrix()` sets `R_sink = getPowerFromTier(node.getTier())`,
  i.e. `getPowerFromTier(20) ≈ 8.8e12 Ω`.
- A real source (MFSU: 512 V, ~128 Ω internal resistance) then sees the sink as an open circuit:
  `V_sink ≈ 512 V`, `P ≈ V² / R_sink ≈ 3e-8 EU/t`. The machine never charges, so it never runs.
- Practical threshold on an MFSU: a base machine (2 EU/t) needs tier ≤ ~7 (≈6 transformer upgrades);
  an Advanced Machine (6 EU/t) needs tier ≤ ~6 (≈5). Beyond that it starves.

This is **pre-existing IC2 linear-model behaviour** — it reproduces with `misc/useLinearTransferModel=true`
and no addon — and it is *not* caused by the overload rules. It becomes visible because the master
switch forces the linear model. 1.12.2 does not have it because its calculator never derives sink
resistance from the tier (it only uses the tier for the explosion check).

Mitigation options:
- **Document it** (recommended): it only bites at 6+ transformer upgrades, and 1.7.10's linear model is
  explicitly experimental.
- **Clamp the linear-model sink resistance** to the grid's strongest source tier (mixin
  `Grid.populateNetworkMatrix`/`Node.setTier`, package access needed), keeping the real tier for the
  explosion check. Medium effort, changes IC2's model.
- **Cap transformer upgrades** globally (mixin `InvSlotUpgrade`/`TileEntityStandardMachine`) — not
  recommended, it changes IC2's upgrade semantics for every machine.

#### Recommended closable follow-ups
1. Gap 3 (overload/power-override hooks) — small, useful for pack/GT interop.
2. Gap 7 (custom blast) only if the exact explosion feel matters.
3. Gap 5 only behind a separate opt-in, if ever.
4. Absorption parity (finding 1, §11) — port the 1.12.2 insulation-based formula when the policy is
   revisited.


---

## 10. In-game validation checklist (current implementation)

Everything below assumes the mixins built from this branch. `enableEnetOverload` lives in the `energynet`
category of `config/ic2tweaks.cfg`.

### 10.1 Setup / gating

- [ ] Default install (`enableEnetOverload=true`): the log contains
      `[IC2Tweaks] Energy-net overload enabled: forced IC2 useLinearTransferModel=true (IC2.ini ignored)`.
- [ ] With `enableEnetOverload=false`, the log says `Not loading the following LATE mixins:
      [ic2.MixinEnergyNetLocalLinearModel, ic2.MixinEnergyNetLocalOverload, ic2.MixinItemCable]` and IC2
      behaves exactly as before (IC2.ini's `misc/useLinearTransferModel` is honoured, and cable tooltips
      have no loss line).
- [ ] With the master switch on, set `IC2.ini` `misc/useLinearTransferModel=false` and confirm the game
      still uses the linear model (see 10.2).
- [ ] The config file keeps the `energynet` category and the chosen values across restarts (no reset, no
      duplicate `energynet`/`energyNet` categories).

### 10.2 Linear-model override

- [ ] With the master switch on, energy loss is observable (a long cable run delivers less than a short
      one; EU reader / machine speed differ) even though `IC2.ini` says `useLinearTransferModel=false`.
- [ ] With the master switch off, energy loss matches `IC2.ini` (no loss if it is `false`).

### 10.3 Machine explosions (`enableEnetExplosions`)

- [ ] MFSU (tier 3) → LV machine, direct or via glass fibre: the machine explodes (2.5 blast) and drops
      nothing.
- [ ] MFE (tier 2) → LV machine: explodes.
- [ ] BatBox (tier 1) → LV machine: **no** explosion.
- [ ] LV machine with a transformer upgrade: 128 EU/t is accepted, 512 EU/t explodes.
- [ ] A machine whose buffer is already full / rejects the packet does **not** explode (we check the
      accepted amount, not the offered one).
- [ ] Storage blocks (BatBox/MFE/MFSU) fed above their tier explode (they are sinks too).
- [ ] Both a plain IC2 machine (Macerator/Compressor/Extractor/Recycler/Ore Washer) and each of the five
      Advanced Machines explode — confirms the feature is global, not addon-only.
- [ ] `enableEnetExplosions=false`: machines survive over-voltage (cables may still melt).
- [ ] Expected non-bug: **two same-tier sources** (e.g. 2× BatBox) feeding one LV machine can trip the
      aggregate check (gap 1). Note the setup and confirm it matches the documented behaviour.

### 10.4 Cable meltdown (`enableEnetCableMeltdown`)

- [ ] MFSU → copper cable → LV machine: the copper cable is removed (fizz/smoke, no blast, no item drop);
      the machine also explodes (gap 4: both effects fire).
- [ ] MFSU → glass fibre → LV machine: the glass fibre survives (breakdown 8193), the machine explodes.
- [ ] MFE → copper cable → MV machine: the copper cable melts (129 threshold); the machine survives.
- [ ] BatBox → copper cable → LV machine: no melt.
- [ ] HV/gold cable thresholds behave (gold 513, HV 2049): feed a packet just under and just over.
- [ ] `enableEnetCableMeltdown=false`: no cable is removed.
- [ ] Other mods' conductors (any `IEnergyConductor`) melt too — confirms global scope.
- [ ] Cable removal does not damage neighbouring blocks (only the conductor is removed).

### 10.5 Shocks (`enableEnetShocks`)

- [ ] Standing next to an overloaded copper cable (MFSU → copper → LV) deals electric damage on the
      tick it melts as well as on the preceding ticks (meltdown and shock are computed in the same
      pass); the death message is IC2's electric source.
- [ ] With IC2 1.7.10's absorption values, only *uninsulated* cables shock (bare copper 5, bare gold 6,
      bare tin 8, bare HV 3; every insulated cable is 9001). A bare copper cable carrying more than
      5 EU/t deals 1 damage per tick window.
- [ ] Shock damage is applied once per tick per entity, not once per cable tile: a long run must not
      out-damage a single tile at the same throughput (max-per-entity accumulation, gap 6 closed).
- [ ] `enableEnetShocks=false`: no damage.
- [ ] BatBox → insulated copper → LV: no damage (absorption 9001).

### 10.6 Robustness / regression

- [ ] Wrench/break a machine or cable in the same tick as an overload: no crash, no item duplication.
- [ ] A world with no IC2 energy tiles loads and ticks cleanly with the master switch on (no
      `EnetCableEffects`/`EnetOverload` stack traces).
- [ ] Dedicated server: same behaviour, no client-only class access, no crash.
- [ ] Two players: explosions and cable removal sync correctly to both clients.
- [ ] With the master switch on, the Advanced Machines' own behaviour is unchanged: 10× speed ramp, 3×
      power draw, 6-overclocker cap, extra output slots, redstone idle.
- [ ] Performance: a base with many cables (1,000+) does not visibly increase tick time (the cable pass
      is O(conductors) per world tick).
- [ ] Unload/reload the world (or change dimension) and confirm no stale conductors are processed and no
      world leak (the registry uses `EnergyTileUnloadEvent` + `WorldEvent.Unload`).

### 10.7 Known linear-model behaviours (not overload bugs)

These are consequences of forcing IC2's linear transfer model, not of the overload rules themselves.
Reproduce them with `misc/useLinearTransferModel=true` and no addon to confirm.

- [ ] A machine with many transformer upgrades (6+ on an MFSU; 64 is the extreme) draws almost no
      power and never runs — see gap 13. Lower the upgrade count and confirm it powers normally.
- [ ] A machine upgraded to a higher tier but fed at a lower voltage charges more slowly than the same
      machine at its default tier (the linear model derives the sink resistance from the tier).
- [ ] Energy loss over long cable runs is now modelled; this is expected with the master switch on.
- [ ] A direct machine-to-machine connection (no cables) loses energy: a full BatBox feeding an empty
      one leaves the sender at ~15 EU and the receiver at ~39,490 EU; the ~495 EU difference is the
      link's 0.4 Ω inner loss, not a bug (see §11.2).

---

## 11. Findings analysis — first in-game validation round

Three findings were logged in `Findings.txt` after the first in-game validation. Root cause, verdict and
what changed, per finding.

### 11.1 Uninsulated tin "shocks more than copper"

**Mechanism.** `EnetCableEffects` shocks a conductor when `throughput > getInsulationEnergyAbsorption()`
(`throughput = max(energyIn, energyOut)`). Only uninsulated cables have a low absorption; insulated
cables return `9001` and never shock.

**1.7.10 table (used by the port, unchanged for now):** tin 8, copper 5, gold 6, HV 3; insulated cables
9001. At equal throughput the ordering is HV > copper > gold > tin, so bare tin is the *least* sensitive
and lowest-damage of the bare cables.

**1.12.2 intent.** `getInsulationEnergyAbsorption()` is derived from the insulation level with a
deliberate tin special case: `getPowerFromTier(insulation)` for tin (bare 8, 1× 32) and
`getPowerFromTier(insulation + 1)` for everything else (bare 32, 1× 128, 2× 512, 3× 2048).
glass/detector/splitter (max insulation 0) never shock. Bare tin *is* intentionally the deadliest cable
in 1.12.2.

**Verdict / decision.** The observation matches 1.12.2's design rather than the port's current table
(our copper is more sensitive than our tin). If the in-game comparison really was bare tin vs bare
copper in the same circuit it needs a repro; the likely explanations are bare tin vs *insulated* copper,
or different circuit throughputs. Absorption parity is **deferred** — the 1.7.10 values stay for now and
are documented here and in the README.

### 11.2 Missing ~495 EU on a direct BatBox → BatBox connection

**Cause.** IC2's forced linear model loses energy on every link. `Grid.add()` sets
`link.loss = (nodeA.getInnerLoss() + nodeB.getInnerLoss()) / 2`; `Node.getInnerLoss()` is `0.4` for both
source and sink, so a cable-less machine-to-machine link is `0.4 Ω`. With a BatBox
(`R_src = 32²/(4·32) = 8 Ω`, `R_sink = 32 Ω`): `I = 32/40.4`, `P_loss = I²·0.4 = 0.251 EU/t` =
`0.4/32.4 = 1.235 %` of what the sender spends → ≈ 494 EU across the 39,985 EU transfer. The 15 EU
leftover is base IC2: `TileEntityElectricBlock.getOfferedEnergy()` returns 0 below `output` (32), so the
sender stops. `510 receiver shortfall = 495 lost + 15 stranded`.

This is faithful to 1.12.2 (`Node.getInnerLoss()` is also 0.4 there, and `EnergyCalculatorLeg.emit`
charges `amount - rejected + path.loss`). **No code change**; documented in the README. The loss is
LV-heavy because `R_sink` scales with tier (≈ 1.2 % at tier 1, ≈ 0.3 % at tier 2).

### 11.3 Cable loss tooltips

Added: `MixinItemCable` appends `Loss: N EU/block` to `ItemCable.addInformation` when the master toggle
is on, using `EnetCableData.conductionLoss(meta)` (pinned to the IC2 build). Note the IC2 quirk that
insulated tin (meta 13) reports `0.025`, the same as glass fibre and *lower* than bare tin; it is shown
verbatim so the tooltip matches the simulation. The existing `N EU/t` line is really the packet size
(EUp); it is left as IC2 prints it.

### 11.4 Other changes in this round

- **Melt + shock in the same pass.** `EnetCableEffects` now computes meltdown and shock from the same
  node data and removes conductors afterwards, so a cable that melts this tick still shocks (matching
  1.12.2). This also makes the §10.5 overloaded-cable check meaningful.
- **Gap 6 closed.** Shock energy is accumulated per entity and applied once per world tick (max across
  conductors, since 1.7.10 exposes no path data).
- **Master switch default.** The code and generated configs already defaulted to `true`; the README and
  this document were corrected to match.
- **Tests.** `EnetOverloadMathTest` covers `shockEnergy`/`shockDamageFromEnergy`; `EnetCableDataTest`
  pins the cable loss table (including the insulated-tin quirk).

---

## 12. Parity recalculation (after the first validation round)

Recalculated after the round-1 fixes: gap 6 closed (per-entity shock accumulation) and meltdown/shock
moved into the same pass. Two earlier assessments are corrected here:

- **Gap 2 (insulation stripping) is not a gap.** 1.12.2's `getInsulationBreakdownEnergy()` is also
  `9001`, and every conductor breakdown is at most `8193`, so `cablesToStrip` can never populate there
  either. Doing nothing is parity.
- **Gap 4 (`conductorOverload` suppression)** was later removed upstream: the modern
  `EnergyCalculatorUnified` has no suppression at all, so letting both effects fire is defensible.

### 12.1 Scorecard

| Dimension | Parity | Notes |
|---|---|---|
| Effect rules (what melts / explodes / shocks) | ~90% | The remaining rule gap is the absorption table (gap 14). |
| Effect math (thresholds, damage, blast) | ~95% | `ceil(energy / 64)`, `capacity + 1` breakdown, tier power, 2.5 / 0.75 blast all match. |
| Tick coherence | ~85% | Both effects fire in the same pass now; the cable pass is still a separate world-tick pass from the machine pass. |
| Per-packet fidelity (junctions, multi-source, multi-packet) | ~50% | Gap 1 knocks on to melt/shock/explosion values and false positives. |
| Interop / config surface | ~70% | No graceful hooks; config lives in `ic2tweaks.cfg`; otherwise clean. |

### 12.2 Revised gap matrix

| # | 1.12.2 behaviour | Ours | Status | Effort |
|---|------------------|------|--------|--------|
| 1 | Per-source `EnergyPath`, `maxPacketConducted` per packet | per-node `max(currentIn, currentOut) · V` | Missing (structural) | Very high |
| 3 | `IOverloadHandler.onOverload(tier)`, `IExplosionPowerOverride` | absent | Missing | Easy |
| 4 | suppress sink explosion when a cable on the path overloaded | both effects fire | Missing | Hard, low value |
| 5 | `amount = min(offer − loss, sinkDemand)` | all-or-nothing `injectEnergy`, no cap | Missing | Easy code / high balance risk |
| 6 | max per conductor per path, sum across paths, one `ceil(/64)` | max across conductors, one `ceil(/64)` | Approximated (closed) | Medium for exact path sums |
| 7 | `ExplosionIC2.Type.Electrical` | `IC2.explodeMachineAt`, no type | Partial | Easy-medium |
| 9 | keys in `IC2.ini`; `enetCableMeltdown=false` also disables explosions/shocks | `ic2tweaks.cfg`; separate toggles (superset) | Cosmetic | Easy |
| 11 | `NodeStats` from path data, same tick | `lastNodeStats` = `current · V`, one-tick stale | Missing | Medium |
| 12 | effects run inside the calculator, one pass per grid tick | machines in `processChanges`, cables at `WorldTickEvent.END` | Partial | Medium |
| 13 | no tier-resistance starvation | high-tier machines starve | Inherent IC2 quirk | Medium (mitigation) |
| 14 | absorption = `tierPower(insulation + (tin ? 0 : 1))`; non-insulatable types immune | 1.7.10 legacy table (Cu 5, Au 6, HV 3, Sn 8; insulated 9001) | Missing (deferred) | Easy |
| 15 | sink explosion on `amount` (post demand cap) | on `accepted` (`amount − returned`) | Equivalent for all-or-nothing sinks | Low |
| 16 | multi-packet sources (`IMultiEnergySource`) | 1.7.10 has no such API | Missing (part of 1) | Structural |

Gaps 2, 8 and 10 are closed or inherent as noted above.

### 12.3 If we want full effect parity

1. **Absorption table (gap 14)** — the one remaining visible rule gap. Map the 1.7.10 metas to insulation
   levels and use `getPowerFromTier(insulation + (tin ? 0 : 1))`, with glass/detector/splitter immune.
   This also closes finding 1 by construction (bare tin becomes the deadliest cable).
2. **Graceful hooks (gap 3)** — own `IOverloadHandler` / `IExplosionPowerOverride`, checked in 1.12.2
   order (`config → onOverload(getTierFromPower(amount)) → shouldExplode / getExplosionPower → explode`).
3. **Cable pass into the enet tick (gap 12)** — run it from the `processChanges` `RETURN` hook (fresh
   `NodeStats`, `locked == false`), removing the tick-ordering mismatch with machine explosions. Needs a
   world reference (take it from a registered conductor or shadow an enet field).
4. **Direct node read for cable stats (gap 11)** — removes the stale-tick window.
5. Optional, opt-in only: demand cap (gap 5), direct `ExplosionIC2` construction (gap 7), IC2.ini key
   mirroring (gap 9).

Items 1–4 put rule/math parity at roughly 97%, with the remaining divergences localised to the
distribution model.

### 12.4 If we want numerical parity

The only route to per-packet values is porting `EnergyCalculatorLeg` / `EnergyCalculatorUnified`: path
discovery, per-source distribution, `maxPacketConducted`, demand caps, rejection handling and
multi-packet sources — replacing `Grid.calculateDistribution` / `calculateEffects`. That one rewrite
closes gaps 1, 4, 5, 6 (exactly), 7 (partial), 11, 12, 15 and 16 at once.

Cost/risk:

- it changes energy *delivery* for every machine and addon (loss, demand caps, rejection
  redistribution), not just the overload effects;
- it must coexist with GTNH's IC2 patches and other enet mixins (StellarCore-style optimizers);
- the async calculation, `locked` rules and performance on large grids all need redoing.

Recommendation: do not do this unless replacing the energy net becomes the mod's goal. The middle path —
keeping 1.7.10 delivery but running one solve per source (others zeroed) to attribute link currents to
sources and synthesise path data *for effects only* — makes effects per-source accurate without touching
delivery, but costs O(sources) extra solves per grid per tick and still cannot model true packet caps.
It is clever but probably not worth it vs. the effect-parity work in §12.3.
