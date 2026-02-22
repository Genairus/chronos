package com.genairus.chronos.compiler.util;

import com.genairus.chronos.core.refs.SymbolRef;
import com.genairus.chronos.ir.model.IrModel;
import com.genairus.chronos.ir.types.ActorDef;
import com.genairus.chronos.ir.types.DataField;
import com.genairus.chronos.ir.types.DenyDef;
import com.genairus.chronos.ir.types.EntityDef;
import com.genairus.chronos.ir.types.EnumDef;
import com.genairus.chronos.ir.types.ErrorDef;
import com.genairus.chronos.ir.types.FieldDef;
import com.genairus.chronos.ir.types.InvariantDef;
import com.genairus.chronos.ir.types.IrShape;
import com.genairus.chronos.ir.types.JourneyDef;
import com.genairus.chronos.ir.types.ListDef;
import com.genairus.chronos.ir.types.MapDef;
import com.genairus.chronos.ir.types.PolicyDef;
import com.genairus.chronos.ir.types.RelationshipDef;
import com.genairus.chronos.ir.types.EventDef;
import com.genairus.chronos.ir.types.RoleDef;
import com.genairus.chronos.ir.types.ShapeStructDef;
import com.genairus.chronos.ir.types.StateMachineDef;
import com.genairus.chronos.ir.types.Step;
import com.genairus.chronos.ir.types.StepField;
import com.genairus.chronos.ir.types.TypeRef;
import com.genairus.chronos.ir.types.Variant;

import java.util.*;

/**
 * Structural walker that exhaustively finds every {@link SymbolRef} reachable
 * from any root object in the Chronos IR object graph.
 *
 * <h2>Traversal rules</h2>
 * <ol>
 *   <li><b>SymbolRef</b> — collected immediately; its internals
 *       ({@link com.genairus.chronos.core.refs.ShapeId},
 *       {@link com.genairus.chronos.core.refs.QualifiedName}, …) are not recursed into.</li>
 *   <li><b>Iterable / Map</b> — contents are walked; map <em>values</em> only
 *       (keys are always strings in the IR).</li>
 *   <li><b>Optional</b> — unwrapped and walked if present.</li>
 *   <li><b>Chronos IR records</b> — walked via explicit field access (see {@link #walkShape}
 *       and the structural dispatch in {@link #walk}).</li>
 *   <li><b>Everything else</b> ({@link String}, enums, {@link Number},
 *       {@link Boolean}, {@link com.genairus.chronos.core.refs.Span}, …) — skipped.</li>
 * </ol>
 *
 * <h2>Cycle / duplicate safety</h2>
 * An {@link IdentityHashMap}-backed visited set tracks every object by reference,
 * preventing both infinite recursion and duplicate collection of the same
 * {@link SymbolRef} instance.
 *
 * <h2>Maintenance note</h2>
 * Traversal is <em>explicit</em> (not reflection-driven) so it works in GraalVM
 * native image without any reflection configuration. When a new {@link SymbolRef}
 * or {@link TypeRef} field is added to an existing IR record, the relevant arm of
 * {@link #walkShape} or the dispatch block in {@link #walk} must be updated.
 * Adding a new {@link IrShape} permit will cause a compile error in the exhaustive
 * {@code switch} in {@link #walkShape}, making omissions impossible to miss.
 */
public final class IrRefWalker {

    private IrRefWalker() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns every {@link SymbolRef} reachable from {@code root}, in traversal
     * (depth-first, declaration) order. The same instance is never returned twice.
     *
     * @param root any IR object, or {@code null} (returns empty list)
     * @return unmodifiable list of all reachable {@link SymbolRef}s
     */
    public static List<SymbolRef> findAllRefs(Object root) {
        List<SymbolRef> result = new ArrayList<>();
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        walk(root, visited, result);
        return List.copyOf(result);
    }

    /**
     * Returns every <em>unresolved</em> {@link SymbolRef} reachable from
     * {@code model}, in traversal order.
     *
     * <p>Equivalent to:
     * <pre>{@code
     * findAllRefs(model).stream().filter(r -> !r.isResolved()).toList()
     * }</pre>
     *
     * @param model the IR model to scan; must not be {@code null}
     * @return unmodifiable list of unresolved refs
     */
    public static List<SymbolRef> findUnresolvedRefs(IrModel model) {
        return findAllRefs(model).stream()
                .filter(ref -> !ref.isResolved())
                .toList();
    }

    // ── Core traversal ────────────────────────────────────────────────────────

