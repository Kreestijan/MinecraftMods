# Changelog

Notable changes to Alchemizer, newest first. This project does not yet follow a numbered release
scheme; entries are grouped by the milestone that produced them.

## Unreleased — placeholder recipes for the machine

The three machine blocks had no crafting recipe, so a still could only be built in creative. They
have one each now, and every one of them is a placeholder: `DESIGN.md` says nothing about what the
blocks should cost, and a recipe is the one thing in this mod a player is guaranteed to read, so
these are deliberately cheap and shaped after the vanilla block each one is nearest to.

### Added

- **Crafting recipes for the Ignition Chamber, the Pressure Chamber and the Alchemical Tank.** A
  furnace walled in stone bricks; a cauldron in glass and iron; a bucket in a glass vessel with an
  iron band. Each unlocks off the vanilla block it is built around, so a player with a furnace, a
  cauldron or a bucket finds it in the recipe book without having to know it exists.
- **The tank's clearing craft now has an id of its own**, `alchemizer:alchemical_tank_clearing`. A
  recipe's id defaults to its result item's id, so a tank built from materials and a tank built from
  a tank both wanted to be `alchemizer:alchemical_tank`, and the generator throws on the second.
  Nothing about either craft changed — only the second one stopped being anonymous.

## Unreleased — the tank keeps its water, and two screens re-laid out

`DESIGN.md` §3.5 has always said a broken tank keeps its water in the item. It did not: the level
lived in the block entity, and breaking the block threw it away. The tank also had no screen, so the
only way to see what one was holding was to place it. Both are now what the design says, along with
the brewing screen's layout, the wording of its faults, and a water readout that says something.

### Added

- **The Alchemical Tank has a screen of its own.** One gauge, filled to the water level, and the
  player's inventory — the tank holds a level rather than items, so there is nothing else to draw.
  Clicking the gauge with a water container on the cursor pours it in, through the same helper the
  brewing screen's gauge already uses.
- **A tank item says how much water it is carrying**, in millibuckets, on hover. An empty tank says
  nothing: an empty tank is what a crafted tank is, and a line reading zero on every tank a player
  has ever made is one more thing to read past. The figure is the client's own reading — the
  capacity is the client's config value, which can differ from the server's — so the level is
  clamped through the same core type the block entity clamps with, and a client whose config is
  smaller than the server's shows a full tank rather than an impossible "5000/4000".
- **Both gauges read in millibuckets on hover**, in the tank's screen and in the brewing screen's.
  The line was added through the game's item tooltip event rather than by an item subclass: the
  method that would override is deprecated in this version, and overriding a deprecated method is
  itself a warning, which this build refuses.
- **Crafting a tank empties it.** One tank, shapeless, in any grid. A craft's result is built from
  the recipe rather than from what went in, so the tank that comes out carries nothing.

### Changed

- **A broken tank keeps its water.** The block builds its own drop rather than letting the loot table
  build it, because the water is the block entity's plain save data and the loot system cannot copy
  that: the one vanilla function that carries a block entity onto its item copies registered data
  components, and there is no component here to name. The tank's table is therefore empty on purpose
  and the drop comes from the block. Placing the item back restores the level — that half is
  vanilla's own block entity tag handling and needed nothing written for it.
- **The brewing screen is re-laid out.** The five ingredient slots are centred on the panel in one
  row; the bottle slot is centred one row above them, and the output slot centred one row below, so
  the machine fills its half in three rows. Under all of them runs the progress bar, thin and the
  full width of the panel, and under the bar is the status line. Holding those rows makes the panel
  taller than vanilla's — 176 by 208 against 166 — so the player's inventory sits under the machine
  rather than beside it.
- **The brew timer is gone.** It was drawn at the very top, above the title. The four hourglass
  buttons already say how long a brew will take and the bar already says how far it has got, so a
  countdown was a third telling of the same thing; the buttons moved up into the space.
- **The fault wording names the limit rather than the count.** A machine with two ignition chambers
  said "2 ignition chambers"; it now says "Maximum 1 ignition chamber allowed." A player who built it
  wrongly wants to be told what is allowed, not how far past it they went.

### Removed

- **The `x/y doses` caption under the tank gauge.** A dose is a coinage of this mod's config that
  appears nowhere in `DESIGN.md`, and it was not clear what it meant. The gauge and its hover
  reading say the same thing in a unit that does not need explaining.

