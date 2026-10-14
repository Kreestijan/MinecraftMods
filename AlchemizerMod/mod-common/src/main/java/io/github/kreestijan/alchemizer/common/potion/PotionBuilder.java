package io.github.kreestijan.alchemizer.common.potion;

import com.mojang.logging.LogUtils;
import io.github.kreestijan.alchemizer.common.effect.EffectAdapter;
import io.github.kreestijan.alchemizer.core.effect.EffectSpec;
import io.github.kreestijan.alchemizer.core.potion.PotionDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import org.slf4j.Logger;

/**
 * Turns a potion as {@code core} describes it into the item a player is handed.
 *
 * <p>This is the outbound half of the seam. The item stays {@code minecraft:potion} — the vanilla
 * one, with vanilla's data component — which is what keeps the potion usable by a brewing stand, by
 * a splash or lingering conversion later, and by anything else that reads a potion. Nothing about
 * it is this mod's own type.
 *
 * <p><b>Why the effects ride on the component rather than on a registered potion.</b> A potion
 * defined by a datapack cannot register a {@code Potion}; registration happens at startup, before
 * any data is read. {@code PotionContents.customEffects} is the field that exists for exactly this
 * case, and a stack carrying it drinks, colours and tooltips the same way a vanilla potion does.
 *
 * <p><b>How the name is decided.</b> The component carries the potion's own id as its {@code
 * custom_name}, and the game looks that up as the translation key {@code
 * item.minecraft.potion.effect.<id>}. The id is the whole name, colon and all, so nothing has to
 * convert between two spellings of it, and a potion shipped by a datapack gets its name from a lang
 * entry that datapack ships.
 *
 * <p><b>An effect that does not resolve is dropped, loudly.</b> The alternative — refusing the
 * whole potion — would turn a missing compatibility mod into a brew that produces nothing at all,
 * and the player would have no way to tell that from a recipe that simply failed.
 */
public final class PotionBuilder {

  private static final Logger LOGGER = LogUtils.getLogger();

  private PotionBuilder() {}

  /**
   * @param potionItem the item to build, normally {@code minecraft:potion}; it is left unchanged
   * @param definition the potion to build from
   * @return one drinkable potion carrying the definition's effects and name
   */
  public static ItemStack build(Item potionItem, PotionDefinition definition) {
    List<MobEffectInstance> effects = new ArrayList<>(definition.effects().size());
    for (EffectSpec spec : definition.effects()) {
      Optional<Holder<MobEffect>> effect = EffectAdapter.resolve(spec.effect());
      if (effect.isEmpty()) {
        LOGGER.warn(
            "Potion {} names effect {}, which no mod registers; the potion is brewed without it",
            definition.potionId(),
            spec.effect());
        continue;
      }
      // The authored duration is passed through untouched. Zero is a legitimate value for an
      // instant effect and is what vanilla's own healing and harming potions use, so there is
      // nothing here to correct.
      effects.add(new MobEffectInstance(effect.get(), spec.durationTicks(), spec.potency()));
    }

    ItemStack stack = new ItemStack(potionItem);
    stack.set(
        DataComponents.POTION_CONTENTS,
        new PotionContents(
            Optional.empty(),
            Optional.empty(),
            List.copyOf(effects),
            Optional.of(definition.potionId())));
    return stack;
  }
}
