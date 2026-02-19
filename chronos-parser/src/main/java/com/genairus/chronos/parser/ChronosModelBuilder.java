package com.genairus.chronos.parser;

import com.genairus.chronos.model.*;
import com.genairus.chronos.parser.generated.ChronosBaseVisitor;
import com.genairus.chronos.parser.generated.ChronosLexer;
import com.genairus.chronos.parser.generated.ChronosParser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Walks the ANTLR parse tree produced by {@link ChronosParser} and constructs
 * the immutable {@link ChronosModel} semantic model.
 *
 * <p>Each {@code visit*} method maps directly to one grammar rule.  Because
 * ANTLR's {@link ChronosBaseVisitor} is typed {@code <Object>} here, every
 * method casts its return value at the call site.  The builder is
 * <em>not</em> thread-safe; create a new instance per parse.
 *
 * <p>Usage — typically via {@link ChronosModelParser}:
 * <pre>{@code
 *   var builder = new ChronosModelBuilder(tokens, "<inline>");
 *   ChronosModel model = (ChronosModel) builder.visitModel(tree);
 * }</pre>
 */
@SuppressWarnings("unchecked")
public class ChronosModelBuilder extends ChronosBaseVisitor<Object> {

    /** The token stream, kept to read HIDDEN-channel doc-comments. */
    private final TokenStream tokens;

    /** Source name (file path or {@code "<string>"}) used in {@link SourceLocation}s. */
    private final String sourceName;

    /**
     * Traits collected by {@link #visitShapeDefinition} and temporarily
     * stored here so that the specific shape visitor can pick them up.
     * Reset to empty after each shape is built.
     */
    private List<TraitApplication> pendingTraits = List.of();

    /**
     * Doc-comment lines collected by {@link #visitShapeDefinition} from the
     * HIDDEN channel immediately before the first trait or keyword token.
     */
    private List<String> pendingDocComments = List.of();

    public ChronosModelBuilder(TokenStream tokens, String sourceName) {
        this.tokens = tokens;
        this.sourceName = sourceName;
    }

    // ── Utility ────────────────────────────────────────────────────────────────

    /** Builds a {@link SourceLocation} from the start token of a rule context. */
    private SourceLocation loc(org.antlr.v4.runtime.ParserRuleContext ctx) {
        Token t = ctx.getStart();
        return SourceLocation.of(sourceName, t.getLine(), t.getCharPositionInLine() + 1);
    }

    /** Builds a {@link SourceLocation} from an explicit token. */
    private SourceLocation loc(Token t) {
        return SourceLocation.of(sourceName, t.getLine(), t.getCharPositionInLine() + 1);
    }

    /**
     * Strips surrounding double-quotes from an ANTLR STRING token text and
     * un-escapes backslash sequences.
     */
    private static String unquote(String raw) {
        // raw is  "..."  — strip first and last character
        String inner = raw.substring(1, raw.length() - 1);
        return inner.replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\n",  "\n")
                    .replace("\\t",  "\t")
                    .replace("\\r",  "\r");
    }

    /**
     * Collects {@code ///} doc-comment tokens from the HIDDEN channel that
     * appear in the token stream immediately before {@code tokenIndex}
     * (skipping whitespace tokens).
     *
     * @param tokenIndex index of the first non-hidden token of the shape keyword
     * @return doc-comment texts in source order, stripped of the leading {@code "/// "} prefix
     */
    private List<String> extractDocComments(int tokenIndex) {
        // Walk backwards through hidden-channel tokens preceding tokenIndex
        List<String> result = new ArrayList<>();
        int i = tokenIndex - 1;
        while (i >= 0) {
            Token t = tokens.get(i);
            if (t.getChannel() == Token.HIDDEN_CHANNEL && t.getType() == ChronosLexer.DOC_COMMENT) {
                // Strip leading "///" and optional following space
                String text = t.getText().stripLeading();
                if (text.startsWith("///")) {
                    text = text.substring(3);
                    if (text.startsWith(" ")) text = text.substring(1);
                }
                result.add(0, text); // prepend to maintain source order
                i--;
            } else if (t.getChannel() == Token.HIDDEN_CHANNEL) {
                // whitespace — keep scanning
                i--;
            } else {
                break;
            }
        }
        return Collections.unmodifiableList(result);
    }

