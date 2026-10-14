package io.github.kreestijan.alchemizer.core.machine;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The verdict on an assembled still: formed or not, at what tier, and — when not — why.
 *
 * <p>The problems are {@link StillProblem} codes, not sentences. They exist to be shown to the
 * player — the red line under the brewing screen names the one thing that is wrong — and the words
 * are chosen by the adapter that draws it, because this module has no idea what language the player
 * reads. The rule that produced the problem is here; the phrasing is not.
 *
 * @param valid whether the parts form a working still
 * @param tier the tier reached; {@link StillTier#NONE} when not valid
 * @param problems why the still is not valid, in the order they were noticed; empty when valid
 */
public record ValidationResult(boolean valid, StillTier tier, List<StillProblem> problems) {

  public ValidationResult {
    Objects.requireNonNull(tier, "tier");
    problems = List.copyOf(Objects.requireNonNull(problems, "problems"));
    if (valid && !problems.isEmpty()) {
      throw new IllegalArgumentException("a valid still cannot have problems");
    }
    if (!valid && problems.isEmpty()) {
      throw new IllegalArgumentException("an invalid still must say why");
    }
  }

  /**
   * @param tier the tier reached
   * @return a valid verdict
   */
  public static ValidationResult valid(StillTier tier) {
    Objects.requireNonNull(tier, "tier");
    if (!tier.isFormed()) {
      throw new IllegalArgumentException("a valid still needs a formed tier");
    }
    return new ValidationResult(true, tier, List.of());
  }

  /**
   * @param problems why the still is not valid
   * @return an invalid verdict
   */
  public static ValidationResult invalid(List<StillProblem> problems) {
    return new ValidationResult(false, StillTier.NONE, problems);
  }

  /**
   * @param problem why the still is not valid
   * @return an invalid verdict naming that one problem
   */
  public static ValidationResult invalid(StillProblem problem) {
    return invalid(List.of(problem));
  }

  /**
   * @return whether a still was formed
   */
  public boolean isFormed() {
    return valid;
  }

  /**
   * The problem worth telling the player about first.
   *
   * <p>The problems are ordered by the validator into the order a builder would want to fix them,
   * so the first is the one to name. A screen has room for one line, and a list of everything that
   * is wrong is worse than the one thing to do next.
   *
   * @return the first problem, or empty when the still is formed
   */
  public Optional<StillProblem> firstProblem() {
    return problems.isEmpty() ? Optional.empty() : Optional.of(problems.getFirst());
  }
}
