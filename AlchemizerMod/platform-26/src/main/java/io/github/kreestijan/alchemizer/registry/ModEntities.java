package io.github.kreestijan.alchemizer.registry;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.customer.Customer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's entity types — the customer, and nothing else yet.
 *
 * <p><b>Registered through {@code createEntities} rather than through the vanilla registry key.</b>
 * The helper supplies this class with the registry the type belongs in, which is why no {@code
 * EntityType.Builder.build} call appears here: the builder is handed to {@code registerEntityType},
 * which stamps it with the key the registration is under. The older habit of building the type
 * first and registering the result leaves the key to be spelled out a second time, and the two
 * spellings drift.
 *
 * <p><b>{@code MISC}, and none of the four settings is cosmetic.</b> Every other category carries a
 * per-chunk spawn cap, and this mod's whole count control is its own — see {@code CustomerSpawner}
 * — so a customer sitting in a capped category would be a customer the vanilla spawner could
 * refuse. {@code noSave} keeps them out of the world's saved data: a customer's whole life is a
 * walk to a shop that is currently open, and one loaded back from a save would be one standing next
 * to a Counter that may no longer exist. {@code noLootTable} makes "no drops" true by construction
 * rather than by remembering to override a method, which is what the design's clean despawning asks
 * for.
 *
 * <p>The size is a player's, because the model is one.
 */
public final class ModEntities {

  public static final DeferredRegister.Entities ENTITIES =
      DeferredRegister.createEntities(Alchemizer.MOD_ID);

  /**
   * A customer: one arrival at one shop.
   *
   * <p>Named after the class it builds, like every other registration here, so that the id a
   * datapack or a command types is the same word the code uses.
   */
  public static final DeferredHolder<EntityType<?>, EntityType<Customer>> CUSTOMER =
      ENTITIES.registerEntityType(
          "customer",
          Customer::new,
          MobCategory.MISC,
          builder -> builder.sized(0.6F, 1.8F).noSave().noLootTable());

  private ModEntities() {}

  /**
   * Gives the customer its numbers.
   *
   * <p>Attributes are not part of the entity type and cannot be declared beside it: they are
   * supplied once per type, during registry loading, through this event. A type with no entry here
   * has no attributes at all, and the first mob of that type to tick throws.
   *
   * @param event the attribute registry, open for this mod to add to
   */
  public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
    event.put(CUSTOMER.get(), Customer.createAttributes().build());
  }
}
