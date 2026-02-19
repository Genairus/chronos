package com.genairus.chronos.generators;

import java.util.Map;

/**
 * The result of a {@link ChronosGenerator} run: a map of relative output-file
 * paths to their text content.
 *
 * <p>Callers may write each entry to disk, pass them to a test assertion, or
 * forward them to a downstream step — no filesystem access is required to
 * inspect the output.
 *
 * @param files relative path → file content (UTF-8 text)
 */
public record GeneratorOutput(Map<String, String> files) {

    /**
     * Convenience factory for generators that produce exactly one output file.
     *
     * @param filename relative file path (e.g. {@code "com-example-prd.md"})
     * @param content  the complete text content of the file
     */
    public static GeneratorOutput of(String filename, String content) {
        return new GeneratorOutput(Map.of(filename, content));
    }

    /**
     * Convenience accessor for generators that produce exactly one output file.
     * Returns the content of the single file in the map.
     *
     * @return the content of the single file
     * @throws IllegalStateException if the output contains zero or multiple files
     */
    public String content() {
        if (files.size() != 1) {
            throw new IllegalStateException(
                    "content() can only be called on single-file output; found " + files.size() + " files");
        }
        return files.values().iterator().next();
    }
}
