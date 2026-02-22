package com.genairus.chronos.parser.lowering;

import com.genairus.chronos.core.refs.QualifiedName;
import com.genairus.chronos.core.refs.Span;
import com.genairus.chronos.parser.generated.ChronosBaseVisitor;
import com.genairus.chronos.parser.generated.ChronosLexer;
import com.genairus.chronos.parser.generated.ChronosParser;
import com.genairus.chronos.syntax.*;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Pass 1 — Lower: walks the ANTLR parse tree and produces {@link SyntaxModel} Syntax DTOs.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>One DTO per grammar rule node.</li>
 *   <li>Every node carries a {@link Span} derived from {@code ctx.getStart()} /
 *       {@code ctx.getStop()}.</li>
 *   <li>All identifiers remain as raw strings. No symbol resolution occurs here.</li>
 *   <li>ANTLR types ({@code ParserRuleContext}, {@code Token}, etc.) do not appear
 *       in the returned DTOs.</li>
 * </ul>
 *
 * <p>Not thread-safe; create a new instance per parse.
 */
@SuppressWarnings("unchecked")
public class LoweringVisitor extends ChronosBaseVisitor<Object> {

    private final TokenStream tokens;
    private final String sourceName;

    /**
     * Traits collected by {@link #visitShapeDefinition} and consumed by the
     * specific shape visitor. Reset to empty after each shape is built.
     */
    private List<SyntaxTrait> pendingTraits = List.of();

    /**
     * Doc-comment lines collected by {@link #visitShapeDefinition} and consumed by the
     * specific shape visitor. Reset to empty after each shape is built.
     */
    private List<String> pendingDocComments = List.of();

    public LoweringVisitor(TokenStream tokens, String sourceName) {
        this.tokens     = tokens;
        this.sourceName = sourceName;
    }

    // ── Utilities ──────────────────────────────────────────────────────────────

    private Span span(ParserRuleContext ctx) {
        Token start = ctx.getStart();
        Token stop  = ctx.getStop() != null ? ctx.getStop() : start;
        return new Span(
                sourceName,
                start.getLine(),
                start.getCharPositionInLine() + 1,
                stop.getLine(),
                stop.getCharPositionInLine() + stop.getText().length() + 1
        );
    }

    private static String unquote(String raw) {
        String inner = raw.substring(1, raw.length() - 1);
        return inner.replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\n",  "\n")
                    .replace("\\t",  "\t")
                    .replace("\\r",  "\r");
    }

    private List<SyntaxTrait> consumePendingTraits() {
        var t = pendingTraits;
        pendingTraits = List.of();
        return t;
    }

    private List<String> consumePendingDocComments() {
        var d = pendingDocComments;
        pendingDocComments = List.of();
        return d;
    }

    /**
     * Extracts {@code DOC_COMMENT} tokens from the HIDDEN channel that immediately
     * precede {@code ctx.start} in the token stream, strips the {@code ///} prefix
     * (and one optional leading space), and returns them in source order.
     *
     * <p>Because {@code WS} is {@code -> skip} in the grammar, only on-channel
     * (DEFAULT) and {@code DOC_COMMENT} (HIDDEN) tokens appear in the stream.
     * {@link CommonTokenStream#getHiddenTokensToLeft} therefore returns exactly the
     * contiguous {@code DOC_COMMENT} block preceding the start token.
     */
    private List<String> extractDocComments(ParserRuleContext ctx) {
        if (!(tokens instanceof CommonTokenStream cts)) return List.of();
        List<Token> hidden = cts.getHiddenTokensToLeft(
                ctx.start.getTokenIndex(), Token.HIDDEN_CHANNEL);
        if (hidden == null || hidden.isEmpty()) return List.of();
        return hidden.stream()
                .filter(t -> t.getType() == ChronosLexer.DOC_COMMENT)
                .map(t -> {
                    String text = t.getText().substring(3); // strip "///"
                    if (!text.isEmpty() && text.charAt(0) == ' ') {
                        text = text.substring(1);           // strip one optional space
                    }
                    return text;
                })
                .toList();
    }

