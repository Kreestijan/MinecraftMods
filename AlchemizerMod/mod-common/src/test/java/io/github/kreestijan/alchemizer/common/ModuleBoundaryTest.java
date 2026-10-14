package io.github.kreestijan.alchemizer.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Enforces the rule that this module never names a client-only type.
 *
 * <p>The rule matters because {@code mod-common} is the seam between the pure logic in {@code core}
 * and the game: it is compiled into the mod, and one stray import of a client class would make the
 * mod fail to load on a dedicated server — a failure that no amount of server testing would catch,
 * because the offending line would only run in a client. A convention would not hold; this does.
 *
 * <p>It reads the compiled classes rather than the sources. A source scan would have to understand
 * imports, fully-qualified names, static imports and wildcards, and would still miss a type that
 * arrived through an inferred {@code var}. The class file cannot lie about what it references.
 *
 * <p>Every constant-pool string is checked, not only class entries. A method that takes a client
 * type names it in a descriptor, and a descriptor is a plain UTF-8 entry with no class entry of its
 * own — checking only class entries would miss exactly the case this test exists for. String
 * literals are therefore checked too, which is a deliberate over-reach: a literal spelling out a
 * client package has no business in this module either.
 */
class ModuleBoundaryTest {

  /** Where Gradle leaves this module's compiled main sources; the test task's working directory. */
  private static final Path CLASSES = Path.of("build", "classes", "java", "main");

  /**
   * Package prefixes that must not appear anywhere in this module.
   *
   * <p>Only the client halves are listed. The mod does use server-side Minecraft types — that is
   * what this module is for — so a blanket ban on {@code net/minecraft} would be wrong.
   */
  private static final List<String> BANNED_PREFIXES =
      List.of("net/minecraft/client/", "net/neoforged/neoforge/client/");

  @Test
  void containsNoClientTypes() throws IOException {
    assertTrue(
        Files.isDirectory(CLASSES),
        "no compiled classes at "
            + CLASSES.toAbsolutePath()
            + " — this test inspects bytecode, so it must run after compilation");

    List<Path> classFiles;
    try (Stream<Path> walk = Files.walk(CLASSES)) {
      classFiles = walk.filter(path -> path.toString().endsWith(".class")).toList();
    }

    // A walk that found nothing would let the test pass by vacuity, which is the one failure mode a
    // boundary check must not have.
    assertFalse(classFiles.isEmpty(), "found no .class files under " + CLASSES.toAbsolutePath());

    List<String> violations = new ArrayList<>();
    for (Path classFile : classFiles) {
      violations.addAll(violationsIn(classFile));
    }

    if (!violations.isEmpty()) {
      fail(
          "mod-common must not reference client-only types, but found:\n  "
              + String.join("\n  ", violations));
    }
  }

  /**
   * @param classFile one compiled class
   * @return a description of each banned reference it makes, or an empty list
   */
  private static List<String> violationsIn(Path classFile) {
    // A single reference appears three times over — once as a class entry, once as the plain name,
    // and once inside a method descriptor — so the raw scan reports it three times. Deduplicating
    // keeps a real violation readable instead of burying it in its own echoes.
    Set<String> texts = new LinkedHashSet<>();
    try {
      var model = ClassFile.of().parse(classFile);
      for (PoolEntry entry : model.constantPool()) {
        String text =
            switch (entry) {
              case ClassEntry classEntry -> classEntry.asInternalName();
              case Utf8Entry utf8Entry -> utf8Entry.stringValue();
              default -> null;
            };
        if (text == null) {
          continue;
        }
        for (String banned : BANNED_PREFIXES) {
          // Descriptors wrap a type in L...; and array types add [ before it, so a containment test
          // is what catches all three spellings from one rule.
          if (text.contains(banned)) {
            texts.add(text);
            break;
          }
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("could not read " + classFile, e);
    }
    return texts.stream()
        .map(text -> classFile.getFileName() + " references " + text)
        .collect(Collectors.toList());
  }
}
