package io.github.kreestijan.alchemizer.datagen;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.registry.ModBlocks;
import io.github.kreestijan.alchemizer.registry.ModItems;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

/**
 * Generates {@code assets/alchemizer/lang/en_us.json}.
 *
 * <p>Names are generated rather than hand-written so that a block item cannot be registered without
 * a name: the provider walks the registry, and a missing entry is a compile-time omission in a file
 * a reviewer can see rather than a raw translation key in a player's inventory.
 *
 * <p>The mod id and locale are supplied here rather than by each caller, which is what lets {@link
 * DataGenEvents} pass this class's constructor directly as a provider factory.
 */
public final class ModLangProvider extends LanguageProvider {

  /** Mirrors {@code PotionContents.getName}'s prefix, which is fixed by the vanilla item. */
  private static final String POTION_NAME_PREFIX = "item.minecraft.potion.effect.";

  /**
   * @param output the generator's output root
   */
  public ModLangProvider(PackOutput output) {
    super(output, Alchemizer.MOD_ID, "en_us");
  }

  @Override
  protected void addTranslations() {
    add("itemGroup." + Alchemizer.MOD_ID, "Alchemizer");

    // One entry per block, not two. A block's item asks for its description id through the block,
    // so
    // addBlock and addItem write the same key — the second call is a duplicate-key error, not an
    // override.
    addBlock(ModBlocks.IGNITION_CHAMBER, "Ignition Chamber");
    addBlock(ModBlocks.PRESSURE_CHAMBER, "Pressure Chamber");
    addBlock(ModBlocks.ALCHEMICAL_TANK, "Alchemical Tank");

    addItem(ModItems.ALCHEMICAL_COAL, "Alchemical Coal");
    addItem(ModItems.MURKY_MESS, "Murky Mess");

    // A datapack potion cannot register a name of its own, so its display name is a lang key
    // derived
    // from its id: PotionContents.getName builds "item.minecraft.potion.effect." + the potion id,
    // which is the same convention the vanilla potion keys use. These two entries are therefore
    // what
    // stands between a brewed potion and a player seeing the raw key in their inventory — and they
    // are also the reason a datapack that adds a potion has to ship its own lang file.
    add(POTION_NAME_PREFIX + "alchemizer:test_potion", "Test Potion");
    add(POTION_NAME_PREFIX + "alchemizer:swiftness", "Potion of Swiftness");

    // The brewing screen. Each of these is looked up by the screens, and a missing one is a raw key
    // drawn across a panel rather than a missing name in an inventory — the same argument, and the
    // reason they belong here rather than beside the screen that uses them.
    add("gui.alchemizer.seconds", "%ss");
    add("gui.alchemizer.hourglass.selected", "[%s]");
    add("gui.alchemizer.hourglass.tooltip", "Brew for %s seconds");
    add("gui.alchemizer.start", "Start");

    // The tank's contents, in millibuckets. Shown on hover over the gauge on both screens that draw
    // one, and on hover over the tank item itself, so all three readings of one tank agree.
    add("gui.alchemizer.water.mb", "%s/%s mB");

    // The machine's health as one line: which still it is while it is sound, and which part of it
    // is wrong while it is not. Deliberately nothing per StartResult — what the chamber is missing
    // is said by the slots that redden and the gauge that runs short, and a status line repeating
    // it
    // would have a player reading two places to be told the same thing.
    add("gui.alchemizer.status.working", "Tier %s - Working perfectly.");

    // One per StillProblem, shown in red on the status line in place of the tier, so that a player
    // who built the machine wrongly is told which piece is wrong. They are kept short on purpose:
    // the line has the width of the panel and no more, and a fault that runs off the edge tells the
    // player nothing.
    add("gui.alchemizer.still.problem.no_parts", "Still parts not defined");
    add("gui.alchemizer.still.problem.no_ignition", "No ignition chamber");
    add("gui.alchemizer.still.problem.multiple_ignition", "Maximum 1 ignition chamber allowed.");
    add("gui.alchemizer.still.problem.no_pressure", "No pressure chamber");
    add("gui.alchemizer.still.problem.multiple_pressure", "Maximum 1 pressure chamber allowed.");
    add("gui.alchemizer.still.problem.not_on_ignition", "Chamber not above ignition");
    add("gui.alchemizer.still.problem.no_tank", "No tank attached");
    add("gui.alchemizer.still.problem.unknown_tier", "Unknown still shape");
    add("gui.alchemizer.still.problem.not_in_a_world", "Not placed in a world");
  }
}