## Unreleased — machine failure rules

The design's addendum fixes what happens to a brew when the machine stops being a machine. The
tier-1 code did the opposite of it in two places, and both are now what the design says.

### Changed

- **A brew is lost, not saved.** It used to be written to the chamber's save data and restored on
  load, so a chunk that unloaded and came back — or a server restart — carried on brewing where it
  left off. It no longer does: the brew lives in memory only, and a chamber that comes back from
  disk is idle with its ingredients and water already spent. The slots still persist, because those
  are the player's; what does not persist is the brew that was made from them.
- **Breaking a part of the still loses the brew.** It used to freeze the timer where it was, so a
  player could knock the tank off, put it back, and carry on. Now the machine breaking is the brew
  breaking, and the ingredients are gone. This is not a rule polled every tick — a chamber cannot
  tell a part that was removed from a part whose chunk has unloaded, and guessing would destroy
  brews that were never broken. The part's block tells the chamber on the way out instead, and the
  chamber re-validates: breaking a tank the still was not drawing on leaves the brew alone.
- **The status line says how the machine is doing.** It was a flat "Not assembled" for every
  unformed machine. A sound still now reads `Tier N - Working perfectly.` in green, and a broken one
  names the fault in red — "No tank attached", "Chamber not above ignition" — so a player who built
  it wrongly is told which block to fix rather than that something is wrong. The line is about the
  multiblock and nothing else: a still that is sound reads that way whether or not the player has
  stocked it, since the missing ingredients, the short tank and the full output are already said by
  the slots that redden and the gauge that drains. The faults are a typed enum in `core` and their
  wording lives in the language file, where all the other player-visible text is.
- The water figure moved off the status line to make room for the longer status text. It sat in a
  caption under its own gauge at this point; the caption has since gone in favour of a hover reading
  — see the entry above.

### Fixed

- **The validator's explanation was thrown away.** It has always worked out why a machine is not a
  still, and the sentences were built in `core` as English and then read by nobody. They are now
  codes the screen translates.

## Unreleased — after the tier-1 baseline

Six things found by playing the baseline, and what was done about them.

### Fixed

- **A water bucket poured into an Alchemical Tank spilled onto the ground beside it.** The tank had
  no interaction handler at all, so the click reached the bucket's own use and placed a water source.
  Right-clicking a tank with a water container now fills the tank, and an empty bucket does nothing
  rather than being refused.

### Added

- **A water container can be poured into the still from the brewing screen.** Clicking the tank
  gauge with a container on the cursor fills the tanks attached to the still, spending the container
  and leaving the empty one on the cursor. A full cluster refuses rather than taking part of it.
  What counts as a water container is the `c:buckets/water` item tag, so a mod's own water bucket
  works without this mod knowing about it; a vanilla water bottle counts too and is worth one dose.
- **A whole stack of bottles may be loaded.** The bottle slot takes a stack and a brew spends the
  bottles it needs rather than requiring exactly one.
- **The finished potion has an output slot of its own**, instead of landing back in the bottle slot.
  Identical potions stack there, and a full output slot refuses a new brew rather than spending its
  ingredients and water on a potion that has nowhere to go.

### Changed

- **Fuel only lights when a brew wants it.** A loaded coal used to burn down to nothing while the
  player was still assembling the machine. Once alight it still burns to exhaustion, whether or not
  the brew is still running.
- **Potions stack, up to a configurable 64**, instead of vanilla's one. This is a rule about the item
  rather than about this mod's machines, so it applies to every potion a player brews, in a chest as
  much as in the still. `potionStackSize` in the config file sets the limit; the value binds as the
  game loads its item components, so changing it takes effect on the next load.

### Corrected

- The notes below claimed the Alchemical Tank "keeps its contents when broken", and a correction
  that stood here said the opposite. The first claim was the right one and the correction was wrong:
  breaking a tank lost its water because nothing had been written to stop it, not because that was
  ever intended. It keeps it now — see the entry above. The parentheticals that pointed down here
  have been removed. (The Pressure Chamber and the Ignition Chamber are a different case, and the
  correction that first stood here was wrong about them too: both hold items, and a broken one drops
  everything that was in it — ingredients, bottles, output, fuel.)

