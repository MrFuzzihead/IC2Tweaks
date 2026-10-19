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

## Requirements

| Dependency | Version | Notes |
| ---------- | ------- | ----- |
| Minecraft | 1.7.10 | — |
| Minecraft Forge | 10.13.4.1614 | [Download](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.7.10.html) |
| [IndustrialCraft²](https://www.curseforge.com/minecraft/mc-mods/industrial-craft) | 2.2.828-experimental | Required at runtime |
| [UniMixins](https://github.com/GTNewHorizons/UniMixins) | any GTNH build | Required at runtime; pulled in automatically as a dependency |

## Configuration

`config/ic2tweaks.cfg`, `energynet` category (global — applies to all IC2 machines and cables):

| Option | Default | Description |
| ------ | ------- | ----------- |
| `enableEnetOverload` | `true` | Master switch. Legacy mode forces IC2's `misc/useLinearTransferModel` to `true`; packet mode replaces the distribution with the 1.12.2 packet calculator instead |
| `enablePacketModel` | `false` | Experimental 1.12.2 packet/path calculator. When on, melt/shock/explosion checks use each path's own packet (no aggregation), the legacy per-node passes are disabled, and the `useLinearTransferModel` override is skipped. Requires a restart |
| `packetModelRoundLossDown` | `false` | Packet model only: floor each path's accumulated loss, mirroring IC2 1.12.2's `misc/roundEnetLoss` |
| `packetModelTierVoltage` | `false` | Packet model only: report 1.12.2's raw tier index from `NodeStats.getVoltage()` instead of the rated tier voltage |
| `enableEnetExplosions` | `true` | Machines explode when they accept a packet above their tier's voltage |
| `enableEnetCableMeltdown` | `true` | Cables melt when their carried energy exceeds the conductor breakdown energy |
| `enableEnetShocks` | `true` | Living entities near an overloaded cable take electric shock damage |
| `enableDemandCappedPackets` | `false` | Cap each injection at the sink's remaining demand (IC2 1.12.2 packet sizing). Off because it changes how much energy every machine receives |

Because the overload system changes global IC2 behaviour, it is **on by default**; set
`enableEnetOverload=false` to disable it and restore stock IC2 behaviour. The default **legacy** model
enables IC2's experimental lossy model; the opt-in **packet** model (`enablePacketModel=true`, restart
required) replaces the per-node distribution with the 1.12.2 path/packet calculator, so fault checks use
each path's packet instead of sums across sources (the gap-1 fix) and the linear model is left alone.

Known limitations (a mix of pre-existing IC2 behaviour and documented port divergences; see
`ENET_OVERLOAD_ANALYSIS.md` gaps 10, 13 and 1):

- IC2's linear model loses energy on every link, including a direct machine-to-machine connection. A
  BatBox feeding another BatBox loses about 1.2% of the transferred energy (the link's 0.4 Ω inner
  loss), and a source that drops below its output packet size (32 EU/t for a BatBox) stops emitting,
  stranding the remainder.
- The linear model derives a sink's resistance from its tier, so machines with many transformer
  upgrades (roughly 6+ on an MFSU) draw almost no power. This is legacy-only; the packet model does not
  derive sink resistance from tier.
- The packet model is experimental: `NodeStats` values are packet-based, and `getVoltage()` reports rated
  volts unless `packetModelTierVoltage=true`; the mixins must be selected at startup (config change
  requires a restart).
- Cable shock sensitivity: bare cables are graded (tin 8, copper/gold/HV 32) and insulated cables are
  shock-safe at any load — they only arc while they overload and melt. This differs from IC2 1.12.2's
  per-layer table because 1.7.10's 2x/3x insulated gold/HV items are remapped to a single layer on
  placement (`TileEntityCable.onLoaded()`), so gold/HV could never be insulated past 128 there.
- Overload checks use 1.7.10's **per-node aggregate** values, not 1.12.2's per-source packet sizes. At a
  junction or with several sources feeding one cable, the cable can trip earlier than it would in 1.12.2,
  and two same-tier sources can trip the machine-explosion check. This is the remaining structural
  divergence; a packet-based calculator is planned separately.
- Insulated tin reports a conduction loss of 0.025 EU/block in IC2 1.7.10 (the same as glass fibre and
  lower than bare tin); the tooltip shows that value verbatim so it matches the simulation.

## API

Mods whose machines should survive (or shrink) an over-voltage packet can implement either interface on
their tile entity:

- `com.mrfuzzihead.ic2tweaks.api.IOverloadHandler` — `boolean onOverload(int tier)`; return `true` to
  cancel the default explosion.
- `com.mrfuzzihead.ic2tweaks.api.IExplosionPowerOverride` — `boolean shouldExplode()` and
  `float getExplosionPower(int tier, float defaultPower)`.

Both mirror IC2 Experimental 1.12.2's interfaces of the same name.

## Diagnostics

Run `/ic2tweaks status` in-game to confirm the feature is live. It reports:

- `mixin active` — whether the energy-net mixins were actually applied to IC2
- `enableEnetOverload` — the master switch
- `energy model` — `legacy per-node` or `packet (1.12.2)`; packet mode also prints its round-loss/tier-voltage options
- `useLinearTransferModel` and the raw `IC2.ini` value — proves the override forced the linear model
- the `enableEnet*` sub-toggles
- how many conductors are registered in the current world

## Building from source

This project uses the [GTNewHorizons](https://github.com/GTNewHorizons) 1.7.10 Gradle toolchain. The
correct JDK is provisioned automatically by Gradle — no manual Java setup required.

### Canary harness (development)

With packet mode enabled, run:

```
JAVA_TOOL_OPTIONS="-Dic2tweaks.canary=true" ./gradlew runServer
```

The harness builds IC2 test rigs at `y=160`, logs `[canary] PASS/FAIL`, and stops the server. It is
inactive unless the system property is set and is not part of normal gameplay.

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
