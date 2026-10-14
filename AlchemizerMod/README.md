# Alchemizer

A manual, tactile alchemy mod: build an upgradeable still, brew potions step by step, and serve
customers who walk into your shop.

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
- `./gradlew runData` — writes `platform-26/src/generated/resources`: the block and item models,
  blockstates, loot tables, recipes, tags and the language files. Only the language files are
  committed, so this is the command that creates the rest after a clone, and it must be run once
  before the mod has models, recipes or loot tables to load.
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