    // ── model ──────────────────────────────────────────────────────────────────

    @Override
    public Object visitModel(ChronosParser.ModelContext ctx) {
        String namespace = (String) visit(ctx.namespaceDecl());

        List<UseDecl> imports = ctx.useDecl().stream()
                .map(u -> (UseDecl) visit(u))
                .toList();

        List<ShapeDefinition> shapes = ctx.shapeDefinition().stream()
                .map(s -> (ShapeDefinition) visit(s))
                .toList();

        return new ChronosModel(namespace, imports, shapes);
    }

    // ── namespaceDecl ──────────────────────────────────────────────────────────

    @Override
    public Object visitNamespaceDecl(ChronosParser.NamespaceDeclContext ctx) {
        return visitQualifiedId(ctx.qualifiedId());
    }

    // ── useDecl ───────────────────────────────────────────────────────────────

    @Override
    public Object visitUseDecl(ChronosParser.UseDeclContext ctx) {
        String namespace = (String) visitQualifiedId(ctx.qualifiedId());
        String shapeName = ctx.ID().getText();
        return new UseDecl(namespace, shapeName, loc(ctx));
    }

    // ── shapeDefinition ───────────────────────────────────────────────────────

    /**
     * Collects leading trait applications and HIDDEN-channel doc-comments,
     * stores them in instance fields, then dispatches to the concrete shape
     * visitor.  The concrete visitors read {@link #pendingTraits} and
     * {@link #pendingDocComments} and reset them.
     */
    @Override
    public Object visitShapeDefinition(ChronosParser.ShapeDefinitionContext ctx) {
        // Collect traits
        pendingTraits = ctx.traitApplication().stream()
                .map(t -> (TraitApplication) visit(t))
                .toList();

        // Determine where the shape keyword token starts and extract doc-comments
        // from tokens on the HIDDEN channel just before the first real token.
        int firstTokenIndex = ctx.getStart().getTokenIndex();
        pendingDocComments = extractDocComments(firstTokenIndex);

        return visit(ctx.shapeDef());
    }

    // shapeDef just dispatches; we don't override it — visitChildren handles it.

    // ── traits ────────────────────────────────────────────────────────────────

    @Override
    public Object visitTraitApplication(ChronosParser.TraitApplicationContext ctx) {
        String name = ctx.traitId().getText();
        List<TraitArg> args = ctx.traitArgList() == null
                ? List.of()
                : ctx.traitArgList().traitArg().stream()
                        .map(a -> (TraitArg) visit(a))
                        .toList();
        return new TraitApplication(name, args, loc(ctx));
    }

    @Override
    public Object visitTraitArg(ChronosParser.TraitArgContext ctx) {
        TraitValue value = (TraitValue) visit(ctx.traitValue());
        // Named arg: has ID child before ':'
        String key = ctx.ID() != null ? ctx.ID().getText() : null;
        return new TraitArg(key, value, loc(ctx));
    }

    @Override
    public Object visitTraitValue(ChronosParser.TraitValueContext ctx) {
        if (ctx.STRING() != null) {
            return new TraitValue.StringValue(unquote(ctx.STRING().getText()));
        }
        if (ctx.NUMBER() != null) {
            return new TraitValue.NumberValue(Double.parseDouble(ctx.NUMBER().getText()));
        }
        if (ctx.BOOL() != null) {
            return new TraitValue.BoolValue(Boolean.parseBoolean(ctx.BOOL().getText()));
        }
        // qualifiedId → reference
        String ref = (String) visitQualifiedId(ctx.qualifiedId());
        return new TraitValue.ReferenceValue(ref);
    }

    // ── typeRef ───────────────────────────────────────────────────────────────

