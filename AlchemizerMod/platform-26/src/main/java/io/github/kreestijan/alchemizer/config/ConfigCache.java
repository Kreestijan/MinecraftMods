package io.github.kreestijan.alchemizer.config;

import io.github.kreestijan.alchemizer.core.tuning.Tuning;
import io.github.kreestijan.alchemizer.core.tuning.TuningDefaults;

/**
 * The current {@link Tuning}, so that game code never touches the config file directly.
 *
 * <p>Two things make this worth a class rather than a call. First, a config value throws if it is
 * asked for its contents before the file has loaded, and something in the mod — a block entity
 * being read from a chunk, say — can easily run before that. Holding the shipping defaults until
 * the load event arrives makes that early read return a sane answer instead of an exception.
 * Second, reading the file walks a tree of values and allocates a record; doing that once per
 * reload rather than once per tick is the difference between free and measurable.
 *
 * <p>The field is volatile because it is written on the config event's thread and read from the
 * server thread. The record it points at is immutable, so publishing it is the only concern.
 */
public final class ConfigCache {

  private static volatile Tuning current = TuningDefaults.defaultTuning();

  private ConfigCache() {}

  /**
   * @return the tuning in force right now, never null
   */
  public static Tuning current() {
    return current;
  }

  /**
   * Re-reads the config file into the cache.
   *
   * <p>Called on config load and on reload, and nowhere else. Package-private because the only
   * caller is {@link ConfigEvents}, which exists precisely so that this happens at the right
   * moment.
   */
  static void refresh() {
    current = AlchemizerConfig.read();
  }
}