    // ── model ──────────────────────────────────────────────────────────────────

    @Override
    public Object visitModel(ChronosParser.ModelContext ctx) {
        String namespace = (String) visit(ctx.namespaceDecl());

        List<SyntaxUseDecl> imports = ctx.useDecl().stream()
                .map(u -> (SyntaxUseDecl) visit(u))
                .toList();

        List<SyntaxDecl> declarations = ctx.shapeDefinition().stream()
                .map(s -> (SyntaxDecl) visit(s))
                .toList();

        return new SyntaxModel(namespace, imports, declarations, span(ctx));
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
        return new SyntaxUseDecl(QualifiedName.qualified(namespace, shapeName), span(ctx));
    }

    // ── shapeDefinition ───────────────────────────────────────────────────────

    @Override
    public Object visitShapeDefinition(ChronosParser.ShapeDefinitionContext ctx) {
        pendingDocComments = extractDocComments(ctx);
        pendingTraits = ctx.traitApplication().stream()
                .map(t -> (SyntaxTrait) visit(t))
                .toList();
        return visit(ctx.shapeDef());
    }

    // ── traits ────────────────────────────────────────────────────────────────

    @Override
    public Object visitTraitApplication(ChronosParser.TraitApplicationContext ctx) {
        String name = ctx.traitId().getText();
        List<SyntaxTraitArg> args = ctx.traitArgList() == null
                ? List.of()
                : ctx.traitArgList().traitArg().stream()
                        .map(a -> (SyntaxTraitArg) visit(a))
                        .toList();
        return new SyntaxTrait(name, args, span(ctx));
    }

    @Override
    public Object visitTraitArg(ChronosParser.TraitArgContext ctx) {
        SyntaxTraitValue value = (SyntaxTraitValue) visit(ctx.traitValue());
        String key = ctx.traitId() != null ? ctx.traitId().getText() : null;
        return new SyntaxTraitArg(key, value, span(ctx));
    }

    @Override
    public Object visitTraitValue(ChronosParser.TraitValueContext ctx) {
        if (ctx.STRING()   != null) return new SyntaxTraitValue.StringVal(unquote(ctx.STRING().getText()));
        if (ctx.NUMBER()   != null) return new SyntaxTraitValue.NumberVal(Double.parseDouble(ctx.NUMBER().getText()));
        if (ctx.BOOL()     != null) return new SyntaxTraitValue.BoolVal(Boolean.parseBoolean(ctx.BOOL().getText()));
        return new SyntaxTraitValue.RefVal((String) visitQualifiedId(ctx.qualifiedId()));
    }

    // ── typeRef ───────────────────────────────────────────────────────────────

    @Override
    public Object visitTypeRef(ChronosParser.TypeRefContext ctx) {
        if (ctx.getChildCount() >= 4 && ctx.getChild(0).getText().equals("List")) {
            return new SyntaxTypeRef.ListType((SyntaxTypeRef) visit(ctx.typeRef(0)), span(ctx));
        }
        if (ctx.getChildCount() >= 6 && ctx.getChild(0).getText().equals("Map")) {
            return new SyntaxTypeRef.MapType(
                    (SyntaxTypeRef) visit(ctx.typeRef(0)),
                    (SyntaxTypeRef) visit(ctx.typeRef(1)),
                    span(ctx));
        }
        if (ctx.primitiveType() != null) {
            return visit(ctx.primitiveType());
        }
        return new SyntaxTypeRef.Named((String) visitQualifiedId(ctx.qualifiedId()), span(ctx));
    }