    @Override
    public Object visitTypeRef(ChronosParser.TypeRefContext ctx) {
        // List<T>
        if (ctx.getChildCount() >= 4 && ctx.getChild(0).getText().equals("List")) {
            TypeRef member = (TypeRef) visit(ctx.typeRef(0));
            return new TypeRef.ListType(member);
        }
        // Map<K, V>
        if (ctx.getChildCount() >= 6 && ctx.getChild(0).getText().equals("Map")) {
            TypeRef key   = (TypeRef) visit(ctx.typeRef(0));
            TypeRef value = (TypeRef) visit(ctx.typeRef(1));
            return new TypeRef.MapType(key, value);
        }
        // primitiveType
        if (ctx.primitiveType() != null) {
            return visit(ctx.primitiveType());
        }
        // qualifiedId → named reference
        String name = (String) visitQualifiedId(ctx.qualifiedId());
        return new TypeRef.NamedTypeRef(name);
    }

    @Override
    public Object visitPrimitiveType(ChronosParser.PrimitiveTypeContext ctx) {
        return new TypeRef.PrimitiveType(switch (ctx.getText()) {
            case "String"    -> TypeRef.PrimitiveKind.STRING;
            case "Integer"   -> TypeRef.PrimitiveKind.INTEGER;
            case "Long"      -> TypeRef.PrimitiveKind.LONG;
            case "Float"     -> TypeRef.PrimitiveKind.FLOAT;
            case "Boolean"   -> TypeRef.PrimitiveKind.BOOLEAN;
            case "Timestamp" -> TypeRef.PrimitiveKind.TIMESTAMP;
            case "Blob"      -> TypeRef.PrimitiveKind.BLOB;
            case "Document"  -> TypeRef.PrimitiveKind.DOCUMENT;
            default -> throw new IllegalStateException("Unknown primitive: " + ctx.getText());
        });
    }

    // ── fieldDef ──────────────────────────────────────────────────────────────

    @Override
    public Object visitFieldDef(ChronosParser.FieldDefContext ctx) {
        List<TraitApplication> traits = ctx.traitApplication().stream()
                .map(t -> (TraitApplication) visit(t))
                .toList();
        String name   = ctx.ID().getText();
        TypeRef type  = (TypeRef) visit(ctx.typeRef());
        return new FieldDef(name, type, traits, loc(ctx));
    }

    // ── entityDef ─────────────────────────────────────────────────────────────

    @Override
    public Object visitEntityDef(ChronosParser.EntityDefContext ctx) {
        List<TraitApplication> traits     = consumePendingTraits();
        List<String>           docComments = consumePendingDocComments();
        String name = ctx.ID(0).getText();
        Optional<String> parentType = ctx.ID().size() > 1
                ? Optional.of(ctx.ID(1).getText())
                : Optional.empty();

        // Collect fields and invariants from entityMember
        List<FieldDef> fields = new ArrayList<>();
        List<EntityInvariant> invariants = new ArrayList<>();
        for (ChronosParser.EntityMemberContext member : ctx.entityMember()) {
            if (member.fieldDef() != null) {
                fields.add((FieldDef) visit(member.fieldDef()));
            } else if (member.entityInvariant() != null) {
                invariants.add((EntityInvariant) visit(member.entityInvariant()));
            }
        }

        return new EntityDef(name, traits, docComments, parentType, fields, invariants, loc(ctx));
    }

    // ── shapeStructDef ────────────────────────────────────────────────────────

    @Override
    public Object visitShapeStructDef(ChronosParser.ShapeStructDefContext ctx) {
        List<TraitApplication> traits     = consumePendingTraits();
        List<String>           docComments = consumePendingDocComments();
        String name = ctx.ID().getText();
        List<FieldDef> fields = ctx.fieldDef().stream()
                .map(f -> (FieldDef) visit(f))
                .toList();
        return new ShapeStructDef(name, traits, docComments, fields, loc(ctx));
    }

    // ── listDef ───────────────────────────────────────────────────────────────

    @Override
    public Object visitListDef(ChronosParser.ListDefContext ctx) {
        List<TraitApplication> traits     = consumePendingTraits();
        List<String>           docComments = consumePendingDocComments();
        String  name       = ctx.ID().getText();
        TypeRef memberType = (TypeRef) visit(ctx.typeRef());
        return new ListDef(name, traits, docComments, memberType, loc(ctx));
    }

