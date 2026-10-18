package io.github.kreestijan.alchemizer.core.flowfield;

import io.github.kreestijan.alchemizer.core.machine.RelativePos;
import java.util.OptionalInt;

/**
 * What the field builder is allowed to ask about the world.
 *
 * <p>An interface rather than a {@code Level}, for the reason {@code StructureView} gives: it is
 * what lets the whole of {@link FlowFieldBuilder} be tested against a map of costs with no world at
 * all.
 *
 * <p><b>One question, not two.</b> The builder needs to know both whether a cell can be stood in
 * and what crossing it costs, and those are the same question asked twice — a cell nothing can
 * stand in is a cell with no price. So the answer is an {@link OptionalInt} rather than a pair of
 * calls that could disagree with each other.
 *
 * <p><b>Costs are the caller's, and so is the judgement behind them.</b> Whether a block is ground
 * a customer may walk on, and whether it is one of the paths they prefer, are questions about block
 * shapes and about a config file — neither of which {@code core} can see. The builder is handed the
 * answers in its own units and does the arithmetic; it never guesses at a block id.
 *
 * <p>The contract that matters, and the one the tests pin: a cell that is unloaded, outside the
 * world, or otherwise unreadable returns {@link OptionalInt#empty()} — never throws, and never
 * reports a cost. The builder treats an empty answer as unreachable, so a field straddling a chunk
 * border simply does not extend into the unloaded half rather than failing.
 *
 * <p><b>A cost must be positive.</b> Zero or a negative is read as "nothing can stand here" rather
 * than as free passage. Answering zero for a broken case is the easy mistake — an unimplemented
 * branch returning its own default — and reading it as free would make the best road in the field
 * the one nothing is known about, so the ambiguous answer is the one that fails safe.
 */
@FunctionalInterface
public interface NavView {

  /**
   * @param pos the offset to price
   * @return what walking into that cell costs, or empty when nothing can stand there
   */
  OptionalInt entryCostAt(RelativePos pos);
}
