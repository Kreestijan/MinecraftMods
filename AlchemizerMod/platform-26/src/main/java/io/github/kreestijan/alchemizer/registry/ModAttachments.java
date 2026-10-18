package io.github.kreestijan.alchemizer.registry;

import com.mojang.serialization.Codec;
import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.defs.ModDefinitions;
import io.github.kreestijan.alchemizer.defs.RecipeBookView;
import java.util.List;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The mod's player attachments: state that belongs to a player rather than to a block.
 *
 * <p>There is one, and it is the recipe book. It lives here rather than on the player entity
 * because only a mod that has been told about the player's {@code PlayerEvent.Clone} can be trusted
 * to carry something across a death or a dimension change, and an attachment with the right copy
 * handler is that notification, written down once instead of remembered at four call sites.
 *
 * <p><b>What is saved and what is recomputed.</b> Only the favourites are written. The recipe
 * entries are derived data: they come out of the datapack, they change when it does, and a copy of
 * them in a player's data file would be a second answer to a question the datapack already answers
 * — one that would go stale the first time a pack was edited. So the format is a list of ids, and
 * the entries are rebuilt on load from whatever book is loaded at that moment.
 *
 * <p><b>Why the sync is filtered to the holder.</b> Attachment sync sends to every player watching
 * the entity as well as to the entity itself, which is right for something like a mob's health and
 * wrong here: a recipe book is one player's list. The predicate keeps a player's book to that
 * player, and the payload is built once per update with that predicate applied per recipient.
 */
public final class ModAttachments {

  /** The name the favourites list is written under, which is the whole of the saved format. */
  private static final String FAVOURITES = "favourites";

  public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
      DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Alchemizer.MOD_ID);

  /**
   * Every recipe a player knows, which today is every recipe there is.
   *
   * <p>Discovery is not implemented, so nothing narrows this yet; {@code
   * PotionDefinition#defaultUnlocked} is already authored and is the flag discovery will read. When
   * it arrives, the only thing that changes is which book {@code RecipeBookSync} hands to {@link
   * RecipeBookView#of} for a given player.
   */
  public static final DeferredHolder<AttachmentType<?>, AttachmentType<RecipeBookView>>
      RECIPE_BOOK =
          ATTACHMENT_TYPES.register(
              "recipe_book",
              () ->
                  AttachmentType.builder(RecipeBookView::empty)
                      .serialize(new FavouritesOnly())
                      // Which favourites a player keeps is a preference, not a possession: dying or
                      // stepping through a portal must not lose them. The default copy handler
                      // serialises and deserialises, so the copy is the favourites and the entries
                      // are
                      // rebuilt against the book in force wherever the player arrives.
                      .copyOnDeath()
                      .sync((holder, to) -> holder == to, RecipeBookView.STREAM_CODEC)
                      .build());

  private ModAttachments() {}

  /**
   * Reads and writes the favourites, and nothing else.
   *
   * <p>An {@link IAttachmentSerializer} rather than a {@code MapCodec} because the read depends on
   * something outside the file — the definitions loaded right now — and a codec is a function of
   * its own input alone.
   */
  private static final class FavouritesOnly implements IAttachmentSerializer<RecipeBookView> {

    @Override
    public RecipeBookView read(IAttachmentHolder holder, ValueInput input) {
      List<String> favourites = input.listOrEmpty(FAVOURITES, Codec.STRING).stream().toList();
      // Rebuilt against the book in force at load time, not the one in force when it was written.
      // Ids that name a recipe the pack no longer ships are dropped here rather than carried
      // forever.
      return RecipeBookView.of(ModDefinitions.current().recipes(), favourites);
    }

    @Override
    public boolean write(RecipeBookView attachment, ValueOutput output) {
      ValueOutput.TypedOutputList<String> list = output.list(FAVOURITES, Codec.STRING);
      for (String id : attachment.favourites()) {
        list.add(id);
      }
      // Written even when empty, so that a player who unstars their last recipe has that recorded
      // rather than being read back as never having starred one.
      return true;
    }
  }
}
