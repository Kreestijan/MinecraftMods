package io.github.kreestijan.alchemizer.core.potion;

import java.util.Objects;

/**
 * What a finished brew produced.
 *
 * <p>There are two endings. A matched recipe yields its potion. Anything else yields Murky Mess —
 * the still consumed the ingredients, the water and the time, and gave back something undrinkable.
 * That failure is the point of the machine: it is what makes experimenting cost something.
 */
public sealed interface BrewOutcome {

  /**
   * @param potion the potion the matched recipe produces
   */
  record Success(PotionDefinition potion) implements BrewOutcome {
    public Success {
      Objects.requireNonNull(potion, "potion");
    }
  }

  /**
   * @param reason an untranslated diagnostic, for logs rather than for the player
   */
  record MurkyMess(String reason) implements BrewOutcome {
    public MurkyMess {
      Objects.requireNonNull(reason, "reason");
    }
  }
}
