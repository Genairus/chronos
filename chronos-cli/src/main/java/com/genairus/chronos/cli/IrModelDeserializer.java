package com.genairus.chronos.cli;

import com.genairus.chronos.core.refs.NamespaceId;
import com.genairus.chronos.core.refs.QualifiedName;
import com.genairus.chronos.core.refs.ShapeId;
import com.genairus.chronos.core.refs.Span;
import com.genairus.chronos.core.refs.SymbolKind;
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
import com.genairus.chronos.ir.types.EventDef;
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
import com.genairus.chronos.ir.types.RelationshipSemantics;
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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Deserializes an {@link IrModel} from the JSON format produced by
 * {@link com.genairus.chronos.ir.json.IrModelSerializer}.
 *
 * <p>This class is the read-side counterpart to {@code IrModelSerializer}.
 * It is used by {@link IrBundleLoader} to reconstruct compiled IR models from
 * an {@code ir-bundle.json} file without recompiling {@code .chronos} sources.
 *
 * <p>Bundles are produced after finalization, so all {@link SymbolRef}s stored
 * in a well-formed bundle should be resolved. Unresolved refs are reconstructed
 * faithfully but should not occur in normal bundles.
 *
 * <p>The parse methods follow the same field ordering used by
 * {@code IrModelSerializer} (discriminator first, remaining fields alphabetical)
 * to simplify maintenance.
 */
final class IrModelDeserializer {

    /**
     * Deserializes an {@link IrModel} from a parsed {@link JsonObject}.
     *
     * <p>The object must match the format produced by
     * {@link com.genairus.chronos.ir.json.IrModelSerializer#toJson}.
     *
     * @param obj the parsed JSON object representing the model
     * @return the reconstructed {@link IrModel}
     */
    IrModel fromJson(JsonObject obj) {
        String namespace = obj.get("namespace").getAsString();
        List<UseDecl> imports = parseArray(obj.getAsJsonArray("imports"), this::parseUseDecl);
        List<IrShape> shapes  = parseArray(obj.getAsJsonArray("shapes"),  this::parseShape);
        return new IrModel(namespace, imports, shapes);
    }

    // ── UseDecl ────────────────────────────────────────────────────────────────
    // fields: name, namespace, span  (alphabetical)

    private UseDecl parseUseDecl(JsonObject obj) {
        String name      = obj.get("name").getAsString();
        String namespace = obj.get("namespace").getAsString();
        Span span        = parseSpan(obj.getAsJsonObject("span"));
        return new UseDecl(namespace, name, span);
    }

    // ── IrShape dispatch ───────────────────────────────────────────────────────

    private IrShape parseShape(JsonObject obj) {
        String kind = obj.get("kind").getAsString();
        return switch (kind) {
            case "entity"       -> parseEntity(obj);
            case "actor"        -> parseActor(obj);
            case "journey"      -> parseJourney(obj);
            case "shape"        -> parseShapeStruct(obj);
            case "enum"         -> parseEnum(obj);
            case "list"         -> parseList(obj);
            case "map"          -> parseMap(obj);
            case "policy"       -> parsePolicy(obj);
            case "relationship" -> parseRelationship(obj);
            case "invariant"    -> parseInvariant(obj);
            case "deny"         -> parseDeny(obj);
            case "error"        -> parseError(obj);
            case "statemachine" -> parseStateMachine(obj);
            case "role"         -> parseRole(obj);
            case "event"        -> parseEvent(obj);
            default -> throw new IllegalArgumentException("Unknown shape kind: " + kind);
        };
    }

    // EntityDef — kind(first), docComments, fields, invariants, name, parentRef, span, traits
    private EntityDef parseEntity(JsonObject obj) {
        List<String> docComments       = parseStringArray(obj.getAsJsonArray("docComments"));
        List<FieldDef> fields          = parseArray(obj.getAsJsonArray("fields"), this::parseField);
        List<EntityInvariant> invariants = parseArray(obj.getAsJsonArray("invariants"), this::parseEntityInvariant);
        String name                    = obj.get("name").getAsString();
        Optional<SymbolRef> parentRef  = parseOptionalSymbolRef(obj.get("parentRef"));
        Span span                      = parseSpan(obj.getAsJsonObject("span"));
        List<TraitApplication> traits  = parseArray(obj.getAsJsonArray("traits"), this::parseTraitApplication);
        return new EntityDef(name, traits, docComments, parentRef, fields, invariants, span);
    }

