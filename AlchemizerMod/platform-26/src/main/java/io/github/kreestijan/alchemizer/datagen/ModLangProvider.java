package io.github.kreestijan.alchemizer.datagen;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.registry.ModBlocks;
import io.github.kreestijan.alchemizer.registry.ModEntities;
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
    addBlock(ModBlocks.COUNTER, "Counter");
    addBlock(ModBlocks.AIR_PRESSURE_VALVE, "Air Pressure Valve");
    addBlock(ModBlocks.BELLOWS, "Bellows");
    // The shift-right-click mode cycle. The keys are built by BellowsBlock.Mode#messageKey, so
    // these three are what stand between the click and a raw key in chat.
    add("message.alchemizer.bellows.always", "Bellows: always working");
    add("message.alchemizer.bellows.while_brewing", "Bellows: works while brewing");
    add("message.alchemizer.bellows.never", "Bellows: standing still");
    addBlock(ModBlocks.FUNNEL, "Funnel");
    addBlock(ModBlocks.FLUE_STACK, "Flue Stack");
    addBlock(ModBlocks.RESONANCE_CRYSTAL, "Resonance Crystal");
    addBlock(ModBlocks.CONDENSER, "Condenser");
    addBlock(ModBlocks.ADVANCED_CONDENSER, "Advanced Condenser");
    addBlock(ModBlocks.ALCHEMICAL_TUBING, "Alchemical Tubing");

    addItem(ModItems.ALCHEMICAL_COAL, "Alchemical Coal");
    addItem(ModItems.ENHANCED_BLAZE_ROD, "Enhanced Blaze Rod");
    addItem(ModItems.MURKY_MESS, "Murky Mess");

    // The mod's one entity. Its name is what a player sees above a customer if a name tag is ever
    // put on one and in the entity's own debug output otherwise; it is generated here for the same
    // reason a block's is, so that a type cannot be registered without a name.
    addEntityType(ModEntities.CUSTOMER, "Customer");

    // A datapack potion cannot register a name of its own, so its display name is a lang key
    // derived
    // from its id: PotionContents.getName builds "item.minecraft.potion.effect." + the potion id,
    // which is the same convention the vanilla potion keys use. These two entries are therefore
    // what
    // stands between a brewed potion and a player seeing the raw key in their inventory — and they
    // are also the reason a datapack that adds a potion has to ship its own lang file.
    add(POTION_NAME_PREFIX + "alchemizer:test_potion", "Test Potion");
    add(POTION_NAME_PREFIX + "alchemizer:swiftness", "Potion of Swiftness");
    add(POTION_NAME_PREFIX + "alchemizer:strength", "Potion of Strength");
    add(POTION_NAME_PREFIX + "alchemizer:fire_resistance", "Potion of Fire Resistance");
    add(POTION_NAME_PREFIX + "alchemizer:regeneration", "Potion of Regeneration");

    // The brewing screen. Each of these is looked up by the screens, and a missing one is a raw key
    // drawn across a panel rather than a missing name in an inventory — the same argument, and the
    // reason they belong here rather than beside the screen that uses them.
    //
    // "seconds" is the key the pressure chamber's hourglass buttons read, and it is the whole of an
    // hourglass button's label. One string in one place, because two keys holding the same text are
    // two things to keep in step.
    add("gui.alchemizer.seconds", "%ss");
    add("gui.alchemizer.hourglass.tooltip", "Brew for %s seconds");
    add("gui.alchemizer.start", "Start");

    // The batch control, which appears at the left of the controls row once a condenser is
    // attached. The label is the multiplier with an x on it, because that is how the design says a
    // batch is written and a player reading "3x" needs no second sentence.
    add("gui.alchemizer.batch.label", "%sx");
    add(
        "gui.alchemizer.batch.tooltip.on",
        "Brewing %s at a time. Scroll to change, click to stop.");
    add(
        "gui.alchemizer.batch.tooltip.off",
        "Brew one at a time. Scroll to choose a batch, click to start.");
    add(
        "gui.alchemizer.batch.tooltip.busy",
        "Brewing %s at a time. The batch is fixed until it finishes.");

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
    add("gui.alchemizer.still.problem.multiple_crystal", "Maximum 1 resonance crystal allowed.");
    add("gui.alchemizer.still.problem.tubing_too_long", "Tubing run too long");

    // The recipe book, which appears beside the panel once a funnel is on top of the chamber. A row
    // is the potion's own name — taken from the output stack, so a datapack's potions are named
    // without this mod knowing how — and then the tier and the setting the recipe brews at, which
    // is the pair of numbers a player has to clear before loading it. The setting reuses the shared
    // seconds key rather than writing the unit a second time.
    add("gui.alchemizer.book.tooltip", "Recipe book");
    add("gui.alchemizer.book.empty", "No recipes");
    add("gui.alchemizer.book.tier", "T%s %s");

    // The Counter's screen: who owns the shop, who else may trade at it, and whether it is trading.
    // The two headings carry a count rather than the names alone, because the panel draws three
    // rows of each and a shop may hold more than that — a heading reading four above three names is
    // the panel saying which of its limits the player has run into.
    //
    // "unnamed" is what an owner with no name is drawn as. The server can only name a player its
    // own
    // name cache has seen, which a world copied between servers will not have, and a blank after
    // "Owner:" would read as a fault in the screen rather than as a missing name.
    add("gui.alchemizer.counter.no_shop", "This Counter has no shop yet.");
    add("gui.alchemizer.counter.unnamed", "Unnamed");
    add("gui.alchemizer.counter.owner", "Owner: %s");
    add("gui.alchemizer.counter.open", "Shop open - click to close");
    add("gui.alchemizer.counter.closed", "Shop closed - click to open");
    add("gui.alchemizer.counter.coowners", "Co-owners (%s)");
    add("gui.alchemizer.counter.candidates", "Add a co-owner (%s)");

    // The customer dialogue, which is opened by right-clicking one of them: a window titled after
    // what it is about and two options, which for now do the same thing. They are named rather than
    // described because the difference between them is the design's business and not the player's
    // yet — see CustomerDialogueMenu.
    add("gui.alchemizer.customer.dialogue", "Customer");
    add("gui.alchemizer.customer.serve", "Serve");
    add("gui.alchemizer.customer.refuse", "Refuse");
  }
}
