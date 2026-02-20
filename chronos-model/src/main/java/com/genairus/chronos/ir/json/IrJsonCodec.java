package com.genairus.chronos.ir.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.genairus.chronos.core.refs.NamespaceId;
import com.genairus.chronos.core.refs.QualifiedName;
import com.genairus.chronos.core.refs.ShapeId;
import com.genairus.chronos.core.refs.Span;
import com.genairus.chronos.core.refs.SymbolKind;
import com.genairus.chronos.core.refs.SymbolRef;
import com.genairus.chronos.ir.model.IrModel;
import com.genairus.chronos.ir.types.ActorDef;
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
import com.genairus.chronos.ir.types.OutcomeExpr;
import com.genairus.chronos.ir.types.PolicyDef;
import com.genairus.chronos.ir.types.RelationshipDef;
import com.genairus.chronos.ir.types.ShapeStructDef;
import com.genairus.chronos.ir.types.StateMachineDef;
import com.genairus.chronos.ir.types.StepField;
import com.genairus.chronos.ir.types.TraitValue;
import com.genairus.chronos.ir.types.TypeRef;

import java.io.IOException;

/**
 * Canonical JSON codec for {@link IrModel}.
 *
 * <p>Serialises and deserialises an IR model to/from a deterministic, pretty-printed JSON
 * representation suitable for snapshot tests and inter-tool consumption.
 *
 * <h2>Determinism</h2>
 * <ul>
 *   <li>Object properties are sorted alphabetically ({@link MapperFeature#SORT_PROPERTIES_ALPHABETICALLY}).</li>
 *   <li>Map entries are sorted by key ({@link SerializationFeature#ORDER_MAP_ENTRIES_BY_KEYS}).</li>
 * </ul>
 *
 * <h2>Polymorphism</h2>
 * Sealed interface hierarchies carry a type discriminator:
 * <ul>
 *   <li>{@link IrShape} — {@code "kind"} property (e.g. {@code "entity"}, {@code "journey"})</li>
 *   <li>{@link TypeRef}  — {@code "typeKind"} property (avoids collision with
 *       {@link TypeRef.PrimitiveType#kind()})</li>
 *   <li>{@link TraitValue}, {@link OutcomeExpr}, {@link StepField} — {@code "kind"} property</li>
 * </ul>
 *
 * <h2>SymbolRef</h2>
 * {@link SymbolRef} uses a custom serializer/deserializer because its constructor is private.
 * Resolved and unresolved forms carry different property sets (see serializer javadoc).
 */
public final class IrJsonCodec {

    private static final ObjectMapper MAPPER = buildMapper();

    private IrJsonCodec() {}

    /**
     * Serialises an {@link IrModel} to a pretty-printed, deterministic JSON string.
     */
    public static String toJson(IrModel model) {
        try {
            return MAPPER.writeValueAsString(model);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize IrModel to JSON", e);
        }
    }

    /**
     * Deserialises an {@link IrModel} from a JSON string produced by {@link #toJson}.
     *
     * @throws IllegalArgumentException if the JSON is invalid or contains unknown properties
     */
    public static IrModel fromJson(String json) {
        try {
            return MAPPER.readValue(json, IrModel.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize IrModel from JSON", e);
        }
    }

    // ── ObjectMapper factory ─────────────────────────────────────────────────────

    private static ObjectMapper buildMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Deterministic output
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        mapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);

        // Strict: reject unknown properties on read
        mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // Optional<T> support + constructor parameter name resolution
        mapper.registerModule(new Jdk8Module());
        mapper.registerModule(new ParameterNamesModule());

        // Sealed interface polymorphism
        mapper.addMixIn(IrShape.class,     IrShapeMixin.class);
        mapper.addMixIn(TypeRef.class,     TypeRefMixin.class);
        mapper.addMixIn(TraitValue.class,  TraitValueMixin.class);
        mapper.addMixIn(OutcomeExpr.class, OutcomeExprMixin.class);
        mapper.addMixIn(StepField.class,   StepFieldMixin.class);

        // chronos-core records: compiled without -parameters, so provide explicit creators
        mapper.addMixIn(Span.class,          SpanMixin.class);
        mapper.addMixIn(NamespaceId.class,   NamespaceIdMixin.class);
        mapper.addMixIn(QualifiedName.class, QualifiedNameMixin.class);
        mapper.addMixIn(ShapeId.class,       ShapeIdMixin.class);