    // ActorDef — kind(first), docComments, name, parentRef, span, traits
    private ActorDef parseActor(JsonObject obj) {
        List<String> docComments      = parseStringArray(obj.getAsJsonArray("docComments"));
        String name                   = obj.get("name").getAsString();
        Optional<SymbolRef> parentRef = parseOptionalSymbolRef(obj.get("parentRef"));
        Span span                     = parseSpan(obj.getAsJsonObject("span"));
        List<TraitApplication> traits = parseArray(obj.getAsJsonArray("traits"), this::parseTraitApplication);
        return new ActorDef(name, traits, docComments, parentRef, span);
    }

    // JourneyDef — kind(first), actorRef, docComments, name, outcomesOrNull,
    //              preconditions, span, steps, traits, variants
    private JourneyDef parseJourney(JsonObject obj) {
        JsonElement actorRefElem  = obj.get("actorRef");
        SymbolRef actorRef        = (actorRefElem == null || actorRefElem.isJsonNull())
                ? null : parseSymbolRef(actorRefElem.getAsJsonObject());
        List<String> docComments  = parseStringArray(obj.getAsJsonArray("docComments"));
        String name               = obj.get("name").getAsString();
        JsonElement outcomesElem  = obj.get("outcomesOrNull");
        JourneyOutcomes outcomes  = (outcomesElem == null || outcomesElem.isJsonNull())
                ? null : parseJourneyOutcomes(outcomesElem.getAsJsonObject());
        List<String> preconditions = parseStringArray(obj.getAsJsonArray("preconditions"));
        Span span                  = parseSpan(obj.getAsJsonObject("span"));
        List<Step> steps           = parseArray(obj.getAsJsonArray("steps"), this::parseStep);
        List<TraitApplication> traits = parseArray(obj.getAsJsonArray("traits"), this::parseTraitApplication);
        Map<String, Variant> variants = parseVariantsMap(obj.getAsJsonObject("variants"));
        return new JourneyDef(name, traits, docComments, actorRef,
                preconditions, steps, variants, outcomes, span);
    }

    // ShapeStructDef — kind(first), docComments, fields, name, span, traits
    private ShapeStructDef parseShapeStruct(JsonObject obj) {
        List<String> docComments      = parseStringArray(obj.getAsJsonArray("docComments"));
        List<FieldDef> fields         = parseArray(obj.getAsJsonArray("fields"), this::parseField);
        String name                   = obj.get("name").getAsString();
        Span span                     = parseSpan(obj.getAsJsonObject("span"));
        List<TraitApplication> traits = parseArray(obj.getAsJsonArray("traits"), this::parseTraitApplication);
        return new ShapeStructDef(name, traits, docComments, fields, span);
    }

    // EnumDef — kind(first), docComments, members, name, span, traits
    private EnumDef parseEnum(JsonObject obj) {
        List<String> docComments      = parseStringArray(obj.getAsJsonArray("docComments"));
        List<EnumMember> members      = parseArray(obj.getAsJsonArray("members"), this::parseEnumMember);
        String name                   = obj.get("name").getAsString();
        Span span                     = parseSpan(obj.getAsJsonObject("span"));
        List<TraitApplication> traits = parseArray(obj.getAsJsonArray("traits"), this::parseTraitApplication);
        return new EnumDef(name, traits, docComments, members, span);
    }

    // ListDef — kind(first), docComments, memberType, name, span, traits
    private ListDef parseList(JsonObject obj) {
        List<String> docComments      = parseStringArray(obj.getAsJsonArray("docComments"));
        TypeRef memberType            = parseTypeRef(obj.getAsJsonObject("memberType"));
        String name                   = obj.get("name").getAsString();
        Span span                     = parseSpan(obj.getAsJsonObject("span"));
        List<TraitApplication> traits = parseArray(obj.getAsJsonArray("traits"), this::parseTraitApplication);
        return new ListDef(name, traits, docComments, memberType, span);
    }