    @Override
    public Object visitPrimitiveType(ChronosParser.PrimitiveTypeContext ctx) {
        SyntaxTypeRef.PrimitiveKind kind = switch (ctx.getText()) {
            case "String"    -> SyntaxTypeRef.PrimitiveKind.STRING;
            case "Integer"   -> SyntaxTypeRef.PrimitiveKind.INTEGER;
            case "Long"      -> SyntaxTypeRef.PrimitiveKind.LONG;
            case "Float"     -> SyntaxTypeRef.PrimitiveKind.FLOAT;
            case "Boolean"   -> SyntaxTypeRef.PrimitiveKind.BOOLEAN;
            case "Timestamp" -> SyntaxTypeRef.PrimitiveKind.TIMESTAMP;
            case "Blob"      -> SyntaxTypeRef.PrimitiveKind.BLOB;
            case "Document"  -> SyntaxTypeRef.PrimitiveKind.DOCUMENT;
            default -> throw new IllegalStateException("Unknown primitive: " + ctx.getText());
        };
        return new SyntaxTypeRef.Primitive(kind, span(ctx));
    }

    // ── fieldDef ──────────────────────────────────────────────────────────────

    @Override
    public Object visitFieldDef(ChronosParser.FieldDefContext ctx) {
        List<SyntaxTrait> traits = ctx.traitApplication().stream()
                .map(t -> (SyntaxTrait) visit(t))
                .toList();
        return new SyntaxFieldDef(ctx.ID().getText(), (SyntaxTypeRef) visit(ctx.typeRef()), traits, span(ctx));
    }

    // ── entityDef ─────────────────────────────────────────────────────────────

    @Override
    public Object visitEntityDef(ChronosParser.EntityDefContext ctx) {
        List<String>     docComments = consumePendingDocComments();
        List<SyntaxTrait> traits     = consumePendingTraits();
        String name         = ctx.ID(0).getText();
        String parentOrNull = ctx.ID().size() > 1 ? ctx.ID(1).getText() : null;

        List<SyntaxFieldDef>       fields     = new ArrayList<>();
        List<SyntaxEntityInvariant> invariants = new ArrayList<>();
        for (ChronosParser.EntityMemberContext member : ctx.entityMember()) {
            if (member.fieldDef() != null) {
                fields.add((SyntaxFieldDef) visit(member.fieldDef()));
            } else if (member.entityInvariant() != null) {
                invariants.add((SyntaxEntityInvariant) visit(member.entityInvariant()));
            }
        }

        return new SyntaxEntityDecl(name, docComments, parentOrNull,
                Collections.unmodifiableList(fields),
                Collections.unmodifiableList(invariants),
                traits, span(ctx));
    }

    // ── entityInvariant ───────────────────────────────────────────────────────

    @Override
    public Object visitEntityInvariant(ChronosParser.EntityInvariantContext ctx) {
        String name         = ctx.ID().getText();
        String expression   = "";
        String severity     = "";
        String messageOrNull = null;

        for (ChronosParser.InvariantFieldContext field : ctx.invariantField()) {
            String fieldName = field.getChild(0).getText();
            switch (fieldName) {
                case "expression" -> expression    = unquote(field.STRING().getText());
                case "severity"   -> severity      = field.ID(0) != null
                        ? field.ID(0).getText() : field.getChild(2).getText();
                case "message"    -> messageOrNull = unquote(field.STRING().getText());
            }
        }

        return new SyntaxEntityInvariant(name, expression, severity, messageOrNull, span(ctx));
    }

    // ── shapeStructDef ────────────────────────────────────────────────────────

    @Override
    public Object visitShapeStructDef(ChronosParser.ShapeStructDefContext ctx) {
        List<String>      docComments = consumePendingDocComments();
        List<SyntaxTrait> traits      = consumePendingTraits();
        List<SyntaxFieldDef> fields = ctx.fieldDef().stream()
                .map(f -> (SyntaxFieldDef) visit(f))
                .toList();
        return new SyntaxShapeDecl(ctx.ID().getText(), docComments, fields, traits, span(ctx));
    }

    // ── listDef ───────────────────────────────────────────────────────────────

    @Override
    public Object visitListDef(ChronosParser.ListDefContext ctx) {
        List<String>      docComments = consumePendingDocComments();
        List<SyntaxTrait> traits      = consumePendingTraits();
        return new SyntaxListDecl(ctx.ID().getText(), docComments,
                (SyntaxTypeRef) visit(ctx.typeRef()), traits, span(ctx));
    }

    // ── mapDef ────────────────────────────────────────────────────────────────

