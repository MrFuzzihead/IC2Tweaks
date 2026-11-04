# IC2 Jetpack Improvement Plan

Status: Phase 0 (config + pure physics), Phase 1 (stock tick replacement, horizontal flight, bug fixes,
armor HUD fix), Phase 2 (Baubles-Expanded/TravellersGear slots + rendering) and the Phase 3 HUD/tooltip/particle, chargepad and optional quantum-chest integration are implemented.
A chest jetpack (stock, quantum or GraviSuite) always wins over a bauble/TG jetpack. The flight/UX pass
adds landing assist, configurable hover momentum, GUI pausing, a dedicated hover key, easy gas refuelling,
balanced electric defaults and removes the arbitrary Y ceiling. Remaining work is release validation and
the optional dedicated model/TG armour-stand rendering. Analysis based on decompiled IC2 2.2.828-experimental
(`ItemArmorJetpack`, `ItemArmorJetpackElectric`, `ItemArmorFluidTank`, `ItemArmorQuantumSuit`), the
1.7.10 movement code, the Baubles/TravellersGear 1.7.10 APIs, Hodgepodge 2.7.222 and GraviSuite 2.0.3.

Scope: the gas jetpack (`Ic2Items.jetpack`) and the electric jetpack (`Ic2Items.electricJetpack`), with
notes on the quantum suit's integrated jetpack and GraviSuite's Advanced JetPack where they matter.

---

## TL;DR / recommended order

1. **Horizontal flight** - the biggest UX win, and the cheapest root-cause fix (the jetpack's own
   horizontal impulse is only 0.0024-0.016 blocks/tick; vanilla air control alone is 0.02).
2. **Wearable with armor** (TravellersGear cloak slot first, Baubles-Expanded cape/wings/body/belt second) - very doable, but
   it needs a worn-stack resolver, a single owner for the tick, and a renderer. IC2 hard-codes
   `player.inventory.armorInventory[2]` and only ticks the chest slot.
3. **Bug fixes** - ground sound, fuel-boundary free thrust, static audio leak, creative/riding/water
   thrust, client toggle flicker, per-tick `detectAndSendChanges`.
4. **Polish** - HUD, tooltips/controls, particles, config for speeds/fuel/altitude, optionally unify the
   quantum suit's duplicate jetpack.

---

## 1. What the current code does

| Piece | Behaviour |
| --- | --- |
| `ItemArmorJetpack` | Gas jetpack. Extends `ItemArmorFluidTank` (biogas, 30,000 mB) and `ItemArmorUtility` (0 armour points). |
| `ItemArmorJetpackElectric` | Extends `ItemArmorJetpack`; overrides fluid methods to no-ops and implements `IElectricItem` (30,000 EU, tier 1, transfer 60 EU/t). |
| Tick | `Item.onArmorTick` is called from `InventoryPlayer.decrementAnimations()` inside `EntityPlayer.onLivingUpdate()`, **before** `super.onLivingUpdate()` does movement, on both sides. |
| Slot | `onArmorTick` only runs for the stack in `player.inventory.armorInventory[2]`; `useJetpack()` re-reads that exact slot. |
| Vertical | `motionY = min(motionY + power*0.2, 0.6)`; hover clamps to `+0.2` (jump), `0` (idle), `-0.2` (sneak), with a `prevmotion` carry-over. |
| Horizontal | Only when `IC2.keyboard.isForwardKeyDown(player)`: `player.moveFlying(0, 0.4*maxFlightHeight, 0.02)`. Nothing for strafe/back. |
| Fuel | Gas: 2 mB/tick (1 in hover). Electric: 8 EU/tick (7 in hover). Only drained while `!player.onGround`. |
| Altitude | Gas ceiling = `world.getHeight()`; electric ceiling = `getHeight()/1.28` (200 in a 256-high world). Power fades over the top 25 blocks. |
| Hover toggle | Jump + IC2 mode-switch key; `hoverMode` and a `toggleTimer` byte live in the stack's NBT; only the server decrements the timer. |
| Sound | Static `audioSource` / `lastJetpackUsed` fields on the item class, driven from `onArmorTick` when `player == localPlayer`. |
| Sync | `player.inventoryContainer.detectAndSendChanges()` every tick while "used" (`onArmorTick` is inherited, so both stock items do it). |

**Third copy of the same physics:** `ItemArmorQuantumSuit.useJetpack()` (the quantum chest's built-in
jetpack) implements the same formulas with different constants (10,000,000 EU, 8/10 EU/t, forward
factor 3.5, hover idle `-0.025`). GraviSuite's `ItemAdvancedJetPack` is independent, but its boost-key
handling is a good precedent.

**Already fixed by Hodgepodge in your pack (important for compatibility):**

- `fixIc2ArmorLag` (default `true`) mixins `ItemArmorJetpack.onArmorTick` and redirects
  `Container.detectAndSendChanges()` to a no-op. Do **not** add another `@Redirect` on that same call;
  our replacement should simply not call it.
- `fixIc2HoverMode` (default `true`) changes the quantum suit's hover constant `-0.025` to `0.0`
  (altitude hold). If we ever replace the quantum's `useJetpack`, that fix becomes redundant but
  harmless.

---

## 2. Why horizontal movement feels bad (measured)

`Entity.moveFlying(strafe, forward, friction)` normalises the input:

```
f3 = sqrt(strafe^2 + forward^2)
if (f3 < 1) f3 = 1
f3 = friction / f3
motion += (strafe, forward) * f3
```

