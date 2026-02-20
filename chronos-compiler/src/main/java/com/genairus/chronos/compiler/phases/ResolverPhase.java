package com.genairus.chronos.compiler.phases;

import com.genairus.chronos.compiler.ResolverContext;

/**
 * A single step in the compiler pipeline.
 *
 * <p>Each phase receives an input value and a {@link ResolverContext} that
 * provides the shared symbol table and diagnostic sink, and returns an output
 * value for the next phase.
 *
 * @param <I> the type consumed by this phase
 * @param <O> the type produced by this phase
 */
@FunctionalInterface
public interface ResolverPhase<I, O> {

    /**
     * Executes this phase.
     *
     * @param input the output of the previous phase (or the initial input)
     * @param ctx   shared compiler context; diagnostics are reported here
     * @return the result passed to the next phase
     */
    O execute(I input, ResolverContext ctx);
}
