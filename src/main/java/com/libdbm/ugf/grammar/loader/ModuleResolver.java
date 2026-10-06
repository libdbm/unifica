package com.libdbm.ugf.grammar.loader;

import com.libdbm.ugf.ErrorDetails;
import com.libdbm.ugf.Result;
import com.libdbm.ugf.grammar.Grammar;
import com.libdbm.ugf.grammar.GrammarRule;
import com.libdbm.ugf.grammar.ImportDeclaration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves module imports and combines grammars. A resolver caches loaded modules and keeps a
 * mutable list of search paths, so it is not thread-safe: use one resolver per thread, or confine a
 * shared one to a single thread.
 */
public final class ModuleResolver {

  /** An imported module could not be found. */
  public static final String MISSING = "import.missing";

  /** A selective import named a symbol the module does not export. */
  public static final String UNEXPORTED = "import.unexported";

  private static final Logger LOGGER = LoggerFactory.getLogger(ModuleResolver.class);

  private final Map<String, Cached> cache = new HashMap<>();
  private final Map<String, Resolved> resolvedCache = new HashMap<>();
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

  /** True if every recorded file still has the modification time and size it had when cached. */
  private static boolean fresh(final Map<String, Stamp> stamps) {
    for (final var entry : stamps.entrySet()) {
      try {
        if (!Stamp.of(Path.of(entry.getKey())).equals(entry.getValue())) {
          return false;
        }
      } catch (final IOException exception) {
        return false;
      }
    }
    return true;
  }

  public void addSearchPath(final Path path) {
    if (path != null && !searchPaths.contains(path)) {
      searchPaths.add(path);
    }
  }

  /**
   * Resolve all imports in a grammar that has no source file, and return a combined grammar.
   * Relative imports are resolved against the search paths only.
   */
  public Result<Grammar, ErrorDetails> resolve(final Grammar root) {
    return resolve(root, null);
  }

  /**
   * Resolve all imports in a grammar loaded from {@code directory}, and return a combined grammar.
   * A relative import is resolved against the directory of the file that declares it first, then
   * against the search paths (IMP-1).
   */
  public Result<Grammar, ErrorDetails> resolve(final Grammar root, final Path directory) {
    final var builder = Grammar.builder();
    builder.module(root.module());
    builder.start(root.start());
    root.skips().forEach(builder::skip);
    builder.whitespace(root.whitespace());

    // Keep track of processed file paths to avoid circular dependencies and duplicates
    final var processed = new HashSet<String>();

    final var failure = resolveImports(root, builder, processed, directory, new HashMap<>());
    return failure == null ? Result.success(builder.build()) : Result.failure(failure);
  }

