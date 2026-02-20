package com.genairus.chronos.compiler.util;

import com.genairus.chronos.core.refs.SymbolRef;
import com.genairus.chronos.ir.model.IrModel;

import java.util.*;

/**
 * Reflective walker that exhaustively finds every {@link SymbolRef} reachable
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
 *   <li><b>Chronos IR records</b> ({@code com.genairus.chronos.ir.*}) — every
 *       record component is walked via reflection.</li>
 *   <li><b>Everything else</b> ({@link String}, enums, {@link Number},
 *       {@link Boolean}, {@link com.genairus.chronos.core.refs.Span}, …) — skipped.</li>
 * </ol>
 *
 * <h2>Cycle / duplicate safety</h2>
 * An {@link IdentityHashMap}-backed visited set tracks every object by reference,
 * preventing both infinite recursion and duplicate collection of the same
 * {@link SymbolRef} instance.
 *
 * <h2>Future-proofing</h2>
 * Because traversal is reflection-driven, any new {@link SymbolRef} field added
 * to any IR record in {@code com.genairus.chronos.ir.*} is automatically discovered
 * without modifying this class.
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

        // ── Recurse into Chronos IR records ───────────────────────────────────
        Class<?> cls = obj.getClass();
        if (!cls.getPackageName().startsWith("com.genairus.chronos.ir.")) return;
        if (!cls.isRecord()) return;

        for (var component : cls.getRecordComponents()) {
            try {
                walk(component.getAccessor().invoke(obj), visited, result);
            } catch (Exception ignored) {
                // Defensive: skip any inaccessible component.
            }
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
