package io.github.kreestijan.alchemizer.defs;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.common.potion.PotionBuilder;
import io.github.kreestijan.alchemizer.core.recipe.BrewRecipe;
import io.github.kreestijan.alchemizer.core.recipe.RecipeBook;
import io.github.kreestijan.alchemizer.core.recipe.SlotMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The recipe book, in the form a client can draw and send intents against.
 *
 * <p><b>Why this exists at all.</b> A client has no recipe data — {@code ModRegistries} registers
 * the three definition registries with no network codec, and {@code DefinitionLoader} is a server
 * reload listener — so a client cannot look a recipe up. This is the one place that decision is
 * departed from, and it is departed from narrowly: the client is sent what it needs to draw a list
 * and nothing that would let it decide anything. Every intent it sends back is an index into this
 * list, which the server resolves against its own copy.
 *
 * <p><b>Why tags are resolved here.</b> An ingredient may be named by tag — {@code
 * minecraft:planks} rather than {@code minecraft:oak_planks} — and a tag is registry data the
 * client does not have either. Resolving it to one representative stack on the server means the
 * client is handed an ordinary item and never has to know what a tag is. The representative is the
 * first the tag offers, which is arbitrary but stable within a session and is only ever a picture.
 *
 * <p><b>Why the entries are capped.</b> This travels on every join and every reload, and a datapack
 * is free to author thousands of recipes. Sixty-four is far more than a list twelve rows tall can
 * show and keeps the packet bounded whatever a pack ships; the excess is dropped with a warning
 * rather than truncated silently, because a recipe a player cannot see is a bug report either way.
 *
 * <p><b>Why the favourites are separate.</b> The entries are derived from the datapack and can be
 * rebuilt at any time; the favourites are the player's and cannot. Keeping them in one list of ids
 * beside the entries is what lets an attachment serialise the second and recompute the first.
 */
public final class RecipeBookView {

  /** How many recipes the book will carry. Entries past this are dropped, with a warning. */
  public static final int MAX_BOOK_ENTRIES = 64;

  /**
   * One recipe, as a row and as something to load.
   *
   * @param recipeId the recipe's namespaced id, which is what a favourite is recorded against
   * @param tier the still tier the recipe demands
   * @param seconds the hourglass setting it brews at, in seconds
   * @param ingredients one display stack per used slot, in slot order, empty where the ingredient's
   *     tag resolved to nothing
   * @param result what the recipe produces, which is also where the row's name comes from
   */
  public record Entry(
      String recipeId, int tier, int seconds, List<ItemStack> ingredients, ItemStack result) {

    public Entry {
      Objects.requireNonNull(recipeId, "recipeId");
      ingredients = List.copyOf(ingredients);
      Objects.requireNonNull(result, "result");
    }
  }

  private static final StreamCodec<RegistryFriendlyByteBuf, Entry> ENTRY_CODEC =
      StreamCodec.composite(
          ByteBufCodecs.STRING_UTF8,
          Entry::recipeId,
          ByteBufCodecs.VAR_INT,
          Entry::tier,
          ByteBufCodecs.VAR_INT,
          Entry::seconds,
          ItemStack.OPTIONAL_LIST_STREAM_CODEC,
          Entry::ingredients,
          ItemStack.STREAM_CODEC,
          Entry::result,
          Entry::new);

