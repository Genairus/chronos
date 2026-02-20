package com.genairus.chronos.generators;

import com.genairus.chronos.ir.model.IrModel;
import com.genairus.chronos.ir.types.EnumDef;
import com.genairus.chronos.ir.types.ErrorDef;
import com.genairus.chronos.ir.types.FieldDef;
import com.genairus.chronos.ir.types.TypeRef;

/**
 * Generates TypeScript type definitions from a {@link ChronosModel}.
 *
 * <p>The output is a single {@code <namespace>.d.ts} file containing TypeScript
 * interfaces for all entities, shapes, enums, and errors defined in the model.
 * This provides type-safe API stubs for frontend applications.
 *
 * <p>Error payload shapes are included as separate interfaces, enabling type-safe
 * error handling in client code.
 */
public final class TypeScriptTypesGenerator implements ChronosGenerator {

    @Override
    public GeneratorOutput generate(IrModel model) {
        var sb = new StringBuilder();
        
        // File header
        sb.append("/**\n");
        sb.append(" * TypeScript type definitions for ").append(model.namespace()).append("\n");
        sb.append(" * Generated from Chronos model - do not edit manually\n");
        sb.append(" */\n\n");
        
        // Generate interfaces for entities
        for (var entity : model.entities()) {
            generateInterface(sb, entity.name(), entity.fields(), entity.docComments());
        }
        
        // Generate interfaces for shape structs
        for (var shape : model.shapeStructs()) {
            generateInterface(sb, shape.name(), shape.fields(), shape.docComments());
        }
        
        // Generate enums
        for (var enumDef : model.enums()) {
            generateEnum(sb, enumDef);
        }
        
        // Generate error interfaces
        for (var error : model.errors()) {
            generateErrorInterface(sb, error);
        }
        
        String filename = model.namespace().replace('.', '-') + ".d.ts";
        return GeneratorOutput.of(filename, sb.toString());
    }
    
    private void generateInterface(StringBuilder sb, String name, java.util.List<FieldDef> fields, 
                                   java.util.List<String> docComments) {
        // Doc comments
        if (!docComments.isEmpty()) {
            sb.append("/**\n");
            for (var doc : docComments) {
                sb.append(" * ").append(doc).append("\n");
            }
            sb.append(" */\n");
        }
        
        sb.append("export interface ").append(name).append(" {\n");
        for (var field : fields) {
            sb.append("  ").append(field.name());
            if (!field.isRequired()) {
                sb.append("?");
            }
            sb.append(": ").append(renderTypeScriptType(field.type())).append(";\n");
        }
        sb.append("}\n\n");
    }
    
    private void generateEnum(StringBuilder sb, EnumDef enumDef) {
        if (!enumDef.docComments().isEmpty()) {
            sb.append("/**\n");
            for (var doc : enumDef.docComments()) {
                sb.append(" * ").append(doc).append("\n");
            }
            sb.append(" */\n");
        }
        
        sb.append("export enum ").append(enumDef.name()).append(" {\n");
        for (int i = 0; i < enumDef.members().size(); i++) {
            var member = enumDef.members().get(i);
            sb.append("  ").append(member.name()).append(" = \"").append(member.name()).append("\"");
            if (i < enumDef.members().size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("}\n\n");
    }
    
    private void generateErrorInterface(StringBuilder sb, ErrorDef error) {
        // Generate payload interface if it has fields
        if (!error.payload().isEmpty()) {
            String payloadName = error.name() + "Payload";
            sb.append("/**\n");
            sb.append(" * Payload for ").append(error.name()).append("\n");
            sb.append(" */\n");
            sb.append("export interface ").append(payloadName).append(" {\n");
            for (var field : error.payload()) {
                sb.append("  ").append(field.name()).append(": ")
                  .append(renderTypeScriptType(field.type())).append(";\n");
            }
            sb.append("}\n\n");
        }
        
        // Generate main error interface
        sb.append("/**\n");
        sb.append(" * ").append(error.message()).append("\n");
        sb.append(" * Code: ").append(error.code()).append("\n");
        sb.append(" * Severity: ").append(error.severity()).append("\n");
        sb.append(" * Recoverable: ").append(error.recoverable()).append("\n");
        sb.append(" */\n");
        sb.append("export interface ").append(error.name()).append(" {\n");
        sb.append("  code: \"").append(error.code()).append("\";\n");
        sb.append("  message: string;\n");
        if (!error.payload().isEmpty()) {
            sb.append("  payload: ").append(error.name()).append("Payload;\n");
        }
        sb.append("}\n\n");
    }
    
    private String renderTypeScriptType(TypeRef type) {
        return switch (type) {
            case TypeRef.PrimitiveType p -> switch (p.kind()) {
                case STRING -> "string";
                case INTEGER, LONG -> "number";
                case FLOAT -> "number";
                case BOOLEAN -> "boolean";
                case TIMESTAMP -> "Date";
                case BLOB -> "Blob";
                case DOCUMENT -> "any";
            };
            case TypeRef.ListType l -> renderTypeScriptType(l.elementType()) + "[]";
            case TypeRef.MapType m -> "Record<" + renderTypeScriptType(m.keyType()) + ", "
                                      + renderTypeScriptType(m.valueType()) + ">";
            case TypeRef.NamedTypeRef n -> n.qualifiedId();
        };
    }
}