    // MapDef — kind(first), docComments, keyType, name, span, traits, valueType
    private MapDef parseMap(JsonObject obj) {
        List<String> docComments      = parseStringArray(obj.getAsJsonArray("docComments"));
        TypeRef keyType               = parseTypeRef(obj.getAsJsonObject("keyType"));
        String name                   = obj.get("name").getAsString();
        Span span                     = parseSpan(obj.getAsJsonObject("span"));
        List<TraitApplication> traits = parseArray(obj.getAsJsonArray("traits"), this::parseTraitApplication);
        TypeRef valueType             = parseTypeRef(obj.getAsJsonObject("valueType"));
        return new MapDef(name, traits, docComments, keyType, valueType, span);
    }

    // PolicyDef — kind(first), description, docComments, name, span, traits
    private PolicyDef parsePolicy(JsonObject obj) {
        String description            = obj.get("description").getAsString();
        List<String> docComments      = parseStringArray(obj.getAsJsonArray("docComments"));
        String name                   = obj.get("name").getAsString();
        Span span                     = parseSpan(obj.getAsJsonObject("span"));
        List<TraitApplication> traits = parseArray(obj.getAsJsonArray("traits"), this::parseTraitApplication);
        return new PolicyDef(name, traits, docComments, description, span);
    }

    // RelationshipDef — kind(first), cardinality, docComments, fromEntityRef,
    //                   inverseField, name, semantics, span, toEntityRef, traits
    private RelationshipDef parseRelationship(JsonObject obj) {
        Cardinality cardinality         = Cardinality.valueOf(obj.get("cardinality").getAsString());
        List<String> docComments        = parseStringArray(obj.getAsJsonArray("docComments"));
        SymbolRef fromEntityRef         = parseSymbolRef(obj.getAsJsonObject("fromEntityRef"));
        JsonElement inverseFieldElem    = obj.get("inverseField");
        Optional<String> inverseField   = (inverseFieldElem == null || inverseFieldElem.isJsonNull())
                ? Optional.empty() : Optional.of(inverseFieldElem.getAsString());
        String name                     = obj.get("name").getAsString();
        JsonElement semanticsElem       = obj.get("semantics");
        Optional<RelationshipSemantics> semantics = (semanticsElem == null || semanticsElem.isJsonNull())
                ? Optional.empty()
                : Optional.of(RelationshipSemantics.valueOf(semanticsElem.getAsString()));
        Span span                       = parseSpan(obj.getAsJsonObject("span"));
        SymbolRef toEntityRef           = parseSymbolRef(obj.getAsJsonObject("toEntityRef"));
        List<TraitApplication> traits   = parseArray(obj.getAsJsonArray("traits"), this::parseTraitApplication);
        return new RelationshipDef(name, traits, docComments, fromEntityRef, toEntityRef,
                cardinality, semantics, inverseField, span);
    }

    // InvariantDef — kind(first), docComments, expression, message, name, scope, severity, span, traits
    private InvariantDef parseInvariant(JsonObject obj) {
        List<String> docComments      = parseStringArray(obj.getAsJsonArray("docComments"));
        String expression             = obj.get("expression").getAsString();
        JsonElement msgElem           = obj.get("message");
        Optional<String> message      = (msgElem == null || msgElem.isJsonNull())
                ? Optional.empty() : Optional.of(msgElem.getAsString());
        String name                   = obj.get("name").getAsString();
        List<String> scope            = parseStringArray(obj.getAsJsonArray("scope"));
        String severity               = obj.get("severity").getAsString();
        Span span                     = parseSpan(obj.getAsJsonObject("span"));
        List<TraitApplication> traits = parseArray(obj.getAsJsonArray("traits"), this::parseTraitApplication);
        return new InvariantDef(name, traits, docComments, scope, expression, severity, message, span);
    }