    @Override
    public Object visitMapDef(ChronosParser.MapDefContext ctx) {
        List<String>      docComments = consumePendingDocComments();
        List<SyntaxTrait> traits      = consumePendingTraits();
        return new SyntaxMapDecl(ctx.ID().getText(), docComments,
                (SyntaxTypeRef) visit(ctx.typeRef(0)),
                (SyntaxTypeRef) visit(ctx.typeRef(1)),
                traits, span(ctx));
    }

    // ── enumDef / enumMember ──────────────────────────────────────────────────

    @Override
    public Object visitEnumDef(ChronosParser.EnumDefContext ctx) {
        List<String>           docComments = consumePendingDocComments();
        List<SyntaxTrait>      traits      = consumePendingTraits();
        List<SyntaxEnumMember> members     = ctx.enumMember().stream()
                .map(m -> (SyntaxEnumMember) visit(m))
                .toList();
        return new SyntaxEnumDecl(ctx.ID().getText(), docComments, members, traits, span(ctx));
    }

    @Override
    public Object visitEnumMember(ChronosParser.EnumMemberContext ctx) {
        Integer ordinalOrNull = ctx.NUMBER() != null
                ? Integer.parseInt(ctx.NUMBER().getText()) : null;
        return new SyntaxEnumMember(ctx.ID().getText(), ordinalOrNull, span(ctx));
    }

    // ── actorDef ──────────────────────────────────────────────────────────────

    @Override
    public Object visitActorDef(ChronosParser.ActorDefContext ctx) {
        List<String>      docComments  = consumePendingDocComments();
        List<SyntaxTrait> traits       = consumePendingTraits();
        String            parentOrNull = ctx.ID().size() > 1 ? ctx.ID(1).getText() : null;
        return new SyntaxActorDecl(ctx.ID(0).getText(), docComments, parentOrNull, traits, span(ctx));
    }

    // ── policyDef ─────────────────────────────────────────────────────────────

    @Override
    public Object visitPolicyDef(ChronosParser.PolicyDefContext ctx) {
        List<String>      docComments = consumePendingDocComments();
        List<SyntaxTrait> traits      = consumePendingTraits();
        return new SyntaxPolicyDecl(ctx.ID().getText(), docComments,
                unquote(ctx.STRING().getText()), traits, span(ctx));
    }

    // ── relationshipDef ───────────────────────────────────────────────────────

    @Override
    public Object visitRelationshipDef(ChronosParser.RelationshipDefContext ctx) {
        List<String>      docComments = consumePendingDocComments();
        List<SyntaxTrait> traits      = consumePendingTraits();
        ChronosParser.RelationshipBodyContext body = ctx.relationshipBody();

        String semanticsOrNull = body.semanticsValue() != null
                ? body.semanticsValue().getText() : null;
        String inverseOrNull   = body.ID().size() > 2 ? body.ID(2).getText() : null;

        return new SyntaxRelationshipDecl(ctx.ID().getText(), docComments,
                body.ID(0).getText(), body.ID(1).getText(),
                body.cardinalityValue().getText(),
                semanticsOrNull, inverseOrNull,
                traits, span(ctx));
    }

    // ── invariantDef ──────────────────────────────────────────────────────────

    @Override
    public Object visitInvariantDef(ChronosParser.InvariantDefContext ctx) {
        List<String>      docComments = consumePendingDocComments();
        List<SyntaxTrait> traits      = consumePendingTraits();
        String name = ctx.ID().getText();

        List<String> scope      = List.of();
        String       expression = "";
        String       severity   = "";
        String       messageOrNull = null;

        for (ChronosParser.InvariantFieldContext field : ctx.invariantField()) {
            String fieldName = field.getChild(0).getText();
            switch (fieldName) {
                case "scope"      -> scope      = field.ID().stream()
                        .map(TerminalNode::getText).toList();
                case "expression" -> expression = unquote(field.STRING().getText());
                case "severity"   -> severity   = field.ID(0) != null
                        ? field.ID(0).getText() : field.getChild(2).getText();
                case "message"    -> messageOrNull = unquote(field.STRING().getText());
            }
        }

        return new SyntaxInvariantDecl(name, docComments, scope, expression, severity, messageOrNull,
                traits, span(ctx));
    }