## Unreleased — tier-1 baseline

The first slice of the still, from an empty scaffold to a working brewing machine. Everything below
is on `minecraft 26.1.2` / `neoforge 26.1.2.116` / Java 25.

### Added

**Machine**

- Ignition Chamber: burns one item of fuel at a time, keeps its remaining burn across a reload, and
  drives a lit block state. Fuel is loaded through its own screen rather than by right-clicking.
- Pressure Chamber: five ordered ingredient slots, one bottle slot, and two reserved enhancer slots
  that are present in the menu at fixed indices but neither shown nor placeable. Owns the brew.
- Alchemical Tank: stores water in millibuckets, and is filled by right-clicking it with a water
  container. A broken tank did not keep its water at this point; it does now.
- Multiblock validation: one ignition chamber, one pressure chamber on top of it, and at least one
  attached tank form a tier-1 still. The result is cached and rechecked when a neighbouring block
  changes.

**Brewing**

- Brew recipes are datapack data (`alchemizer:brew_recipe`): an ordered list of one to five
  ingredients plus an hourglass preset, resolving to a potion.
- Potions are datapack data (`alchemizer:potion`): a named list of effects. An empty list is legal
  and means a drinkable potion that does nothing.
- Effect values are datapack data (`alchemizer:effect_value`) keyed by any mob effect id, vanilla or
  not. An effect with no entry falls back to a configured base value rather than failing, so a
  potion built from a newly added effect still brews.
- Adding a potion or an effect value is a JSON file and no Java change.
- Four hourglass presets — 5, 15, 30 and 45 seconds. Brew time is the preset scaled by
  `0.9^n` for each bellows or valve, floored at a configurable one second.
- A brew freezes rather than resets when the fire goes out, and resumes when it is lit again.
- A layout no recipe describes brews Murky Mess, and a failed brew is paid for at the same moment a
  successful one is.
- Brew output is a real `minecraft:potion` carrying the recipe's effects, so it stays compatible
  with vanilla brewing stands, splash and lingering forms, and tipped arrows.

**Screens**

- A fuel screen for the Ignition Chamber: one slot, a flame that empties downwards, and a burn bar.
- A brewing screen for the Pressure Chamber: five ingredient wells, a bottle well, a tank gauge, a
  brew progress bar, four hourglass buttons, a start button, a status line, and a red highlight over
  the slots a refused brew is waiting on. The gauge read in doses and was captioned as such; it reads
  in millibuckets since — see the entry at the top.
- Both screens are views of the machine: every figure they draw arrives from the server over the
  container synchroniser, and a button press sends a button id and nothing else. A start intent is
  re-derived server-side from the machine's own state.

**Foundations**

- Three modules: `core` (pure Java, no Minecraft on the classpath), `mod-common` (the adapter seam,
  with a test that fails the build on a client import), and `platform-26` (the mod).
- A quality gate: Spotless with google-java-format, and `-Xlint:all -Werror`. The build is
  warning-clean.
- Unit tests for the whole of `core`, and in-game tests for the still, the brew loop, the three
  menus, and a tank keeping its water across a break.
- A config file covering tank capacity, the dose, the potion stack size, the still scan radius, the
  hourglass base times, the bellows factor, the minimum brew time, the value factors and the
  fuel-tier gate.

### Not in this baseline

Still tiers 2–4, air pressure valves, bellows, condensers, funnels, flue stacks, resonance
crystals, batch brewing, enhancement, customers, the shop, reputation, barter, recipe discovery, the
Almanac, labels, decor, workshops, splash and lingering potions. Each is designed to land as an
addition rather than a rewrite: the enhancer slot indices, the tier counts, the bellows term in the
brew-time formula and `defaultUnlocked` on potions are all already in place.

### Known gaps

- The art is placeholder. Every texture ships as a correctly sized stand-in drawn to be redrawn.
- The three machine recipes are placeholders awaiting sign-off, and there are two brew recipes
  rather than the three an earlier draft of this file claimed.
- Murky Mess does not yet grant the experience `DESIGN.md` describes.
- The fuel tags, the item model definitions, the block models, the loot tables and the recipes are
  generated into `platform-26/src/generated/resources`. That tree is not committed — `./gradlew
  runData` rebuilds it — with one exception: the language files are, because the text a player reads
  is worth reviewing in a diff.