    // DenyDef — kind(first), description, docComments, name, scope, severity, span, traits
    private DenyDef parseDeny(JsonObject obj) {
        String description            = obj.get("description").getAsString();
        List<String> docComments      = parseStringArray(obj.getAsJsonArray("docComments"));
        String name                   = obj.get("name").getAsString();
        List<String> scope            = parseStringArray(obj.getAsJsonArray("scope"));
        String severity               = obj.get("severity").getAsString();
        Span span                     = parseSpan(obj.getAsJsonObject("span"));
        List<TraitApplication> traits = parseArray(obj.getAsJsonArray("traits"), this::parseTraitApplication);
        return new DenyDef(name, traits, docComments, description, scope, severity, span);
    }

    // ErrorDef — kind(first), code, docComments, message, name, payload, recoverable, severity, span, traits
    private ErrorDef parseError(JsonObject obj) {
        String code                   = obj.get("code").getAsString();
        List<String> docComments      = parseStringArray(obj.getAsJsonArray("docComments"));
        String message                = obj.get("message").getAsString();
        String name                   = obj.get("name").getAsString();
        List<FieldDef> payload        = parseArray(obj.getAsJsonArray("payload"), this::parseField);
        boolean recoverable           = obj.get("recoverable").getAsBoolean();
        String severity               = obj.get("severity").getAsString();
        Span span                     = parseSpan(obj.getAsJsonObject("span"));
        List<TraitApplication> traits = parseArray(obj.getAsJsonArray("traits"), this::parseTraitApplication);
        return new ErrorDef(name, traits, docComments, code, severity, recoverable, message, payload, span);
    }

    // StateMachineDef — kind(first), docComments, entityName, fieldName, initialState,
    //                   name, span, states, terminalStates, traits, transitions
    private StateMachineDef parseStateMachine(JsonObject obj) {
        List<String> docComments       = parseStringArray(obj.getAsJsonArray("docComments"));
        String entityName              = obj.get("entityName").getAsString();
        String fieldName               = obj.get("fieldName").getAsString();
        String initialState            = obj.get("initialState").getAsString();
        String name                    = obj.get("name").getAsString();
        Span span                      = parseSpan(obj.getAsJsonObject("span"));
        List<String> states            = parseStringArray(obj.getAsJsonArray("states"));
        List<String> terminalStates    = parseStringArray(obj.getAsJsonArray("terminalStates"));
        List<TraitApplication> traits  = parseArray(obj.getAsJsonArray("traits"), this::parseTraitApplication);
        List<Transition> transitions   = parseArray(obj.getAsJsonArray("transitions"), this::parseTransition);
        return new StateMachineDef(name, traits, docComments, entityName, fieldName,
                states, initialState, terminalStates, transitions, span);
    }

    // RoleDef — kind(first), allowedPermissions, deniedPermissions, docComments, name, span, traits
    private RoleDef parseRole(JsonObject obj) {
        List<String> allowedPermissions = parseStringArray(obj.getAsJsonArray("allowedPermissions"));
        List<String> deniedPermissions  = parseStringArray(obj.getAsJsonArray("deniedPermissions"));
        List<String> docComments        = parseStringArray(obj.getAsJsonArray("docComments"));
        String name                     = obj.get("name").getAsString();
        Span span                       = parseSpan(obj.getAsJsonObject("span"));
        List<TraitApplication> traits   = parseArray(obj.getAsJsonArray("traits"), this::parseTraitApplication);
        return new RoleDef(name, traits, docComments, allowedPermissions, deniedPermissions, span);
    }

    // EventDef — kind(first), docComments, fields, name, span, traits
    private EventDef parseEvent(JsonObject obj) {
        List<String> docComments      = parseStringArray(obj.getAsJsonArray("docComments"));
        List<FieldDef> fields         = parseArray(obj.getAsJsonArray("fields"), this::parseField);
        String name                   = obj.get("name").getAsString();
        Span span                     = parseSpan(obj.getAsJsonObject("span"));
        List<TraitApplication> traits = parseArray(obj.getAsJsonArray("traits"), this::parseTraitApplication);
        return new EventDef(name, traits, docComments, fields, span);
    }

