package com.libdbm.ugf.parser;

import com.libdbm.ugf.grammar.Grammar;
import com.libdbm.ugf.grammar.GrammarRule;
import com.libdbm.ugf.grammar.ImportDeclaration;
import com.libdbm.ugf.grammar.loader.UnificationGrammarParserFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Resolves module imports and combines grammars. */
public final class ModuleResolver {
  private static final Logger logger = LoggerFactory.getLogger(ModuleResolver.class);

  private final Map<String, Grammar> cache = new HashMap<>();
  private final Map<String, Grammar> resolvedCache = new HashMap<>();
  private final List<Path> searchPaths = new ArrayList<>();

  public ModuleResolver() {
    // Add default search path (current directory)
    searchPaths.add(Path.of("."));
  }

  public ModuleResolver(final List<Path> paths) {
    if (paths == null || paths.isEmpty()) {
      searchPaths.add(Path.of("."));
      return;
    }
    for (final var path : paths) {
      if (path != null) {
        searchPaths.add(path);
      }
    }
  }

  public void addSearchPath(final Path path) {
    if (path != null && !searchPaths.contains(path)) {
      searchPaths.add(path);
    }
  }

  /** Resolve all imports in a grammar and return a combined grammar. */
  public Grammar resolve(final Grammar root) throws IOException {
    final var builder = Grammar.builder();
    builder.module(root.module());
    builder.start(root.start());

    // Keep track of processed file paths to avoid circular dependencies and duplicates
    final var processed = new HashSet<String>();

    // Process imports recursively (root has no file path)
    resolveImports(root, builder, processed, null);

    return builder.build();
  }

  private void resolveImports(
      final Grammar source,
      final Grammar.Builder target,
      final Set<String> processed,
      final String sourceFilePath)
      throws IOException {
    // Process each import
    for (final var decl : source.imports()) {
      final var loaded = load(decl);
      final var imported = loaded.grammar;
      final var importedPath = loaded.filePath;

      // Check if we've already fully resolved this grammar
      final Grammar resolvedImported;
      if (resolvedCache.containsKey(importedPath)) {
        resolvedImported = resolvedCache.get(importedPath);
        logger.debug("Reusing cached resolved grammar from: {}", importedPath);
      } else {
        // Prevent circular dependencies for this resolution path
        if (processed.contains(importedPath)) {
          logger.warn("Circular dependency detected for module: {}", importedPath);
          continue;
        }
        processed.add(importedPath);

        // Fully resolve the imported grammar first
        final var resolvedBuilder = Grammar.builder();
        resolvedBuilder.module(imported.module());
        resolvedBuilder.start(imported.start());
        resolveImports(imported, resolvedBuilder, processed, importedPath);

        // Cache the fully resolved grammar
        final var resolved = resolvedBuilder.build();
        resolvedCache.put(importedPath, resolved);

        // Remove from processed set to allow other import paths to use it
        processed.remove(importedPath);

        resolvedImported = resolved;
      }

      // Add rules based on import type
      if (decl instanceof ImportDeclaration.All all) {
        // Import all exported rules
        for (final Map.Entry<String, List<GrammarRule>> entry :
            resolvedImported.rules().entrySet()) {
          if (resolvedImported.module().isExported(entry.getKey())) {
            for (final var rule : entry.getValue()) {
              target.add(rule);
            }
          }
        }
      } else if (decl instanceof ImportDeclaration.Selective selective) {
        // Import only specified symbols
        for (final var symbol : selective.symbols()) {
          if (resolvedImported.module().isExported(symbol)) {
            for (final var rule : resolvedImported.rulesFor(symbol)) {
              target.add(rule);
            }
          } else {
            logger.warn("Symbol {} not exported from module {}", symbol, selective.path());
          }
        }
      } else if (decl instanceof ImportDeclaration.File file) {
        // Import all rules from file
        for (final var rules : resolvedImported.rules().values()) {
          for (final var rule : rules) {
            target.add(rule);
          }
        }
      }
    }

    // Add rules from source grammar
    for (final var rules : source.rules().values()) {
      for (final var rule : rules) {
        target.add(rule);
      }
    }
  }

  private LoadedModule load(final ImportDeclaration decl) throws IOException {
    final var file = locateModule(decl.path());

    if (file == null) {
      throw new IOException("Cannot find module: " + decl.path());
    }

    final var filePath = file.toString();
    if (cache.containsKey(filePath)) {
      return new LoadedModule(cache.get(filePath), filePath);
    }

    logger.debug("Loading module from: {}", file);

    // Parse the grammar without validation (validation happens after all imports are resolved)
    final var grammar = UnificationGrammarParserFactory.unvalidated(file);

    cache.put(filePath, grammar);

    return new LoadedModule(grammar, filePath);
  }

  private Path locateModule(final String path) {
    final var candidates = new ArrayList<Path>();
    final var modulePath = path.replace('.', '/') + ".ug";
    // Also try hyphenated version (clean_common -> clean-common)
    final var hyphenated = path.replace('_', '-');
    final var hyphenatedPath = hyphenated.replace('.', '/') + ".ug";

    candidates.add(Paths.get(path));
    candidates.add(Paths.get(modulePath));
    candidates.add(Paths.get(hyphenatedPath));

    if (!path.endsWith(".ug")) {
      candidates.add(Paths.get(path + ".ug"));
      candidates.add(Paths.get(hyphenated + ".ug"));
    }

    for (final var base : searchPaths) {
      candidates.add(base.resolve(path));
      candidates.add(base.resolve(modulePath));
      candidates.add(base.resolve(hyphenatedPath));
      if (!path.endsWith(".ug")) {
        candidates.add(base.resolve(path + ".ug"));
        candidates.add(base.resolve(hyphenated + ".ug"));
      }
    }

    for (final var candidate : candidates) {
      if (candidate != null && Files.exists(candidate)) {
        return candidate.normalize().toAbsolutePath();
      }
    }

    return null;
  }

  public void clearCache() {
    cache.clear();
    resolvedCache.clear();
  }

  /** Record holding a loaded grammar and its resolved file path. */
  private record LoadedModule(Grammar grammar, String filePath) {}
}
