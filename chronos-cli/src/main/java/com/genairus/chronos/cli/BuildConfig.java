package com.genairus.chronos.cli;

import java.util.List;
import java.util.Map;

/**
 * In-memory representation of {@code chronos-build.json}.
 *
 * @param sources     glob patterns (relative to the config file) for source {@code .chronos} files
 * @param targets     list of generation targets
 * @param credentials key→value map; values may contain {@code ${VAR}} placeholders
 */
public record BuildConfig(
        List<String> sources,
        List<BuildTarget> targets,
        Map<String, String> credentials) {

    public BuildConfig {
        sources     = sources     != null ? List.copyOf(sources)     : List.of();
        targets     = targets     != null ? List.copyOf(targets)     : List.of();
        credentials = credentials != null ? Map.copyOf(credentials)  : Map.of();
    }
}
