package com.genairus.chronos.core.refs;

import com.genairus.chronos.core.diagnostics.DiagnosticCollector;

import java.util.Objects;

/**
 * A typed reference to a Chronos shape declaration — the bridge between a raw
 * source-level name and a fully resolved {@link ShapeId}.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li><b>Unresolved</b> — created by {@link #unresolved} immediately after the
 *       Lower pass.  The ref carries the raw {@link QualifiedName} as written in
 *       source; {@link #id()} returns {@code null}.</li>
 *   <li><b>Resolved</b> — replaced by {@link #resolved} during Pass 3–6.  The ref
 *       carries the canonical {@link ShapeId}; {@link #name()} returns {@code null}
 *       (no longer needed).</li>
 * </ol>
 *
 * <h2>Why not generic?</h2>
 * {@code SymbolRef} is intentionally non-generic ({@code SymbolRef<IrEntity>} was
 * considered and rejected) so that:
 * <ul>
 *   <li>Jackson can serialize/deserialize it without type-token gymnastics.</li>
 *   <li>Collections of mixed-kind refs ({@code List<SymbolRef>}) can be stored in
 *       {@code scope} fields (invariant / deny) without unchecked-cast noise.</li>
 *   <li>The {@link SymbolKind} field provides the same type-safety at runtime.</li>
 * </ul>
 *
 * <h2>Immutability</h2>
 * Instances are immutable.  To "resolve" a ref, discard the unresolved instance
 * and create a new one via {@link #resolved}.
 */
public final class SymbolRef {

    private final SymbolKind kind;

    /**
     * Canonical identity of the target shape.
     * {@code null} when this ref is unresolved (pre-finalize).
     */
    private final ShapeId id;

    /**
     * Raw source-level name as written in the {@code .chronos} file.
     * {@code null} once this ref is resolved (post-finalize, not needed anymore).
     */
    private final QualifiedName name;

    /** Source position of the name token. Never {@code null}. */
    private final Span span;

    // ── Private constructor ────────────────────────────────────────────────────

    private SymbolRef(SymbolKind kind, ShapeId id, QualifiedName name, Span span) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.span = Objects.requireNonNull(span, "span");
        // Exactly one of id / name must be non-null.
        if (id == null && name == null) {
            throw new IllegalArgumentException(
                    "SymbolRef: at least one of id or name must be non-null");
        }
        this.id   = id;
        this.name = name;
    }

    // ── Factories ──────────────────────────────────────────────────────────────

    /**
     * Creates an <em>unresolved</em> reference — the form used immediately after
     * lowering the parse tree.  Resolution passes will replace this with a
     * {@link #resolved} instance.
     *
     * @param kind  expected kind of the target declaration
     * @param name  raw name as it appears in source
     * @param span  source position of the name token
     */
    public static SymbolRef unresolved(SymbolKind kind, QualifiedName name, Span span) {
        Objects.requireNonNull(name, "name must not be null for an unresolved ref");
        return new SymbolRef(kind, null, name, span);
    }

    /**
     * Creates a <em>resolved</em> reference — the form produced after a resolution
     * pass successfully maps a name to its declaration.
     *
     * @param kind  actual kind of the resolved declaration (verified against expected)
     * @param id    canonical identity of the resolved declaration
     * @param span  source position of the original name token (preserved for diagnostics)
     */
    public static SymbolRef resolved(SymbolKind kind, ShapeId id, Span span) {
        Objects.requireNonNull(id, "id must not be null for a resolved ref");
        return new SymbolRef(kind, id, null, span);
    }

    // ── Accessors ──────────────────────────────────────────────────────────────

    /** The expected (or confirmed) kind of the referenced declaration. */
    public SymbolKind kind() {
        return kind;
    }

    /**
     * The resolved canonical identity, or {@code null} if this ref is still
     * unresolved.  Use {@link #isResolved()} to guard before calling.
     */
    public ShapeId id() {
        return id;
    }

    /**
     * The raw source name, or {@code null} once this ref has been resolved.
     * Primarily useful for diagnostics when resolution fails.
     */
    public QualifiedName name() {
        return name;
    }

    /** Source position of the name token. Never {@code null}. */
    public Span span() {
        return span;
    }

    // ── State predicates ───────────────────────────────────────────────────────

    /**
     * Returns {@code true} if this reference has been successfully resolved to a
     * {@link ShapeId}, {@code false} if the raw name is still pending resolution.
     */
    public boolean isResolved() {
        return id != null;
    }

    // ── Resolution helper ─────────────────────────────────────────────────────

    /**
     * Returns the resolved {@link ShapeId} if this ref is resolved, or reports
     * a diagnostic and returns {@code null} if it is not.
     *
     * <p>This method is intended for use inside the Finalizer (Pass 8) and inside
     * validators that require a fully resolved IR.  Example usage:
     *
     * <pre>{@code
     *   ShapeId actorId = journey.actorRef()
     *       .requireResolvedOrReport(diag, "CHR-008",
     *           "Journey '" + journey.name() + "' actor");
     *   if (actorId == null) return; // diagnostic already recorded
     * }</pre>
     *
     * @param diag          collector that receives the diagnostic if unresolved
     * @param code          CHR-xxx code to use in the reported diagnostic
     * @param messagePrefix prefix prepended to the standard "unresolved reference '...'" text
     * @return the {@link ShapeId} if resolved, or {@code null} if unresolved
     *         (the diagnostic has already been reported to {@code diag})
     */
    public ShapeId requireResolvedOrReport(
            DiagnosticCollector diag, String code, String messagePrefix) {
        if (isResolved()) {
            return id;
        }
        String rawName = name != null ? name.toString() : "<null>";
        diag.error(
                code,
                messagePrefix + ": unresolved reference '" + rawName + "'",
                span);
        return null;
    }

    // ── Object overrides ──────────────────────────────────────────────────────

    /**
     * Human-readable form, useful in diagnostic output and debug logs:
     * <ul>
     *   <li>Resolved: {@code "SymbolRef[ENTITY -> com.example#Order @file:3:5]"}</li>
     *   <li>Unresolved: {@code "SymbolRef[ENTITY -> ?Order @file:3:5]"}</li>
     * </ul>
     */
    @Override
    public String toString() {
        String target = isResolved()
                ? id.toString()
                : "?" + (name != null ? name.toString() : "<null>");
        return "SymbolRef[" + kind + " -> " + target + " @" + span + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SymbolRef other)) return false;
        return kind == other.kind
                && Objects.equals(id, other.id)
                && Objects.equals(name, other.name)
                && Objects.equals(span, other.span);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, id, name, span);
    }
}