    // ── Supporting types ───────────────────────────────────────────────────────

    // FieldDef: name, span, traits, type
    private FieldDef parseField(JsonObject obj) {
        String name                   = obj.get("name").getAsString();
        Span span                     = parseSpan(obj.getAsJsonObject("span"));
        List<TraitApplication> traits = parseArray(obj.getAsJsonArray("traits"), this::parseTraitApplication);
        TypeRef type                  = parseTypeRef(obj.getAsJsonObject("type"));
        return new FieldDef(name, type, traits, span);
    }

    // EnumMember: name, ordinalOrNull, span
    private EnumMember parseEnumMember(JsonObject obj) {
        String name                = obj.get("name").getAsString();
        JsonElement ordinalElem    = obj.get("ordinalOrNull");
        Integer ordinalOrNull      = (ordinalElem == null || ordinalElem.isJsonNull())
                ? null : ordinalElem.getAsInt();
        Span span                  = parseSpan(obj.getAsJsonObject("span"));
        return new EnumMember(name, ordinalOrNull, span);
    }

    // EntityInvariant: expression, message, name, severity, span
    private EntityInvariant parseEntityInvariant(JsonObject obj) {
        String expression       = obj.get("expression").getAsString();
        JsonElement msgElem     = obj.get("message");
        Optional<String> message = (msgElem == null || msgElem.isJsonNull())
                ? Optional.empty() : Optional.of(msgElem.getAsString());
        String name             = obj.get("name").getAsString();
        String severity         = obj.get("severity").getAsString();
        Span span               = parseSpan(obj.getAsJsonObject("span"));
        return new EntityInvariant(name, expression, severity, message, span);
    }

    // TraitApplication: args, name, span
    private TraitApplication parseTraitApplication(JsonObject obj) {
        List<TraitArg> args = parseArray(obj.getAsJsonArray("args"), this::parseTraitArg);
        String name         = obj.get("name").getAsString();
        Span span           = parseSpan(obj.getAsJsonObject("span"));
        return new TraitApplication(name, args, span);
    }

    // TraitArg: keyOrNull, span, value
    private TraitArg parseTraitArg(JsonObject obj) {
        JsonElement keyElem  = obj.get("keyOrNull");
        String keyOrNull     = (keyElem == null || keyElem.isJsonNull())
                ? null : keyElem.getAsString();
        Span span            = parseSpan(obj.getAsJsonObject("span"));
        TraitValue value     = parseTraitValue(obj.getAsJsonObject("value"));
        return new TraitArg(keyOrNull, value, span);
    }

    // TraitValue: kind + value/ref
    private TraitValue parseTraitValue(JsonObject obj) {
        String kind = obj.get("kind").getAsString();
        return switch (kind) {
            case "string" -> new TraitValue.StringValue(obj.get("value").getAsString());
            case "number" -> new TraitValue.NumberValue(obj.get("value").getAsDouble());
            case "bool"   -> new TraitValue.BoolValue(obj.get("value").getAsBoolean());
            case "ref"    -> new TraitValue.ReferenceValue(obj.get("ref").getAsString());
            default -> throw new IllegalArgumentException("Unknown TraitValue kind: " + kind);
        };
    }

    // Step: fields, name, span, traits
    private Step parseStep(JsonObject obj) {
        List<StepField> fields        = parseArray(obj.getAsJsonArray("fields"), this::parseStepField);
        String name                   = obj.get("name").getAsString();
        Span span                     = parseSpan(obj.getAsJsonObject("span"));
        List<TraitApplication> traits = parseArray(obj.getAsJsonArray("traits"), this::parseTraitApplication);
        return new Step(name, traits, fields, span);
    }