So if the input magnitude is >= 1 the added impulse saturates at `friction` (0.02 in IC2's calls);
only inputs below 1 scale down. With the 0.91 horizontal air drag the terminal speed is
`impulse / 0.09`.

| Jetpack / mode | `0.4*maxFlightHeight` input | Impulse per tick | Terminal speed |
| --- | ---: | ---: | ---: |
| Vanilla air control (no jetpack) | 1.0 | 0.0200 | 0.222 b/t = **4.44 m/s** |
| Gas jetpack, normal | 0.12 | 0.0024 | 0.027 b/t = **0.53 m/s** |
| Gas jetpack, hover | 0.80 | 0.0160 | 0.178 b/t = **3.56 m/s** |
| Electric jetpack, normal | 0.168 | 0.0034 | 0.037 b/t = **0.75 m/s** |
| Electric jetpack, hover | 0.644 | 0.0129 | 0.143 b/t = **2.86 m/s** |
| Quantum suit, normal | 2.80 | 0.0200 | 0.222 b/t = **4.44 m/s** |
| Quantum suit, hover | 0.40 | 0.0080 | 0.089 b/t = **1.78 m/s** |
| GraviSuite, normal | 0.24 | 0.0048 | 0.053 b/t = **1.07 m/s** |
| GraviSuite, boost | 0.33 (friction 0.11) | 0.0363 | 0.403 b/t = **8.07 m/s** |

Consequences:

- The **gas/electric jetpack's own horizontal contribution in normal mode is 0.53/0.75 m/s** - you are
  mostly just air-strafing at 4.44 m/s. That is exactly "only good up and down".
- **Back and side movement get no jetpack thrust at all** - only the vanilla 4.44 m/s air control. IC2's
  `Keyboard` even only tracks the forward key, not strafe/back (`KeyboardClient` sends
  alt/boost/forward/modeSwitch/jump/sideInventory/hubMode).
- Hover mode is better (3.56 m/s added), but it is the mode players use least and it still has no
  directional control.
- Vertical is the opposite: normal ascent ramps to 0.6 b/t = **12 m/s**, hover ascent/descent is
  0.2 b/t = **4 m/s**.

So the fix is not just "increase a number": it is full 8-direction input, a sensible acceleration/drag
model, and a speed cap.

---

## 3. Feature: wear with armor (Baubles / TravellersGear)

### 3.1 API facts (1.7.10)

**TravellersGear** (present in the target pack as `TravellersGear-1.7.10-1.16.7.jar`):

- `ITravellersGear.getSlot(ItemStack)` returns `0` cloak, `1` shoulders, `2` vambraces, `3` title, or
  `-1` not TG gear.
- The TG inventory container accepts an item when `ModCompatability.getTravellersGearSlot(stack)` returns
  a valid slot; implementing `ITravellersGear` is enough.
- TG ticks equipped gear from `TickEvent.PlayerTickEvent` (both sides) via `onTravelGearTick`.
- TG renders gear in `RenderPlayerEvent.Specials.Pre` **only if** `item.getArmorModel(player, stack, ...)`
  is non-null; it binds `item.getArmorTexture(stack, player, 0, null)`.
- The cloak slot is the natural "back" slot for a jetpack. It conflicts with TG cloaks (which are speed
  items); shoulders conflicts with pauldrons. Make it configurable.

**Baubles / Baubles-Expanded** (the pack ships `BaublesExpanded-2.2.27-GTNH.jar` in `mods/1.7.10`):

- The actual mod id is `Baubles` (the `mcmod.info` says `Baubles|Expanded`, but the `@Mod` annotation is
  `Baubles`), so `Loader.isModLoaded("Baubles")` and `TargetMods.BAUBLES("Baubles")` are correct. My
  first scan missed it because it only looked at the top-level `mods` folder.
- The legacy API has 4 slots: `AMULET`, `RING` x2, `BELT`; `UNIVERSAL` fits any slot.
- Baubles-Expanded adds `IBaubleExpanded.getBaubleTypes(ItemStack)` returning **type strings**, so an item
  can fit several slot types. The registered types include `ring`, `amulet`, `belt`, `universal`, `head`,
  `body`, `charm`, `cape`, `shield`, `quiver`, `gauntlet`, `earring`, `wings`. The pack currently assigns:
  amulet, 2 rings, belt, 2 quivers, 3 heart canisters, Terminal, wings, focus pouch, 2 charms, **cape**,
  gauntlet, title. There is **no `body` slot by default** (it is registered but not assigned).
- Best target for a jetpack: the **`cape`** slot (a back slot, and the pack has one), with `body`/`wings`
  accepted when present and `belt` as the original-Baubles fallback. A single item can report all of them
  via `getBaubleTypes`, so it fits whichever slot the pack actually has. `belt` alone is the most
  portable but conflicts with belts; `cape` is the least intrusive in this pack.
- `IBaubleExpanded` extends `IBauble`, so implement both: `getBaubleTypes` for expanded slots and
  `getBaubleType` for original Baubles. Two **mutually exclusive** gated mixins are used: the legacy
  `IBauble` one when the expanded API class is absent, the `IBaubleExpanded` one (with all legacy methods)
  when it is present. The legacy fallback returns `BaubleType.BELT`; the 1.7.10 `BaubleType` copies do not
  reliably expose `UNIVERSAL`, and belt is the safe common slot.
- Baubles-Expanded 2.2.27 ticks `onWornTick` from `LivingEvent.LivingUpdateEvent` (vanilla Forge 1.7.10,
  both sides). Only `setInventorySlotContents` triggers a slot sync packet, so **NBT-only changes
  (fuel/energy/mode) are not synced while worn**.
- **There is still no 1.7.10 Baubles render API** (the `baubles/client` package is GUI only), so the
  jetpack needs its own renderer.
- **TravellersGear + Baubles-Expanded in the same pack**: TG 1.16.7 ships a shaded `baubles/api`; the
  classloader picks one copy. TG's copy lacks Baubles-Expanded's `IBauble.onPlayerLoad` default method,
  so do not rely on `onPlayerLoad`; the common methods and `IBaubleExpanded` work with either copy.

**IC2's blockers:**

- `onArmorTick` is only called for the chest slot, and `useJetpack` hard-codes
  `player.inventory.armorInventory[2]`.
- The per-tick inventory sync only covers `inventoryContainer`, not the Baubles/TG inventories.
- IC2's chargepad charges `armorInventory` + `mainInventory` only, so a bauble-worn electric jetpack
  cannot be recharged by a chargepad (fixed in Phase 3 by `MixinTileEntityChargepadBlock`).

### 3.2 Design

1. **One worn-stack resolver** (`JetpackWorn`):
   - chest slot first (stock behaviour; preserves current feel),
   - then a Baubles/Expanded slot matching the configured `baubleSlotTypes` (default `cape,wings,body,belt`),
   - then the TravellersGear cloak/shoulders slot (config `travellersGearSlot`).
   - Return exactly one stack per player per tick; never tick two jetpacks at once.
2. **One tick owner**: a `TickEvent.PlayerTickEvent` handler (FML bus) that resolves the stack and runs
   the flight/fuel logic once. The stock `ItemArmorJetpack.onArmorTick` is cancelled at HEAD by a late
   mixin, so a chest jetpack is not ticked twice.
   - This also fixes the "chest + bauble" double-drain/double-thrust problem for free.
   - `IBauble.onWornTick` and `ITravellersGear.onTravelGearTick` are implemented as no-ops; they exist
     only so the slots accept the item.
3. **Equip the same item in the new slots** with gated mixins:
   - `MixinItemArmorJetpackBauble` (`@Mixin(ItemArmorJetpack.class)` + `implements IBauble`), applied only
     when Baubles is loaded and `jetpack.baubleSlotTypes` is not `OFF`.
   - `MixinItemArmorJetpackBaubleExpanded` (`implements IBaubleExpanded`, adds `getBaubleTypes` and the
     legacy methods), applied only when the Baubles-Expanded API class is present. It is mutually
     exclusive with the legacy mixin, so there are no duplicate `IBauble` methods.
   - `MixinItemArmorJetpackTravellersGear` (`implements ITravellersGear`), applied only when
     TravellersGear is loaded and `jetpack.travellersGearSlot != OFF`.
   - Both Baubles mixins target the gas class; the electric class inherits `instanceof`, so one mixin
     covers both.
   - Add `TargetMods.BAUBLES("Baubles")` / `TargetMods.TRAVELLERS_GEAR("TravellersGear")` and gate with
     `addRequiredMod(...)` so the class never loads a missing interface.
4. **Do not rely on the other mod's tick**: use our `PlayerTickEvent` for both Baubles and TG. This gives
   us consistent ordering and avoids double ticking (Baubles 2.2.27 uses `LivingEvent.LivingUpdateEvent`,
   which is fine but not something we need to depend on).
5. **Rendering**: a `RenderPlayerEvent.Specials.Post` handler (client-only) that resolves the worn stack;
   if it is not in the chest slot, bind a texture and draw a small `ModelBiped`/model on the back. Return
   immediately for the chest slot (vanilla armour rendering already shows it), so there is no double
   render. If TG is installed, TG's renderer skips the item because `getArmorModel` returns null - our
   renderer is the only one. The implemented MVP is the offset torso described below; Phase 3 can add a
   dedicated tank/nozzle model.
   - Zero-art MVP: a `ModelBiped` with only `bipedBody` visible, `offsetZ` +1.5 model units (behind the
     torso) and the existing `ic2:textures/armor/jetpack_1.png`. The chestplate in front stays visible;
     the jetpack reads as a backpack. (The texture is a full torso texture, so its front face will be
     inside the player/chestplate and depth-hidden.)
   - Better: a dedicated `ModelJetpack` (two tanks + nozzles, new 32x32 texture in
     `assets/ic2tweaks/textures/`) so it looks intentional and does not depend on IC2's texture layout.
   - Optional: add `getArmorModel`/`getArmorTexture` to the item so TG (and TG armour stands) render it
     for free. This needs a client-only method in a common class; prefer the separate renderer to avoid
     server class-loading risk and double rendering.
6. **Charging**: for a bauble/TG electric jetpack, optionally mixin the four
   `TileEntityChargepad*.getItems` methods (or redirect `player.inventory.armorInventory`) to also charge
   the resolved worn stack. Without it, the player can still recharge it in a BatBox/MFE item slot.
7. **Balance**: freeing the chest slot is a buff (you gain the chestplate's armour). The jetpack's
   `ISpecialArmor` absorption is 0, but because it is an `ItemArmor` with the diamond chestplate material,
   vanilla's `InventoryPlayer.getTotalArmorValue()` displays **8 armour points** on the HUD even though it
   reduces no damage. `ForgeHooks.getTotalArmorValue` (which honours `ISpecialArmor.getArmorDisplay`)
   reports 0. Strip that misleading display with `stripJetpackArmorDisplay=true` (Phase 1); the same fix
   covers the hazmat suit and CF pack. If you also want to strip actual armour from addon jetpacks
   (quantum/GraviSuite), that is a separate target. Options: keep the buff from the chestplate you can now
   wear, charge a small extra fuel rate while in a slot (config `slotFuelMultiplier`), or make the
   bauble/TG slot opt-in.

### 3.3 Sketch

```java
// tick owner (both sides)
@SubscribeEvent
public void onPlayerTick(TickEvent.PlayerTickEvent event) {
    if (event.phase != TickEvent.Phase.END) return;
    ItemStack stack = JetpackWorn.get(event.player); // chest -> bauble -> TG
    if (stack == null || !JetpackConfig.enabled) return;
    JetpackHandler.tick(event.player, stack);
}

// stock tick is now dead when our feature is on
@Mixin(value = ItemArmorJetpack.class, remap = false)
public abstract class MixinItemArmorJetpack {
    @Inject(method = "onArmorTick", at = @At("HEAD"), cancellable = true)
    private void ic2tweaks$replaceTick(World world, EntityPlayer player, ItemStack stack, CallbackInfo ci) {
        if (JetpackConfig.enabled && JetpackWorn.isStockJetpack(stack)) ci.cancel();
    }
}

// slot eligibility only; ticking is ours
@Mixin(value = ItemArmorJetpack.class, remap = false)
public abstract class MixinItemArmorJetpackBauble implements IBauble {
    // For original Baubles: the best legacy slot. The expanded mixin adds getBaubleTypes().
    @Override public BaubleType getBaubleType(ItemStack stack) { return BaubleType.BELT; }
    @Override public void onWornTick(ItemStack stack, EntityLivingBase player) {}
    @Override public void onEquipped(ItemStack stack, EntityLivingBase player) {}
    @Override public void onUnequipped(ItemStack stack, EntityLivingBase player) {}
    @Override public boolean canEquip(ItemStack stack, EntityLivingBase player) { return true; }
    @Override public boolean canUnequip(ItemStack stack, EntityLivingBase player) { return true; }
}
```

Do **not** gate the `IBauble`/`ITravellersGear` mixin only by a runtime field if the interface class may
be absent: use `MixinBuilder.addRequiredMod(...)` so the mixin is not applied at all without the mod.

### 3.4 Caveats

- **Double jetpacks**: a bauble jetpack + quantum chest with its jetpack enabled (or a GraviSuite chest)
  gives two thrust sources. Default policy: one IC2 jetpack per player (chest wins), and optionally
  suppress the quantum/GraviSuite jetpack when a bauble IC2 jetpack is worn.
- **No NBT sync for bauble/TG stacks**: both sides simulate the fuel/charge, so the client's copy stays
  in step; the server's copy is authoritative for persistence/drops. Add a throttled sync packet only if
  a GUI needs the server value.
- **Baubles + TravellersGear classloader conflict**: TG ships a shaded `baubles/api` and Baubles-Expanded
  ships the same FQNs. Test both; gate each integration by its mod id and do not rely on the extra
  `onPlayerLoad` default method.
- **Slot conflicts**: Baubles-Expanded's `cape` slot is the best fit in this pack (no `body` slot is
  assigned by default), but it conflicts with cape items; `belt` is the portable fallback. Make the type
  list configurable and document the trade-off.

---

## 4. Feature: real horizontal flight

### 4.1 Model

Client-side, only while the jetpack is engaged (`jump` held or hover mode), and only when airborne (or
taking off):

- Read `player.movementInput.moveForward` / `moveStrafe` (current tick, supports custom controls) rather
  than IC2's forward-only keyboard state.
- Build a world-space direction from yaw and blend the horizontal velocity toward a target velocity:
  - `maxHorizontalSpeed` (default 0.32 b/t = 6.4 m/s; sprint is 5.61 m/s, creative flight 10.9 m/s),
  - `horizontalAccel` (default 0.05 b/t per tick -> ~7 ticks to reach target),
  - `horizontalBrake` (default 0.08 b/t per tick -> ~4 ticks to stop; do not rely on 0.91 drag),
  - `strafeFactor` 0.8 / `backFactor` 0.6 (so sideways/back are useful but not equal to forward),
  - optional `boostMultiplier` (1.6) on the IC2 boost key with extra fuel.
- Do **not** overwrite knockback/explosion velocity: cap the change per tick to `accel` and blend toward
  the target instead of assigning `motionX/Z`.
- Apply on the client only (`world.isRemote`). The server only accounts fuel/fall distance. 1.7.10
  player movement is client-authoritative, so server-side horizontal motion is wasted and can confuse
  the server player state.
- Keep the vertical defaults initially (ascent 0.6, hover +/-0.2, hover idle 0) so existing players do
  not get a surprise; expose them as config. Optionally smooth the ascent later.

```java
static void applyHorizontal(EntityPlayer player, float forward, float strafe,
                            float maxSpeed, float accel, float brake) {
    float len = MathHelper.sqrt_float(forward * forward + strafe * strafe);
    if (len < 1.0E-4F) { // brake
        double speed = Math.sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ);
        if (speed > 1.0E-4D) {
            double k = Math.min(speed, brake) / speed;
            player.motionX *= 1.0D - k;
            player.motionZ *= 1.0D - k;
        }
        return;
    }
    float yaw = player.rotationYaw * (float) Math.PI / 180.0F;
    double fX = -Math.sin(yaw), fZ = Math.cos(yaw);
    double rX = Math.cos(yaw), rZ = Math.sin(yaw);
    double tX = (fX * forward + rX * strafe) / len * maxSpeed;
    double tZ = (fZ * forward + rZ * strafe) / len * maxSpeed;
    double dX = tX - player.motionX, dZ = tZ - player.motionZ;
    double d = Math.sqrt(dX * dX + dZ * dZ);
    if (d > accel) { dX *= accel / d; dZ *= accel / d; }
    player.motionX += dX;
    player.motionZ += dZ;
}
```

Note: 1.7.10 applies horizontal drag `0.91` after movement; the target is therefore approximate by ~9%.
Tune the default against the vanilla air control (which still adds its own 0.02 impulse when the player
holds W) and decide whether to suppress vanilla air control while the jetpack is active (setting motion
directly does that implicitly, because we blend toward the target).

### 4.2 Vertical

- Keep the existing `prevmotion` carry-over for a smooth hover.
- Add config: `ascentSpeed` (0.6), `hoverRise` (0.2), `hoverSink` (0.2), `hoverIdle` (0.0).
- Optional: allow sneak to descend in normal (non-hover) mode by clamping `motionY` to `-hoverSink`.
- Optional: pitch-based flight is a bigger feel change; skip for v1.

### 4.3 Engagement / edge cases

- `engaged = jump || hoverMode`.
- Do not thrust while `player.isRiding()`, `player.isDead`, or (config) `player.capabilities.isFlying`.
- Do not thrust in water/lava unless enabled (`allowInWater=false`, `allowInLava=false`).
- In hover mode on the ground, do not thrust/sound unless jump is held (take-off); this also fixes the
  current "roaring jetpack while standing" bug.
- Creative: optionally skip fuel drain (`creativeNoDrain=true`) or skip the jetpack entirely.

### 4.4 Quick win vs full controller

A one-line-ish improvement is to keep `moveFlying` but pass strafe/forward and a larger friction, e.g.
`player.moveFlying(strafe * 0.8F, forward, 0.027F)` gives ~6 m/s terminal. It is still additive with
vanilla air control (~10 m/s combined) and has no speed cap/braking. The velocity-target controller is
more predictable and testable; use the quick win only if you want a minimal first patch.

---

## 5. Bug list

Severity: **H**igh = visible/gameplay, **M**edium = annoyance/desync, **L**ow = polish. Phase 1 fixes:
B1-B4, B6-B9, B11, B16, B22; the entries below remain as the technical record.

| # | Sev | Bug | Evidence / fix |
| --- | --- | --- | --- |
| B1 | H | Jetpack loop sound plays while standing still with hover mode on | `useJetpack` returns true whenever charge > 0, regardless of `onGround`; `onArmorTick` maps that to `jetpackUsed` and plays the loop. Fix: report "used" only when thrust is actually applied (and airborne unless jump is held). |
| B2 | H | Fuel-boundary free thrust | `useJetpack` ignores the return of `use()`; `drainfromJetpack` returns false if the tank has 1 mB and 2 are requested, so the last tick thrusts for free. Electric `discharge` likewise returns less than requested. Fix: check `getCharge(stack) >= cost` first and consume before/only when applying. |
| B3 | H | Static `audioSource` / `lastJetpackUsed` leak | Both are static on the item class and never reset on unequip/disconnect/world change. Leaving the world while flying can leave the loop playing or make it silent on rejoin (`lastJetpackUsed` still true). Fix: per-player audio state in the tick handler; stop when not used; reset on logout/unload. |
| B4 | H | Any non-gas jetpack is treated as electric | `boolean electric = jetpack.getItem() != Ic2Items.jetpack.getItem();` - an addon subclass/fluid jetpack is misclassified; `Ic2Items.jetpack` can also be null early. Fix: `instanceof IElectricItem` (or the electric class) and a type enum. |
| B5 | M | `getSubItems` hard-codes `Ic2Items.jetpack` | Fixed by the `MixinItemArmorJetpack` creative-item injection, which uses the actual item instance (`new ItemStack(this, 1)`). |
| B6 | H | Hover toggle is not reliable on the client | `hoverMode` / `toggleTimer` are NBT and only written when `IC2.platform.isSimulating()` (server). The client reads the NBT each tick, flips a local boolean but never writes it, so while the combo is held it flips every tick (the client timer never decrements) and after release it reverts to the stale NBT value. With Hodgepodge's `fixIc2ArmorLag=true` the only NBT sync is gone as well, so the client and server can disagree about hover mode (code-level finding; worth confirming in-game). Fix: per-side local mode + cooldown state, seeded from NBT on equip/login; write NBT on both sides (the client copy is local anyway). |
| B7 | H/M | `detectAndSendChanges()` every tick while used | 46-slot deep NBT compare + slot packets per tick (both stock classes). Hodgepodge already no-ops this (`fixIc2ArmorLag`, default true in the pack). Fix: do not call it in the replacement; the client simulates fuel/charge, so a periodic sync is only needed for GUIs. |
| B8 | H | Thrust while riding / creative-flying / in water/lava | `onArmorTick` runs for the armour slot regardless of these states; hover mode drains fuel on a horse/boat and can fight creative flight. Fix: explicit guards. |
| B9 | M | Altitude ceiling is opaque/hard-coded | Electric = `getHeight()/1.28` (200), gas = 256; `getHeight()` returns 256 even in the Nether. Players cannot tell why lift fades. Fix: config + HUD/altitude feedback. |
| B10 | M | No i18n / no controls tooltip | `"Hover Mode enabled."`, `"Quantum Hover Mode disabled."`, raw `FluidRegistry.getFluidName` in the gas tooltip; electric `addInformation` is empty. Fix: keys + `ItemTooltipEvent` + localized fluid name + controls. |
| B11 | L | Gas `use()` parses/writes NBT twice | `drainfromJetpack` calls `drain(..., false)` and `drain(..., true)`, and `drain` calls `Updatedamage` each time. Fix: one `drain` + one damage update (or let the caller update once). |
| B12 | L | Electric jetpack still implements `IFluidContainerItem` | Inherited from `ItemArmorFluidTank` with no-op fill/drain; shows up as an empty fluid container in fluid UIs. Harmless but confusing; clean up only if the class hierarchy is refactored. |
| B13 | M | Quantum suit has the same bugs | Ground drain (8 EU/t while standing with jetpack mode + jump/fall), static audio, per-tick sync, hover sink (Hodgepodge fixes to 0). Consider routing it through the same physics helper later. |
| B14 | L | Fuel is spent at the ceiling with zero lift | Moot by default now that `altitudeFadeBlocks=0` removes the arbitrary ceiling; if a pack restores the fade, the last fade band still costs full fuel. |
| B15 | L | Hover carry-over feels floaty | Fixed by `hoverMomentum` (default 0 stops upward carry-over immediately; 1 preserves the old behaviour). |
| B16 | H | No strafe/back thrust at all | IC2's `KeyboardClient` only tracks forward; `useJetpack` only calls `moveFlying` on the forward key. Fix: read `movementInput` on the client. (This is the root of the user's complaint.) |
| B17 | M | Inconsistent horizontal tuning vs the quantum suit | Gas/electric normal: 0.53/0.75 m/s; quantum normal: 4.44 m/s (its input saturates at the 0.02 friction); gas hover: 3.56 m/s vs quantum hover: 1.78 m/s. The 0.15 factor is likely an oversight. |
| B18 | M | Electric jetpack is strictly worse in endurance and lift | Balanced defaults: power/hover 1.0 (gas parity), 4/2 EU/t (half gas endurance but rechargeable), and no arbitrary altitude ceiling. Config still exposes the old stock values. |
| B19 | L | `distanceWalkedModified = 0` kills footsteps (intended) | It also suppresses movement-stat/exhaustion effects while flying. Probably fine; just be aware if a food mod relies on it. |
| B20 | L | NBT keys are not namespaced | `"hoverMode"`, `"toggleTimer"` can collide with other mods on the same stack (low risk; keep IC2's keys for compatibility and namespace new state). |
| B21 | M | Flight input while a GUI is open is inconsistent | `pauseInGui` stops all thrust/hover client-side when a screen is open; the default keeps the existing behaviour (jump stops, hover continues). |
| B22 | H | Jetpack shows 8 HUD armour points despite 0 absorption | `ItemArmorUtility` passes `ArmorMaterial.DIAMOND` to `ItemArmor`, so `damageReduceAmount` is 8; the vanilla HUD's `InventoryPlayer.getTotalArmorValue()` sums it, while `ISpecialArmor.getProperties` reduces 0. `ForgeHooks.getTotalArmorValue` honours `getArmorDisplay` (0) but the HUD does not use it. Fix: redirect the HUD/inventory armour total to the Forge hook when `stripJetpackArmorDisplay=true`; this also fixes the hazmat suit and CF pack. |

---

## 6. Optimizations

| # | Optimization | Notes |
| --- | --- | --- |
| O1 | Remove the per-tick `detectAndSendChanges()` | Hodgepodge precedent. Let the client simulate; if a GUI needs the authoritative value, sync every N ticks (config `syncIntervalTicks`, default 0/100). |
| O2 | One fluid drain per tick | Replace the simulate+drain pair with a single `drain` + one `Updatedamage`; cache `stack.stackTagCompound` when non-null instead of `getOrCreateNbtData` every call. |
| O3 | Batch fuel drain | Gas: drain 4 mB every 2 ticks instead of 2 every tick; electric: 16 EU every 2 ticks. Fewer NBT writes/manager lookups; keep a local remainder so the displayed value stays smooth. |
| O4 | Early-out guards | `stack == null`, `isDead`, `riding`, creative flying, water/lava, config off. Avoids all work in the common "wearing a jetpack but not flying" case. |
| O5 | Client-only motion | Do not call `moveFlying`/set `motionY` on the server; only fall-distance/air-time resets need the server. |
| O6 | One tick owner | Resolve the worn stack once per player per tick; no double scans, no double drain. |
| O7 | Unify the three physics copies | `ItemArmorJetpack`, `ItemArmorQuantumSuit`, and (optionally) GraviSuite share the same core; a pure `JetpackPhysics` class removes duplication and makes tests possible. |
| O8 | Audio state only on change | Play/stop on state transitions; do not recreate/update the source when nothing changed. |
| O9 | Cache the world height / config lookups | Store `worldHeight` per tick, not per branch; config fields are already static. |
| O10 | No per-tick packet for the bauble/TG slot | Both sides simulate; only sync on equip/unequip or a low-frequency config sync. |

---

## 7. Additional features worth considering

| # | Feature | Why |
| --- | --- | --- |
| F1 | TravellersGear cloak slot + Baubles-Expanded cape/wings/body/belt slot, with rendering | The two requested integrations; frees the chest slot. |
| F2 | Full 8-direction horizontal flight + boost key (afterburner) | The other requested change; IC2's boost key is already synced and unused by the jetpack. GraviSuite's boost is a good precedent. |
| F3 | Hover improvements: true altitude hold, configurable rise/sink, optional sneak-descend in normal mode | Done through `hoverIdle` (0 holds altitude), configurable rise/sink and `hoverMomentum`. |
| F4 | HUD (fuel %, mode, speed, altitude) + control tooltips + localized messages | The durability bar is coarse (27 states) and the controls are undiscoverable. |
| F5 | Exhaust particles (smoke/flame) and a start/stop sound | Feedback; the current loop has no visual. |
| F6 | Fall protection / landing assist | Done: `landingAssist` brakes a fall near the ground on a charged jetpack (`landingAssistDistance`/`landingAssistSpeed`). |
| F7 | Config for fuel/charge, speed, altitude, slots, guards | The core of a "tweaks" mod; lets packs balance the gas/electric asymmetry. |
| F8 | Chargepad support for a bauble/TG electric jetpack | Otherwise the new slot cannot be recharged by the usual block. |
| F9 | Creative no-drain | QoL; the stock jetpack burns fuel in creative. |
| F10 | Quantum suit unification | Apply the same controller to the quantum's `useJetpack` (config, default off) so all IC2 jetpacks feel the same. |
| F11 | Optional "armour insert" (NBT-stored chestplate) | A fallback for packs without Baubles/TG (e.g. your 20/26-mod packs); IC2 1.12.2's jetpack attachment is the same idea. Bigger design, only if needed. |
| F12 | API for addon jetpacks | Optional; a small interface so addons can opt into the new controller. |
| F13 | Easier gas refuelling | Done: right-click a biogas cell/container to fill a worn gas jetpack (`easyGasRefuel`). |
| F14 | Dedicated hover key binding | Done: default H, configurable in Controls, with the old jump + mode-switch combo retained (`dedicatedHoverKey`). |

---

## 8. Implementation plan

### Phase 0 - Config + pure physics (implemented)

Status: done. No mixins or behaviour changes are applied yet; the config values are read, validated and
visible in-game.

- `JetpackConfig` (`ic2tweaks.cfg`, category `jetpack`), read in `LateMixinsLoader` before
  `IMixins.getLateMixins` and again in `preInit`, like `WrenchConfig`/`EnetOverloadConfig`.
- `JetpackPhysics` (pure functions, no Minecraft/IC2 types) with stock-IC2 parity helpers and the Phase 1
  flight model.
- `JetpackPhysicsTest` + `JetpackConfigTest` (JUnit 5).
- `/ic2tweaks jetpack` diagnostics and a `CanaryHarness` physics self-check.
- Master switch `enableJetpackTweaks` (default true); the mixin gate follows in Phase 1.

Implemented config keys:

```
enableJetpackTweaks, tweakAddonJetpacks, tweakQuantumJetpack
horizontalSpeed, horizontalAccel, horizontalBrake,
strafeFactor, backwardFactor, boostMultiplier, boostFuelMultiplier
ascentSpeed, hoverRise, hoverSink, hoverIdle, electricHoverFactor, electricPowerFactor
gasFuelPerTick, gasHoverFuelPerTick, electricEuPerTick, electricHoverEuPerTick,
gasLowFuelThreshold, electricLowFuelThreshold
altitudeFadeBlocks, electricAltitudeFactor
allowInWater, allowInLava, creativeNoDrain, fallProtection,
stripJetpackArmorDisplay
hudEnabled, hudCorner (TOP_LEFT/TOP_RIGHT/BOTTOM_LEFT/BOTTOM_RIGHT),
hudShowMode, hudShowSpeed, hudShowAltitude, hudLowFuelPercent
tooltipsEnabled, particlesEnabled, particleIntervalTicks, particleYOffset,
landingAssist, landingAssistDistance, landingAssistSpeed, landingAssistDrain, hoverMomentum,
pauseInGui, dedicatedHoverKey, dedicatedPowerKey, easyGasRefuel
chargepadChargesBaubles
tweakQuantumJetpack, quantumEuPerTick, quantumHoverEuPerTick
baubleSlotTypes (cape,wings,body,belt / OFF), travellersGearSlot (OFF/CLOAK/SHOULDERS),
slotFuelMultiplier, renderWornJetpack, syncIntervalTicks
```

Notes on the Phase 0 defaults:

- The stock costs are preserved: gas 2 mB/t (hover 1), electric 8 EU/t (hover 7), low-fuel thresholds
  0.20/0.05, altitude fade 25 blocks, electric ceiling factor 1.28.
- `electricHoverFactor=0.5` and `electricPowerFactor=0.7` keep IC2's stock electric hover speed (0.1
  instead of 0.2) and normal lift/horizontal multiplier (0.7). Set `electricPowerFactor=1.0` for gas
  parity.
