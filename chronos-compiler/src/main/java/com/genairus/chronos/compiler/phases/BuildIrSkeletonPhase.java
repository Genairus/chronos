package com.genairus.chronos.compiler.phases;

import com.genairus.chronos.compiler.ResolverContext;
import com.genairus.chronos.core.refs.*;
import com.genairus.chronos.ir.model.IrModel;
import com.genairus.chronos.ir.types.*;
import com.genairus.chronos.syntax.*;

import java.util.*;

/**
 * Pass 3: Converts the {@link SyntaxModel} into an {@link IrModel}.
 *
 * <p>All names are preserved as raw strings — no symbol resolution is performed
 * here.  {@link TypeRef.NamedTypeRef} values carry the identifier exactly as
 * written in source; resolution occurs in subsequent passes.
 *
 * <p>Cross-links (journey actor, relationship targets, inheritance parents) are
 * stored as {@link SymbolRef#unresolved unresolved} {@link SymbolRef}s so that
 * later passes can validate and finalize them.
 *
 * <p>Doc-comment lines from {@code ///} comment tokens are now available in the
 * Syntax DTO layer and are forwarded to the corresponding IR shape's
 * {@code docComments} field.
 */
public final class BuildIrSkeletonPhase implements ResolverPhase<SyntaxModel, IrModel> {

    @Override
    public IrModel execute(SyntaxModel syntax, ResolverContext ctx) {
        return new IrBuilder().build(syntax);
    }

    // ── IrBuilder ─────────────────────────────────────────────────────────────

    private static final class IrBuilder {

        IrModel build(SyntaxModel syntax) {
            List<UseDecl> imports = syntax.imports().stream()
                    .map(this::convertUseDecl)
                    .toList();

            List<IrShape> shapes = syntax.declarations().stream()
                    .map(this::convertDecl)
                    .toList();

            return new IrModel(syntax.namespace(), imports, shapes);
        }

        // ── Use declarations ──────────────────────────────────────────────────

        private UseDecl convertUseDecl(SyntaxUseDecl u) {
            var qname = u.name();
            String ns = qname.namespaceOrNull() != null ? qname.namespaceOrNull() : "";
            return new UseDecl(ns, qname.name(), u.span());
        }

        // ── Shape declarations ─────────────────────────────────────────────────

        private IrShape convertDecl(SyntaxDecl decl) {
            return switch (decl) {
                case SyntaxEntityDecl       d -> convertEntity(d);
                case SyntaxShapeDecl        d -> convertShape(d);
                case SyntaxEnumDecl         d -> convertEnum(d);
                case SyntaxListDecl         d -> convertList(d);
                case SyntaxMapDecl          d -> convertMap(d);
                case SyntaxActorDecl        d -> convertActor(d);
                case SyntaxPolicyDecl       d -> convertPolicy(d);
                case SyntaxJourneyDecl      d -> convertJourney(d);
                case SyntaxRelationshipDecl d -> convertRelationship(d);
                case SyntaxInvariantDecl    d -> convertInvariant(d);
                case SyntaxDenyDecl         d -> convertDeny(d);
                case SyntaxErrorDecl        d -> convertError(d);
                case SyntaxStateMachineDecl d -> convertStateMachine(d);
                case SyntaxRoleDecl         d -> convertRole(d);
                case SyntaxEventDecl        d -> convertEvent(d);
            };
        }

        private EntityDef convertEntity(SyntaxEntityDecl d) {
            Optional<SymbolRef> parentRef = d.parentOrNull() != null
                    ? Optional.of(SymbolRef.unresolved(
                            SymbolKind.ENTITY, QualifiedName.local(d.parentOrNull()), d.span()))
                    : Optional.empty();
            return new EntityDef(
                    d.name(),
                    convertTraits(d.traits()),
                    d.docComments(),
                    parentRef,
                    d.fields().stream().map(this::convertField).toList(),
                    d.invariants().stream().map(this::convertEntityInvariant).toList(),
                    d.span());
        }

        private ShapeStructDef convertShape(SyntaxShapeDecl d) {
            return new ShapeStructDef(
                    d.name(),
                    convertTraits(d.traits()),
                    d.docComments(),
                    d.fields().stream().map(this::convertField).toList(),
                    d.span());
        }

        private EnumDef convertEnum(SyntaxEnumDecl d) {
            List<EnumMember> members = d.members().stream()
                    .map(m -> m.ordinalOrNull() != null
                            ? EnumMember.of(m.name(), m.ordinalOrNull(), m.span())
                            : EnumMember.of(m.name(), m.span()))
                    .toList();
            return new EnumDef(d.name(), convertTraits(d.traits()), d.docComments(), members, d.span());
        }

        private ListDef convertList(SyntaxListDecl d) {
            return new ListDef(
                    d.name(),
                    convertTraits(d.traits()),
                    d.docComments(),
                    convertTypeRef(d.memberType()),
                    d.span());
        }

