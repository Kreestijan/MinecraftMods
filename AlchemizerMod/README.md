# Alchemizer

A manual, tactile alchemy mod: build an upgradeable still, brew potions step by step, and serve
customers who walk into your shop.

## Status

Work in progress, at the **tier-1 baseline**: a working still, and the first slice of the core loop.

In place:

- the three tier-1 blocks — Ignition Chamber, Pressure Chamber and Alchemical Tank — with
  multiblock validation, fuel, a lit state and cached structure checks
- tanks filled by right-clicking them with a water container, or by clicking one onto the brewing
  screen's gauge; the containers are matched by item tag, so a mod's own water bucket works
- the five-slot brew: ordered ingredients, a stack of bottles, water from the attached tanks, four
  hourglass presets, and a brew that freezes rather than resets when the fire goes out
- a brew's potion lands in its own output slot, where identical potions stack up to a configurable
  64 — a global override of vanilla's one-per-slot, so brewed potions stack everywhere
- fuel that lights when a brew wants it and not before, and burns to exhaustion once lit
- real, vanilla-compatible potion items as the output, plus Murky Mess for a layout no recipe
  describes
- a screen for each machine: fuel and flame for the Ignition Chamber, and ingredients, tank gauge,
  progress, hourglasses, an output slot and a start button for the Pressure Chamber
- potions, their effects and effect values as datapack data, so a new potion is a JSON file and no
  Java change

Not yet written: still tiers 2–4, condensers, batch brewing, enhancement, customers, the shop,
reputation, barter, recipe discovery, the Almanac, labels, decor and workshops.

The art is placeholder throughout, drawn to be redrawn; the block and item textures are
correctly-sized stand-ins.

## Requirements

- Minecraft **26.1.2**
- NeoForge **26.1.2.116**
- Java **25**

## About this repository

This repository publishes the mod's own source — its Java code, resources and mod metadata.

The Gradle build is deliberately **not** published: `settings.gradle`, the per-module
`build.gradle` files, `gradle.properties` and the wrapper are all local. **A clone of this
repository therefore does not build on its own.** Recreating a working build means supplying:

- a `settings.gradle` that includes the `core`, `mod-common` and `platform-26` modules
- per-module `build.gradle` files applying NeoForge's ModDevGradle plugin, with `platform-26`
  declaring `core` and `mod-common` as both `implementation` and `jarJar` dependencies
- a `gradle.properties` holding the Minecraft, NeoForge and mod metadata versions

The **quality gate is local-only for the same reason.** Spotless (pinned to google-java-format) and
the `-Xlint:all -Werror` compiler args live in the per-module `build.gradle` files, so a clone can
compile the source but cannot run the formatter or reproduce the warning-clean build.

## Building

With the local build files in place:

- `./gradlew build` — compiles every module and runs the unit tests
- `./gradlew spotlessCheck build` — the full gate, formatter included
- `./gradlew runData` — regenerates `platform-26/src/generated/resources`, which is committed
- `./gradlew runGameTestServer` — the in-game tests, on a headless server
- `./gradlew runClient` — a development client

## Layout

| Module | Contents |
| --- | --- |
| `core` | Pure Java, with no Minecraft or NeoForge imports: value maths, recipe matching, reputation and pathing rules. |
| `mod-common` | Minecraft-facing logic shared across versions. A library, not a mod. |
| `platform-26` | The Minecraft 26.x adapter and the mod entrypoint. |

## License

MIT — see [LICENSE](LICENSE).