    // ── mapDef ────────────────────────────────────────────────────────────────

    @Override
    public Object visitMapDef(ChronosParser.MapDefContext ctx) {
        List<TraitApplication> traits     = consumePendingTraits();
        List<String>           docComments = consumePendingDocComments();
        String  name      = ctx.ID().getText();
        TypeRef keyType   = (TypeRef) visit(ctx.typeRef(0));
        TypeRef valueType = (TypeRef) visit(ctx.typeRef(1));
        return new MapDef(name, traits, docComments, keyType, valueType, loc(ctx));
    }

    // ── enumDef / enumMember ──────────────────────────────────────────────────

    @Override
    public Object visitEnumDef(ChronosParser.EnumDefContext ctx) {
        List<TraitApplication> traits     = consumePendingTraits();
        List<String>           docComments = consumePendingDocComments();
        String name = ctx.ID().getText();
        List<EnumMember> members = ctx.enumMember().stream()
                .map(m -> (EnumMember) visit(m))
                .toList();
        return new EnumDef(name, traits, docComments, members, loc(ctx));
    }

    @Override
    public Object visitEnumMember(ChronosParser.EnumMemberContext ctx) {
        String name = ctx.ID().getText();
        if (ctx.NUMBER() != null) {
            int ordinal = Integer.parseInt(ctx.NUMBER().getText());
            return EnumMember.of(name, ordinal, loc(ctx));
        }
        return EnumMember.of(name, loc(ctx));
    }

    // ── actorDef ──────────────────────────────────────────────────────────────

    @Override
    public Object visitActorDef(ChronosParser.ActorDefContext ctx) {
        List<TraitApplication> traits     = consumePendingTraits();
        List<String>           docComments = consumePendingDocComments();
        String name = ctx.ID(0).getText();
        Optional<String> parentType = ctx.ID().size() > 1
                ? Optional.of(ctx.ID(1).getText())
                : Optional.empty();
        return new ActorDef(name, traits, docComments, parentType, loc(ctx));
    }

    // ── policyDef ─────────────────────────────────────────────────────────────

    @Override
    public Object visitPolicyDef(ChronosParser.PolicyDefContext ctx) {
        List<TraitApplication> traits     = consumePendingTraits();
        List<String>           docComments = consumePendingDocComments();
        String name        = ctx.ID().getText();
        String description = unquote(ctx.STRING().getText());
        return new PolicyDef(name, description, traits, docComments, loc(ctx));
    }

    // ── relationshipDef ───────────────────────────────────────────────────────

    @Override
    public Object visitRelationshipDef(ChronosParser.RelationshipDefContext ctx) {
        List<TraitApplication> traits     = consumePendingTraits();
        List<String>           docComments = consumePendingDocComments();
        String name = ctx.ID().getText();

        ChronosParser.RelationshipBodyContext body = ctx.relationshipBody();
        String fromEntity = body.ID(0).getText();
        String toEntity   = body.ID(1).getText();
        Cardinality cardinality = Cardinality.fromChronosName(body.cardinalityValue().getText());

        Optional<RelationshipSemantics> semantics = body.semanticsValue() != null
                ? Optional.of(RelationshipSemantics.fromChronosName(body.semanticsValue().getText()))
                : Optional.empty();

        Optional<String> inverseField = body.ID().size() > 2
                ? Optional.of(body.ID(2).getText())
                : Optional.empty();

        return new RelationshipDef(name, traits, docComments,
                fromEntity, toEntity, cardinality, semantics, inverseField, loc(ctx));
    }

    // ── entityInvariant ───────────────────────────────────────────────────────