    // ── denyDef ───────────────────────────────────────────────────────────────

    @Override
    public Object visitDenyDef(ChronosParser.DenyDefContext ctx) {
        List<String>      docComments = consumePendingDocComments();
        List<SyntaxTrait> traits      = consumePendingTraits();
        String name = ctx.ID().getText();

        String       description = "";
        List<String> scope       = List.of();
        String       severity    = "";

        for (ChronosParser.DenyFieldContext field : ctx.denyField()) {
            String fieldName = field.getChild(0).getText();
            switch (fieldName) {
                case "description" -> description = unquote(field.STRING().getText());
                case "scope"       -> scope       = field.ID().stream()
                        .map(TerminalNode::getText).toList();
                case "severity"    -> severity    = field.ID(0).getText();
            }
        }

        return new SyntaxDenyDecl(name, docComments, description, scope, severity, traits, span(ctx));
    }

    // ── errorDef ──────────────────────────────────────────────────────────────

    @Override
    public Object visitErrorDef(ChronosParser.ErrorDefContext ctx) {
        List<String>      docComments = consumePendingDocComments();
        List<SyntaxTrait> traits      = consumePendingTraits();
        String name = ctx.ID().getText();

        String            code        = "";
        String            severity    = "";
        boolean           recoverable = false;
        String            message     = "";
        List<SyntaxFieldDef> payload  = List.of();

        for (ChronosParser.ErrorFieldContext field : ctx.errorField()) {
            String fieldName = field.getChild(0).getText();
            switch (fieldName) {
                case "code"        -> code        = unquote(field.STRING().getText());
                case "severity"    -> severity    = field.ID().getText();
                case "recoverable" -> recoverable = Boolean.parseBoolean(field.BOOL().getText());
                case "message"     -> message     = unquote(field.STRING().getText());
                case "payload"     -> payload     = field.fieldDef().stream()
                        .map(f -> (SyntaxFieldDef) visit(f)).toList();
            }
        }

        return new SyntaxErrorDecl(name, docComments, code, severity, recoverable, message,
                payload, traits, span(ctx));
    }

    // ── statemachineDef ───────────────────────────────────────────────────────

    @Override
    public Object visitStatemachineDef(ChronosParser.StatemachineDefContext ctx) {
        List<String>      docComments = consumePendingDocComments();
        List<SyntaxTrait> traits      = consumePendingTraits();
        String name = ctx.ID().getText();

        String            entityName     = "";
        String            fieldName      = "";
        List<String>      states         = List.of();
        String            initialState   = "";
        List<String>      terminalStates = List.of();
        List<SyntaxTransition> transitions = List.of();

        for (ChronosParser.StatemachineFieldContext field : ctx.statemachineField()) {
            String fieldType = field.getChild(0).getText();
            switch (fieldType) {
                case "entity"      -> entityName     = field.ID(0).getText();
                case "field"       -> fieldName      = field.ID(0).getText();
                case "states"      -> states         = field.ID().stream()
                        .map(TerminalNode::getText).toList();
                case "initial"     -> initialState   = field.ID(0).getText();
                case "terminal"    -> terminalStates = field.ID().stream()
                        .map(TerminalNode::getText).toList();
                case "transitions" -> transitions    = field.transition().stream()
                        .map(t -> (SyntaxTransition) visitTransition(t)).toList();
            }
        }

        return new SyntaxStateMachineDecl(name, docComments, entityName, fieldName,
                states, initialState, terminalStates, transitions, traits, span(ctx));
    }

    // ── roleDef ───────────────────────────────────────────────────────────────