  /**
   * Adds the source's imports and rules to {@code target}. Returns null on success, or the failure.
   */
  private ErrorDetails resolveImports(
      final Grammar source,
      final Grammar.Builder target,
      final Set<String> processed,
      final Path directory,
      final Map<String, Stamp> stamps) {
    // Process each import
    for (final var decl : source.imports()) {
      final var result = load(decl, directory);
      if (result instanceof Result.Failure<LoadedModule, ErrorDetails>(var error)) {
        return error;
      }
      final var loaded = result.orElseThrow();
      final var imported = loaded.grammar;
      final var importedPath = loaded.filePath;
      stamps.put(importedPath, loaded.stamp);

      // Reuse a fully resolved grammar only if none of the files it was built from changed (IMP-4)
      final Grammar resolvedImported;
      final var cachedResolution = resolvedCache.get(importedPath);
      if (cachedResolution != null && fresh(cachedResolution.stamps())) {
        resolvedImported = cachedResolution.grammar();
        stamps.putAll(cachedResolution.stamps());
        LOGGER.debug("Reusing cached resolved grammar from: {}", importedPath);
      } else {
        // Prevent circular dependencies for this resolution path
        if (processed.contains(importedPath)) {
          LOGGER.warn("Circular dependency detected for module: {}", importedPath);
          continue;
        }
        processed.add(importedPath);

        // Fully resolve the imported grammar first
        final var resolvedBuilder = Grammar.builder();
        resolvedBuilder.module(imported.module());
        resolvedBuilder.start(imported.start());
        imported.skips().forEach(resolvedBuilder::skip);
        final var nested = new HashMap<String, Stamp>();
        nested.put(importedPath, loaded.stamp);
        final var failure =
            resolveImports(imported, resolvedBuilder, processed, loaded.file.getParent(), nested);
        if (failure != null) {
          return failure;
        }

        // Cache the fully resolved grammar with the stamps of every file it was built from
        final var resolved = resolvedBuilder.build();
        resolvedCache.put(importedPath, new Resolved(resolved, Map.copyOf(nested)));
        stamps.putAll(nested);

        // Remove from processed set to allow other import paths to use it
        processed.remove(importedPath);

        resolvedImported = resolved;
      }

      // Imported skip declarations apply to the importing grammar too
      resolvedImported.skips().forEach(target::skip);

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
            return ErrorDetails.of(
                UNEXPORTED, "Symbol " + symbol + " not exported from module " + selective.path());
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
    return null;
  }

  private Result<LoadedModule, ErrorDetails> load(
      final ImportDeclaration decl, final Path directory) {
    final Path file;
    final Stamp stamp;
    try {
      file = locateModule(decl.path(), directory);
      if (file == null) {
        return Result.failure(ErrorDetails.of(MISSING, "Cannot find module: " + decl.path()));
      }
      stamp = Stamp.of(file);
    } catch (final IOException exception) {
      return Result.failure(
          ErrorDetails.of(
              UnificationGrammarParserFactory.IO,
              "Cannot read module " + decl.path() + ": " + exception.getMessage()));
    }

    // The real path identifies a module however the import spelled it (IMP-3).
    final var filePath = file.toString();
    final var cached = cache.get(filePath);
    if (cached != null && cached.stamp().equals(stamp)) {
      return Result.success(new LoadedModule(cached.grammar(), filePath, file, stamp));
    }

    LOGGER.debug("Loading module from: {}", file);

    // Parse the grammar without validation (validation happens after all imports are resolved)
    return UnificationGrammarParserFactory.unvalidated(file)
        .map(
            grammar -> {
              cache.put(filePath, new Cached(grammar, stamp));
              return new LoadedModule(grammar, filePath, file, stamp);
            });
  }

  /**
   * Finds an imported module: relative to {@code directory} (the importing file's directory, if
   * any) first, then relative to each search path. Returns the module's real path, or null.
   */
  private Path locateModule(final String path, final Path directory) throws IOException {
    final var names = new ArrayList<String>();
    final var modulePath = path.replace('.', '/') + ".ug";
    // Also try hyphenated version (clean_common -> clean-common)
    final var hyphenated = path.replace('_', '-');
    final var hyphenatedPath = hyphenated.replace('.', '/') + ".ug";
    names.add(path);
    names.add(modulePath);
    names.add(hyphenatedPath);
    if (!path.endsWith(".ug")) {
      names.add(path + ".ug");
      names.add(hyphenated + ".ug");
    }

    final var bases = new ArrayList<Path>();
    if (directory != null) {
      bases.add(directory);
    }
    bases.addAll(searchPaths);

    for (final var base : bases) {
      for (final var name : names) {
        final var candidate = base.resolve(name);
        if (Files.exists(candidate)) {
          return candidate.toRealPath();
        }
      }
    }

    return null;
  }

  public void clearCache() {
    cache.clear();
    resolvedCache.clear();
  }

  /** Record holding a loaded grammar and its resolved file path. */
  private record LoadedModule(Grammar grammar, String filePath, Path file, Stamp stamp) {}

  /** What identifies a version of a file for caching: modification time and size (IMP-4). */
  private record Stamp(FileTime modified, long size) {
    private static Stamp of(final Path file) throws IOException {
      return new Stamp(Files.getLastModifiedTime(file), Files.size(file));
    }
  }

  /** A parsed module and the version of its file. */
  private record Cached(Grammar grammar, Stamp stamp) {}

  /** A fully resolved module and the versions of every file it was built from. */
  private record Resolved(Grammar grammar, Map<String, Stamp> stamps) {}
}
