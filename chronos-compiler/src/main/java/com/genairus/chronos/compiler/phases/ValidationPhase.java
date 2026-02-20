package com.genairus.chronos.compiler.phases;

import com.genairus.chronos.compiler.ResolverContext;
import com.genairus.chronos.ir.model.IrModel;
import com.genairus.chronos.validator.ChronosValidator;

/**
 * Pass 6: Runs all semantic validation rules (CHR-001 through CHR-034,
 * CHR-W001) via {@link ChronosValidator} and merges any issues into the
 * shared {@link com.genairus.chronos.core.diagnostics.DiagnosticCollector}.
 *
 * <p>The model is passed through unchanged.
 */
public final class ValidationPhase implements ResolverPhase<IrModel, IrModel> {

    private final ChronosValidator validator;

    public ValidationPhase() {
        this.validator = new ChronosValidator();
    }

    @Override
    public IrModel execute(IrModel model, ResolverContext ctx) {
        var result = validator.validate(model);
        result.diagnostics().forEach(ctx.diagnostics()::add);
        return model;
    }
}
