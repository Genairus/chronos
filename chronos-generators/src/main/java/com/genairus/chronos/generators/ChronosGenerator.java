package com.genairus.chronos.generators;

import com.genairus.chronos.model.ChronosModel;

/**
 * Generates one or more output files from a {@link ChronosModel}.
 *
 * <p>Implementations are stateless and safe to call multiple times with different
 * models.  Each call to {@link #generate} returns a fresh {@link GeneratorOutput}
 * containing the relative file path(s) and their text content.
 */
public interface ChronosGenerator {

    /**
     * Generates output artifacts for the given model.
     *
     * @param model the parsed (and optionally validated) Chronos model
     * @return a {@link GeneratorOutput} holding relative-path → content pairs
     */
    GeneratorOutput generate(ChronosModel model);
}
