package io.github.kreestijan.alchemizer.registry;

import io.github.kreestijan.alchemizer.Alchemizer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mob effects this mod registers, of which there are currently none.
 *
 * <p>It is here, and empty, because of what the alternative costs. Potions and their effects are
 * datapack data, so a potion built from vanilla effects needs no Java at all — but an effect the
 * game has never heard of does, because a mob effect is a registered object with behaviour
 * attached, not a number. When the first one arrives it needs a registry, and that registry has to
 * exist before it can hold anything.
 *
 * <p>Registering it now rather than then is the whole point: the first custom effect becomes a
 * change to this file alone instead of a change to this file and the mod constructor, and the
 * wiring it needs has already been exercised by every launch up to that point.
 */
public final class ModMobEffects {

  public static final DeferredRegister<MobEffect> MOB_EFFECTS =
      DeferredRegister.create(BuiltInRegistries.MOB_EFFECT, Alchemizer.MOD_ID);

  private ModMobEffects() {}
}
