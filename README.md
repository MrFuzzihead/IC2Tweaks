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
| `enableEnetOverload` | `true` | Master switch. When on, the mixins are applied and IC2's `misc/useLinearTransferModel` is forced to `true` (ignoring `IC2.ini`) |
| `enableEnetExplosions` | `true` | Machines explode when they accept a packet above their tier's voltage |
| `enableEnetCableMeltdown` | `true` | Cables melt when their carried energy exceeds the conductor breakdown energy |
| `enableEnetShocks` | `true` | Living entities near an overloaded cable take electric shock damage |

Because the overload system changes global IC2 behaviour (and enables IC2's experimental lossy energy
model), it is **on by default**; set `enableEnetOverload=false` to disable it and restore stock IC2
behaviour. Cable meltdown and shocks only work while the linear transfer model is active, which the
master switch forces.

Known limitations (pre-existing IC2 linear-model behaviour unless noted; see
`ENET_OVERLOAD_ANALYSIS.md` gaps 10 and 13):

- IC2's linear model loses energy on every link, including a direct machine-to-machine connection. A
  BatBox feeding another BatBox loses about 1.2% of the transferred energy (the link's 0.4 Ω inner
  loss), and a source that drops below its output packet size (32 EU/t for a BatBox) stops emitting,
  stranding the remainder.
- The linear model derives a sink's resistance from its tier, so machines with many transformer
  upgrades (roughly 6+ on an MFSU) draw almost no power.
- Cable shock sensitivity still uses IC2 1.7.10's hard-coded absorption values (bare copper 5, gold 6,
  HV 3, tin 8; insulated cables never shock), not IC2 1.12.2's insulation-based formula. This is
  documented for now and will be revisited.

## Diagnostics

Run `/ic2tweaks status` in-game to confirm the feature is live. It reports:

- `mixin active` — whether the energy-net mixins were actually applied to IC2
- `enableEnetOverload` — the master switch
- `useLinearTransferModel` and the raw `IC2.ini` value — proves the override forced the linear model
- the `enableEnet*` sub-toggles
- how many conductors are registered in the current world

## Building from source

This project uses the [GTNewHorizons](https://github.com/GTNewHorizons) 1.7.10 Gradle toolchain. The
correct JDK is provisioned automatically by Gradle — no manual Java setup required.

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
