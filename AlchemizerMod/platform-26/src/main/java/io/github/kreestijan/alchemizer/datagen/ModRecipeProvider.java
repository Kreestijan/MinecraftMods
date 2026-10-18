package io.github.kreestijan.alchemizer.datagen;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.registry.ModBlocks;
import io.github.kreestijan.alchemizer.registry.ModItems;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;

/**
 * The mod's crafting recipes.
 *
 * <p>The machine's recipes are placeholders, added so that a still can be assembled in a test world
 * without dropping into creative first. None of them is signed off, and `DESIGN.md` says nothing
 * about what the three blocks should cost: a recipe is the one thing in this mod a player is
 * guaranteed to read, so the numbers are kept cheap, obvious and in one place, where a decision
 * replaces them wholesale rather than uncovering a guess buried somewhere else.
 *
 * <p>The two halves of this class exist because of how the generator works: the provider builds the
 * recipes and the runner is what the gather event registers, holding the registry lookup that the
 * provider needs but cannot be handed until the generator runs.
 */
public final class ModRecipeProvider extends RecipeProvider {

  /**
   * @param registries the registry lookup the generator is running against
   * @param output where a built recipe is written
   */
  public ModRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
    super(registries, output);
  }

  @Override
  protected void buildRecipes() {
    // Four redstone in a plus around coal, which is the design's "coal/charcoal + 4 redstone"
    // written out. The centre is a tag rather than an item so that one recipe covers both coals;
    // two recipes producing the same thing would differ only in which one a player happened to
    // unlock first, and the recipe book would show both.
    shaped(RecipeCategory.MISC, ModItems.ALCHEMICAL_COAL.get())
        .pattern(" R ")
        .pattern("RCR")
        .pattern(" R ")
        .define('R', Items.REDSTONE)
        .define('C', ItemTags.COALS)
        .unlockedBy("has_coal", has(ItemTags.COALS))
        .save(output);

    // The top rung of the fuel ladder. Alchemical Coal's recipe reads off a vanilla one — coal with
    // redstone round it — and this one has nothing to read off, so it is its own idea: a blaze rod
    // ringed by glowstone, four corners left open. Placeholder like the rest, and the same argument
    // applies. A player who sees it in the recipe book can tell what it is made of, which is the
    // whole of what the ladder asks of a fuel: that where it sits is guessable from what it is.
    shaped(RecipeCategory.MISC, ModItems.ENHANCED_BLAZE_ROD.get())
        .pattern(" G ")
        .pattern("GBG")
        .pattern(" G ")
        .define('G', Items.GLOWSTONE_DUST)
        .define('B', Items.BLAZE_ROD)
        .unlockedBy("has_blaze_rod", has(Items.BLAZE_ROD))
        .save(output);

    // The three machine blocks. These are placeholders, and they are deliberately cheap and
    // familiar: nothing here is signed off, and what they are for is putting a still together in a
    // test world without dropping into creative first. Each reads off the vanilla block it is
    // closest to — a furnace, a cauldron, a bucket — so that a player who sees one in the recipe
    // book can guess what it does.
    //
    // A sign-off replaces these wholesale. They are shaped rather than shapeless so that the shape
    // itself carries the meaning while the numbers are still open to change.
    shaped(RecipeCategory.MISC, ModBlocks.IGNITION_CHAMBER.get())
        .pattern("BBB")
        .pattern("BFB")
        .pattern("BBB")
        .define('B', Items.STONE_BRICKS)
        .define('F', Items.FURNACE)
        .unlockedBy("has_furnace", has(Items.FURNACE))
        .save(output);

    shaped(RecipeCategory.MISC, ModBlocks.PRESSURE_CHAMBER.get())
        .pattern("IGI")
        .pattern("GCG")
        .pattern("IGI")
        .define('I', Items.IRON_INGOT)
        .define('G', Items.GLASS)
        .define('C', Items.CAULDRON)
        .unlockedBy("has_cauldron", has(Items.CAULDRON))
        .save(output);

    shaped(RecipeCategory.MISC, ModBlocks.ALCHEMICAL_TANK.get())
        .pattern("GGG")
        .pattern("IBI")
        .pattern("GGG")
        .define('I', Items.IRON_INGOT)
        .define('G', Items.GLASS)
        .define('B', Items.BUCKET)
        .unlockedBy("has_bucket", has(Items.BUCKET))
        .save(output);

    // A sign-off replaces this too. Planks around a chest, because a counter is a place to put
    // things
    // and a chest is the one thing every player already reads that way; it unlocks off the chest so
    // that a player who has one finds the block without having to be told it exists.
    shaped(RecipeCategory.MISC, ModBlocks.COUNTER.get())
        .pattern("PPP")
        .pattern("PCP")
        .pattern("PPP")
        .define('P', ItemTags.PLANKS)
        .define('C', Items.CHEST)
        .unlockedBy("has_chest", has(Items.CHEST))
        .save(output);

    // Emptying a tank. A tank carries its water, and this is how a player gets it out again: the
    // result of a craft is built from the recipe, not from what went in, so the tank that comes out
    // has no carried water on it. One ingredient and no shape, which is what makes it work in any
    // grid, a 2x2 included.
    //
    // It cannot ask for a *filled* tank. A vanilla ingredient matches an item and nothing else, and
    // the one component-aware ingredient this loader adds matches an exact component value, which
    // cannot mean "any amount of water". So the recipe matches any tank, and crafting an empty one
    // is a no-op that hands back an empty one. That is the cost of the phrasing; the alternative
    // would be a button in the tank's screen.
    //
    // Its id is named rather than defaulted, and it has to be: a recipe's default id is its result
    // item's id, so a tank built from materials and a tank built from a tank would both want to be
    // `alchemizer:alchemical_tank` and the generator throws on the second. The suffix says which of
    // the tank's two recipes this is.
    shapeless(RecipeCategory.MISC, ModBlocks.ALCHEMICAL_TANK.get())
        .requires(ModBlocks.ALCHEMICAL_TANK.get())
        .unlockedBy("has_alchemical_tank", has(ModBlocks.ALCHEMICAL_TANK.get()))
        .save(output, Alchemizer.MOD_ID + ":alchemical_tank_clearing");
  }

  /** The provider factory the gather event registers. */
  public static final class Runner extends RecipeProvider.Runner {

    /**
     * @param output the generator's output root
     * @param registries the registry lookup, resolved by the generator before the provider is built
     */
    public Runner(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
      super(output, registries);
    }

    @Override
    protected RecipeProvider createRecipeProvider(
        HolderLookup.Provider registries, RecipeOutput output) {
      return new ModRecipeProvider(registries, output);
    }

    @Override
    public String getName() {
      return "Alchemizer recipes";
    }
  }
}