- `baubleSlotTypes` defaults to `cape,wings,body,belt`: the pack has `cape` and `wings` slots, `body` is
  accepted if a pack assigns it, and `belt` keeps original-Baubles compatibility. The old
  `cape,body,belt` default is migrated to include wings.
- `syncIntervalTicks=0` means no extra sync; both sides simulate, matching IC2/Hodgepodge.
- Old stock electric values (8/7/0.7/0.5) and the old `altitudeFadeBlocks=25` default are migrated to the
  balanced/no-ceiling values; partially customised configs are untouched.
- `stripJetpackArmorDisplay=true` is the fix for B22 (the misleading 8 HUD armour points). It is an
  early mixin on `EntityPlayer` (see Phase 1), because vanilla `EntityPlayer` is loaded before the late
  mixin phase.

### Phase 1 - Replace the stock tick and fix bugs (implemented)

Status: done. `enableJetpackTweaks=true` now replaces the stock tick for the two IC2 items.

- `MixinItemArmorJetpack` (`onArmorTick` HEAD cancel; only exact stock classes unless
  `tweakAddonJetpacks`). The runtime config check makes a no-restart disable fall back to stock.
- `JetpackHandler` on FML `PlayerTickEvent.START` (stock armour-tick timing, before movement): resolves the chest stack, guards dead/riding/creative
  flight/water/lava, owns the hover mode with an edge-triggered toggle and per-side cooldown, consumes
  fuel before thrusting, applies the vertical + horizontal physics on the client, resets fall
  distance/air time, owns per-player audio, and optionally syncs every `syncIntervalTicks`.
