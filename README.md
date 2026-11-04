# IC2Tweaks

[![Minecraft](https://img.shields.io/badge/Minecraft-1.7.10-62a34a)](https://minecraft.wiki/w/Java_Edition_1.7.10)
[![Forge](https://img.shields.io/badge/Forge-10.13.4.1614-1e2b4f)](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.7.10.html)
[![IC2](https://img.shields.io/badge/IC2-2.2.828--experimental-blue)](https://www.curseforge.com/minecraft/mc-mods/industrial-craft)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

**IC2Tweaks** is a standalone [IndustrialCraft²](https://www.curseforge.com/minecraft/mc-mods/industrial-craft)
core-tweak mod for **Minecraft 1.7.10 (Forge)**. It is deliberately separate from machine-addon mods so
that changes to IC2 itself can be shipped and versioned independently.

## Features

- **Global energy-net overload system** (on by default): an IC2 Experimental 1.12.2 style
  voltage/packet overload layer for **every** IC2 machine and cable, not just one addon's machines —
  over-voltage machines explode, overloaded cables melt, and nearby entities take shock damage.
  While enabled it forces IC2's linear transfer model (ignoring `IC2.ini`), which is the model that
  carries physical voltage/current data. Cable items also gain an energy-loss tooltip line.
  See [Configuration](#configuration).
- **1.12.2-style cable rules**: bare cables shock above their graded thresholds (tin 8 EU/t;
  copper/gold/HV 32 EU/t), while insulated cables are shock-safe unless they actually overload and melt.
  TEs can opt out of or resize an over-voltage blast via `com.mrfuzzihead.ic2tweaks.api.IOverloadHandler`
  / `IExplosionPowerOverride`.
- **Lossless wrenching** (on by default): machines removed with a wrench always drop themselves instead of
  breaking into a machine casing. IC2 only has a per-item NBT toggle on the electric wrench
  (`losslessMode`) and per-machine drop rates (0.7–0.9 for most IC2 machines); this config makes every
  wrench use IC2's lossless path. The cost is balanced: normal wrenches pay 40 durability (4× the normal
  10) and electric wrenches pay 10,000 EU (IC2's native lossless cost, 200 damage × 50 EU).
  Blocks that already drop 100% of the time keep the normal 10-damage cost.
- **Jetpack improvements (Phase 2/3)**: IC2's stock jetpack tick is replaced by a configurable flight
  controller: full 8-direction horizontal flight with acceleration/braking and the IC2 boost key,
  configurable hover/ascent, drain-before-thrust, guards for riding/creative flight/water/lava, a
  per-player hover state that no longer depends on the old NBT sync, and a HUD fix that hides the
  jetpack's fake 8 armour points. Stock jetpacks can also be worn in Baubles-Expanded `cape`/`wings`/`body`/`belt`
  slots or the TravellersGear cloak/shoulder slot, with a rendered backpack. Phase 3 adds a compact
  fuel/mode/speed/altitude HUD, localized fuel/mode/slot/controls tooltips, exhaust particles, chargepad
  support for bauble/TG electric jetpacks, and optional quantum-chest integration. A chest jetpack always
  wins over a bauble/TG jetpack. The flight pass also removes the arbitrary Y ceiling, adds soft-landing
  assist, dedicated hover/power keys, easier gas refuelling and balanced electric defaults; see
  `JETPACK_IMPROVEMENTS.md`.
- **Optional Waila/Wawla support for base IC2**: Waila's built-in IC2 addon only covers generators, and
  Wawla has no IC2 module at all. IC2Tweaks registers a Waila provider for every `IEnergyStorage`
  (BatBox, CESU, MFE, MFSU, chargepads and addon storage) and for ordinary `TileEntityElectricMachine`
  buffers. It shows stored/capacity EU, tier and rated voltage, and the storage block's output. The
  provider is loaded through Waila's IMC callback, so Waila remains optional.

## Waila / Wawla integration

Waila 1.19.34's IC2 addon (`mcp.mobius.waila.addons.ic2.IC2Module`) only reflects into the five
generator families (`TileEntityBaseGenerator`, geo, kinetic, semifluid, stirling). It registers no
provider for `ic2.api.tile.IEnergyStorage` or `TileEntityElectricMachine`, so IC2 storage blocks and
machines have no body lines. Wawla 1.3.4-GTNH has no IC2 module; its generic block provider only adds
harvest/light/hardness information.

IC2Tweaks fills the gap through Waila's IMC callback. The integration is optional and is only loaded
when Waila is installed. It displays:

| Block | Lines |
| ----- | ----- |
| BatBox / CESU / MFE / MFSU, chargepads, addon `IEnergyStorage` | `Energy` bar, `Tier: LV/MV/HV/EV (voltage EU/t)`, `Output: N EU/t` |
| `TileEntityStandardMachine` (macerator, compressor, extractor, recycler, metal former, electric furnace, centrifuge, canner, block cutter, …) | `Buffer` bar, `Progress` bar, `Tier: LV/MV/HV/EV (voltage EU/t)` |

The `Energy`/`Buffer` and `Progress` lines use Waila's `waila.bar` renderer — the same gradient bar the
vanilla furnace uses for its fuel and smelt lines — rather than plain text. The progress bar shows the
current operation in seconds (for example `Progress 2.5s / 5.0s` for an Electric Furnace) and follows
overclocker changes to `operationLength`. Machines that do not extend `TileEntityStandardMachine` (Induction, Magnetizer,
Scanner, AdvMiner, and the non-electric Iron/Blast Furnace) do not expose those standard progress fields
and are not covered yet.

The lines can be toggled in Waila's own config under the `IC2Tweaks` module (`ic2tweaks.energy`,
`ic2tweaks.tier`, `ic2tweaks.output`, `ic2tweaks.progress`). Waila's server-side NBT sync is used when
available; if Waila is installed only on the client, the provider falls back to reading the client tile
entity directly. IC2 only continuously syncs a storage block's `energy` field while its GUI is open, so in
client-only mode the stored value is best-effort; capacity, tier and output are static and remain accurate.
Note that Waila's built-in IC2 generator options (`ic2.storage`, `ic2.outputeu`) are registered as *remote*
configs, so they silently return false when Waila is not installed on the server; IC2Tweaks' options are
client-side and do not have that restriction.

### Addon compatibility

Waila resolves providers with `Class.isInstance`, so IC2Tweaks automatically covers addon TEs that are
instances of the registered types:

| Addon TE | Automatic lines |
| -------- | --------------- |
| extends `TileEntityStandardMachine` (for example AdvancedMachines) | Buffer bar, Progress bar, Tier |
| extends `TileEntityElectricMachine` | Buffer bar, Tier |
| implements `IEnergyStorage` | Energy bar, Tier, Output |
| extends one of Waila's five IC2 generator classes | Waila's built-in generator storage/output lines |

Custom generator bases that only implement `IEnergySource` (for example CompactSolars'
`TileEntityCompactSolar` or AdvancedSolarPanels' `TileEntitySolarPanel`) are not matched by either
provider. Those addons need their own Waila provider, or IC2Tweaks would need a generic
`IEnergySource` provider; CompactSolars keeps its storage fields private, so it would also need getters
or an interface. A TE that implements `IEnergyStorage` and also extends `TileEntityElectricMachine` is
currently treated as storage (energy/tier/output) rather than a machine (buffer/progress).

## Requirements

| Dependency | Version | Notes |
| ---------- | ------- | ----- |
| Minecraft | 1.7.10 | — |
| Minecraft Forge | 10.13.4.1614 | [Download](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.7.10.html) |
| [IndustrialCraft²](https://www.curseforge.com/minecraft/mc-mods/industrial-craft) | 2.2.828-experimental | Required at runtime |
| [UniMixins](https://github.com/GTNewHorizons/UniMixins) | any GTNH build | Required at runtime; pulled in automatically as a dependency |
| [Baubles-Expanded](https://github.com/GTNewHorizons/Baubles-Expanded) | 2.2.27-GTNH | Optional: jetpack in a `cape`/`wings`/`body`/`belt` slot |
| [TravellersGear](https://www.curseforge.com/minecraft/mc-mods/travellers-gear) | 1.16.x | Optional: jetpack in the cloak/shoulder slot |

## Configuration

`config/ic2tweaks.cfg`, `wrench` category:

| Option | Default | Description |
| ------ | ------- | ----------- |
| `losslessWrench` | `true` | Every wrench uses IC2's lossless path for machines whose drop rate is below 100%, so wrenched machines always drop themselves instead of a casing. Blocks already at 100% keep their normal cost. Requires a restart |
| `losslessWrenchDurabilityCost` | `20` | Durability charged per forced-lossless operation with a normal wrench (IC2's normal wrench cost is 10) |
| `losslessWrenchEnergyCost` | `5000` | EU charged per forced-lossless operation with an electric wrench. Half of IC2's native lossless cost; rounded up to the next 50 EU. Set `0` for free lossless wrenching |

`energynet` category (global — applies to all IC2 machines and cables):

| Option | Default | Description |
| ------ | ------- | ----------- |
| `enableEnetOverload` | `true` | Master switch. Legacy mode forces IC2's `misc/useLinearTransferModel` to `true`; packet mode replaces the distribution with the 1.12.2 packet calculator instead |
| `enablePacketModel` | `true` | 1.12.2 packet/path calculator (default). Melt/shock/explosion checks use each path's own packet (no aggregation), the legacy per-node passes are disabled, and the `useLinearTransferModel` override is skipped. Set `false` for the one-release legacy fallback. Requires a restart |
| `packetModelRoundLossDown` | `true` | Packet model only: floor each path's accumulated loss, matching IC2 1.12.2's default `misc/roundEnetLoss=true`; set `false` for fractional 1.7.10 losses |
| `packetModelTierVoltage` | `false` | Packet model only: report 1.12.2's raw tier index from `NodeStats.getVoltage()` instead of the rated tier voltage |
| `enableEnetExplosions` | `true` | Machines explode when they accept a packet above their tier's voltage |
| `enableEnetCableMeltdown` | `true` | Cables melt when their carried energy exceeds the conductor breakdown energy |
| `enableEnetShocks` | `true` | Living entities near an overloaded cable take electric shock damage |
| `enableDemandCappedPackets` | `false` | Legacy model only: cap each injection at the sink's remaining demand. The packet model always does this as part of its distribution |

Because the overload system changes global IC2 behaviour, it is **on by default**; set
`enableEnetOverload=false` to disable it and restore stock IC2 behaviour. The **packet** model is the
default since Phase 4: it replaces the per-node distribution with the 1.12.2 path/packet calculator, so
fault checks use each path's packet instead of sums across sources (the gap-1 fix) and the linear model is
left alone. The legacy per-node model remains available with `enablePacketModel=false` (restart required)
as a one-release fallback; in that mode voltages are solved values (roughly 80% of the tier) and machines
with many transformer upgrades still starve. With packet mode, `NodeStats.getVoltage()` reports rated tier
volts (32/128/512/2048) by default — set `packetModelTierVoltage=true` for tier indices (1/2/3/4).

Packet-mode stats are per current tick, like 1.12.2: once a machine's buffer is full and no packet flows,
the meter reads 0. The legacy model kept the last solved voltage, so a storage block that read a voltage
while idle in legacy mode can read 0 in packet mode; measure while the machine is actively consuming.
A nearly full sink also receives only its remaining demand (demand cap), so the reported tier can be 0 even
while a small amount flows.

`NodeStats` energy in/out **sums** the packets, while `voltage` is the rated tier of the **largest packet**,
rounded up: 33–128 EU/t → 128 V, 129–512 → 512 V. Two 32 EU/t packets read as 64 EU/t total at 32 V with
the default `roundLossDown=true`; with it false the total is ≈64.0 for a direct machine link or ≈63.6
through a cable. They are not one 64 EU/t packet at 128 V. A demand-capped 49 EU/t packet from an MFSU
reads 128 V even though the MFSU's own tier is 2048.

Known limitations (a mix of pre-existing IC2 behaviour and documented port divergences; see
`ENET_OVERLOAD_ANALYSIS.md` gaps 10, 13 and 1):

- Packet mode uses IC2 1.12.2's loss constants: a 0.002 EU source/sink inner loss and the type-based
  cable table (copper 0.2, gold 0.4, HV 0.8, tin 0.2, glass 0.025; insulation does not change loss in
  1.12.2). A direct machine-to-machine link is therefore 0.002 EU — effectively lossless, and floored to
  zero by the default `roundLossDown=true`. The legacy fallback keeps the 1.7.10 constants (0.4 machine
  links; 1.2% BatBox-to-BatBox loss) so it remains a true fallback.
- The linear model derives a sink's resistance from its tier, so machines with many transformer
  upgrades (roughly 6+ on an MFE) draw almost no power. This is legacy-only; the packet model does not
  derive sink resistance from tier.
- Packet mode is the default since Phase 4: `NodeStats` values are packet-based, and `getVoltage()` reports
  rated volts unless `packetModelTierVoltage=true`; the mixins are selected at startup, so a config change
  requires a restart. Set `enablePacketModel=false` for the legacy fallback.
- Cable shock sensitivity: bare cables are graded (tin 8, copper/gold/HV 32) and insulated cables are
  shock-safe at any load — they only arc while they overload and melt. This differs from IC2 1.12.2's
  per-layer table because 1.7.10's 2x/3x insulated gold/HV items are remapped to a single layer on
  placement (`TileEntityCable.onLoaded()`), so gold/HV could never be insulated past 128 there.
- In the **legacy fallback** (`enablePacketModel=false`), overload checks use 1.7.10's per-node aggregate
  values, so a junction or several sources feeding one cable can trip earlier than in 1.12.2 and two
  same-tier sources can trip the machine-explosion check. This is exactly what the packet model (the
  default) fixes; the fallback exists for one release only.
- In the **legacy fallback**, insulated tin reports 0.025 EU/block (the 1.7.10 quirk, lower than bare
  tin). Packet mode uses 1.12.2's 0.2 for tin regardless of insulation; the tooltip follows the active
  table.

`jetpack` category (Phase 2/3: the flight tick, optional Baubles-Expanded/TravellersGear slots, HUD,
tooltips and particles are active):

| Option | Default | Description |
| ------ | ------- | ----------- |
| `enableJetpackTweaks` | `true` | Master switch for the jetpack tweaks. Requires a restart |
| `horizontalSpeed` | `0.32` | Target horizontal speed in blocks/tick while a movement key is held (about 6.4 m/s) |
| `horizontalAccel` / `horizontalBrake` | `0.05` / `0.08` | How quickly horizontal velocity builds up / bleeds off, in blocks/tick per tick |
| `strafeFactor` / `backwardFactor` | `0.8` / `0.6` | Sideways and backwards speed multipliers |
| `boostMultiplier` / `boostFuelMultiplier` | `1.6` / `2.5` | IC2 boost-key speed and fuel multipliers |
| `ascentSpeed` | `0.6` | Maximum normal ascent speed in blocks/tick (stock IC2 is 0.6 b/t) |
| `hoverRise` / `hoverSink` / `hoverIdle` | `0.2` / `0.2` / `0.0` | Hover-mode vertical speeds in blocks/tick |
| `electricHoverFactor` | `1.0` | Scales the electric jetpack's hover speeds; balanced parity with gas (stock 0.5) |
| `electricPowerFactor` | `1.0` | Normal-mode lift/horizontal multiplier; balanced parity with gas (stock 0.7) |
| `gasFuelPerTick` / `gasHoverFuelPerTick` | `2` / `1` | Biogas mB/tick in normal/hover mode |
| `electricEuPerTick` / `electricHoverEuPerTick` | `4` / `2` | Balanced EU/tick for the electric jetpack (stock 8/7); half gas endurance but rechargeable |
| `quantumEuPerTick` / `quantumHoverEuPerTick` | `8` / `10` | EU/tick for the integrated quantum chest in normal/hover mode |
| `altitudeFadeBlocks` / `electricAltitudeFactor` | `0` / `1.28` | `0` removes the arbitrary Y ceiling; set 25 for IC2's stock fade/ceiling |
| `baubleSlotTypes` | `cape,wings,body,belt` | Comma-separated Baubles slot types (cape/wings/body require Baubles-Expanded; belt works with original Baubles); `OFF` disables. The old `cape,body,belt` default is migrated to include wings. Requires a restart |
| `travellersGearSlot` | `CLOAK` | `OFF`, `CLOAK` or `SHOULDERS`. Requires a restart |
| `tweakQuantumJetpack` | `true` | Drive the quantum chest with the new controller (8/10 EU/t, same movement/HUD/particles). Set false for stock quantum behaviour; requires a restart |
| `chargepadChargesBaubles` | `true` | Let chargepads charge an electric jetpack worn in a Baubles/TG slot. Requires a restart |
| `slotFuelMultiplier` | `1.0` | Extra fuel multiplier while worn in a bauble/TG slot |
| `allowInWater` / `allowInLava` | `false` | Allow thrust in water/lava |
| `creativeNoDrain` / `fallProtection` | `true` / `false` | Creative no-drain; keep fall protection while charged |
| `stripJetpackArmorDisplay` | `true` | Hide the misleading 8 HUD armour points the diamond-material jetpack shows despite 0 absorption |
| `hudEnabled` / `hudCorner` | `true` / `TOP_LEFT` | Show the jetpack HUD and pick its corner (`TOP_LEFT`, `TOP_RIGHT`, `BOTTOM_LEFT`, `BOTTOM_RIGHT`) |
| `hudShowMode` | `false` | Show the Normal/Hover/Disabled mode line |
| `hudShowSpeed` / `hudShowAltitude` | `false` / `false` | Add the horizontal-speed and Y-altitude lines to the HUD |
| `hudLowFuelPercent` | `10` | Fuel percentage at/below which the HUD warns; 0 disables the warning |
| `tooltipsEnabled` | `true` | Add localized fuel/charge, mode, worn-slot and controls lines to the jetpack tooltip |
| `particlesEnabled` / `particleIntervalTicks` | `true` / `2` | Exhaust particles while thrusting, and the ticks between emissions (0 disables) |
| `particleYOffset` | `0.75` | Height above the player's feet where exhaust spawns; lower it if the particles look too high |
| `landingAssist` | `true` | Soft-landing braking near the ground when falling with a charged jetpack |
| `landingAssistDistance` / `landingAssistSpeed` | `3.0` / `0.08` | Start height and maximum sink speed for landing assist |
| `landingAssistDrain` | `true` | Consume hover fuel while landing assist brakes; empty/insufficient jetpacks fall normally |
| `hoverMomentum` | `0.0` | Upward hover velocity carried after releasing jump; 0 stops immediately |
| `pauseInGui` | `false` | Stop jetpack thrust/hover while a GUI is open (client-side pause) |
| `dedicatedHoverKey` / `dedicatedPowerKey` | `true` / `true` | Enable the dedicated hover key (H) and jetpack power switch (J), both configurable in Controls |
| `easyGasRefuel` | `true` | Right-click a biogas container to refill a worn gas jetpack |
| `syncIntervalTicks` | `0` | Extra server-to-client fuel sync interval; 0 means client simulation only |

**Quantum chest:** the integrated jetpack is part of the quantum chestplate (`quantumBodyarmor`), not a
separate item. With `tweakQuantumJetpack=true` and a restart, wear the chest; a fresh chest auto-enables its
jetpack, Boost + Mode toggles it, and Jump + Mode toggles hover.

Old configs that still contain IC2's stock electric quartet (`8`/`7`/`0.7`/`0.5`) and
`altitudeFadeBlocks=25` are migrated to the balanced/no-ceiling defaults on load. Partially customised
values are left untouched.

## API

Mods whose machines should survive (or shrink) an over-voltage packet can implement either interface on
their tile entity:

- `com.mrfuzzihead.ic2tweaks.api.IOverloadHandler` — `boolean onOverload(int tier)`; return `true` to
  cancel the default explosion.
- `com.mrfuzzihead.ic2tweaks.api.IExplosionPowerOverride` — `boolean shouldExplode()` and
  `float getExplosionPower(int tier, float defaultPower)`.

Both mirror IC2 Experimental 1.12.2's interfaces of the same name.

## Diagnostics

Run `/ic2tweaks status` in-game to confirm the energy-net and wrench features are live, or
`/ic2tweaks jetpack` for the jetpack config, the tick/HUD/chargepad/quantum mixin markers and the
stock/configured horizontal speeds. `status` reports:

- `mixin active` — whether the energy-net mixins were actually applied to IC2
- `wrench mixin active` — whether the wrench drop-rate mixin was applied, plus the `losslessWrench` value and its durability/EU costs
- `enableEnetOverload` — the master switch
- `energy model` — `legacy per-node` or `packet (1.12.2)`; packet mode also prints its round-loss/tier-voltage options
- `useLinearTransferModel` and the raw `IC2.ini` value — proves the override forced the linear model
- the `enableEnet*` sub-toggles
- how many conductors are registered in the current world
- `waila plugin` — whether Waila's IMC callback registered the optional provider

## Building from source

This project uses the [GTNewHorizons](https://github.com/GTNewHorizons) 1.7.10 Gradle toolchain. The
correct JDK is provisioned automatically by Gradle — no manual Java setup required.

### Canary harness (development)

With packet mode enabled, run:

```
JAVA_TOOL_OPTIONS="-Dic2tweaks.canary=true" ./gradlew runServer
```

The harness builds IC2 test rigs at `y=160`, logs `[canary] PASS/FAIL`, and stops the server. It also
checks the pure jetpack physics, the jetpack/HUD/chargepad/quantum mixin markers, and ticks a real
`EntityPlayer` with a filled gas jetpack in hover mode to verify the handler drains fuel and resets fall
distance. With Baubles loaded it also verifies the slot resolver, charges a bauble-slot electric jetpack
from a real chargepad tile and refills an empty gas jetpack from a real biogas cell; with
`tweakQuantumJetpack=true` it checks the quantum mixin, chest-wins resolver and a real quantum hover
drain. It is inactive unless the system property is set and is not part of normal gameplay.

```bash
git clone https://github.com/MrFuzzihead/IC2Tweaks.git
cd IC2Tweaks
./gradlew build        # on Windows: gradlew.bat build
```

The built jars are placed in `build/libs/`. To run the unit tests or launch a development client:

```bash
./gradlew test         # on Windows: gradlew.bat test
./gradlew runClient    # on Windows: gradlew.bat runClient
```

## License

MIT — see [LICENSE](LICENSE).