    @Override
    public Object visitRoleDef(ChronosParser.RoleDefContext ctx) {
        List<String>      docComments = consumePendingDocComments();
        List<SyntaxTrait> traits      = consumePendingTraits();
        String name = ctx.ID().getText();

        List<String> allowedPermissions = List.of();
        List<String> deniedPermissions  = List.of();

        for (ChronosParser.RoleBodyFieldContext field : ctx.roleBody().roleBodyField()) {
            String keyword = field.getChild(0).getText();
            List<String> permissions = field.ID().stream()
                    .map(TerminalNode::getText)
                    .toList();
            if ("allow".equals(keyword)) {
                allowedPermissions = permissions;
            } else if ("deny".equals(keyword)) {
                deniedPermissions = permissions;
            }
        }

        return new SyntaxRoleDecl(name, docComments, allowedPermissions, deniedPermissions,
                traits, span(ctx));
    }

    // ── eventDef ──────────────────────────────────────────────────────────────

    @Override
    public Object visitEventDef(ChronosParser.EventDefContext ctx) {
        List<String>      docComments = consumePendingDocComments();
        List<SyntaxTrait> traits      = consumePendingTraits();
        String name = ctx.ID().getText();
        List<SyntaxFieldDef> fields = ctx.fieldDef().stream()
                .map(f -> (SyntaxFieldDef) visit(f))
                .toList();
        return new SyntaxEventDecl(name, docComments, fields, traits, span(ctx));
    }

    @Override
    public Object visitTransition(ChronosParser.TransitionContext ctx) {
        String guardOrNull  = null;
        String actionOrNull = null;

        if (ctx.transitionBody() != null) {
            for (ChronosParser.TransitionFieldContext field : ctx.transitionBody().transitionField()) {
                String type = field.getChild(0).getText();
                if ("guard".equals(type))  guardOrNull  = unquote(field.STRING().getText());
                if ("action".equals(type)) actionOrNull = unquote(field.STRING().getText());
            }
        }

        return new SyntaxTransition(ctx.ID(0).getText(), ctx.ID(1).getText(),
                guardOrNull, actionOrNull, span(ctx));
    }

    // ── outcomeExpr ───────────────────────────────────────────────────────────

    @Override
    public Object visitOutcomeExpr(ChronosParser.OutcomeExprContext ctx) {
        String target = ctx.ID().getText();
        Span   span   = span(ctx);
        return switch (ctx.getChild(0).getText()) {
            case "TransitionTo" -> new SyntaxOutcomeExpr.TransitionTo(target, span);
            case "ReturnToStep" -> new SyntaxOutcomeExpr.ReturnToStep(target, span);
            default -> throw new IllegalStateException(
                    "Unknown outcome keyword: " + ctx.getChild(0).getText());
        };
    }

    // ── dataField ─────────────────────────────────────────────────────────────

    @Override
    public Object visitDataField(ChronosParser.DataFieldContext ctx) {
        String         name = ctx.ID().getText();
        SyntaxTypeRef  type = (SyntaxTypeRef) visit(ctx.typeRef());
        return new SyntaxDataField(name, type, span(ctx));
    }

    // ── stepField ─────────────────────────────────────────────────────────────

    @Override
    public Object visitStepField(ChronosParser.StepFieldContext ctx) {
        String keyword = ctx.getChild(0).getText();
        Span   span    = span(ctx);
        return switch (keyword) {
            case "action"      -> new SyntaxStepField.Action(
                    unquote(ctx.STRING().getText()), span);
            case "expectation" -> new SyntaxStepField.Expectation(
                    unquote(ctx.STRING().getText()), span);
            case "outcome"     -> new SyntaxStepField.Outcome(
                    (SyntaxOutcomeExpr) visit(ctx.outcomeExpr()), span);
            case "telemetry"   -> new SyntaxStepField.Telemetry(
                    ctx.ID().stream().map(TerminalNode::getText).toList(), span);
            case "risk"        -> new SyntaxStepField.Risk(
                    unquote(ctx.STRING().getText()), span);
            case "input"       -> new SyntaxStepField.Input(
                    ctx.dataField().stream()
                            .map(df -> (SyntaxDataField) visitDataField(df))
                            .toList(), span);
            case "output"      -> new SyntaxStepField.Output(
                    ctx.dataField().stream()
                            .map(df -> (SyntaxDataField) visitDataField(df))
                            .toList(), span);
            default -> throw new IllegalStateException("Unknown step field: " + keyword);
        };
    }