- `JetpackRules` (pure guards) and `JetpackState` (per-side audio/motion/hover state).
- `JetpackWorn` resolver (chest only in Phase 1; the Phase 2 slots plug in here).
- The gas `use()` double-drain is replaced with a single `drain` in the mixin (B11).
- B22: `MixinEntityPlayerArmorValue` is an **early** mixin because `EntityPlayer` is loaded before the
  late phase. It required the repo's first coremod/early-mixin loader (`EarlyMixinsLoader` +
  `mixins.ic2tweaks.early.json` + `coreModClass`). It routes `EntityPlayer.getTotalArmorValue()` through
  `ForgeHooks.getTotalArmorValue` when `stripJetpackArmorDisplay` is on (runtime check).
- The static `audioSource`/`lastJetpackUsed` fields are no longer used for the stock items (the mixin
  cancels before the stock body); the handler uses per-player state and cleans up on logout/dimension/
  world unload/disconnect.
- The canary now ticks a real `EntityPlayer` with a filled gas jetpack in hover mode; it verifies the
  handler drains fuel and resets fall distance, and checks the two mixin markers.

Phase 1 fixes: B1, B2, B3, B4, B6, B7, B8, B9 (configurable), B11, B16, B22. Deferred: B5 (addon creative
items only) is resolved by the Phase 3 creative-item mixin; B12-B15, B19-B21 are resolved or documented by
the Phase 3 flight/UX pass. B10 is resolved by the tooltip pass.