    private static void walk(Object obj, Set<Object> visited, List<SymbolRef> result) {
        if (obj == null) return;

        // ── Collect SymbolRef (deduplicated by object identity) ───────────────
        if (obj instanceof SymbolRef ref) {
            if (visited.add(ref)) {
                result.add(ref);
            }
            return;
        }

        // ── Skip scalar / non-traversable types early ─────────────────────────
        if (isScalar(obj.getClass())) return;

        // ── Cycle / revisit guard for traversable objects ─────────────────────
        if (!visited.add(obj)) return;

        // ── Transparent containers: recurse into contents ─────────────────────
        if (obj instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                walk(item, visited, result);
            }
            return;
        }
        if (obj instanceof Map<?, ?> map) {
            // Keys are always Strings in the IR; only values matter.
            for (Object value : map.values()) {
                walk(value, visited, result);
            }
            return;
        }
        if (obj instanceof Optional<?> opt) {
            opt.ifPresent(v -> walk(v, visited, result));
            return;
        }

        // ── Recurse into Chronos IR records (explicit structural dispatch) ─────
        // IrModel
        if (obj instanceof IrModel m) {
            walk(m.imports(), visited, result);
            walk(m.shapes(), visited, result);
            return;
        }
        // IrShape subtypes — exhaustive sealed switch; compiler enforces completeness
        if (obj instanceof IrShape shape) {
            walkShape(shape, visited, result);
            return;
        }
        // Other IR record types reachable from IrShape fields
        if (obj instanceof FieldDef f) {
            walk(f.type(), visited, result);
            return;
        }
        if (obj instanceof Step s) {
            walk(s.fields(), visited, result);
            return;
        }
        if (obj instanceof Variant v) {
            walk(v.steps(), visited, result);
            return;
        }
        if (obj instanceof StepField.Outcome o) {
            // OutcomeExpr (ReturnToStep / TransitionTo) contains only String ids — no SymbolRefs
            walk(o.expr(), visited, result);
            return;
        }
        if (obj instanceof StepField.Input i) {
            walk(i.fields(), visited, result);
            return;
        }
        if (obj instanceof StepField.Output o) {
            walk(o.fields(), visited, result);
            return;
        }
        if (obj instanceof DataField df) {
            walk(df.type(), visited, result);
            return;
        }
        // TypeRef variants
        if (obj instanceof TypeRef.NamedTypeRef n) {
            walk(n.ref(), visited, result);
            return;
        }
        if (obj instanceof TypeRef.ListType l) {
            walk(l.elementType(), visited, result);
            return;
        }
        if (obj instanceof TypeRef.MapType m) {
            walk(m.keyType(), visited, result);
            walk(m.valueType(), visited, result);
            return;
        }
        // Everything else (UseDecl, EnumMember, Transition, TraitApplication,
        // TraitArg, TraitValue.*, StepField.Action/Expectation/Telemetry/Risk,
        // OutcomeExpr.*, JourneyOutcomes, EntityInvariant, Span, core refs, …)
        // has no SymbolRef fields — silently skipped.
    }

    /**
     * Exhaustive switch over all {@link IrShape} sealed subtypes.
     *
     * <p>If a new subtype is added to the {@link IrShape} sealed interface, this
     * method will fail to compile until the new case is handled, preventing
     * accidental omissions.
     */
    private static void walkShape(IrShape shape, Set<Object> visited, List<SymbolRef> result) {
        switch (shape) {
            case EntityDef e -> {
                walk(e.parentRef(), visited, result);
                walk(e.fields(), visited, result);
            }
            case ActorDef a ->
                walk(a.parentRef(), visited, result);
            case JourneyDef j -> {
                walk(j.actorRef(), visited, result);
                walk(j.steps(), visited, result);
                walk(j.variants(), visited, result);
            }
            case ShapeStructDef s ->
                walk(s.fields(), visited, result);
            case EnumDef ignored -> {
                // EnumDef has no TypeRef or SymbolRef fields
            }
            case ListDef l ->
                walk(l.memberType(), visited, result);
            case MapDef m -> {
                walk(m.keyType(), visited, result);
                walk(m.valueType(), visited, result);
            }
            case PolicyDef ignored -> {
                // PolicyDef has no TypeRef or SymbolRef fields
            }
            case RelationshipDef r -> {
                walk(r.fromEntityRef(), visited, result);
                walk(r.toEntityRef(), visited, result);
            }
            case InvariantDef ignored -> {
                // InvariantDef scope is List<String>, no SymbolRef fields
            }
            case DenyDef ignored -> {
                // DenyDef scope is List<String>, no SymbolRef fields
            }
            case ErrorDef e ->
                walk(e.payload(), visited, result);
            case StateMachineDef ignored -> {
                // StateMachineDef has no TypeRef or SymbolRef fields
            }
            case RoleDef ignored -> {
                // RoleDef has no TypeRef or SymbolRef fields
            }
            case EventDef e ->
                walk(e.fields(), visited, result);
        }
    }

    // ── Scalar predicate ──────────────────────────────────────────────────────

    /**
     * Returns {@code true} for types that cannot contain a {@link SymbolRef}
     * and should not be recursed into.
     */
    private static boolean isScalar(Class<?> cls) {
        return cls.isPrimitive()
                || cls == String.class
                || cls.isEnum()
                || Number.class.isAssignableFrom(cls)
                || cls == Boolean.class
                || cls == Character.class;
    }
}
