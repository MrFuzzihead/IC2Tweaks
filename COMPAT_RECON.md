# IC2 Internals Compatibility Scan

Generated: 2026-10-19 10:04

Offline scan of the production mod packs for references to IC2 internals that the packet-model work touches. The scanner reads every class constant pool, mixin config, access transformer and manifest entry in each jar; it does not execute any mod code.

## Severity legend

| Severity | Meaning |
|---|---|
| critical | References `ic2/core/energy` or injects classes under `ic2/core/energy` — direct overlap with our hooks |
| high | **Modifies** cabling internals (`ic2/core/block/wiring`) via mixin/coremod/AT, or injects other `ic2/core/**` classes |
| medium | References or mixins other IC2 internals (machines, reactors, items, cables) — delivery/behavior exposure rather than a hook conflict |
| api | Only uses `ic2/api/energy` (expected; delivery-change exposure, not a hook conflict) |

"Modifies" means a mixin class, coremod transformer, access transformer or package injection; a jar that merely extends or calls an IC2 internal class is at most medium.

## Summary

| Pack | Jars | critical | high | medium | api | none |
|---|---:|---:|---:|---:|---:|---:|
| FuzzCraft-Legacy | 233 | 0 | 1 | 26 | 10 | 196 |
| FuzzCraft-Modrinth | 20 | 0 | 0 | 0 | 0 | 20 |
| Alone-Modded-World | 26 | 0 | 0 | 0 | 0 | 26 |

## Baseline conclusion

* No jar references `ic2/core/energy` in this snapshot. The packet-model hooks (`Grid`, `EnergyNetLocal.processChanges`, `getNodeStats`) have no static conflict in these packs.
* Cabling-internal modifications to inspect: `EnergyControl-1.7.10-0.3.7.jar`.
* Jars with mixin/transformer/hook classes that reference IC2 internals: `EnergyControl-1.7.10-0.3.7.jar`, `angelica-2.2.34.jar`, `backhand-1.8.17.jar`, `hodgepodge-2.7.216.jar`, `unidict-1.0.2.jar`. These are the ones to re-check against our hooks and delivery changes.
* Medium rows are addons/mixins that reference IC2 machines, reactors, items or cables; treat them as delivery-change canaries. Plain class references are not hook conflicts.

## FuzzCraft-Legacy

Directory: `C:/Users/Duncan/AppData/Roaming/PrismLauncher/instances/FuzzCraft-Legacy/minecraft/mods` — 233 jars.

### High findings

| Jar | Mechanisms | IC2 internals referenced | Notes |
|---|---|---|---|
| `EnergyControl-1.7.10-0.3.7.jar` | coremod | `ic2.core.block.wiring.BlockElectric`<br>`ic2.core.block.wiring.TileEntityElectricBlock`<br>`ic2.core.block.wiring.TileEntityElectricMFSU` | coremod `com.zuxelus.energycontrol.hooks.ECHookLoader` |

### Medium findings

