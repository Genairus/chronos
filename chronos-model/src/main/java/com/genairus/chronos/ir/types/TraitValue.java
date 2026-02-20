package com.genairus.chronos.ir.types;

/**
 * A scalar value carried by a trait argument in the IR type system.
 */
public sealed interface TraitValue
        permits TraitValue.StringValue,
                TraitValue.NumberValue,
                TraitValue.BoolValue,
                TraitValue.ReferenceValue {

    record StringValue(String value)    implements TraitValue {}
    record NumberValue(double value)    implements TraitValue {}
    record BoolValue(boolean value)     implements TraitValue {}
    record ReferenceValue(String ref)   implements TraitValue {}
}