### Phase 2 - Slots + rendering (implemented)

Status: done. The flight tick is unchanged; the resolver now also finds a jetpack in the optional slot
mods.

- `TargetMods`: `BAUBLES("Baubles")`, `TRAVELLERS_GEAR("TravellersGear")`.
- Baubles mixins:
  - `MixinItemArmorJetpackBauble` (legacy `IBauble`, `BaubleType.BELT`) applies when the expanded API is
    absent.
  - `MixinItemArmorJetpackBaubleExpanded` (`IBaubleExpanded.getBaubleTypes` = `baubleSlotTypes`, default
    `cape,wings,body,belt`) applies when `baubles.api.expanded.IBaubleExpanded` is present. The two are
    mutually exclusive.
- `MixinItemArmorJetpackTravellersGear` (`ITravellersGear.getSlot` = `travellersGearSlot.index`) applies
  only when TravellersGear is loaded and the slot is not `OFF`.
- `JetpackWorn` now has common `SlotProvider` hooks; `IC2Tweaks.preInit` registers `BaublesCompat` /
  `TravellersGearCompat` only when the corresponding mod is loaded, so the common resolver never
  references the optional APIs.
- `BaublesCompat` scans the Baubles/Expanded inventory; `TravellersGearCompat` reads the configured
  extended-inventory slot. Chest slot still wins; exactly one stack is ticked.