| Jar | Mechanisms | IC2 internals referenced | Notes |
|---|---|---|---|
| `AFSU-1.3.2-GTNH.jar` | bytecode | `ic2.core.block.wiring.TileEntityElectricBlock` |  |
| `AdvancedSolarPanel_1.7.10_Edition.jar` | bytecode | `ic2.core.block.BlockMultiID` |  |
| `DistantHorizons-upstream-preview8.jar` | coremod | `ic2.core.block.BlockTextureStitched` | coremod `com.seibel.distanthorizons.DistantHorizonsTweaker` |
| `Galacticraft-3.4.35-GTNH.jar` | coremod | `ic2.core.block.wiring.TileEntityCable` | coremod `micdoodle8.mods.galacticraft.core.asm.GCLoadingPlugin` |
| `GraviSuite-1.7.10-2.0.3.jar` | bytecode | `ic2.core.IC2`<br>`ic2.core.audio.AudioManager`<br>`ic2.core.audio.AudioSource`<br>`ic2.core.audio.PositionSpec` |  |
| `IC2NuclearControl-2.7.17.jar` | bytecode | `ic2.core.IC2`<br>`ic2.core.Ic2Items`<br>`ic2.core.block.generator.tileentity.TileEntityBaseGenerator`<br>`ic2.core.block.reactor.tileentity.TileEntityNuclearReactorElectric` (+8) |  |
| `JABBA-1.5.23.jar` | bytecode | `ic2.core.block`<br>`ic2.core.block.machine.tileentity.TileEntityNuke` |  |
| `NodalMechanics-1.3.4-GTNH.jar` | bytecode | `ic2.core.Ic2Items` |  |
| `NotEnoughResources-1.7.10-0.1.0-122.jar` | bytecode | `ic2.core.IC2`<br>`ic2.core.Ic2Items`<br>`ic2.core.init.MainConfig`<br>`ic2.core.util.Config` (+1) |  |
| `OpenPeripheralIntegration-1.7.10-0.6.jar` | bytecode | `ic2.core.block.machine.tileentity.TileEntityElectricMachine` |  |
| `StevesCarts-2.3.15.jar` | bytecode | `ic2.core.Ic2Items`<br>`ic2.core.crop.TileEntityCrop`<br>`ic2.core.item.ItemCropSeed` |  |
| `TConstruct-1.14.119-GTNH.jar` | bytecode | `ic2.core.IC2` |  |
| `WAILAPlugins-0.7.2.jar` | bytecode | `ic2.core.crop.TileEntityCrop` |  |
| `Waila-1.19.34.jar` | bytecode | `ic2.core.block.generator.tileentity.TileEntityBaseGenerator`<br>`ic2.core.block.generator.tileentity.TileEntityGeoGenerator`<br>`ic2.core.block.generator.tileentity.TileEntityKineticGenerator`<br>`ic2.core.block.generator.tileentity.TileEntitySemifluidGenerator` (+1) |  |
| `advancedmachines-1.1.7.jar` | bytecode | `ic2.core.ContainerBase`<br>`ic2.core.IC2`<br>`ic2.core.IHasGui`<br>`ic2.core.Platform` (+8) |  |
| `angelica-2.2.34.jar` | mixin, coremod | `ic2.core.coremod.IC2core`<br>`ic2.core.item.ItemFluidCell`<br>`ic2.core.item.RenderLiquidCell` | coremod `com.gtnewhorizons.angelica.loading.AngelicaTweaker`; 1 mixin classes |
| `appliedenergistics2-rv3-beta-1087-GTNH.jar` | coremod | `ic2.core.crop.TileEntityCrop` | coremod `appeng.transformer.AppEngCore` |
| `backhand-1.8.17.jar` | mixin, coremod | `ic2.core.slot.SlotArmor` | coremod `xonin.backhand.coremod.BackhandLoadingPlugin`; 1 mixin classes |
| `bogosorter-1.3.54-GTNH.jar` | coremod | `ic2.core.block.personal.ContainerPersonalChest`<br>`ic2.core.item.tool.ContainerToolbox` | coremod `com.cleanroommc.bogosorter.core.BogoSorterCore` |
| `buildcraft-compat-7.1.22.jar` | bytecode | `ic2.core.block.BlockMultiID` |  |
| `gravisuiteneo-1.3.16.jar` | bytecode | `ic2.core.item.armor.ItemArmorQuantumSuit` |  |
| `gtnh-web-map-0.4-beta-1.jar` | bytecode | `ic2.core.block.wiring.BlockCable`<br>`ic2.core.block.wiring.BlockChargepad`<br>`ic2.core.block.wiring.BlockElectric`<br>`ic2.core.block.wiring.BlockLuminator` (+2) |  |
| `hodgepodge-2.7.216.jar` | mixin, coremod | `ic2.core.ContainerBase`<br>`ic2.core.IC2`<br>`ic2.core.Ic2Items`<br>`ic2.core.Platform` (+8) | coremod `com.mitchej123.hodgepodge.core.HodgepodgeCore`; 12 mixin classes |
| `logisticspipes-1.5.36-GTNH.jar` | coremod | `ic2.core.block.machine.tileentity.TileEntityStandardMachine` | coremod `logisticspipes.asm.LogisticsPipesCoreLoader` |
| `lwjgl3ify-3.0.39.jar` | coremod | `ic2.core.init.InternalName` | coremod `me.eigenraven.lwjgl3ify.core.Lwjgl3ifyCoremod` |
| `unidict-1.0.2.jar` | mixin, coremod | `ic2.core.AdvRecipe`<br>`ic2.core.AdvShapelessRecipe` | coremod `com.mrfuzzihead.unidict.mixins.EarlyMixinsLoader`; 2 mixin classes |

### API consumers (informational)

These jars only reference `ic2/api/energy`; no hook conflict is expected, but they are subject to the packet model's delivery changes (multi-injection, demand caps, source throttling, `NodeStats` semantics).

`CompactSolars-1.7.10-4.4.39.315-universal.jar`, `Forestry-4.11.39.jar`, `ImmersiveEngineering-0.7.12.jar`, `MineFactoryReloaded-[1.7.10]2.8.2B1-201.jar`, `Railcraft-9.17.34-gtnh-compat.9+c11a72963f.jar`, `RemoteIO-2.7.11.jar`, `ThaumicTinkerer-2.12.33.jar`, `binnie-mods-2.6.48.jar`, `extrautilities-1.2.12.jar`, `gendustry-1.9.13-GTNH.jar`

## FuzzCraft-Modrinth

Directory: `C:/Users/Duncan/AppData/Roaming/ModrinthApp/profiles/FuzzCraft/mods` — 20 jars.

## Alone-Modded-World

Directory: `C:/Users/Duncan/AppData/Roaming/ModrinthApp/profiles/Alone_ Modded World/mods` — 26 jars.

## Scan limitations

* Static analysis only: a coremod that matches classes/methods by obfuscated name or computes target names at runtime can be missed. ASM transformers are detected only through literal IC2 class-name references.
* Nested jars are listed in the JSON but not scanned recursively; none were expected to matter for IC2 internals, but check the list before release.
* This pack snapshot is authoritative only for this snapshot; re-run the scanner after every pack update and before the Phase 4 default flip.

## Follow-up

* Inspect every critical/high hit by hand and add a row to the compatibility matrix.
* For medium hits, decide whether the mod is an IC2 addon (delivery-change canary) or only uses internals for its own classes.
* Re-run on the secondary packs; compare the JSON (hashes identify the same jar).
* Re-check after Phase 2 lands and again at Phase 4.
