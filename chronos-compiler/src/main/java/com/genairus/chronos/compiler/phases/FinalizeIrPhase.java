package com.genairus.chronos.compiler.phases;

import com.genairus.chronos.compiler.ResolverContext;
import com.genairus.chronos.compiler.util.IrRefWalker;
import com.genairus.chronos.core.refs.SymbolRef;
import com.genairus.chronos.ir.model.IrModel;

/**
 * Pass 7: Final integrity check before the compiler declares the IR complete.
 *
 * <p>Uses {@link IrRefWalker} to exhaustively scan <em>every</em> reachable
 * {@link SymbolRef} in the model's object graph via reflection.  Any remaining
 * unresolved reference emits a {@code CHR-012} diagnostic.
 * {@code finalized=true} is set only if no ERROR diagnostics exist after the scan —
 * this includes both errors from prior phases (e.g. {@code CHR-008}) and any
 * newly emitted {@code CHR-012} errors.
 *
 * <p><b>Invariant:</b> {@code finalized == true} implies no ERROR diagnostics
 * and no unresolved {@link SymbolRef} anywhere in the IR object graph.
 *
 * <p><b>Why {@link IrRefWalker} instead of a manual list?</b><br>
 * A manual list silently misses any new {@link SymbolRef} field added to an IR
 * type.  The reflective walker discovers every field automatically, making the
 * invariant self-enforcing as the IR evolves.
 */
public final class FinalizeIrPhase implements ResolverPhase<IrModel, FinalizeIrPhase.Result> {

    /**
     * The output of the Finalize phase: the model paired with a flag indicating
     * whether all compilation invariants were satisfied.
     */
    public record Result(IrModel model, boolean finalized) {}

    @Override
    public Result execute(IrModel model, ResolverContext ctx) {
        for (SymbolRef ref : IrRefWalker.findUnresolvedRefs(model)) {
            String name = ref.name() != null ? ref.name().name() : "<null>";
            ctx.diagnostics().error(
                    "CHR-012",
                    "Unresolved reference '" + name + "' remains after resolution",
                    ref.span());
        }

        return new Result(model, !ctx.diagnostics().hasErrors());
    }
}