- `JetpackRenderer` + `ModelJetpack` on `RenderPlayerEvent.Specials.Post`: a torso model offset behind the
  player, using IC2's `jetpack_1.png`; the chest slot is skipped (vanilla renders it). The renderer is
  registered by the client proxy (`ClientProxy`), so no client class enters the server path.
- Dependencies: `Baubles-Expanded:2.2.27-GTNH:dev` for compile + dev runtime; TravellersGear's CurseForge
  jar is `compileOnly` because it is reobfuscated (production packs ship the real jar).
- The canary now checks the Baubles mixin marker and drains a jetpack placed in a Baubles slot
  (`drained=10.0`); TravellersGear checks are conditional on the mod being loaded.
- Remaining Phase 2 limitations: the model is the zero-art offset-torso version (a dedicated tank/nozzle
  model is still optional); TG armour-stand rendering is not covered. The quantum/GraviSuite chest plus
  bauble double-thrust case is resolved by the Phase 3 chest-wins policy.

### Phase 3 - Polish (HUD/tooltips/particles/chargepad/quantum implemented)

- [x] HUD (`RenderGameOverlayEvent.Post`): fuel %, hover/normal mode, horizontal speed and altitude,
  low-fuel/empty warning, configurable corner and line toggles. The quantum chest HUD is hidden while
  its own jetpack mode is off.
