package com.genairus.chronos.generators;

import com.genairus.chronos.ir.model.IrModel;

/**
 * Generates one or more output files from a fully-compiled {@link IrModel}.
 *
 * <p>Implementations are stateless and safe to call multiple times with different
 * models. Each call to {@link #generate} returns a fresh {@link GeneratorOutput}
 * containing the relative file path(s) and their text content.
 */
public interface ChronosGenerator {

    /**
     * Generates output artifacts for the given model.
     *
     * @param model the compiled (and validated) Chronos IR model
     * @return a {@link GeneratorOutput} holding relative-path → content pairs
     */
    GeneratorOutput generate(IrModel model);
}
