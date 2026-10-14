package io.github.kreestijan.alchemizer.registry;

import io.github.kreestijan.alchemizer.Alchemizer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's items.
 *
 * <p>An item appears here with the feature that needs it, rather than with the design document that
 * names it: an item with no recipe and no use is a thing the creative tab has to apologise for.
 *
 * <p>Block items are registered here rather than alongside their block because the item registry is
 * what the creative tab and the lang file iterate, and keeping them in one place means a new block
 * is a two-line change in a known order.
 */
public final class ModItems {

  public static final DeferredRegister.Items ITEMS =
      DeferredRegister.createItems(Alchemizer.MOD_ID);

  public static final DeferredItem<BlockItem> IGNITION_CHAMBER =
      ITEMS.registerSimpleBlockItem(ModBlocks.IGNITION_CHAMBER);

  public static final DeferredItem<BlockItem> PRESSURE_CHAMBER =
      ITEMS.registerSimpleBlockItem(ModBlocks.PRESSURE_CHAMBER);

  public static final DeferredItem<BlockItem> ALCHEMICAL_TANK =
      ITEMS.registerSimpleBlockItem(ModBlocks.ALCHEMICAL_TANK);

  /**
   * Coal with redstone worked through it: the second rung of the fuel ladder.
   *
   * <p>A plain item. It is worth more than coal as fuel rather than doing anything on its own, and
   * both halves of "worth more" — the tier and the burn time — are declared as data rather than
   * here, so a datapack can disagree with this file without recompiling anything.
   */
  public static final DeferredItem<Item> ALCHEMICAL_COAL =
      ITEMS.registerSimpleItem("alchemical_coal");

  /**
   * What a brew becomes when the ingredients do not add up to anything.
   *
   * <p>A plain item, and deliberately not drinkable. It is the receipt for an experiment: it is
   * worth nothing, it does nothing, and the only thing it says is that the still worked and the
   * recipe did not. Making it edible — even to no effect — would invite a player to treat failure
   * as a slow way to succeed.
   *
   * <p>It stacks, which {@code DESIGN.md} does not settle either way. A failure that arrives in
   * sixes is easier to carry back to a chest than six failures, and nothing reads the stack size.
   */
  public static final DeferredItem<Item> MURKY_MESS = ITEMS.registerSimpleItem("murky_mess");

  private ModItems() {}
}
