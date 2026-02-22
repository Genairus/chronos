package com.genairus.chronos.ir.json;

import com.genairus.chronos.core.refs.Span;
import com.genairus.chronos.core.refs.SymbolRef;
import com.genairus.chronos.ir.model.IrModel;
import com.genairus.chronos.ir.types.ActorDef;
import com.genairus.chronos.ir.types.Cardinality;
import com.genairus.chronos.ir.types.DataField;
import com.genairus.chronos.ir.types.DenyDef;
import com.genairus.chronos.ir.types.EntityDef;
import com.genairus.chronos.ir.types.EntityInvariant;
import com.genairus.chronos.ir.types.EnumDef;
import com.genairus.chronos.ir.types.EnumMember;
import com.genairus.chronos.ir.types.ErrorDef;
import com.genairus.chronos.ir.types.FieldDef;
import com.genairus.chronos.ir.types.InvariantDef;
import com.genairus.chronos.ir.types.IrShape;
import com.genairus.chronos.ir.types.JourneyDef;
import com.genairus.chronos.ir.types.JourneyOutcomes;
import com.genairus.chronos.ir.types.ListDef;
import com.genairus.chronos.ir.types.MapDef;
import com.genairus.chronos.ir.types.OutcomeExpr;
import com.genairus.chronos.ir.types.PolicyDef;
import com.genairus.chronos.ir.types.RelationshipDef;
import com.genairus.chronos.ir.types.EventDef;
import com.genairus.chronos.ir.types.RoleDef;
import com.genairus.chronos.ir.types.ShapeStructDef;
import com.genairus.chronos.ir.types.StateMachineDef;
import com.genairus.chronos.ir.types.Step;
import com.genairus.chronos.ir.types.StepField;
import com.genairus.chronos.ir.types.TraitApplication;
import com.genairus.chronos.ir.types.TraitArg;
import com.genairus.chronos.ir.types.TraitValue;
import com.genairus.chronos.ir.types.Transition;
import com.genairus.chronos.ir.types.TypeRef;
import com.genairus.chronos.ir.types.UseDecl;
import com.genairus.chronos.ir.types.Variant;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Zero-dependency, hand-coded JSON serialiser for {@link IrModel}.
 *
 * <p>Produces deterministic, pretty-printed JSON without any reflection or
 * Jackson-specific configuration. Sealed-interface {@code switch} expressions
 * provide compile-time exhaustiveness guarantees: adding a new IR variant
 * without updating this class results in a compile error.
 *
 * <h2>Format contract</h2>
 * <ul>
 *   <li>2-space indentation</li>
 *   <li>Object properties in alphabetical order; discriminator property ({@code kind}
 *       or {@code typeKind}) always written first for polymorphic sealed types.</li>
 *   <li>Map entries sorted by key</li>
 *   <li>{@code Optional.empty()} serialised as JSON {@code null}</li>
 *   <li>Whole-number {@code double} values written as integers (e.g. {@code 500.0 → 500})</li>
 *   <li>Empty arrays: {@code [ ]};  empty objects: {@code {}}</li>
 * </ul>
 *
 * <h2>Discriminators</h2>
 * <ul>
 *   <li>{@link IrShape} — {@code "kind"} (entity, actor, journey, shape, enum, list, map,
 *       policy, relationship, invariant, deny, error, statemachine)</li>
 *   <li>{@link TypeRef}  — {@code "typeKind"} (primitive, list, map, named)</li>
 *   <li>{@link TraitValue}, {@link OutcomeExpr}, {@link StepField} — {@code "kind"}</li>
 * </ul>
 */
public final class IrModelSerializer {

    private IrModelSerializer() {}

    /** Serialises {@code model} to a pretty-printed, deterministic JSON string. */
    public static String toJson(IrModel model) {
        JWriter w = new JWriter();
        writeModel(w, model);
        return w.toString();
    }

    // ── JWriter ───────────────────────────────────────────────────────────────────

