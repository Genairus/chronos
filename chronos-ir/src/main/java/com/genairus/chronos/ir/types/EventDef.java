package com.genairus.chronos.ir.types;

import com.genairus.chronos.core.refs.Span;

import java.util.List;

/**
 * IR representation of a top-level {@code event} declaration.
 *
 * <p>Events describe typed telemetry signals emitted by journey steps.
 * Payload fields use the same {@link FieldDef} structure as {@code entity}
 * and {@code shape} declarations. Zero-field events are permitted.
 *
 * <pre>
 *   event CartReviewed {
 *       cartId: String
 *       itemCount: Integer
 *   }
 * </pre>
 */
public record EventDef(
        String name,
        List<TraitApplication> traits,
        List<String> docComments,
        List<FieldDef> fields,
        Span span
) implements IrShape {}
