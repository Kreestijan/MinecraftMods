package io.github.kreestijan.alchemizer.client.jei;

import io.github.kreestijan.alchemizer.Alchemizer;
import io.github.kreestijan.alchemizer.client.screen.PressureChamberScreen;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;

/**
 * Tells JEI to keep the brewing screen's recipe book — its button, and its list while that is open
 * — clear of the ingredient list.
 *
 * <p><b>What this is not.</b> There is no recipe category here, no catalyst and no transfer
 * handler, so JEI learns nothing about brewing from this class and reveals nothing. The mod's
 * recipes are meant to be discovered by brewing them, and a plugin that listed them would be the
 * discovery feature undone from the side. All this does is the one thing JEI cannot work out for
 * itself: that a screen has drawn things outside its own panel.
 *
 * <p><b>What an exclusion area actually costs.</b> JEI's ingredient list is laid out in the strip
 * to the right of the panel, and it does not move that list out of the way of an area — it works
 * out its slots and marks the individual ones that overlap, then reflows the ingredients into the
 * slots that are left. So an exclusion area costs the icons that would have been drawn over it, not
 * a column of the player's screen, which is what makes it worth asking for the button as well as
 * the list. On a narrow enough strip — GUI scale 4 is where it shows — JEI's grid reaches the
 * button otherwise, and an icon drawn across a control is worse than a page a couple of icons
 * short.
 *
 * <p><b>Why it is asked for rather than computed here.</b> The rectangles are the screen's own
 * layout — where the button and the list are, and how wide this player's book measured — so the
 * answer belongs to the screen. This plugin is the adapter between that answer and JEI's own
 * vocabulary, nothing more.
 *
 * <p><b>Why the set changes as little as it does.</b> JEI asks for the exclusion areas once per
 * frame and re-lays out only when they change — {@code GuiEventHandler.updateForScreenRender} runs
 * from the render event and hands the set to {@code GuiPropertiesCache.Updater}, which compares it
 * with the previous set and notifies only on a difference. So the list's rectangle, which exists
 * only while the dropdown is open, costs one re-layout as it opens and one as it closes; the
 * button's is there whenever the button is, which is a set that changes when the Funnel is built or
 * lost and not otherwise.
 *
 * <p><b>Why this is in a package of its own.</b> Nothing else in the mod names a JEI type, and the
 * dependency is {@code compileOnly}: the class is only ever loaded by JEI's own scan for {@link
 * JeiPlugin}, so with JEI absent nothing refers to it and the mod runs unchanged. Keeping it here
 * is what makes that checkable by reading one directory rather than every import in the mod.
 *
 * <p>Without {@code @OnlyIn(Dist.CLIENT)}, like the screens: the class is never loaded on a server,
 * and the annotation would be a comment on a file whose client-only nature is already written in
 * its imports.
 */
@JeiPlugin
public class AlchemizerJeiPlugin implements IModPlugin {

  @Override
  public Identifier getPluginUid() {
    return Identifier.fromNamespaceAndPath(Alchemizer.MOD_ID, "jei");
  }

  @Override
  public void registerGuiHandlers(IGuiHandlerRegistration registration) {
    registration.addGuiContainerHandler(PressureChamberScreen.class, new BookColumn());
  }

  /**
   * Reports the book's areas, or nothing, as the screen sees them.
   *
   * <p>The coordinates are the screen's, not the panel's. JEI's own helpers build every other extra
   * area from the screen's left and top, so a rectangle given in panel coordinates would be drawn
   * beside the wrong screen edge the moment a player moved the window. The screen's accessor does
   * that translation for the same reason: this class is not the place where panel offsets are
   * known.
   *
   * <p>An empty list is a real answer — it means the ingredient list may use the whole width — and
   * is what is returned by a brewing screen whose still has no Funnel, which is the only screen
   * that draws no book at all.
   *
   * <p>{@code getLeftPos}/{@code getTopPos} rather than {@code getGuiLeft}/{@code getGuiTop}: the
   * pair of {@code getGui*} accessors is deprecated for removal in this version and delegates to
   * these.
   */
  private static final class BookColumn implements IGuiContainerHandler<PressureChamberScreen> {

    @Override
    public List<Rect2i> getGuiExtraAreas(PressureChamberScreen screen) {
      return screen.guiExtraAreas();
    }
  }
}
