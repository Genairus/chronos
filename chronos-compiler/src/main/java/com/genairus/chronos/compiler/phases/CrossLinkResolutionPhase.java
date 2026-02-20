package com.genairus.chronos.compiler.phases;

import com.genairus.chronos.compiler.ResolverContext;
import com.genairus.chronos.compiler.resolve.RefResolver;
import com.genairus.chronos.core.refs.SymbolKind;
import com.genairus.chronos.core.refs.SymbolRef;
import com.genairus.chronos.ir.model.IrModel;
import com.genairus.chronos.ir.types.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Pass 5: Resolves cross-shape references — actor names in journeys, entity
 * names in relationships, and inheritance parent names in entities and actors —
 * and rewrites the model with resolved {@link SymbolRef}s.
 *
 * <p>Reports {@code CHR-008} for unresolved actor references in journeys and
 * unresolved inheritance parent references in entities/actors.
 * Reports {@code CHR-011} for unresolved entity references in relationships.
 * Returns a new {@link IrModel} with updated shapes; unresolvable references
 * remain unresolved and the corresponding diagnostics are emitted.
 */
public final class CrossLinkResolutionPhase implements ResolverPhase<IrModel, IrModel> {

    private static final Set<SymbolKind> ACTOR  = Set.of(SymbolKind.ACTOR);
    private static final Set<SymbolKind> ENTITY = Set.of(SymbolKind.ENTITY);

    @Override
    public IrModel execute(IrModel model, ResolverContext ctx) {
        List<IrShape> updated = new ArrayList<>(model.shapes().size());
        for (IrShape shape : model.shapes()) {
            updated.add(switch (shape) {
                case JourneyDef      j -> resolveJourney(j, ctx);
                case RelationshipDef r -> resolveRelationship(r, ctx);
                case EntityDef       e -> resolveEntity(e, ctx);
                case ActorDef        a -> resolveActor(a, ctx);
                default                -> shape;
            });
        }
        return new IrModel(model.namespace(), model.imports(), updated);
    }

    private JourneyDef resolveJourney(JourneyDef j, ResolverContext ctx) {
        SymbolRef resolvedActor = RefResolver.resolve(
                ctx,
                j.actorRef(),
                ACTOR,
                "CHR-008",
                "Journey '" + j.name() + "' references undefined actor '%s'");

        if (resolvedActor == j.actorRef()) {
            return j;
        }
        return new JourneyDef(
                j.name(), j.traits(), j.docComments(),
                resolvedActor,
                j.preconditions(), j.steps(), j.variants(), j.outcomesOrNull(),
                j.span());
    }

    private RelationshipDef resolveRelationship(RelationshipDef r, ResolverContext ctx) {
        SymbolRef resolvedFrom = RefResolver.resolve(
                ctx,
                r.fromEntityRef(),
                ENTITY,
                "CHR-011",
                "Relationship '" + r.name() + "' references undefined entity '%s' in 'from' field");

        SymbolRef resolvedTo = RefResolver.resolve(
                ctx,
                r.toEntityRef(),
                ENTITY,
                "CHR-011",
                "Relationship '" + r.name() + "' references undefined entity '%s' in 'to' field");

        if (resolvedFrom == r.fromEntityRef() && resolvedTo == r.toEntityRef()) {
            return r;
        }
        return new RelationshipDef(
                r.name(), r.traits(), r.docComments(),
                resolvedFrom, resolvedTo,
                r.cardinality(), r.semantics(), r.inverseField(),
                r.span());
    }

    private EntityDef resolveEntity(EntityDef e, ResolverContext ctx) {
        if (e.parentRef().isEmpty()) {
            return e;
        }
        SymbolRef resolvedParent = RefResolver.resolve(
                ctx,
                e.parentRef().get(),
                ENTITY,
                "CHR-008",
                "Entity '" + e.name() + "' extends undefined entity '%s'");

        if (resolvedParent == e.parentRef().get()) {
            return e;
        }
        return new EntityDef(
                e.name(), e.traits(), e.docComments(),
                Optional.of(resolvedParent),
                e.fields(), e.invariants(),
                e.span());
    }

    private ActorDef resolveActor(ActorDef a, ResolverContext ctx) {
        if (a.parentRef().isEmpty()) {
            return a;
        }
        SymbolRef resolvedParent = RefResolver.resolve(
                ctx,
                a.parentRef().get(),
                ACTOR,
                "CHR-008",
                "Actor '" + a.name() + "' extends undefined actor '%s'");

        if (resolvedParent == a.parentRef().get()) {
            return a;
        }
        return new ActorDef(
                a.name(), a.traits(), a.docComments(),
                Optional.of(resolvedParent),
                a.span());
    }
}