    // StepField: dispatched on "kind"
    private StepField parseStepField(JsonObject obj) {
        String kind = obj.get("kind").getAsString();
        Span span   = parseSpan(obj.getAsJsonObject("span"));
        return switch (kind) {
            case "action"      -> new StepField.Action(obj.get("text").getAsString(), span);
            case "expectation" -> new StepField.Expectation(obj.get("text").getAsString(), span);
            case "outcome"     -> new StepField.Outcome(
                    parseOutcomeExpr(obj.getAsJsonObject("expr")), span);
            case "telemetry"   -> new StepField.Telemetry(
                    parseStringArray(obj.getAsJsonArray("ids")), span);
            case "risk"        -> new StepField.Risk(obj.get("text").getAsString(), span);
            case "input"       -> new StepField.Input(
                    parseArray(obj.getAsJsonArray("fields"), this::parseDataField), span);
            case "output"      -> new StepField.Output(
                    parseArray(obj.getAsJsonArray("fields"), this::parseDataField), span);
            default -> throw new IllegalArgumentException("Unknown StepField kind: " + kind);
        };
    }

    // DataField: name, span, type
    private DataField parseDataField(JsonObject obj) {
        String name  = obj.get("name").getAsString();
        Span span    = parseSpan(obj.getAsJsonObject("span"));
        TypeRef type = parseTypeRef(obj.getAsJsonObject("type"));
        return new DataField(name, type, span);
    }

    // Variant: name, outcomeOrNull, span, steps, triggerName
    private Variant parseVariant(JsonObject obj) {
        String name              = obj.get("name").getAsString();
        JsonElement outcomeElem  = obj.get("outcomeOrNull");
        OutcomeExpr outcomeOrNull = (outcomeElem == null || outcomeElem.isJsonNull())
                ? null : parseOutcomeExpr(outcomeElem.getAsJsonObject());
        Span span                = parseSpan(obj.getAsJsonObject("span"));
        List<Step> steps         = parseArray(obj.getAsJsonArray("steps"), this::parseStep);
        String triggerName       = obj.get("triggerName").getAsString();
        return new Variant(name, triggerName, steps, outcomeOrNull, span);
    }