        private MapDef convertMap(SyntaxMapDecl d) {
            return new MapDef(
                    d.name(),
                    convertTraits(d.traits()),
                    d.docComments(),
                    convertTypeRef(d.keyType()),
                    convertTypeRef(d.valueType()),
                    d.span());
        }

        private ActorDef convertActor(SyntaxActorDecl d) {
            Optional<SymbolRef> parentRef = d.parentOrNull() != null
                    ? Optional.of(SymbolRef.unresolved(
                            SymbolKind.ACTOR, QualifiedName.local(d.parentOrNull()), d.span()))
                    : Optional.empty();
            return new ActorDef(
                    d.name(),
                    convertTraits(d.traits()),
                    d.docComments(),
                    parentRef,
                    d.span());
        }

        private PolicyDef convertPolicy(SyntaxPolicyDecl d) {
            return new PolicyDef(
                    d.name(),
                    convertTraits(d.traits()),
                    d.docComments(),
                    d.description(),
                    d.span());
        }

        private RelationshipDef convertRelationship(SyntaxRelationshipDecl d) {
            Cardinality cardinality = Cardinality.fromChronosName(d.cardinality());
            Optional<RelationshipSemantics> semantics = d.semanticsOrNull() != null
                    ? Optional.of(RelationshipSemantics.fromChronosName(d.semanticsOrNull()))
                    : Optional.empty();
            SymbolRef fromRef = SymbolRef.unresolved(
                    SymbolKind.ENTITY, QualifiedName.local(d.fromEntity()), d.span());
            SymbolRef toRef = SymbolRef.unresolved(
                    SymbolKind.ENTITY, QualifiedName.local(d.toEntity()), d.span());
            return new RelationshipDef(
                    d.name(),
                    convertTraits(d.traits()),
                    d.docComments(),
                    fromRef,
                    toRef,
                    cardinality,
                    semantics,
                    Optional.ofNullable(d.inverseOrNull()),
                    d.span());
        }

        private InvariantDef convertInvariant(SyntaxInvariantDecl d) {
            return new InvariantDef(
                    d.name(),
                    convertTraits(d.traits()),
                    d.docComments(),
                    d.scope(),
                    d.expression(),
                    d.severity(),
                    Optional.ofNullable(d.messageOrNull()),
                    d.span());
        }

        private DenyDef convertDeny(SyntaxDenyDecl d) {
            return new DenyDef(
                    d.name(),
                    convertTraits(d.traits()),
                    d.docComments(),
                    d.description(),
                    d.scope(),
                    d.severity(),
                    d.span());
        }

        private ErrorDef convertError(SyntaxErrorDecl d) {
            return new ErrorDef(
                    d.name(),
                    convertTraits(d.traits()),
                    d.docComments(),
                    d.code(),
                    d.severity(),
                    d.recoverable(),
                    d.message(),
                    d.payload().stream().map(this::convertField).toList(),
                    d.span());
        }

        private RoleDef convertRole(SyntaxRoleDecl d) {
            return new RoleDef(
                    d.name(),
                    convertTraits(d.traits()),
                    d.docComments(),
                    d.allowedPermissions(),
                    d.deniedPermissions(),
                    d.span());
        }

        private EventDef convertEvent(SyntaxEventDecl d) {
            return new EventDef(
                    d.name(),
                    convertTraits(d.traits()),
                    d.docComments(),
                    d.fields().stream().map(this::convertField).toList(),
                    d.span());
        }

        private StateMachineDef convertStateMachine(SyntaxStateMachineDecl d) {
            return new StateMachineDef(
                    d.name(),
                    convertTraits(d.traits()),
                    d.docComments(),
                    d.entityName(),
                    d.fieldName(),
                    d.states(),
                    d.initialState(),
                    d.terminalStates(),
                    d.transitions().stream().map(this::convertTransition).toList(),
                    d.span());
        }

        private JourneyDef convertJourney(SyntaxJourneyDecl d) {
            SymbolRef actorRef = d.actorOrNull() != null
                    ? SymbolRef.unresolved(
                            SymbolKind.ACTOR,
                            QualifiedName.local(d.actorOrNull()),
                            d.span())
                    : null;
            List<Step> steps = d.steps().stream().map(this::convertStep).toList();
            Map<String, Variant> variants = new LinkedHashMap<>();
            for (SyntaxVariant sv : d.variants()) {
                Variant v = convertVariant(sv);
                variants.put(v.name(), v);
            }
            JourneyOutcomes outcomes = d.outcomesOrNull() != null
                    ? new JourneyOutcomes(
                            d.outcomesOrNull().successOrNull(),
                            d.outcomesOrNull().failureOrNull(),
                            d.outcomesOrNull().span())
                    : null;
            return new JourneyDef(
                    d.name(),
                    convertTraits(d.traits()),
                    d.docComments(),
                    actorRef,
                    d.preconditions(),
                    steps,
                    Collections.unmodifiableMap(variants),
                    outcomes,
                    d.span());
        }

