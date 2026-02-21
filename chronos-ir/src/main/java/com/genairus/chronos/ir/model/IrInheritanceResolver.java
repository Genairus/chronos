package com.genairus.chronos.ir.model;

import com.genairus.chronos.ir.types.ActorDef;
import com.genairus.chronos.ir.types.EntityDef;
import com.genairus.chronos.ir.types.FieldDef;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Utility for resolving inheritance hierarchies for IR entity and actor definitions.
 *
 * <p>Mirrors the functionality of the legacy {@code InheritanceResolver} but operates
 * entirely on canonical IR types ({@code com.genairus.chronos.ir.types.*}).
 *
 * <p>After {@code CrossLinkResolutionPhase} and {@code FinalizeIrPhase}, entity and actor
 * {@code parentRef} values are resolved {@link com.genairus.chronos.core.refs.SymbolRef}s,
 * so parent names can be extracted reliably.
 */
public class IrInheritanceResolver {

    private final IrModel model;

    public IrInheritanceResolver(IrModel model) {
        this.model = model;
    }

    // ── Field resolution ──────────────────────────────────────────────────────

    /**
     * Returns the full list of fields for an entity, including all inherited fields
     * from parent entities. Fields are returned in inheritance order: parent fields
     * first, then child fields (child overrides parent on same name).
     */
    public List<FieldDef> resolveAllFields(EntityDef entity) {
        Map<String, FieldDef> fieldMap = new LinkedHashMap<>();
        collectFieldsRecursive(entity, fieldMap, new HashSet<>());
        return new ArrayList<>(fieldMap.values());
    }

    private void collectFieldsRecursive(EntityDef entity,
                                        Map<String, FieldDef> fieldMap,
                                        Set<String> visited) {
        if (visited.contains(entity.name())) return;
        visited.add(entity.name());

        parentName(entity).ifPresent(parentName ->
                findEntity(parentName).ifPresent(parent ->
                        collectFieldsRecursive(parent, fieldMap, visited)));

        for (FieldDef field : entity.fields()) {
            fieldMap.put(field.name(), field);
        }
    }

    // ── Circular inheritance detection ────────────────────────────────────────

    /**
     * Returns {@code true} if the given entity participates in a circular
     * inheritance chain (including self-reference).
     */
    public boolean hasCircularInheritance(EntityDef entity) {
        return hasCircularInheritanceEntity(entity, new HashSet<>());
    }

    private boolean hasCircularInheritanceEntity(EntityDef entity, Set<String> visited) {
        if (visited.contains(entity.name())) return true;
        visited.add(entity.name());

        var pName = parentName(entity);
        if (pName.isEmpty()) return false;
        var parent = findEntity(pName.get());
        if (parent.isEmpty()) return false;
        return hasCircularInheritanceEntity(parent.get(), visited);
    }

    /**
     * Returns {@code true} if the given actor participates in a circular
     * inheritance chain (including self-reference).
     */
    public boolean hasCircularInheritance(ActorDef actor) {
        return hasCircularInheritanceActor(actor, new HashSet<>());
    }

    private boolean hasCircularInheritanceActor(ActorDef actor, Set<String> visited) {
        if (visited.contains(actor.name())) return true;
        visited.add(actor.name());

        var pName = parentName(actor);
        if (pName.isEmpty()) return false;
        var parent = findActor(pName.get());
        if (parent.isEmpty()) return false;
        return hasCircularInheritanceActor(parent.get(), visited);
    }

    // ── Parent name helpers ───────────────────────────────────────────────────

    /**
     * Returns the simple name of the parent entity, extracted from the resolved (or
     * unresolved) {@code parentRef}. Returns empty if no parent is declared.
     */
    public static Optional<String> parentName(EntityDef entity) {
        return entity.parentRef().map(ref ->
                ref.isResolved() ? ref.id().name() : ref.name().name());
    }

    /**
     * Returns the simple name of the parent actor, extracted from the resolved (or
     * unresolved) {@code parentRef}. Returns empty if no parent is declared.
     */
    public static Optional<String> parentName(ActorDef actor) {
        return actor.parentRef().map(ref ->
                ref.isResolved() ? ref.id().name() : ref.name().name());
    }

    // ── Lookup helpers ────────────────────────────────────────────────────────

    private Optional<EntityDef> findEntity(String name) {
        return model.entities().stream()
                .filter(e -> e.name().equals(name))
                .findFirst();
    }

    private Optional<ActorDef> findActor(String name) {
        return model.actors().stream()
                .filter(a -> a.name().equals(name))
                .findFirst();
    }
}