    /**
     * Stateful, stack-based JSON writer.
     *
     * <p>Two invariants hold at all times:
     * <ol>
     *   <li>Every {@code beginObj}/{@code beginArr} call must be paired with the
     *       matching {@code endObj}/{@code endArr}.</li>
     *   <li>{@code key()} must only be called when the current container is an object;
     *       {@code item()} must only be called inside an array.</li>
     * </ol>
     */
    static final class JWriter {

        private final StringBuilder sb = new StringBuilder();
        /** {@code true} = no element written yet at this container level. */
        private final Deque<Boolean> firstStack = new ArrayDeque<>();
        /** Tracks object-nesting depth for indentation (arrays do not increment depth). */
        private int depth = 0;

        // ── Container boundaries ─────────────────────────────────────────────────

        void beginObj() {
            sb.append('{');
            firstStack.push(true);
            depth++;
        }

        void endObj() {
            boolean wasFirst = firstStack.pop();
            depth--;
            if (!wasFirst) {
                sb.append('\n');
                appendIndent(depth);
            }
            sb.append('}');
        }

        void beginArr() {
            sb.append('[');
            firstStack.push(true);
            // Arrays do not increment depth — FixedSpaceIndenter behaviour.
        }

        void endArr() {
            firstStack.pop();
            // Always write trailing space before ] (produces "[ ]" for empty arrays,
            // matching Jackson's FixedSpaceIndenter behaviour observed in golden files).
            sb.append(' ');
            sb.append(']');
        }

        // ── Separators ───────────────────────────────────────────────────────────

        /**
         * Writes the field-name prefix for an object field:
         * {@code ,\n<indent>"key" : } (comma omitted before first field).
         */
        void key(String k) {
            boolean first = firstStack.pop();
            firstStack.push(false);
            if (!first) sb.append(',');
            sb.append('\n');
            appendIndent(depth);
            sb.append('"');
            appendEscaped(sb, k);
            sb.append("\" : ");
        }

        /**
         * Writes the element prefix for an array element:
         * {@code , } (comma omitted before first element).
         */
        void item() {
            boolean first = firstStack.pop();
            firstStack.push(false);
            if (!first) sb.append(',');
            sb.append(' ');
        }

        // ── Primitive value writers ──────────────────────────────────────────────

        void strVal(String v) {
            sb.append('"');
            appendEscaped(sb, v);
            sb.append('"');
        }

        void nullVal() {
            sb.append("null");
        }

        void boolVal(boolean v) {
            sb.append(v);
        }

        void intVal(long v) {
            sb.append(v);
        }

        /**
         * Writes a {@code double} as an integer when it has no fractional part
         * (e.g. {@code 500.0 → 500}), otherwise in standard notation.
         */
        void numVal(double v) {
            if (v == Math.floor(v) && !Double.isInfinite(v)) {
                sb.append((long) v);
            } else {
                sb.append(v);
            }
        }

        // ── Convenience field writers (key + value) ──────────────────────────────

        void strField(String k, String v) {
            key(k);
            strVal(v);
        }

        void boolField(String k, boolean v) {
            key(k);
            boolVal(v);
        }

        void intField(String k, long v) {
            key(k);
            intVal(v);
        }

        void numField(String k, double v) {
            key(k);
            numVal(v);
        }

        @Override
        public String toString() {
            return sb.toString();
        }

        // ── Internal helpers ─────────────────────────────────────────────────────

        private void appendIndent(int d) {
            for (int i = 0; i < d; i++) sb.append("  ");
        }

