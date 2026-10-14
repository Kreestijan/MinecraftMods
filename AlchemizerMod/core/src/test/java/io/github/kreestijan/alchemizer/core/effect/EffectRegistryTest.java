package io.github.kreestijan.alchemizer.core.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** An effect nobody has priced must still brew, and an id that is not a pair must be refused. */
class EffectRegistryTest {

  private static final EffectId SPEED = new EffectId("minecraft:speed");
  private static final EffectId CUSTOM = new EffectId("alchemizer:fortune_brew");

  @Test
  void authoredEffectResolvesToItsOwnDefinition() {
    EffectRegistry registry =
        new EffectRegistry(Map.of(SPEED, new EffectDefinition(SPEED, 12, false)), 1);

    EffectDefinition resolved = registry.resolve(SPEED);

    assertEquals(12, resolved.baseValue());
    assertFalse(resolved.instant());
    assertTrue(registry.isPriced(SPEED));
  }

  @Test
  void unknownEffectFallsBackRatherThanFailing() {
    EffectRegistry registry = EffectRegistry.withFallbackOnly(7);

    EffectDefinition resolved = registry.resolve(CUSTOM);

    assertEquals(7, resolved.baseValue());
    assertEquals(CUSTOM, resolved.id());
    assertFalse(resolved.instant(), "an unpriced effect is assumed to last, not to be instant");
    assertFalse(registry.isPriced(CUSTOM));
  }

  @Test
  void emptyRegistryFallsBackForEverything() {
    EffectRegistry registry = EffectRegistry.withFallbackOnly(3);

    assertEquals(3, registry.resolve(SPEED).baseValue());
    assertEquals(0, registry.byId().size());
  }

  @Test
  void definitionsAreCopiedSoTheRegistryCannotBeMutatedLater() {
    EffectRegistry registry =
        new EffectRegistry(Map.of(SPEED, new EffectDefinition(SPEED, 5, false)), 1);

    assertThrows(
        UnsupportedOperationException.class,
        () -> registry.byId().put(CUSTOM, new EffectDefinition(CUSTOM, 9, false)));
  }

  @Test
  void negativeFallbackIsRefused() {
    assertThrows(IllegalArgumentException.class, () -> EffectRegistry.withFallbackOnly(-1));
  }

  @Test
  void effectIdMustBeOneNamespacePathPair() {
    assertThrows(IllegalArgumentException.class, () -> new EffectId("speed"));
    assertThrows(IllegalArgumentException.class, () -> new EffectId("minecraft:speed:ii"));
    assertThrows(IllegalArgumentException.class, () -> new EffectId(":speed"));
    assertThrows(IllegalArgumentException.class, () -> new EffectId("minecraft:"));
    assertThrows(IllegalArgumentException.class, () -> new EffectId("  "));
  }

  @Test
  void effectIdSplitsIntoItsParts() {
    assertEquals("minecraft", SPEED.namespace());
    assertEquals("speed", SPEED.path());
    assertEquals("alchemizer", CUSTOM.namespace());
    assertEquals("minecraft:speed", SPEED.toString());
  }
}
