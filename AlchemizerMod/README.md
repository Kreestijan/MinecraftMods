# Alchemizer

A manual, tactile alchemy mod: build an upgradeable still, brew potions step by step, and serve
customers who walk into your shop.

## Status

Work in progress. The module layout and the entrypoint are in place; gameplay code is not written
yet.

## Requirements

- Minecraft **26.2**
- NeoForge **26.2.0.90**
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

## Layout

| Module | Contents |
| --- | --- |
| `core` | Pure Java, with no Minecraft or NeoForge imports: value maths, recipe matching, reputation and pathing rules. |
| `mod-common` | Minecraft-facing logic shared across versions. A library, not a mod. |
| `platform-26` | The Minecraft 26.2 adapter and the mod entrypoint. |

## License

MIT — see [LICENSE](LICENSE).