    @Override
    public Object visitEntityInvariant(ChronosParser.EntityInvariantContext ctx) {
        String name = ctx.ID().getText();
        String expression = "";
        String severity = "";
        Optional<String> message = Optional.empty();

        for (ChronosParser.InvariantFieldContext field : ctx.invariantField()) {
            if (field.getChild(0).getText().equals("expression")) {
                expression = unquote(field.STRING().getText());
            } else if (field.getChild(0).getText().equals("severity")) {
                // severity can be ID or 'error' keyword
                severity = field.ID(0) != null ? field.ID(0).getText() : field.getChild(2).getText();
            } else if (field.getChild(0).getText().equals("message")) {
                message = Optional.of(unquote(field.STRING().getText()));
            }
        }

        return new EntityInvariant(name, expression, severity, message, loc(ctx));
    }

    // ── invariantDef ──────────────────────────────────────────────────────────

    @Override
    public Object visitInvariantDef(ChronosParser.InvariantDefContext ctx) {
        List<TraitApplication> traits     = consumePendingTraits();
        List<String>           docComments = consumePendingDocComments();
        String name = ctx.ID().getText();

        List<String> scope = List.of();
        String expression = "";
        String severity = "";
        Optional<String> message = Optional.empty();

        for (ChronosParser.InvariantFieldContext field : ctx.invariantField()) {
            String fieldName = field.getChild(0).getText();
            if (fieldName.equals("scope")) {
                scope = field.ID().stream()
                        .map(id -> id.getText())
                        .toList();
            } else if (fieldName.equals("expression")) {
                expression = unquote(field.STRING().getText());
            } else if (fieldName.equals("severity")) {
                // severity can be ID or 'error' keyword
                severity = field.ID(0) != null ? field.ID(0).getText() : field.getChild(2).getText();
            } else if (fieldName.equals("message")) {
                message = Optional.of(unquote(field.STRING().getText()));
            }
        }

        return new InvariantDef(name, traits, docComments, scope, expression, severity, message, loc(ctx));
    }

    // ── denyDef ───────────────────────────────────────────────────────────────

    @Override
    public Object visitDenyDef(ChronosParser.DenyDefContext ctx) {
        List<TraitApplication> traits     = consumePendingTraits();
        List<String>           docComments = consumePendingDocComments();
        String name = ctx.ID().getText();

        String description = "";
        List<String> scope = List.of();
        String severity = "";

        for (ChronosParser.DenyFieldContext field : ctx.denyField()) {
            String fieldName = field.getChild(0).getText();
            if (fieldName.equals("description")) {
                description = unquote(field.STRING().getText());
            } else if (fieldName.equals("scope")) {
                scope = field.ID().stream()
                        .map(id -> id.getText())
                        .toList();
            } else if (fieldName.equals("severity")) {
                severity = field.ID(0).getText();
            }
        }

        return new DenyDef(name, traits, docComments, description, scope, severity, loc(ctx));
    }

    // ── errorDef ──────────────────────────────────────────────────────────────

    @Override
    public Object visitErrorDef(ChronosParser.ErrorDefContext ctx) {
        List<TraitApplication> traits     = consumePendingTraits();
        List<String>           docComments = consumePendingDocComments();
        String name = ctx.ID().getText();

        String code = "";
        String severity = "";
        boolean recoverable = false;
        String message = "";
        List<FieldDef> payload = List.of();

        for (ChronosParser.ErrorFieldContext field : ctx.errorField()) {
            String fieldName = field.getChild(0).getText();
            if (fieldName.equals("code")) {
                code = unquote(field.STRING().getText());
            } else if (fieldName.equals("severity")) {
                severity = field.ID().getText();
            } else if (fieldName.equals("recoverable")) {
                recoverable = Boolean.parseBoolean(field.BOOL().getText());
            } else if (fieldName.equals("message")) {
                message = unquote(field.STRING().getText());
            } else if (fieldName.equals("payload")) {
                payload = field.fieldDef().stream()
                        .map(f -> (FieldDef) visit(f))
                        .toList();
            }
        }

        return new ErrorDef(name, traits, docComments, code, severity, recoverable, message, payload, loc(ctx));
    }