    // ── step ──────────────────────────────────────────────────────────────────

    @Override
    public Object visitStep(ChronosParser.StepContext ctx) {
        List<SyntaxTrait>    traits = ctx.traitApplication().stream()
                .map(t -> (SyntaxTrait) visit(t))
                .toList();
        List<SyntaxStepField> fields = ctx.stepField().stream()
                .map(f -> (SyntaxStepField) visit(f))
                .toList();
        return new SyntaxStep(ctx.ID().getText(), traits, fields, span(ctx));
    }

    // ── variantsDecl / variantEntry / variantBody ─────────────────────────────

    @Override
    public Object visitVariantsDecl(ChronosParser.VariantsDeclContext ctx) {
        return ctx.variantEntry().stream()
                .map(e -> (SyntaxVariant) visit(e))
                .toList();
    }

    @Override
    public Object visitVariantEntry(ChronosParser.VariantEntryContext ctx) {
        String      name    = ctx.ID().getText();
        SyntaxVariant partial = (SyntaxVariant) visit(ctx.variantBody());
        return new SyntaxVariant(name, partial.triggerName(), partial.steps(),
                partial.outcomeOrNull(), span(ctx));
    }

    @Override
    public Object visitVariantBody(ChronosParser.VariantBodyContext ctx) {
        String              trigger       = ctx.ID().getText();
        List<SyntaxStep>    steps         = ctx.step().stream()
                .map(s -> (SyntaxStep) visit(s))
                .toList();
        SyntaxOutcomeExpr   outcomeOrNull = ctx.outcomeExpr() != null
                ? (SyntaxOutcomeExpr) visit(ctx.outcomeExpr()) : null;
        return new SyntaxVariant("", trigger, steps, outcomeOrNull, span(ctx));
    }

    // ── outcomesDecl ──────────────────────────────────────────────────────────

    @Override
    public Object visitOutcomesDecl(ChronosParser.OutcomesDeclContext ctx) {
        String successOrNull = null;
        String failureOrNull = null;
        for (var entry : ctx.outcomeEntry()) {
            String key   = entry.getChild(0).getText();
            String value = unquote(entry.STRING().getText());
            if ("success".equals(key)) successOrNull = value;
            else                       failureOrNull  = value;
        }
        return new SyntaxOutcomes(successOrNull, failureOrNull, span(ctx));
    }

    // ── journeyDef ────────────────────────────────────────────────────────────

    @Override
    public Object visitJourneyDef(ChronosParser.JourneyDefContext ctx) {
        List<String>      docComments = consumePendingDocComments();
        List<SyntaxTrait> traits      = consumePendingTraits();
        ChronosParser.JourneyBodyContext body = ctx.journeyBody();

        String actorOrNull = body.actorDecl() != null
                ? body.actorDecl().ID().getText() : null;

        List<String> preconditions = body.preconditionsDecl() != null
                ? body.preconditionsDecl().STRING().stream()
                        .map(s -> unquote(s.getText())).toList()
                : List.of();

        List<SyntaxStep> steps = body.stepsDecl() != null
                ? body.stepsDecl().step().stream()
                        .map(s -> (SyntaxStep) visit(s)).toList()
                : List.of();

        List<SyntaxVariant> variants = body.variantsDecl() != null
                ? (List<SyntaxVariant>) visit(body.variantsDecl())
                : List.of();

        SyntaxOutcomes outcomesOrNull = body.outcomesDecl() != null
                ? (SyntaxOutcomes) visit(body.outcomesDecl()) : null;

        return new SyntaxJourneyDecl(ctx.ID().getText(), docComments, actorOrNull, preconditions,
                steps, variants, outcomesOrNull, traits, span(ctx));
    }

    // ── qualifiedId ───────────────────────────────────────────────────────────

    @Override
    public Object visitQualifiedId(ChronosParser.QualifiedIdContext ctx) {
        return ctx.ID().stream()
                .map(TerminalNode::getText)
                .collect(Collectors.joining("."));
    }
}
