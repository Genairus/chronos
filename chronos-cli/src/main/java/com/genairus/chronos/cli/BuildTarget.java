package com.genairus.chronos.cli;

import java.util.List;

/**
 * One entry in the {@code targets} array of {@code chronos-build.json}.
 *
 * @param name      human-readable label used in summary output
 * @param generator generator key passed to {@link com.genairus.chronos.generators.GeneratorRegistry}
 * @param output    relative path to the output directory (relative to the config file)
 * @param include   shape-filter patterns to include (empty = include all)
 * @param exclude   shape-filter patterns to exclude after inclusion
 */
public record BuildTarget(
        String name,
        String generator,
        String output,
        List<String> include,
        List<String> exclude) {

    public BuildTarget {
        include = include != null ? List.copyOf(include) : List.of();
        exclude = exclude != null ? List.copyOf(exclude) : List.of();
    }
}