    @Override
    public Object visitStatemachineDef(ChronosParser.StatemachineDefContext ctx) {
        List<TraitApplication> traits     = consumePendingTraits();
        List<String>           docComments = consumePendingDocComments();
        String name = ctx.ID().getText();

        String entityName = "";
        String fieldName = "";
        List<String> states = List.of();
        String initialState = "";
        List<String> terminalStates = List.of();
        List<Transition> transitions = List.of();

        for (ChronosParser.StatemachineFieldContext field : ctx.statemachineField()) {
            String fieldType = field.getChild(0).getText();
            if (fieldType.equals("entity")) {
                entityName = field.ID(0).getText();
            } else if (fieldType.equals("field")) {
                fieldName = field.ID(0).getText();
            } else if (fieldType.equals("states")) {
                states = field.ID().stream()
                        .map(org.antlr.v4.runtime.tree.TerminalNode::getText)
                        .toList();
            } else if (fieldType.equals("initial")) {
                initialState = field.ID(0).getText();
            } else if (fieldType.equals("terminal")) {
                terminalStates = field.ID().stream()
                        .map(org.antlr.v4.runtime.tree.TerminalNode::getText)
                        .toList();
            } else if (fieldType.equals("transitions")) {
                transitions = field.transition().stream()
                        .map(t -> (Transition) visitTransition(t))
                        .toList();
            }
        }

        return new StateMachineDef(name, traits, docComments, entityName, fieldName,
                states, initialState, terminalStates, transitions, loc(ctx));
    }

    @Override
    public Object visitTransition(ChronosParser.TransitionContext ctx) {
        String fromState = ctx.ID(0).getText();
        String toState = ctx.ID(1).getText();

        Optional<String> guard = Optional.empty();
        Optional<String> action = Optional.empty();

        if (ctx.transitionBody() != null) {
            for (ChronosParser.TransitionFieldContext field : ctx.transitionBody().transitionField()) {
                String fieldType = field.getChild(0).getText();
                if (fieldType.equals("guard")) {
                    guard = Optional.of(unquote(field.STRING().getText()));
                } else if (fieldType.equals("action")) {
                    action = Optional.of(unquote(field.STRING().getText()));
                }
            }
        }

        return new Transition(fromState, toState, guard, action, loc(ctx));
    }

    // ── outcomeExpr ───────────────────────────────────────────────────────────

    @Override
    public Object visitOutcomeExpr(ChronosParser.OutcomeExprContext ctx) {
        String keyword = ctx.getChild(0).getText(); // "TransitionTo" or "ReturnToStep"
        String target  = ctx.ID().getText();
        SourceLocation loc = loc(ctx);
        return switch (keyword) {
            case "TransitionTo" -> new OutcomeExpr.TransitionTo(target, loc);
            case "ReturnToStep" -> new OutcomeExpr.ReturnToStep(target, loc);
            default -> throw new IllegalStateException("Unknown outcome keyword: " + keyword);
        };
    }

    // ── stepField ─────────────────────────────────────────────────────────────

    @Override
    public Object visitStepField(ChronosParser.StepFieldContext ctx) {
        String keyword = ctx.getChild(0).getText();
        SourceLocation loc = loc(ctx);
        return switch (keyword) {
            case "action"      -> new StepField.ActionField(
                    unquote(ctx.STRING().getText()), loc);
            case "expectation" -> new StepField.ExpectationField(
                    unquote(ctx.STRING().getText()), loc);
            case "outcome"     -> new StepField.OutcomeField(
                    (OutcomeExpr) visit(ctx.outcomeExpr()), loc);
            case "telemetry"   -> new StepField.TelemetryField(
                    ctx.ID().stream().map(id -> id.getText()).toList(), loc);
            case "risk"        -> new StepField.RiskField(
                    unquote(ctx.STRING().getText()), loc);
            default -> throw new IllegalStateException("Unknown step field: " + keyword);
        };
    }

    // ── step ──────────────────────────────────────────────────────────────────

    @Override
    public Object visitStep(ChronosParser.StepContext ctx) {
        List<TraitApplication> traits = ctx.traitApplication().stream()
                .map(t -> (TraitApplication) visit(t))
                .toList();
        String name = ctx.ID().getText();
        List<StepField> fields = ctx.stepField().stream()
                .map(f -> (StepField) visit(f))
                .toList();
        return new Step(name, traits, fields, loc(ctx));
    }