- [x] Tooltips (`ItemTooltipEvent`) and lang keys: localized fuel/charge values, mode, worn slot,
  tier (advanced tooltips) and controls. IC2's raw `<fluid, amount mB>` gas line is replaced.
- [x] Exhaust particles (`JetpackParticles`): client-only smoke for gas / flame for electric, with a
  configurable interval and extra puffs on boost.
- [x] Optional quantum-chest controller mixin (`tweakQuantumJetpack`, default on): cancels the stock
  chest tick/`useJetpack` and routes movement, hover/enable toggles, drain, HUD/tooltip and particles
  through the common handler. Other quantum pieces keep their stock ticks.
- [x] Chargepad support for an electric jetpack in a Baubles/TG slot (`chargepadChargesBaubles`).
- [x] Double-jetpack policy: chest (stock/quantum/GraviSuite) wins over a bauble/TG jetpack.
- [x] Soft-landing assist (`landingAssist`, distance/speed/drain), configurable hover carry-over
  (`hoverMomentum`), GUI pause (`pauseInGui`), dedicated hover key (`dedicatedHoverKey`), per-stack power
  switch (`dedicatedPowerKey`, namespaced `ic2tweaks:disabled`), and easy gas refuelling from a biogas
  container (`easyGasRefuel`).
- [x] Removed the arbitrary gas/electric Y ceiling: `altitudeFadeBlocks=0` disables the fade and the
  electric 200-block ceiling; set 25 to restore stock behaviour.
- [x] Balanced electric defaults: gas-parity lift/hover speed and 4/2 EU/t (stock 8/7), i.e. half the gas
  endurance but rechargeable.
- [x] B5 addon creative items; B14 is moot with no ceiling; B15 is controlled by `hoverMomentum`; B19/B20
  remain documented compatibility notes (stock IC2 NBT keys are intentionally retained).

Implementation: `JetpackHudOverlay` and `JetpackTooltipHandler` are client-only and registered by the
`@SidedProxy` client proxy (`ClientProxy`), which FML loads only on the physical client; `JetpackReadout`
holds the pure percentage/speed/altitude maths and
is unit-tested; `JetpackWorn.slotOf` supplies the tooltip's worn-slot line. The HUD reads the worn stack
through `JetpackWorn`, so it works for the chest, Baubles-Expanded and TravellersGear slots. The
`hudEnabled`/`hudCorner`/`hudShowSpeed`/`hudShowAltitude`/`hudLowFuelPercent`, `tooltipsEnabled` and
`particlesEnabled`/`particleIntervalTicks`/`particleYOffset` options are in the `jetpack` config category.
The chargepad mixin charges the bauble/TG electric stack at the pad's tier limit (32/128/512/2048 EU).
The quantum integration uses `quantumEuPerTick`/`quantumHoverEuPerTick` (8/10 EU/t) and preserves the
quantum chest's separate `jetpack` enable toggle (Boost + Mode Switch); `hoverMode` remains Jump + Mode.

### Phase 4 - Docs and release validation

- [x] README features/config table and this analysis doc.
- [ ] Real-pack client matrix, reobfuscated/CurseForge jar, mixin export/refmap check and release notes.

### File sketch

```
com/mrfuzzihead/ic2tweaks/jetpack/
  JetpackConfig.java            (done, Phase 0)
  JetpackPhysics.java           (done, Phase 0)
  JetpackRules.java             (done, Phase 1)
  JetpackHandler.java           (done, Phase 1: server fuel/fall; client motion/audio)
  JetpackState.java             (done, Phase 1)
  JetpackWorn.java              (done, Phase 1/2; slotOf for the tooltip)
  JetpackItem.java              (done, Phase 3: stock/electric/quantum access)
  JetpackParticles.java         (done, Phase 3)
  JetpackReadout.java           (done, Phase 3)
  JetpackGuiState.java          (done, Phase 3: GUI pause state)
  JetpackNetwork.java           (done, Phase 3: hover key packet channel)
  ToggleHoverMessage.java       (done, Phase 3: client -> server hover toggle)
  JetpackRefuelHandler.java     (done, Phase 3: biogas container refuel)
  client/JetpackRenderer.java   (done, Phase 2)
  client/ModelJetpack.java      (done, Phase 2)
  client/JetpackHudOverlay.java (done, Phase 3)
  client/JetpackTooltipHandler.java (done, Phase 3)
  client/JetpackClientTick.java (done, Phase 3: GUI state)
  client/JetpackKeyHandler.java (done, Phase 3: dedicated hover key)
  mixins/early/minecraft/MixinEntityPlayerArmorValue.java   (done, Phase 1)
  mixins/EarlyMixinsLoader.java                             (done, Phase 1)
  mixins/late/ic2/MixinItemArmorJetpack.java                (done, Phase 1)
  mixins/late/ic2/MixinItemArmorJetpackBauble.java          (done, Phase 2)
  mixins/late/ic2/MixinItemArmorJetpackBaubleExpanded.java  (done, Phase 2)
  mixins/late/ic2/MixinItemArmorJetpackTravellersGear.java  (done, Phase 2)
  jetpack/compat/BaublesCompat.java                         (done, Phase 2)
  jetpack/compat/TravellersGearCompat.java                  (done, Phase 2)
  mixins/late/ic2/MixinTileEntityChargepad*.java     (optional)
  mixins/late/ic2/MixinItemArmorQuantumSuit.java     (optional)
src/main/resources/mixins.ic2tweaks.early.json         (done, Phase 1)
src/test/java/com/mrfuzzihead/ic2tweaks/jetpack/
  JetpackPhysicsTest.java       (done, Phase 0)
  JetpackConfigTest.java        (done, Phase 0)
  JetpackRulesTest.java         (done, Phase 1/3: landing rules)
  JetpackReadoutTest.java       (done, Phase 3)
  JetpackParticlesTest.java     (done, Phase 3)
```

