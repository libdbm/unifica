package com.libdbm.ugf.grammar;

import java.util.*;

/**
 * Represents the import dependency graph of a grammar module system. Useful for visualizing
 * dependencies and detecting circular imports.
 */
public final class ImportGraph {

    private final Map<String, Set<String>> adjacency = new HashMap<>();
    private final Map<String, Grammar> grammars = new HashMap<>();

    /**
     * Add a grammar and its imports to the graph.
     *
     * @param moduleName The module name (or file path)
     * @param grammar    The grammar
     */
    public void add(final String moduleName, final Grammar grammar) {
        grammars.put(moduleName, grammar);
        adjacency.putIfAbsent(moduleName, new HashSet<>());

        for (final var imp : grammar.imports()) {
            final var target = imp.path();
            adjacency.get(moduleName).add(target);
            adjacency.putIfAbsent(target, new HashSet<>());
        }
    }

    /**
     * Get all modules in the graph.
     */
    public Set<String> modules() {
        return Collections.unmodifiableSet(adjacency.keySet());
    }

    /**
     * Get direct dependencies of a module.
     */
    public Set<String> dependencies(final String module) {
        return Collections.unmodifiableSet(adjacency.getOrDefault(module, Set.of()));
    }

    /**
     * Detect circular dependencies using DFS.
     *
     * @return List of cycles, each cycle represented as a list of module names
     */
    public List<List<String>> cycles() {
        final var cycles = new ArrayList<List<String>>();
        final var visited = new HashSet<String>();
        final var stack = new HashSet<String>();
        final var path = new ArrayDeque<String>();

        for (final var module : adjacency.keySet()) {
            if (!visited.contains(module)) {
                detectCycles(module, visited, stack, path, cycles);
            }
        }

        return cycles;
    }

    private void detectCycles(
            final String module,
            final Set<String> visited,
            final Set<String> stack,
            final Deque<String> path,
            final List<List<String>> cycles) {
        visited.add(module);
        stack.add(module);
        path.addLast(module);

        for (final var neighbor : adjacency.getOrDefault(module, Set.of())) {
            if (!visited.contains(neighbor)) {
                detectCycles(neighbor, visited, stack, path, cycles);
            } else if (stack.contains(neighbor)) {
                // Found a cycle - extract it from path
                final var cycle = new ArrayList<String>();
                var found = false;
                for (final var node : path) {
                    if (node.equals(neighbor)) {
                        found = true;
                    }
                    if (found) {
                        cycle.add(node);
                    }
                }
                cycle.add(neighbor); // Close the cycle
                cycles.add(cycle);
            }
        }

        path.removeLast();
        stack.remove(module);
    }