    // ── variantsDecl / variantEntry / variantBody ─────────────────────────────

    @Override
    public Object visitVariantsDecl(ChronosParser.VariantsDeclContext ctx) {
        Map<String, Variant> result = new LinkedHashMap<>();
        for (var entry : ctx.variantEntry()) {
            var pair = (Map.Entry<String, Variant>) visit(entry);
            result.put(pair.getKey(), pair.getValue());
        }
        return Collections.unmodifiableMap(result);
    }

    @Override
    public Object visitVariantEntry(ChronosParser.VariantEntryContext ctx) {
        String name = ctx.ID().getText();
        Variant variant = (Variant) visit(ctx.variantBody());
        // Patch the variant name in (variantBody builds it without the name)
        var namedVariant = new Variant(name, variant.trigger(), variant.steps(),
                variant.outcome(), variant.location());
        return Map.entry(name, namedVariant);
    }

    @Override
    public Object visitVariantBody(ChronosParser.VariantBodyContext ctx) {
        String trigger = ctx.ID().getText();  // Now references an error type, not a string
        SourceLocation loc = loc(ctx);

        List<Step> steps = ctx.step().stream()
                .map(s -> (Step) visit(s))
                .toList();

        Optional<OutcomeExpr> outcome = ctx.outcomeExpr() != null
                ? Optional.of((OutcomeExpr) visit(ctx.outcomeExpr()))
                : Optional.empty();

        // Name is filled in by visitVariantEntry
        return new Variant("", trigger, steps, outcome, loc);
    }

    // ── outcomesDecl ──────────────────────────────────────────────────────────

    @Override
    public Object visitOutcomesDecl(ChronosParser.OutcomesDeclContext ctx) {
        String success = null;
        String failure = null;
        for (var entry : ctx.outcomeEntry()) {
            String key   = entry.getChild(0).getText(); // 'success' or 'failure'
            String value = unquote(entry.STRING().getText());
            if ("success".equals(key)) success = value;
            else                        failure = value;
        }
        return new JourneyOutcomes(success, failure, loc(ctx));
    }

    // ── journeyDef ────────────────────────────────────────────────────────────

    @Override
    public Object visitJourneyDef(ChronosParser.JourneyDefContext ctx) {
        List<TraitApplication> traits     = consumePendingTraits();
        List<String>           docComments = consumePendingDocComments();
        String name = ctx.ID().getText();
        SourceLocation loc = loc(ctx);

        ChronosParser.JourneyBodyContext body = ctx.journeyBody();

        // actor
        String actor = body.actorDecl() != null
                ? body.actorDecl().ID().getText()
                : null;

        // preconditions
        List<String> preconditions = body.preconditionsDecl() != null
                ? body.preconditionsDecl().STRING().stream()
                        .map(s -> unquote(s.getText()))
                        .toList()
                : List.of();

        // steps
        List<Step> steps = body.stepsDecl() != null
                ? body.stepsDecl().step().stream()
                        .map(s -> (Step) visit(s))
                        .toList()
                : List.of();

        // variants
        Map<String, Variant> variants = body.variantsDecl() != null
                ? (Map<String, Variant>) visit(body.variantsDecl())
                : Map.of();

        // journey-level outcomes
        JourneyOutcomes journeyOutcomes = body.outcomesDecl() != null
                ? (JourneyOutcomes) visit(body.outcomesDecl())
                : null;

        return new JourneyDef(name, traits, docComments, actor, preconditions,
                steps, variants, journeyOutcomes, loc);
    }

    // ── qualifiedId ───────────────────────────────────────────────────────────

    @Override
    public Object visitQualifiedId(ChronosParser.QualifiedIdContext ctx) {
        return ctx.ID().stream()
                .map(id -> id.getText())
                .collect(Collectors.joining("."));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private List<TraitApplication> consumePendingTraits() {
        var t = pendingTraits;
        pendingTraits = List.of();
        return t;
    }

    private List<String> consumePendingDocComments() {
        var d = pendingDocComments;
        pendingDocComments = List.of();
        return d;
    }
}
