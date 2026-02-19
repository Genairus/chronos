package com.genairus.chronos.model;

import java.util.*;

/**
 * Utility for resolving inheritance hierarchies for entities and actors.
 *
 * <p>Provides methods to:
 * <ul>
 *   <li>Resolve the full field set for an entity (parent fields + child fields)
 *   <li>Resolve the full trait set for an entity or actor (parent traits + child traits)
 *   <li>Detect circular inheritance chains
 *   <li>Validate field overrides for type compatibility
 * </ul>
 */
public class InheritanceResolver {

    private final ChronosModel model;

    public InheritanceResolver(ChronosModel model) {
        this.model = model;
    }

    /**
     * Returns the full list of fields for an entity, including all inherited fields
     * from parent entities. Fields are returned in inheritance order: parent fields
     * first, then child fields.
     *
     * <p>If a child redefines a parent field, the child's version appears in the list
     * (the parent's version is replaced).
     *
     * @param entity the entity to resolve fields for
     * @return the complete list of fields including inherited ones
     */
    public List<FieldDef> resolveAllFields(EntityDef entity) {
        Map<String, FieldDef> fieldMap = new LinkedHashMap<>();
        collectFieldsRecursive(entity, fieldMap, new HashSet<>());
        return new ArrayList<>(fieldMap.values());
    }

    /**
     * Returns the full list of traits for an entity, including all inherited traits
     * from parent entities. Traits are returned in inheritance order: parent traits
     * first, then child traits.
     *
     * <p>If a child redefines a parent trait (same trait name), the child's version
     * appears in the list (the parent's version is replaced). This implements CHR-017.
     *
     * @param entity the entity to resolve traits for
     * @return the complete list of traits including inherited ones
     */
    public List<TraitApplication> resolveAllTraits(EntityDef entity) {
        Map<String, TraitApplication> traitMap = new LinkedHashMap<>();
        collectEntityTraitsRecursive(entity, traitMap, new HashSet<>());
        return new ArrayList<>(traitMap.values());
    }

    /**
     * Returns the full list of traits for an actor, including all inherited traits
     * from parent actors. Traits are returned in inheritance order: parent traits
     * first, then child traits.
     *
     * <p>If a child redefines a parent trait (same trait name), the child's version
     * appears in the list (the parent's version is replaced). This implements CHR-017.
     *
     * @param actor the actor to resolve traits for
     * @return the complete list of traits including inherited ones
     */
    public List<TraitApplication> resolveAllTraits(ActorDef actor) {
        Map<String, TraitApplication> traitMap = new LinkedHashMap<>();
        collectActorTraitsRecursive(actor, traitMap, new HashSet<>());
        return new ArrayList<>(traitMap.values());
    }

    /**
     * Recursively collects fields from the inheritance hierarchy.
     * Parent fields are added first, then child fields override if they have the same name.
     */
    private void collectFieldsRecursive(EntityDef entity, Map<String, FieldDef> fieldMap, Set<String> visited) {
        // Check for circular inheritance
        if (visited.contains(entity.name())) {
            return; // Circular reference - will be caught by validation
        }
        visited.add(entity.name());

        // First, collect parent fields if there's a parent
        if (entity.parentType().isPresent()) {
            String parentName = entity.parentType().get();
            Optional<EntityDef> parent = findEntity(parentName);
            if (parent.isPresent()) {
                collectFieldsRecursive(parent.get(), fieldMap, visited);
            }
        }

        // Then add/override with this entity's fields
        for (FieldDef field : entity.fields()) {
            fieldMap.put(field.name(), field);
        }
    }

    /**
     * Recursively collects traits from the entity inheritance hierarchy.
     * Parent traits are added first, then child traits override if they have the same name.
     */
    private void collectEntityTraitsRecursive(EntityDef entity, Map<String, TraitApplication> traitMap, Set<String> visited) {
        // Check for circular inheritance
        if (visited.contains(entity.name())) {
            return; // Circular reference - will be caught by validation
        }
        visited.add(entity.name());

        // First, collect parent traits if there's a parent
        if (entity.parentType().isPresent()) {
            String parentName = entity.parentType().get();
            Optional<EntityDef> parent = findEntity(parentName);
            if (parent.isPresent()) {
                collectEntityTraitsRecursive(parent.get(), traitMap, visited);
            }
        }

        // Then add/override with this entity's traits
        for (TraitApplication trait : entity.traits()) {
            traitMap.put(trait.name(), trait);
        }
    }

    /**
     * Recursively collects traits from the actor inheritance hierarchy.
     * Parent traits are added first, then child traits override if they have the same name.
     */
    private void collectActorTraitsRecursive(ActorDef actor, Map<String, TraitApplication> traitMap, Set<String> visited) {
        // Check for circular inheritance
        if (visited.contains(actor.name())) {
            return; // Circular reference - will be caught by validation
        }
        visited.add(actor.name());

        // First, collect parent traits if there's a parent
        if (actor.parentType().isPresent()) {
            String parentName = actor.parentType().get();
            Optional<ActorDef> parent = findActor(parentName);
            if (parent.isPresent()) {
                collectActorTraitsRecursive(parent.get(), traitMap, visited);
            }
        }

        // Then add/override with this actor's traits
        for (TraitApplication trait : actor.traits()) {
            traitMap.put(trait.name(), trait);
        }
    }

    /**
     * Detects if there is a circular inheritance chain starting from the given entity.
     * 
     * @param entity the entity to check
     * @return true if a circular inheritance chain is detected
     */
    public boolean hasCircularInheritance(EntityDef entity) {
        Set<String> visited = new HashSet<>();
        return hasCircularInheritanceRecursive(entity, visited);
    }

    private boolean hasCircularInheritanceRecursive(EntityDef entity, Set<String> visited) {
        if (visited.contains(entity.name())) {
            return true; // Found a cycle
        }
        visited.add(entity.name());

        if (entity.parentType().isEmpty()) {
            return false; // No parent, no cycle
        }

        String parentName = entity.parentType().get();
        Optional<EntityDef> parent = findEntity(parentName);
        if (parent.isEmpty()) {
            return false; // Parent not found - will be caught by other validation
        }

        return hasCircularInheritanceRecursive(parent.get(), visited);
    }

    /**
     * Detects if there is a circular inheritance chain starting from the given actor.
     * 
     * @param actor the actor to check
     * @return true if a circular inheritance chain is detected
     */
    public boolean hasCircularInheritance(ActorDef actor) {
        Set<String> visited = new HashSet<>();
        return hasCircularInheritanceRecursive(actor, visited);
    }

    private boolean hasCircularInheritanceRecursive(ActorDef actor, Set<String> visited) {
        if (visited.contains(actor.name())) {
            return true; // Found a cycle
        }
        visited.add(actor.name());

        if (actor.parentType().isEmpty()) {
            return false; // No parent, no cycle
        }

        String parentName = actor.parentType().get();
        Optional<ActorDef> parent = findActor(parentName);
        if (parent.isEmpty()) {
            return false; // Parent not found - will be caught by other validation
        }

        return hasCircularInheritanceRecursive(parent.get(), visited);
    }

    /**
     * Finds an entity by name in the model.
     */
    private Optional<EntityDef> findEntity(String name) {
        return model.entities().stream()
                .filter(e -> e.name().equals(name))
                .findFirst();
    }

    /**
     * Finds an actor by name in the model.
     */
    private Optional<ActorDef> findActor(String name) {
        return model.actors().stream()
                .filter(a -> a.name().equals(name))
                .findFirst();
    }
}