        private static void appendEscaped(StringBuilder buf, String s) {
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"'  -> buf.append("\\\"");
                    case '\\' -> buf.append("\\\\");
                    case '\n' -> buf.append("\\n");
                    case '\r' -> buf.append("\\r");
                    case '\t' -> buf.append("\\t");
                    default   -> buf.append(c);
                }
            }
        }
    }

    // ── Generic helpers ───────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface ElemWriter<T> {
        void write(JWriter w, T elem);
    }

    private static <T> void writeListField(JWriter w, String field, List<T> list, ElemWriter<T> writer) {
        w.key(field);
        w.beginArr();
        for (T item : list) {
            w.item();
            writer.write(w, item);
        }
        w.endArr();
    }

    // ── IrModel ───────────────────────────────────────────────────────────────────

    // IrModel: imports, namespace, shapes  (alphabetical)
    private static void writeModel(JWriter w, IrModel model) {
        w.beginObj();
        writeListField(w, "imports", model.imports(), IrModelSerializer::writeUseDecl);
        w.strField("namespace", model.namespace());
        writeListField(w, "shapes", model.shapes(), IrModelSerializer::writeShape);
        w.endObj();
    }

    // UseDecl: name, namespace, span  (alphabetical)
    private static void writeUseDecl(JWriter w, UseDecl u) {
        w.beginObj();
        w.strField("name", u.name());
        w.strField("namespace", u.namespace());
        writeSpanField(w, "span", u.span());
        w.endObj();
    }

    // ── IrShape dispatch (sealed switch → compile-time exhaustiveness) ────────────

    private static void writeShape(JWriter w, IrShape shape) {
        switch (shape) {
            case EntityDef       s -> writeEntity(w, s);
            case ActorDef        s -> writeActor(w, s);
            case JourneyDef      s -> writeJourney(w, s);
            case ShapeStructDef  s -> writeShapeStruct(w, s);
            case EnumDef         s -> writeEnum(w, s);
            case ListDef         s -> writeListShape(w, s);
            case MapDef          s -> writeMap(w, s);
            case PolicyDef       s -> writePolicy(w, s);
            case RelationshipDef s -> writeRelationship(w, s);
            case InvariantDef    s -> writeInvariant(w, s);
            case DenyDef         s -> writeDeny(w, s);
            case ErrorDef        s -> writeError(w, s);
            case StateMachineDef s -> writeStateMachine(w, s);
            case RoleDef         s -> writeRole(w, s);
            case EventDef        s -> writeEvent(w, s);
        }
    }

    // EntityDef: kind(first), docComments, fields, invariants, name, parentRef, span, traits
    private static void writeEntity(JWriter w, EntityDef e) {
        w.beginObj();
        w.strField("kind", "entity");
        writeListField(w, "docComments", e.docComments(), JWriter::strVal);
        writeListField(w, "fields", e.fields(), IrModelSerializer::writeField);
        writeListField(w, "invariants", e.invariants(), IrModelSerializer::writeEntityInvariant);
        w.strField("name", e.name());
        writeOptionalSymbolRefField(w, "parentRef", e.parentRef());
        writeSpanField(w, "span", e.span());
        writeListField(w, "traits", e.traits(), IrModelSerializer::writeTraitApplication);
        w.endObj();
    }

    // ActorDef: kind(first), docComments, name, parentRef, span, traits
    private static void writeActor(JWriter w, ActorDef a) {
        w.beginObj();
        w.strField("kind", "actor");
        writeListField(w, "docComments", a.docComments(), JWriter::strVal);
        w.strField("name", a.name());
        writeOptionalSymbolRefField(w, "parentRef", a.parentRef());
        writeSpanField(w, "span", a.span());
        writeListField(w, "traits", a.traits(), IrModelSerializer::writeTraitApplication);
        w.endObj();
    }

    // JourneyDef: kind(first), actorRef, docComments, name, outcomesOrNull,
    //             preconditions, span, steps, traits, variants
    private static void writeJourney(JWriter w, JourneyDef j) {
        w.beginObj();
        w.strField("kind", "journey");
        w.key("actorRef");
        if (j.actorRef() != null) {
            writeSymbolRef(w, j.actorRef());
        } else {
            w.nullVal();
        }
        writeListField(w, "docComments", j.docComments(), JWriter::strVal);
        w.strField("name", j.name());
        w.key("outcomesOrNull");
        if (j.outcomesOrNull() != null) {
            writeJourneyOutcomes(w, j.outcomesOrNull());
        } else {
            w.nullVal();
        }
        writeListField(w, "preconditions", j.preconditions(), JWriter::strVal);
        writeSpanField(w, "span", j.span());
        writeListField(w, "steps", j.steps(), IrModelSerializer::writeStep);
        writeListField(w, "traits", j.traits(), IrModelSerializer::writeTraitApplication);
        // variants: written as a sorted JSON object (keys in alphabetical order)
        w.key("variants");
        w.beginObj();
        new TreeMap<>(j.variants()).forEach((k, v) -> {
            w.key(k);
            writeVariant(w, v);
        });
        w.endObj();
        w.endObj();
    }

    // ShapeStructDef: kind(first), docComments, fields, name, span, traits
    private static void writeShapeStruct(JWriter w, ShapeStructDef s) {
        w.beginObj();
        w.strField("kind", "shape");
        writeListField(w, "docComments", s.docComments(), JWriter::strVal);
        writeListField(w, "fields", s.fields(), IrModelSerializer::writeField);
        w.strField("name", s.name());
        writeSpanField(w, "span", s.span());
        writeListField(w, "traits", s.traits(), IrModelSerializer::writeTraitApplication);
        w.endObj();
    }

    // EnumDef: kind(first), docComments, members, name, span, traits
    private static void writeEnum(JWriter w, EnumDef e) {
        w.beginObj();
        w.strField("kind", "enum");
        writeListField(w, "docComments", e.docComments(), JWriter::strVal);
        writeListField(w, "members", e.members(), IrModelSerializer::writeEnumMember);
        w.strField("name", e.name());
        writeSpanField(w, "span", e.span());
        writeListField(w, "traits", e.traits(), IrModelSerializer::writeTraitApplication);
        w.endObj();
    }

    // ListDef: kind(first), docComments, memberType, name, span, traits
    private static void writeListShape(JWriter w, ListDef l) {
        w.beginObj();
        w.strField("kind", "list");
        writeListField(w, "docComments", l.docComments(), JWriter::strVal);
        w.key("memberType");
        writeTypeRef(w, l.memberType());
        w.strField("name", l.name());
        writeSpanField(w, "span", l.span());
        writeListField(w, "traits", l.traits(), IrModelSerializer::writeTraitApplication);
        w.endObj();
    }

    // MapDef: kind(first), docComments, keyType, name, span, traits, valueType
    private static void writeMap(JWriter w, MapDef m) {
        w.beginObj();
        w.strField("kind", "map");
        writeListField(w, "docComments", m.docComments(), JWriter::strVal);
        w.key("keyType");
        writeTypeRef(w, m.keyType());
        w.strField("name", m.name());
        writeSpanField(w, "span", m.span());
        writeListField(w, "traits", m.traits(), IrModelSerializer::writeTraitApplication);
        w.key("valueType");
        writeTypeRef(w, m.valueType());
        w.endObj();
    }

    // PolicyDef: kind(first), description, docComments, name, span, traits
    private static void writePolicy(JWriter w, PolicyDef p) {
        w.beginObj();
        w.strField("kind", "policy");
        w.strField("description", p.description());
        writeListField(w, "docComments", p.docComments(), JWriter::strVal);
        w.strField("name", p.name());
        writeSpanField(w, "span", p.span());
        writeListField(w, "traits", p.traits(), IrModelSerializer::writeTraitApplication);
        w.endObj();
    }

    // RelationshipDef: kind(first), cardinality, docComments, fromEntityRef,
    //                  inverseField, name, semantics, span, toEntityRef, traits
    private static void writeRelationship(JWriter w, RelationshipDef r) {
        w.beginObj();
        w.strField("kind", "relationship");
        w.strField("cardinality", r.cardinality().name());
        writeListField(w, "docComments", r.docComments(), JWriter::strVal);
        w.key("fromEntityRef");
        writeSymbolRef(w, r.fromEntityRef());
        w.key("inverseField");
        if (r.inverseField().isPresent()) {
            w.strVal(r.inverseField().get());
        } else {
            w.nullVal();
        }
        w.strField("name", r.name());
        w.key("semantics");
        if (r.semantics().isPresent()) {
            w.strVal(r.semantics().get().name());
        } else {
            w.nullVal();
        }
        writeSpanField(w, "span", r.span());
        w.key("toEntityRef");
        writeSymbolRef(w, r.toEntityRef());
        writeListField(w, "traits", r.traits(), IrModelSerializer::writeTraitApplication);
        w.endObj();
    }

    // InvariantDef: kind(first), docComments, expression, message, name, scope, severity, span, traits
    private static void writeInvariant(JWriter w, InvariantDef inv) {
        w.beginObj();
        w.strField("kind", "invariant");
        writeListField(w, "docComments", inv.docComments(), JWriter::strVal);
        w.strField("expression", inv.expression());
        w.key("message");
        if (inv.message().isPresent()) {
            w.strVal(inv.message().get());
        } else {
            w.nullVal();
        }
        w.strField("name", inv.name());
        writeListField(w, "scope", inv.scope(), JWriter::strVal);
        w.strField("severity", inv.severity());
        writeSpanField(w, "span", inv.span());
        writeListField(w, "traits", inv.traits(), IrModelSerializer::writeTraitApplication);
        w.endObj();
    }

    // DenyDef: kind(first), description, docComments, name, scope, severity, span, traits
    private static void writeDeny(JWriter w, DenyDef d) {
        w.beginObj();
        w.strField("kind", "deny");
        w.strField("description", d.description());
        writeListField(w, "docComments", d.docComments(), JWriter::strVal);
        w.strField("name", d.name());
        writeListField(w, "scope", d.scope(), JWriter::strVal);
        w.strField("severity", d.severity());
        writeSpanField(w, "span", d.span());
        writeListField(w, "traits", d.traits(), IrModelSerializer::writeTraitApplication);
        w.endObj();
    }

    // ErrorDef: kind(first), code, docComments, message, name, payload, recoverable, severity, span, traits
    private static void writeError(JWriter w, ErrorDef e) {
        w.beginObj();
        w.strField("kind", "error");
        w.strField("code", e.code());
        writeListField(w, "docComments", e.docComments(), JWriter::strVal);
        w.strField("message", e.message());
        w.strField("name", e.name());
        writeListField(w, "payload", e.payload(), IrModelSerializer::writeField);
        w.boolField("recoverable", e.recoverable());
        w.strField("severity", e.severity());
        writeSpanField(w, "span", e.span());
        writeListField(w, "traits", e.traits(), IrModelSerializer::writeTraitApplication);
        w.endObj();
    }

    // StateMachineDef: kind(first), docComments, entityName, fieldName, initialState,
    //                  name, span, states, terminalStates, traits, transitions
    private static void writeStateMachine(JWriter w, StateMachineDef sm) {
        w.beginObj();
        w.strField("kind", "statemachine");
        writeListField(w, "docComments", sm.docComments(), JWriter::strVal);
        w.strField("entityName", sm.entityName());
        w.strField("fieldName", sm.fieldName());
        w.strField("initialState", sm.initialState());
        w.strField("name", sm.name());
        writeSpanField(w, "span", sm.span());
        writeListField(w, "states", sm.states(), JWriter::strVal);
        writeListField(w, "terminalStates", sm.terminalStates(), JWriter::strVal);
        writeListField(w, "traits", sm.traits(), IrModelSerializer::writeTraitApplication);
        writeListField(w, "transitions", sm.transitions(), IrModelSerializer::writeTransition);
        w.endObj();
    }

    // RoleDef: kind(first), allowedPermissions, deniedPermissions, docComments, name, span, traits
    private static void writeRole(JWriter w, RoleDef r) {
        w.beginObj();
        w.strField("kind", "role");
        writeListField(w, "allowedPermissions", r.allowedPermissions(), JWriter::strVal);
        writeListField(w, "deniedPermissions", r.deniedPermissions(), JWriter::strVal);
        writeListField(w, "docComments", r.docComments(), JWriter::strVal);
        w.strField("name", r.name());
        writeSpanField(w, "span", r.span());
        writeListField(w, "traits", r.traits(), IrModelSerializer::writeTraitApplication);
        w.endObj();
    }

    // EventDef: kind(first), docComments, fields, name, span, traits
    private static void writeEvent(JWriter w, EventDef e) {
        w.beginObj();
        w.strField("kind", "event");
        writeListField(w, "docComments", e.docComments(), JWriter::strVal);
        writeListField(w, "fields", e.fields(), IrModelSerializer::writeField);
        w.strField("name", e.name());
        writeSpanField(w, "span", e.span());
        writeListField(w, "traits", e.traits(), IrModelSerializer::writeTraitApplication);
        w.endObj();
    }

    // ── Supporting types ──────────────────────────────────────────────────────────

    // FieldDef: name, span, traits, type  (alphabetical)
    private static void writeField(JWriter w, FieldDef f) {
        w.beginObj();
        w.strField("name", f.name());
        writeSpanField(w, "span", f.span());
        writeListField(w, "traits", f.traits(), IrModelSerializer::writeTraitApplication);
        w.key("type");
        writeTypeRef(w, f.type());
        w.endObj();
    }

    // EnumMember: name, ordinalOrNull, span  (alphabetical)
    private static void writeEnumMember(JWriter w, EnumMember m) {
        w.beginObj();
        w.strField("name", m.name());
        w.key("ordinalOrNull");
        if (m.ordinalOrNull() != null) {
            w.intVal(m.ordinalOrNull());
        } else {
            w.nullVal();
        }
        writeSpanField(w, "span", m.span());
        w.endObj();
    }

    // EntityInvariant: expression, message, name, severity, span  (alphabetical)
    private static void writeEntityInvariant(JWriter w, EntityInvariant inv) {
        w.beginObj();
        w.strField("expression", inv.expression());
        w.key("message");
        if (inv.message().isPresent()) {
            w.strVal(inv.message().get());
        } else {
            w.nullVal();
        }
        w.strField("name", inv.name());
        w.strField("severity", inv.severity());
        writeSpanField(w, "span", inv.span());
        w.endObj();
    }

    // TraitApplication: args, name, span  (alphabetical)
    private static void writeTraitApplication(JWriter w, TraitApplication t) {
        w.beginObj();
        writeListField(w, "args", t.args(), IrModelSerializer::writeTraitArg);
        w.strField("name", t.name());
        writeSpanField(w, "span", t.span());
        w.endObj();
    }

    // TraitArg: keyOrNull, span, value  (alphabetical)
    private static void writeTraitArg(JWriter w, TraitArg a) {
        w.beginObj();
        w.key("keyOrNull");
        if (a.keyOrNull() != null) {
            w.strVal(a.keyOrNull());
        } else {
            w.nullVal();
        }
        writeSpanField(w, "span", a.span());
        w.key("value");
        writeTraitValue(w, a.value());
        w.endObj();
    }

    // TraitValue (sealed switch): kind(discriminator, first), then value/ref
    private static void writeTraitValue(JWriter w, TraitValue tv) {
        switch (tv) {
            case TraitValue.StringValue sv -> {
                w.beginObj();
                w.strField("kind", "string");
                w.strField("value", sv.value());
                w.endObj();
            }
            case TraitValue.NumberValue nv -> {
                w.beginObj();
                w.strField("kind", "number");
                w.numField("value", nv.value());
                w.endObj();
            }
            case TraitValue.BoolValue bv -> {
                w.beginObj();
                w.strField("kind", "bool");
                w.boolField("value", bv.value());
                w.endObj();
            }
            case TraitValue.ReferenceValue rv -> {
                w.beginObj();
                w.strField("kind", "ref");
                w.strField("ref", rv.ref());
                w.endObj();
            }
        }
    }

    // Step: fields, name, span, traits  (alphabetical)
    private static void writeStep(JWriter w, Step step) {
        w.beginObj();
        writeListField(w, "fields", step.fields(), IrModelSerializer::writeStepField);
        w.strField("name", step.name());
        writeSpanField(w, "span", step.span());
        writeListField(w, "traits", step.traits(), IrModelSerializer::writeTraitApplication);
        w.endObj();
    }

    // StepField (sealed switch): kind(discriminator, first), then alphabetical remaining
    private static void writeStepField(JWriter w, StepField sf) {
        switch (sf) {
            case StepField.Action a -> {
                // kind, span, text
                w.beginObj();
                w.strField("kind", "action");
                writeSpanField(w, "span", a.span());
                w.strField("text", a.text());
                w.endObj();
            }
            case StepField.Expectation e -> {
                // kind, span, text
                w.beginObj();
                w.strField("kind", "expectation");
                writeSpanField(w, "span", e.span());
                w.strField("text", e.text());
                w.endObj();
            }
            case StepField.Outcome o -> {
                // kind, expr, span  (e < s)
                w.beginObj();
                w.strField("kind", "outcome");
                w.key("expr");
                writeOutcomeExpr(w, o.expr());
                writeSpanField(w, "span", o.span());
                w.endObj();
            }
            case StepField.Telemetry t -> {
                // kind, ids, span  (i < s)
                w.beginObj();
                w.strField("kind", "telemetry");
                writeListField(w, "ids", t.ids(), JWriter::strVal);
                writeSpanField(w, "span", t.span());
                w.endObj();
            }
            case StepField.Risk r -> {
                // kind, span, text
                w.beginObj();
                w.strField("kind", "risk");
                writeSpanField(w, "span", r.span());
                w.strField("text", r.text());
                w.endObj();
            }
            case StepField.Input i -> {
                // kind, fields, span  (f < s)
                w.beginObj();
                w.strField("kind", "input");
                writeListField(w, "fields", i.fields(), IrModelSerializer::writeDataField);
                writeSpanField(w, "span", i.span());
                w.endObj();
            }
            case StepField.Output o -> {
                // kind, fields, span  (f < s)
                w.beginObj();
                w.strField("kind", "output");
                writeListField(w, "fields", o.fields(), IrModelSerializer::writeDataField);
                writeSpanField(w, "span", o.span());
                w.endObj();
            }
        }
    }

    // DataField: name, span, type  (alphabetical)
    private static void writeDataField(JWriter w, DataField df) {
        w.beginObj();
        w.strField("name", df.name());
        writeSpanField(w, "span", df.span());
        w.key("type");
        writeTypeRef(w, df.type());
        w.endObj();
    }

    // Variant: name, outcomeOrNull, span, steps, triggerName  (alphabetical)
    private static void writeVariant(JWriter w, Variant v) {
        w.beginObj();
        w.strField("name", v.name());
        w.key("outcomeOrNull");
        if (v.outcomeOrNull() != null) {
            writeOutcomeExpr(w, v.outcomeOrNull());
        } else {
            w.nullVal();
        }
        writeSpanField(w, "span", v.span());
        writeListField(w, "steps", v.steps(), IrModelSerializer::writeStep);
        w.strField("triggerName", v.triggerName());
        w.endObj();
    }

    // OutcomeExpr (sealed switch): kind(discriminator, first), then alphabetical remaining
    private static void writeOutcomeExpr(JWriter w, OutcomeExpr oe) {
        switch (oe) {
            case OutcomeExpr.TransitionTo t -> {
                // kind, span, stateId  ("spa" < "sta")
                w.beginObj();
                w.strField("kind", "transition");
                writeSpanField(w, "span", t.span());
                w.strField("stateId", t.stateId());
                w.endObj();
            }
            case OutcomeExpr.ReturnToStep r -> {
                // kind, span, stepId  ("spa" < "ste")
                w.beginObj();
                w.strField("kind", "return");
                writeSpanField(w, "span", r.span());
                w.strField("stepId", r.stepId());
                w.endObj();
            }
        }
    }

    // JourneyOutcomes: failureOrNull, span, successOrNull  (alphabetical)
    private static void writeJourneyOutcomes(JWriter w, JourneyOutcomes jo) {
        w.beginObj();
        w.key("failureOrNull");
        if (jo.failureOrNull() != null) {
            w.strVal(jo.failureOrNull());
        } else {
            w.nullVal();
        }
        writeSpanField(w, "span", jo.span());
        w.key("successOrNull");
        if (jo.successOrNull() != null) {
            w.strVal(jo.successOrNull());
        } else {
            w.nullVal();
        }
        w.endObj();
    }

    // Transition: action, fromState, guard, span, toState  (alphabetical)
    private static void writeTransition(JWriter w, Transition t) {
        w.beginObj();
        w.key("action");
        if (t.action().isPresent()) {
            w.strVal(t.action().get());
        } else {
            w.nullVal();
        }
        w.strField("fromState", t.fromState());
        w.key("guard");
        if (t.guard().isPresent()) {
            w.strVal(t.guard().get());
        } else {
            w.nullVal();
        }
        writeSpanField(w, "span", t.span());
        w.strField("toState", t.toState());
        w.endObj();
    }

    // ── TypeRef (sealed switch) ───────────────────────────────────────────────────

    // typeKind(discriminator, first), then alphabetical remaining
    private static void writeTypeRef(JWriter w, TypeRef tr) {
        switch (tr) {
            case TypeRef.PrimitiveType pt -> {
                // typeKind, kind  (kind is the PrimitiveKind field)
                w.beginObj();
                w.strField("typeKind", "primitive");
                w.strField("kind", pt.kind().name());
                w.endObj();
            }
            case TypeRef.ListType lt -> {
                // typeKind, elementType
                w.beginObj();
                w.strField("typeKind", "list");
                w.key("elementType");
                writeTypeRef(w, lt.elementType());
                w.endObj();
            }
            case TypeRef.MapType mt -> {
                // typeKind, keyType, valueType  (k < v)
                w.beginObj();
                w.strField("typeKind", "map");
                w.key("keyType");
                writeTypeRef(w, mt.keyType());
                w.key("valueType");
                writeTypeRef(w, mt.valueType());
                w.endObj();
            }
            case TypeRef.NamedTypeRef ntr -> {
                // typeKind, ref  (qualifiedId is suppressed — derived)
                w.beginObj();
                w.strField("typeKind", "named");
                w.key("ref");
                writeSymbolRef(w, ntr.ref());
                w.endObj();
            }
        }
    }

    // ── Span ─────────────────────────────────────────────────────────────────────

    private static void writeSpanField(JWriter w, String field, Span span) {
        w.key(field);
        writeSpan(w, span);
    }

    // Span: endCol, endLine, sourceName, startCol, startLine  (alphabetical)
    private static void writeSpan(JWriter w, Span span) {
        w.beginObj();
        w.intField("endCol", span.endCol());
        w.intField("endLine", span.endLine());
        w.strField("sourceName", span.sourceName());
        w.intField("startCol", span.startCol());
        w.intField("startLine", span.startLine());
        w.endObj();
    }

    // ── SymbolRef ─────────────────────────────────────────────────────────────────

    private static void writeOptionalSymbolRefField(JWriter w, String field, Optional<SymbolRef> opt) {
        w.key(field);
        if (opt.isPresent()) {
            writeSymbolRef(w, opt.get());
        } else {
            w.nullVal();
        }
    }

    /**
     * Resolved form — alphabetical: {@code id}, {@code kind}, {@code resolved}, {@code span}.
     * Unresolved form — alphabetical: {@code kind}, {@code name}, {@code resolved}, {@code span}.
     */
    private static void writeSymbolRef(JWriter w, SymbolRef ref) {
        w.beginObj();
        if (ref.isResolved()) {
            // id: { name, namespace: { value } }
            w.key("id");
            w.beginObj();
            w.strField("name", ref.id().name());
            w.key("namespace");
            w.beginObj();
            w.strField("value", ref.id().namespace().value());
            w.endObj();
            w.endObj();
            w.strField("kind", ref.kind().name());
            w.boolField("resolved", true);
        } else {
            // name: { name, namespaceOrNull }
            w.strField("kind", ref.kind().name());
            w.key("name");
            w.beginObj();
            w.strField("name", ref.name().name());
            w.key("namespaceOrNull");
            if (ref.name().namespaceOrNull() != null) {
                w.strVal(ref.name().namespaceOrNull());
            } else {
                w.nullVal();
            }
            w.endObj();
            w.boolField("resolved", false);
        }
        writeSpanField(w, "span", ref.span());
        w.endObj();
    }
}