  /**
   * The codec the attachment is synced through.
   *
   * <p>Both lists are bounded by the same cap. The favourites can never outnumber the entries,
   * because {@link #of} drops any that name a recipe the book does not hold, so one number serves
   * for both.
   */
  public static final StreamCodec<RegistryFriendlyByteBuf, RecipeBookView> STREAM_CODEC =
      StreamCodec.composite(
          ENTRY_CODEC.apply(ByteBufCodecs.list(MAX_BOOK_ENTRIES)),
          RecipeBookView::entries,
          ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_BOOK_ENTRIES)),
          RecipeBookView::favourites,
          RecipeBookView::new);

  private final List<Entry> entries;
  private final List<String> favourites;

  /**
   * @param entries the recipes, in the book's own order
   * @param favourites the ids of the favourited recipes, in the order they were starred
   */
  public RecipeBookView(List<Entry> entries, List<String> favourites) {
    Objects.requireNonNull(entries, "entries");
    Objects.requireNonNull(favourites, "favourites");
    this.entries = List.copyOf(entries);
    this.favourites = List.copyOf(favourites);
  }

  /**
   * @return a book with no recipes, which is what a client holds until the first sync arrives and
   *     what an empty datapack produces
   */
  public static RecipeBookView empty() {
    return new RecipeBookView(List.of(), List.of());
  }

  /**
   * @param book the recipes to show
   * @return a view with no favourites, which is the book as every player sees it today
   */
  public static RecipeBookView of(RecipeBook book) {
    return of(book, List.of());
  }

  /**
   * @param book the recipes to show
   * @param favourites the favourited ids, of which any the book does not hold are dropped
   * @return the book, with the favourites that are still real
   */
  public static RecipeBookView of(RecipeBook book, List<String> favourites) {
    Objects.requireNonNull(book, "book");
    List<BrewRecipe> all = book.all();
    if (all.size() > MAX_BOOK_ENTRIES) {
      // Said out loud rather than truncated in silence. A recipe missing from the list looks to a
      // player exactly like a recipe that does not exist, and the only person who can fix it is the
      // one who wrote the datapack.
      Alchemizer.LOGGER.warn(
          "Alchemizer: {} brew recipes are defined, but the recipe book holds {}; the rest will not"
              + " be listed",
          all.size(),
          MAX_BOOK_ENTRIES);
    }

    List<Entry> entries = new ArrayList<>(Math.min(all.size(), MAX_BOOK_ENTRIES));
    for (int i = 0; i < all.size() && i < MAX_BOOK_ENTRIES; i++) {
      entries.add(entry(all.get(i)));
    }
    return new RecipeBookView(entries, knownOnly(favourites, entries));
  }

  /**
   * @param favourites the favourited ids, of which any the book does not hold are dropped
   * @return the same recipes, with this player's favourites instead of the ones this view holds
   */
  public RecipeBookView withFavourites(List<String> favourites) {
    Objects.requireNonNull(favourites, "favourites");
    return new RecipeBookView(entries, knownOnly(favourites, entries));
  }

  /**
   * @return the recipes, in the book's own order, which is the index space intents are sent in
   */
  public List<Entry> entries() {
    return entries;
  }

  /**
   * @return the favourited ids, in the order they were starred
   */
  public List<String> favourites() {
    return favourites;
  }

  /**
   * The order the list is drawn in: the favourites first, in the order they were starred, and then
   * everything else in the book's order.
   *
   * <p>Which is not the order the intents are indexed in. The two are deliberately different lists:
   * this one is a picture and may reorder freely, while the index an intent carries has to mean the
   * same thing on both sides for as long as a screen is open.
   *
   * @return the entries in display order
   */
  public List<Entry> displayOrder() {
    if (favourites.isEmpty()) {
      return entries;
    }
    List<Entry> ordered = new ArrayList<>(entries.size());
    for (String id : favourites) {
      Entry entry = find(id);
      if (entry != null) {
        ordered.add(entry);
      }
    }
    for (Entry entry : entries) {
      if (!favourites.contains(entry.recipeId())) {
        ordered.add(entry);
      }
    }
    return List.copyOf(ordered);
  }

  /**
   * @param recipeId the recipe's id
   * @return its position in {@link #entries()}, or -1 when the book does not hold it
   */
  public int indexOf(String recipeId) {
    for (int i = 0; i < entries.size(); i++) {
      if (entries.get(i).recipeId().equals(recipeId)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * @param recipeId the recipe's id
   * @return whether the player has starred it
   */
  public boolean isFavourite(String recipeId) {
    return favourites.contains(recipeId);
  }

  /**
   * @param recipeId the recipe's id
   * @return a view with that recipe starred, or unstarred if it already was
   */
  public RecipeBookView toggled(String recipeId) {
    Objects.requireNonNull(recipeId, "recipeId");
    List<String> wanted = new ArrayList<>(favourites);
    if (!wanted.remove(recipeId)) {
      wanted.add(recipeId);
    }
    return new RecipeBookView(entries, knownOnly(wanted, entries));
  }

  /**
   * @param recipeId the recipe's id
   * @return the entry, or null when the book does not hold it
   */
  private Entry find(String recipeId) {
    int index = indexOf(recipeId);
    return index < 0 ? null : entries.get(index);
  }

  /**
   * @return whether the book holds no recipes
   */
  public boolean isEmpty() {
    return entries.isEmpty();
  }

  /**
   * @param favourites the ids to keep, in order
   * @param entries the recipes the book holds
   * @return the ids that name a recipe the book still holds, without duplicates
   */
  private static List<String> knownOnly(List<String> favourites, List<Entry> entries) {
    List<String> kept = new ArrayList<>(favourites.size());
    for (String id : favourites) {
      if (id == null || kept.contains(id)) {
        continue;
      }
      for (Entry entry : entries) {
        if (entry.recipeId().equals(id)) {
          kept.add(id);
          break;
        }
      }
    }
    return List.copyOf(kept);
  }

  /**
   * @param recipe the recipe to describe
   * @return what a client needs to draw its row and load it
   */
  private static Entry entry(BrewRecipe recipe) {
    List<ItemStack> ingredients = new ArrayList<>(recipe.slots().size());
    for (SlotMatcher matcher : recipe.slots()) {
      ingredients.add(displayStack(matcher));
    }
    return new Entry(
        recipe.id(),
        recipe.tier(),
        recipe.time().baseSeconds(),
        ingredients,
        PotionBuilder.build(Items.POTION, recipe.potion()));
  }

  /**
   * The switch has no default, so a third kind of matcher is a compile error here until someone
   * decides what it looks like — which is the point of the sealed interface it switches over.
   *
   * @param matcher what the slot accepts
   * @return a stack that slot could be filled with, or empty when nothing can be found to show
   */
  private static ItemStack displayStack(SlotMatcher matcher) {
    return switch (matcher) {
      case SlotMatcher.ByItem byItem -> itemStack(byItem.itemId());
      case SlotMatcher.ByTag byTag -> tagStack(byTag.tagId());
    };
  }

  /**
   * @param itemId the item's namespaced id
   * @return one of that item, or empty when no item answers to that id
   */
  private static ItemStack itemStack(String itemId) {
    Identifier id = Identifier.tryParse(itemId);
    if (id == null) {
      return ItemStack.EMPTY;
    }
    // Asked through the holder lookup rather than getValue, which on the item registry answers air
    // for an id nothing registered — an unregistered ingredient would then be drawn as air rather
    // than recognised as missing.
    return BuiltInRegistries.ITEM.get(id).<ItemStack>map(ItemStack::new).orElse(ItemStack.EMPTY);
  }

  /**
   * @param tagId the tag's namespaced id
   * @return one item carrying that tag, or empty when the tag is empty or unknown
   */
  private static ItemStack tagStack(String tagId) {
    Identifier id = Identifier.tryParse(tagId);
    if (id == null) {
      return ItemStack.EMPTY;
    }
    for (Holder<Item> holder :
        BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, id))) {
      return new ItemStack(holder);
    }
    return ItemStack.EMPTY;
  }
}