        // ── Steps / Variants ──────────────────────────────────────────────────

        private Step convertStep(SyntaxStep s) {
            List<StepField> fields = s.fields().stream().map(this::convertStepField).toList();
            return new Step(s.name(), convertTraits(s.traits()), fields, s.span());
        }

        private StepField convertStepField(SyntaxStepField sf) {
            return switch (sf) {
                case SyntaxStepField.Action      f -> new StepField.Action(f.text(), f.span());
                case SyntaxStepField.Expectation f -> new StepField.Expectation(f.text(), f.span());
                case SyntaxStepField.Outcome     f -> new StepField.Outcome(convertOutcomeExpr(f.expr()), f.span());
                case SyntaxStepField.Telemetry   f -> new StepField.Telemetry(f.ids(), f.span());
                case SyntaxStepField.Risk        f -> new StepField.Risk(f.text(), f.span());
                case SyntaxStepField.Input       f -> new StepField.Input(
                        f.fields().stream().map(this::convertDataField).toList(), f.span());
                case SyntaxStepField.Output      f -> new StepField.Output(
                        f.fields().stream().map(this::convertDataField).toList(), f.span());
            };
        }

        private DataField convertDataField(SyntaxDataField df) {
            return new DataField(df.name(), convertTypeRef(df.type()), df.span());
        }

        private OutcomeExpr convertOutcomeExpr(SyntaxOutcomeExpr expr) {
            return switch (expr) {
                case SyntaxOutcomeExpr.TransitionTo e -> new OutcomeExpr.TransitionTo(e.stateId(), e.span());
                case SyntaxOutcomeExpr.ReturnToStep e -> new OutcomeExpr.ReturnToStep(e.stepId(), e.span());
            };
        }

        private Variant convertVariant(SyntaxVariant sv) {
            OutcomeExpr outcome = sv.outcomeOrNull() != null
                    ? convertOutcomeExpr(sv.outcomeOrNull()) : null;
            return new Variant(
                    sv.name(),
                    sv.triggerName(),
                    sv.steps().stream().map(this::convertStep).toList(),
                    outcome,
                    sv.span());
        }

        private Transition convertTransition(SyntaxTransition t) {
            return new Transition(
                    t.fromState(),
                    t.toState(),
                    Optional.ofNullable(t.guardOrNull()),
                    Optional.ofNullable(t.actionOrNull()),
                    t.span());
        }

        // ── Fields / Invariants ───────────────────────────────────────────────

        private FieldDef convertField(SyntaxFieldDef f) {
            return new FieldDef(f.name(), convertTypeRef(f.type()), convertTraits(f.traits()), f.span());
        }

        private EntityInvariant convertEntityInvariant(SyntaxEntityInvariant inv) {
            return new EntityInvariant(
                    inv.name(),
                    inv.expression(),
                    inv.severity(),
                    Optional.ofNullable(inv.messageOrNull()),
                    inv.span());
        }

        // ── Traits ────────────────────────────────────────────────────────────

        private List<TraitApplication> convertTraits(List<SyntaxTrait> traits) {
            return traits.stream().map(this::convertTrait).toList();
        }

        private TraitApplication convertTrait(SyntaxTrait t) {
            List<TraitArg> args = t.args().stream().map(this::convertTraitArg).toList();
            return new TraitApplication(t.name(), args, t.span());
        }

        private TraitArg convertTraitArg(SyntaxTraitArg a) {
            return new TraitArg(a.keyOrNull(), convertTraitValue(a.value()), a.span());
        }

        private TraitValue convertTraitValue(SyntaxTraitValue v) {
            return switch (v) {
                case SyntaxTraitValue.StringVal sv -> new TraitValue.StringValue(sv.value());
                case SyntaxTraitValue.NumberVal nv -> new TraitValue.NumberValue(nv.value());
                case SyntaxTraitValue.BoolVal   bv -> new TraitValue.BoolValue(bv.value());
                case SyntaxTraitValue.RefVal    rv -> new TraitValue.ReferenceValue(rv.ref());
            };
        }

        // ── Type references ───────────────────────────────────────────────────

        private TypeRef convertTypeRef(SyntaxTypeRef ref) {
            return switch (ref) {
                case SyntaxTypeRef.Primitive p ->
                        new TypeRef.PrimitiveType(TypeRef.PrimitiveKind.valueOf(p.kind().name()));
                case SyntaxTypeRef.Named n ->
                        new TypeRef.NamedTypeRef(
                                SymbolRef.unresolved(SymbolKind.TYPE, QualifiedName.local(n.name()), n.span()));
                case SyntaxTypeRef.ListType l ->
                        new TypeRef.ListType(convertTypeRef(l.element()));
                case SyntaxTypeRef.MapType m ->
                        new TypeRef.MapType(convertTypeRef(m.key()), convertTypeRef(m.value()));
            };
        }
    }
}