    // variants stored as JSON object: { "VariantName": { ... }, ... }
    private Map<String, Variant> parseVariantsMap(JsonObject obj) {
        if (obj == null) return Map.of();
        Map<String, Variant> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            result.put(entry.getKey(), parseVariant(entry.getValue().getAsJsonObject()));
        }
        return Collections.unmodifiableMap(result);
    }

    // OutcomeExpr: kind + span + stateId / stepId
    private OutcomeExpr parseOutcomeExpr(JsonObject obj) {
        String kind = obj.get("kind").getAsString();
        Span span   = parseSpan(obj.getAsJsonObject("span"));
        return switch (kind) {
            case "transition" -> new OutcomeExpr.TransitionTo(obj.get("stateId").getAsString(), span);
            case "return"     -> new OutcomeExpr.ReturnToStep(obj.get("stepId").getAsString(), span);
            default -> throw new IllegalArgumentException("Unknown OutcomeExpr kind: " + kind);
        };
    }

    // JourneyOutcomes: failureOrNull, span, successOrNull
    private JourneyOutcomes parseJourneyOutcomes(JsonObject obj) {
        JsonElement failureElem  = obj.get("failureOrNull");
        String failureOrNull     = (failureElem == null || failureElem.isJsonNull())
                ? null : failureElem.getAsString();
        Span span                = parseSpan(obj.getAsJsonObject("span"));
        JsonElement successElem  = obj.get("successOrNull");
        String successOrNull     = (successElem == null || successElem.isJsonNull())
                ? null : successElem.getAsString();
        return new JourneyOutcomes(successOrNull, failureOrNull, span);
    }

    // Transition: action, fromState, guard, span, toState
    private Transition parseTransition(JsonObject obj) {
        JsonElement actionElem    = obj.get("action");
        Optional<String> action   = (actionElem == null || actionElem.isJsonNull())
                ? Optional.empty() : Optional.of(actionElem.getAsString());
        String fromState          = obj.get("fromState").getAsString();
        JsonElement guardElem     = obj.get("guard");
        Optional<String> guard    = (guardElem == null || guardElem.isJsonNull())
                ? Optional.empty() : Optional.of(guardElem.getAsString());
        Span span                 = parseSpan(obj.getAsJsonObject("span"));
        String toState            = obj.get("toState").getAsString();
        return new Transition(fromState, toState, guard, action, span);
    }

    // ── TypeRef ────────────────────────────────────────────────────────────────
    // typeKind(discriminator, first), then alphabetical remaining

    private TypeRef parseTypeRef(JsonObject obj) {
        String typeKind = obj.get("typeKind").getAsString();
        return switch (typeKind) {
            case "primitive" -> new TypeRef.PrimitiveType(
                    TypeRef.PrimitiveKind.valueOf(obj.get("kind").getAsString()));
            case "list"      -> new TypeRef.ListType(
                    parseTypeRef(obj.getAsJsonObject("elementType")));
            case "map"       -> new TypeRef.MapType(
                    parseTypeRef(obj.getAsJsonObject("keyType")),
                    parseTypeRef(obj.getAsJsonObject("valueType")));
            case "named"     -> new TypeRef.NamedTypeRef(
                    parseSymbolRef(obj.getAsJsonObject("ref")));
            default -> throw new IllegalArgumentException("Unknown typeKind: " + typeKind);
        };
    }

    // ── Span ───────────────────────────────────────────────────────────────────
    // fields: endCol, endLine, sourceName, startCol, startLine  (alphabetical)

    private Span parseSpan(JsonObject obj) {
        int endCol      = obj.get("endCol").getAsInt();
        int endLine     = obj.get("endLine").getAsInt();
        String source   = obj.get("sourceName").getAsString();
        int startCol    = obj.get("startCol").getAsInt();
        int startLine   = obj.get("startLine").getAsInt();
        return new Span(source, startLine, startCol, endLine, endCol);
    }

    // ── SymbolRef ──────────────────────────────────────────────────────────────
    // Resolved form:   { id: { name, namespace: { value } }, kind, resolved: true, span }
    // Unresolved form: { kind, name: { name, namespaceOrNull }, resolved: false, span }

    private Optional<SymbolRef> parseOptionalSymbolRef(JsonElement elem) {
        if (elem == null || elem.isJsonNull()) return Optional.empty();
        return Optional.of(parseSymbolRef(elem.getAsJsonObject()));
    }

    private SymbolRef parseSymbolRef(JsonObject obj) {
        boolean resolved  = obj.get("resolved").getAsBoolean();
        SymbolKind kind   = SymbolKind.valueOf(obj.get("kind").getAsString());
        Span span         = parseSpan(obj.getAsJsonObject("span"));

        if (resolved) {
            JsonObject idObj = obj.getAsJsonObject("id");
            String name      = idObj.get("name").getAsString();
            String nsValue   = idObj.getAsJsonObject("namespace").get("value").getAsString();
            ShapeId id       = ShapeId.of(nsValue, name);
            return SymbolRef.resolved(kind, id, span);
        } else {
            JsonObject nameObj    = obj.getAsJsonObject("name");
            String name           = nameObj.get("name").getAsString();
            JsonElement nsElem    = nameObj.get("namespaceOrNull");
            String namespaceOrNull = (nsElem == null || nsElem.isJsonNull())
                    ? null : nsElem.getAsString();
            QualifiedName qn = namespaceOrNull != null
                    ? QualifiedName.qualified(namespaceOrNull, name)
                    : QualifiedName.local(name);
            return SymbolRef.unresolved(kind, qn, span);
        }
    }

    // ── Generic helpers ────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface ElemParser<T> {
        T parse(JsonObject obj);
    }

    private <T> List<T> parseArray(JsonArray arr, ElemParser<T> parser) {
        if (arr == null) return List.of();
        List<T> result = new ArrayList<>(arr.size());
        for (JsonElement elem : arr) {
            result.add(parser.parse(elem.getAsJsonObject()));
        }
        return Collections.unmodifiableList(result);
    }

    private List<String> parseStringArray(JsonArray arr) {
        if (arr == null) return List.of();
        List<String> result = new ArrayList<>(arr.size());
        for (JsonElement elem : arr) {
            result.add(elem.getAsString());
        }
        return Collections.unmodifiableList(result);
    }
}