### Server/client split (important)

- Physics motion: **client only** (`world.isRemote`); 1.7.10 player movement is client-authoritative.
- Fuel/charge, fall-distance and air-time: both sides (the client simulates for prediction/display, the
  server copy is authoritative for saving/drops).
- Rendering, HUD, particles, audio: client only; never reference client classes from the common handler
  (instantiate them lazily when `FMLCommonHandler.instance().getSide().isClient()`).
- The mod currently declares `acceptableRemoteVersions = "*"`; a vanilla client on a server with
  IC2Tweaks gets stock IC2 jetpack physics but the server's fuel rules. Keep that in mind for balance
  and document it.
- The late mixins are applied on both sides; the `IBauble`/`IBaubleExpanded`/`ITravellersGear` mixins are
  only applied when the corresponding mod is present and the config is on (restart required).
- The optional slot APIs stay out of the common resolver: `JetpackWorn` uses the `SlotProvider` hooks and
  `IC2Tweaks.preInit` only loads `BaublesCompat`/`TravellersGearCompat` when the mod is present. The
  renderer is registered by the client proxy on the client only.
- Vanilla classes such as `EntityPlayer` need an **early** mixin: the mod now has the
  `EarlyMixinsLoader` coremod and `mixins.ic2tweaks.early.json`. The early mixin is always applied and
  its runtime config check makes it inert when disabled.

---

## 9. Test plan

**Unit (JUnit 5, Phase 0 + Phase 1):**

- `JetpackPhysicsTest`: stock horizontal impulses/terminal speeds (gas/electric, normal/hover, the 0.02
  saturation), stock vertical ramp/hover jump/sneak sequences with the MC tick order (armour tick ->
  `moveEntity` -> gravity `-0.08` then `*0.98`), low-fuel fade, altitude fade, configured horizontal
  target/accel/brake/yaw/diagonal/strafe/back/boost, configured vertical (including low-power hover
  rise), fuel cost and endurance, and degenerate inputs.
- `JetpackConfigTest`: defaults, file round-trip, slot list trimming/lower-casing/deduplication/OFF,
  `TravellersGearSlot` parsing and indices, and Forge's min/max clamping.
- `JetpackRulesTest`: hover-on-the-ground/no-thrust, take-off/jump, riding/dead/creative-flight/water/
  lava guards, and the fuel boundary.
- `JetpackReadoutTest`: HUD/tooltip percentage clamping/rounding, thousands grouping, horizontal speed,
  altitude flooring and the low-fuel threshold (including 0 = disabled).
- `JetpackPhysicsTest`: zero-fade/no-ceiling and hover-momentum sequences.
- `JetpackRulesTest`: landing-assist eligibility and ground-range checks.
- Run with `./gradlew test spotlessCheck checkstyleMain checkstyleTest`.

**In-game harness:**

- `/ic2tweaks jetpack` prints the loaded config, the tick/HUD mixin markers and the stock/configured speeds.
- `-Dic2tweaks.canary=true` runs the existing canary and now also:
  - checks the stock gas/electric horizontal speeds, the configured 0.32 b/t target and the 12.5-minute
    gas endurance;
  - checks the guard rules and both mixin markers;
  - when `tweakQuantumJetpack=true`, checks the quantum mixin, the chest-wins resolver and a real quantum
    hover drain (100 EU over 10 ticks);
  - charges a bauble-slot electric jetpack from a real chargepad tile and checks the gained EU;
  - fills an empty gas jetpack from a real biogas cell (`gained=1000.0`);
  - ticks a real `EntityPlayer` with a filled gas jetpack in hover mode for 10 ticks and verifies the
    handler drained 10 mB and reset fall distance (`drained=10.0`, `fall=0.0` in the log).

**Manual matrix (release):**

- gas / electric / quantum chest x chest / TG cloak / TG shoulders / Baubles-Expanded cape/wings/belt /
  no slot mods; chest-wins with a bauble and with GraviSuite.
- hover on/off, boost, low/empty fuel, exact-1-unit boundary, landing assist, hover momentum, GUI pause and
  the dedicated hover key in single-player and on a dedicated server with latency.
- chargepad charging in Baubles and TravellersGear; biogas refill from a cell and a fluid container.
- no Y ceiling at y>200; opt-in `altitudeFadeBlocks=25` still works.
- with Hodgepodge 2.7.222 (must not conflict with `fixIc2ArmorLag`; quantum hover fix still applies when
  we do not replace the quantum), TravellersGear 1.16.x, Baubles-Expanded 2.2.27, GraviSuite 2.0.3.
- Mixin export (`-Dmixin.debug.export=true`) to confirm the `onArmorTick` cancellations, chargepad/quantum
  injections and the added interfaces.

---

## 10. Decisions / open questions

1. **Replace vs add on top** of IC2's physics: **resolved in Phase 1** - the stock tick is replaced,
   gated by `enableJetpackTweaks`; a runtime disable falls back to the stock body.
2. **Slot priority** when several jetpack sources are worn: **resolved** - chest wins, then Baubles, then
   TravellersGear; exactly one stack is ticked.
3. **Baubles slot type list**: **resolved** - default `cape,wings,body,belt`; legacy original Baubles falls back
   to the belt slot because the 1.7.10 `BaubleType` copies do not reliably expose `UNIVERSAL`.
4. **TG slot**: **resolved** - default CLOAK (back), configurable to SHOULDERS/OFF.
5. **Balance**: **resolved** - electric defaults now match gas lift/hover and use 4/2 EU/t (half gas
   endurance, rechargeable). Old stock configs are migrated; partial custom values are preserved.
6. **Quantum suit**: **resolved** - implemented as `tweakQuantumJetpack` (default on, restart required),
   with its own 8/10 EU/t values and the stock jetpack-enable toggle preserved.
7. **Sync strategy**: client simulation by default (`syncIntervalTicks=0`); a low-frequency authoritative
   sync is available for GUIs. Confirm in the manual latency test.
