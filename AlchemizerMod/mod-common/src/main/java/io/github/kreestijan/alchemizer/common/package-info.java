/**
 * Minecraft-facing logic shared across platform adapters.
 *
 * <p>Code here may reference Minecraft and NeoForge types. It must not register anything, declare
 * a {@code @Mod} entrypoint, or carry resources: a jar-in-jar library is not a mod, so its
 * {@code assets/} would never be exposed as a resource pack. Content registration and all
 * resources belong to the platform module.
 */
package io.github.kreestijan.alchemizer.common;