    /**
     * Get topological sort of modules (dependencies before dependents). Returns empty if there are
     * cycles.
     *
     * <p>In this graph, A -> B means "A imports B", so we want B before A.
     */
    public Optional<List<String>> sort() {
        final var outbound = new HashMap<String, Integer>();
        final var queue = new LinkedList<String>();
        final var result = new ArrayList<String>();

        // Initialize out-degrees for all modules
        for (final var module : adjacency.keySet()) {
            outbound.put(module, adjacency.get(module).size());
        }

        // Start with modules that import nothing (out-degree 0) - these are leaves
        for (final var entry : outbound.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        // Process nodes
        while (!queue.isEmpty()) {
            final var current = queue.poll();
            result.add(current);

            // Find all modules that import current
            for (final var entry : adjacency.entrySet()) {
                if (entry.getValue().contains(current)) {
                    final var importer = entry.getKey();
                    final var degree = outbound.get(importer) - 1;
                    outbound.put(importer, degree);
                    if (degree == 0) {
                        queue.add(importer);
                    }
                }
            }
        }

        // If result doesn't contain all nodes, there's a cycle
        if (result.size() != adjacency.size()) {
            return Optional.empty();
        }

        return Optional.of(result);
    }

    /**
     * Format the graph as a DOT file for Graphviz visualization.
     */
    public String toDot() {
        final var builder = new StringBuilder();
        builder.append("digraph ImportGraph {\n");
        builder.append("  rankdir=LR;\n");
        builder.append("  node [shape=box];\n\n");

        for (final var entry : adjacency.entrySet()) {
            final var module = entry.getKey();
            final var cleanName = cleanModuleName(module);

            // Add node with attributes
            final var grammar = grammars.get(module);
            if (grammar != null && grammar.module() != null) {
                builder.append(
                        String.format(
                                "  \"%s\" [label=\"%s\\n(%s exports)\"];\n",
                                cleanName, cleanName, grammar.module().exports().size()));
            } else {
                builder.append(String.format("  \"%s\";\n", cleanName));
            }

            // Add edges
            for (final var dep : entry.getValue()) {
                final var cleanDep = cleanModuleName(dep);
                builder.append(String.format("  \"%s\" -> \"%s\";\n", cleanName, cleanDep));
            }
        }

        builder.append("}\n");
        return builder.toString();
    }

    /**
     * Format the graph as ASCII art (simple tree view).
     */
    public String toAscii() {
        final var builder = new StringBuilder();
        builder.append("Import Graph\n");
        builder.append("============\n\n");

        final var visited = new HashSet<String>();

        for (final var module : adjacency.keySet()) {
            if (!visited.contains(module)) {
                printTree(module, "", visited, builder);
            }
        }

        return builder.toString();
    }

    private void printTree(
            final String module,
            final String prefix,
            final Set<String> visited,
            final StringBuilder builder) {
        if (visited.contains(module)) {
            builder.append(prefix).append(cleanModuleName(module)).append(" [CYCLE]\n");
            return;
        }

        visited.add(module);
        builder.append(prefix).append(cleanModuleName(module));

        final var grammar = grammars.get(module);
        if (grammar != null && grammar.module() != null) {
            builder.append(" (exports: ");
            builder.append(String.join(", ", grammar.module().exports()));
            builder.append(")");
        }
        builder.append("\n");

        final var deps = adjacency.getOrDefault(module, Set.of());
        final var sortedDeps = new ArrayList<>(deps);
        Collections.sort(sortedDeps);

        for (int i = 0; i < sortedDeps.size(); i++) {
            final var isLast = (i == sortedDeps.size() - 1);
            final var childPrefix = prefix + (isLast ? "  └─ " : "  ├─ ");
            final var grandchildPrefix = prefix + (isLast ? "     " : "  │  ");

            final var childVisited = new HashSet<>(visited);
            printChild(sortedDeps.get(i), childPrefix, grandchildPrefix, childVisited, builder);
        }
    }

    private void printChild(
            final String module,
            final String prefix,
            final String childPrefix,
            final Set<String> visited,
            final StringBuilder builder) {
        if (visited.contains(module)) {
            builder.append(prefix).append(cleanModuleName(module)).append(" [CYCLE]\n");
            return;
        }

        visited.add(module);
        builder.append(prefix).append(cleanModuleName(module));

        final var grammar = grammars.get(module);
        if (grammar != null && grammar.module() != null && !grammar.module().exports().isEmpty()) {
            builder.append(" (").append(grammar.module().exports().size()).append(" exports)");
        }
        builder.append("\n");

        final var deps = adjacency.getOrDefault(module, Set.of());
        final var sortedDeps = new ArrayList<>(deps);
        Collections.sort(sortedDeps);

        for (int i = 0; i < sortedDeps.size(); i++) {
            final var isLast = (i == sortedDeps.size() - 1);
            final var newPrefix = childPrefix + (isLast ? "└─ " : "├─ ");
            final var newChildPrefix = childPrefix + (isLast ? "   " : "│  ");

            final var childVisited = new HashSet<>(visited);
            printChild(sortedDeps.get(i), newPrefix, newChildPrefix, childVisited, builder);
        }
    }

    private String cleanModuleName(final String path) {
        // Extract just the filename without .ug extension
        final var name = path.replaceAll(".*/", "").replaceAll("\\.ug$", "");
        return name.isEmpty() ? path : name;
    }

    /**
     * Get statistics about the import graph.
     */
    public GraphStatistics statistics() {
        final var totalModules = adjacency.size();
        final var totalImports = adjacency.values().stream().mapToInt(Set::size).sum();

        final var cycles = cycles();
        final var cycleCount = cycles.size();

        var maxFanOut = 0;
        String maxFanOutModule = null;
        var maxFanIn = 0;
        String maxFanInModule = null;

        final var fanIn = new HashMap<String, Integer>();
        for (final var module : adjacency.keySet()) {
            fanIn.put(module, 0);
        }

        for (final var entry : adjacency.entrySet()) {
            final var fanOut = entry.getValue().size();
            if (fanOut > maxFanOut) {
                maxFanOut = fanOut;
                maxFanOutModule = entry.getKey();
            }

            for (final var dep : entry.getValue()) {
                fanIn.put(dep, fanIn.getOrDefault(dep, 0) + 1);
            }
        }

        for (final var entry : fanIn.entrySet()) {
            if (entry.getValue() > maxFanIn) {
                maxFanIn = entry.getValue();
                maxFanInModule = entry.getKey();
            }
        }

        return new GraphStatistics(
                totalModules,
                totalImports,
                cycleCount,
                cycles,
                maxFanOut,
                maxFanOutModule,
                maxFanIn,
                maxFanInModule);
    }

    /**
     * Statistics about the import graph.
     */
    public record GraphStatistics(
            int totalModules,
            int totalImports,
            int cycleCount,
            List<List<String>> cycles,
            int maxFanOut,
            String maxFanOutModule,
            int maxFanIn,
            String maxFanInModule) {
        public String format() {
            final var builder = new StringBuilder();
            builder.append("Import Graph Statistics\n");
            builder.append("=======================\n\n");
            builder.append(String.format("Total modules: %d\n", totalModules));
            builder.append(String.format("Total imports: %d\n", totalImports));
            builder.append(String.format("Circular dependencies: %d\n", cycleCount));

            if (cycleCount > 0) {
                builder.append("\nCycles detected:\n");
                for (final var cycle : cycles) {
                    builder.append("  - ").append(String.join(" -> ", cycle)).append("\n");
                }
            }

            if (maxFanOutModule != null) {
                builder.append(
                        String.format("\nMax imports (fan-out): %d in '%s'\n", maxFanOut, maxFanOutModule));
            }

            if (maxFanInModule != null) {
                builder.append(
                        String.format("Max dependents (fan-in): %d for '%s'\n", maxFanIn, maxFanInModule));
            }

            return builder.toString();
        }
    }
}