        // Suppress derived convenience methods that are not record components.
        // Jackson bean introspection would otherwise serialize isXxx()/getXxx() helpers
        // as additional JSON properties, breaking round-trip deserialization.
        mapper.addMixIn(FieldDef.class,               FieldDefMixin.class);
        mapper.addMixIn(ActorDef.class,               ActorDefMixin.class);
        mapper.addMixIn(JourneyDef.class,             JourneyDefMixin.class);
        mapper.addMixIn(PolicyDef.class,              PolicyDefMixin.class);
        mapper.addMixIn(TypeRef.NamedTypeRef.class,   NamedTypeRefMixin.class);

        // SymbolRef — private constructor requires custom handling
        SimpleModule symbolRefModule = new SimpleModule("SymbolRefModule");
        symbolRefModule.addSerializer(SymbolRef.class,   new SymbolRefSerializer());
        symbolRefModule.addDeserializer(SymbolRef.class, new SymbolRefDeserializer());
        mapper.registerModule(symbolRefModule);

        return mapper;
    }

    // ── Sealed-interface mixins ──────────────────────────────────────────────────

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = ActorDef.class,        name = "actor"),
        @JsonSubTypes.Type(value = DenyDef.class,         name = "deny"),
        @JsonSubTypes.Type(value = EntityDef.class,       name = "entity"),
        @JsonSubTypes.Type(value = EnumDef.class,         name = "enum"),
        @JsonSubTypes.Type(value = ErrorDef.class,        name = "error"),
        @JsonSubTypes.Type(value = InvariantDef.class,    name = "invariant"),
        @JsonSubTypes.Type(value = JourneyDef.class,      name = "journey"),
        @JsonSubTypes.Type(value = ListDef.class,         name = "list"),
        @JsonSubTypes.Type(value = MapDef.class,          name = "map"),
        @JsonSubTypes.Type(value = PolicyDef.class,       name = "policy"),
        @JsonSubTypes.Type(value = RelationshipDef.class, name = "relationship"),
        @JsonSubTypes.Type(value = ShapeStructDef.class,  name = "shape"),
        @JsonSubTypes.Type(value = StateMachineDef.class, name = "statemachine")
    })
    abstract static class IrShapeMixin {}

    /** Uses {@code typeKind} to avoid collision with {@link TypeRef.PrimitiveType#kind()}. */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "typeKind")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = TypeRef.ListType.class,     name = "list"),
        @JsonSubTypes.Type(value = TypeRef.MapType.class,      name = "map"),
        @JsonSubTypes.Type(value = TypeRef.NamedTypeRef.class, name = "named"),
        @JsonSubTypes.Type(value = TypeRef.PrimitiveType.class, name = "primitive")
    })
    abstract static class TypeRefMixin {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = TraitValue.BoolValue.class,      name = "bool"),
        @JsonSubTypes.Type(value = TraitValue.NumberValue.class,    name = "number"),
        @JsonSubTypes.Type(value = TraitValue.ReferenceValue.class, name = "ref"),
        @JsonSubTypes.Type(value = TraitValue.StringValue.class,    name = "string")
    })
    abstract static class TraitValueMixin {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = OutcomeExpr.ReturnToStep.class, name = "return"),
        @JsonSubTypes.Type(value = OutcomeExpr.TransitionTo.class, name = "transition")
    })
    abstract static class OutcomeExprMixin {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = StepField.Action.class,      name = "action"),
        @JsonSubTypes.Type(value = StepField.Expectation.class, name = "expectation"),
        @JsonSubTypes.Type(value = StepField.Outcome.class,     name = "outcome"),
        @JsonSubTypes.Type(value = StepField.Risk.class,        name = "risk"),
        @JsonSubTypes.Type(value = StepField.Telemetry.class,   name = "telemetry")
    })
    abstract static class StepFieldMixin {}

    // ── chronos-core record mixins ────────────────────────────────────────────────

    // Span.isUnknown() would be serialised as "unknown" by bean introspection;
    // suppress it so the 5 record components are the only properties.
    @JsonIgnoreProperties("unknown")
    abstract static class SpanMixin {
        @JsonCreator
        SpanMixin(
                @JsonProperty("sourceName") String sourceName,
                @JsonProperty("startLine")  int startLine,
                @JsonProperty("startCol")   int startCol,
                @JsonProperty("endLine")    int endLine,
                @JsonProperty("endCol")     int endCol) {}
    }

    abstract static class NamespaceIdMixin {
        @JsonCreator
        NamespaceIdMixin(@JsonProperty("value") String value) {}
    }

    // QualifiedName.isQualified() would appear as "qualified"; suppress it.
    @JsonIgnoreProperties("qualified")
    abstract static class QualifiedNameMixin {
        @JsonCreator
        QualifiedNameMixin(
                @JsonProperty("namespaceOrNull") String namespaceOrNull,
                @JsonProperty("name")            String name) {}
    }

    abstract static class ShapeIdMixin {
        @JsonCreator
        ShapeIdMixin(
                @JsonProperty("namespace") NamespaceId namespace,
                @JsonProperty("name")      String name) {}
    }

    // ── Convenience-method suppression mixins ────────────────────────────────────

    // FieldDef.isRequired() → "required"; not a record component, must not round-trip
    @JsonIgnoreProperties("required")
    abstract static class FieldDefMixin {}

    // ActorDef.description() → "description"; derived from @description trait
    @JsonIgnoreProperties("description")
    abstract static class ActorDefMixin {}

    // JourneyDef.actorName()/actorRefOpt()/journeyOutcomes() → derived helpers
    @JsonIgnoreProperties({"actorName", "actorRefOpt", "journeyOutcomes"})
    abstract static class JourneyDefMixin {}

    // PolicyDef.complianceFramework() → derived from @compliance trait
    @JsonIgnoreProperties("complianceFramework")
    abstract static class PolicyDefMixin {}

    // TypeRef.NamedTypeRef.qualifiedId() → derived from ref; must not be serialized
    @JsonIgnoreProperties("qualifiedId")
    abstract static class NamedTypeRefMixin {}

    // ── SymbolRef custom serializer/deserializer ─────────────────────────────────

    /**
     * Serialises a {@link SymbolRef} with properties in strict alphabetical order.
     *
     * <p>Resolved form (alphabetical: {@code id}, {@code kind}, {@code resolved}, {@code span}):
     * <pre>{@code
     * {
     *   "id": { "name": "Foo", "namespace": { "value": "com.example" } },
     *   "kind": "ACTOR",
     *   "resolved": true,
     *   "span": { ... }
     * }
     * }</pre>
     *
     * <p>Unresolved form (alphabetical: {@code kind}, {@code name}, {@code resolved}, {@code span}):
     * <pre>{@code
     * {
     *   "kind": "ACTOR",
     *   "name": { "name": "Foo", "namespaceOrNull": null },
     *   "resolved": false,
     *   "span": { ... }
     * }
     * }</pre>
     */
    private static final class SymbolRefSerializer extends StdSerializer<SymbolRef> {

        SymbolRefSerializer() { super(SymbolRef.class); }

        @Override
        public void serialize(SymbolRef ref, JsonGenerator gen, SerializerProvider provider)
                throws IOException {
            gen.writeStartObject();
            if (ref.isResolved()) {
                // Alphabetical: id, kind, resolved, span
                gen.writeObjectFieldStart("id");
                gen.writeStringField("name", ref.id().name());
                gen.writeObjectFieldStart("namespace");
                gen.writeStringField("value", ref.id().namespace().value());
                gen.writeEndObject();
                gen.writeEndObject();
                gen.writeStringField("kind", ref.kind().name());
                gen.writeBooleanField("resolved", true);
            } else {
                // Alphabetical: kind, name, resolved, span
                gen.writeStringField("kind", ref.kind().name());
                gen.writeObjectFieldStart("name");
                gen.writeStringField("name", ref.name().name());
                if (ref.name().namespaceOrNull() != null) {
                    gen.writeStringField("namespaceOrNull", ref.name().namespaceOrNull());
                } else {
                    gen.writeNullField("namespaceOrNull");
                }
                gen.writeEndObject();
                gen.writeBooleanField("resolved", false);
            }
            gen.writeObjectField("span", ref.span());
            gen.writeEndObject();
        }
    }

    private static final class SymbolRefDeserializer extends StdDeserializer<SymbolRef> {

        SymbolRefDeserializer() { super(SymbolRef.class); }

        @Override
        public SymbolRef deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            JsonNode node     = p.readValueAsTree();
            SymbolKind kind   = SymbolKind.valueOf(node.get("kind").asText());
            boolean resolved  = node.get("resolved").asBoolean();
            Span span         = ctx.readTreeAsValue(node.get("span"), Span.class);

            if (resolved) {
                JsonNode idNode  = node.get("id");
                String nsValue   = idNode.get("namespace").get("value").asText();
                String shapeName = idNode.get("name").asText();
                return SymbolRef.resolved(kind, ShapeId.of(nsValue, shapeName), span);
            } else {
                JsonNode nameNode = node.get("name");
                JsonNode nsNode   = nameNode.get("namespaceOrNull");
                String ns         = (nsNode == null || nsNode.isNull()) ? null : nsNode.asText();
                String simpleName = nameNode.get("name").asText();
                QualifiedName qualName = ns != null
                        ? QualifiedName.qualified(ns, simpleName)
                        : QualifiedName.local(simpleName);
                return SymbolRef.unresolved(kind, qualName, span);
            }
        }
    }
}
