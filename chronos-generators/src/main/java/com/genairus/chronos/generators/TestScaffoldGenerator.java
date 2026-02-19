package com.genairus.chronos.generators;

import com.genairus.chronos.model.*;

/**
 * Generates test scaffolding with assertion stubs for invariants and deny blocks.
 *
 * <p>For each entity with invariants and each global invariant, this generator
 * creates a test method stub that includes:
 * <ul>
 *   <li>A descriptive test method name</li>
 *   <li>A TODO comment with the invariant expression</li>
 *   <li>A placeholder assertion that needs to be implemented</li>
 * </ul>
 *
 * <p>For each deny block, this generator creates a negative test stub that
 * asserts the prohibited condition never holds.
 *
 * <p>The generated tests are in JUnit 5 format and serve as a starting point
 * for developers to implement actual invariant validation tests and prohibition checks.
 */
public final class TestScaffoldGenerator implements ChronosGenerator {

    @Override
    public GeneratorOutput generate(ChronosModel model) {
        var sb = new StringBuilder();
        
        String packageName = model.namespace();
        String className = toClassName(model.namespace()) + "InvariantTests";
        
        // Package and imports
        sb.append("package ").append(packageName).append(".tests;\n\n");
        sb.append("import org.junit.jupiter.api.Test;\n");
        sb.append("import static org.junit.jupiter.api.Assertions.*;\n\n");
        
        // Class declaration
        sb.append("/**\n");
        sb.append(" * Test scaffolding for invariants defined in ").append(model.namespace()).append(".\n");
        sb.append(" *\n");
        sb.append(" * <p>Each test method corresponds to an invariant and contains a TODO stub\n");
        sb.append(" * that should be implemented to verify the invariant holds.\n");
        sb.append(" */\n");
        sb.append("public class ").append(className).append(" {\n\n");
        
        // Generate tests for entity-scoped invariants
        for (var entity : model.entities()) {
            if (!entity.invariants().isEmpty()) {
                sb.append("    // ── ").append(entity.name()).append(" Invariants ──────────────────────────────────\n\n");
                for (var inv : entity.invariants()) {
                    generateEntityInvariantTest(sb, entity, inv);
                }
            }
        }
        
        // Generate tests for global invariants
        if (!model.invariants().isEmpty()) {
            sb.append("    // ── Global Invariants ──────────────────────────────────────────\n\n");
            for (var inv : model.invariants()) {
                generateGlobalInvariantTest(sb, inv);
            }
        }

        // Generate tests for deny blocks (negative requirements)
        if (!model.denies().isEmpty()) {
            sb.append("    // ── Deny Blocks (Negative Requirements) ────────────────────────\n\n");
            for (var deny : model.denies()) {
                generateDenyTest(sb, deny);
            }
        }

        sb.append("}\n");
        
        String filename = className + ".java";
        return GeneratorOutput.of(filename, sb.toString());
    }
    
    private void generateEntityInvariantTest(StringBuilder sb, EntityDef entity, EntityInvariant inv) {
        String testName = "test" + entity.name() + "_" + inv.name();
        
        sb.append("    @Test\n");
        sb.append("    void ").append(testName).append("() {\n");
        sb.append("        // TODO: Implement test for ").append(entity.name()).append(".").append(inv.name()).append("\n");
        sb.append("        // Expression: ").append(inv.expression()).append("\n");
        sb.append("        // Severity: ").append(inv.severity()).append("\n");
        if (inv.message().isPresent()) {
            sb.append("        // Message: ").append(inv.message().get()).append("\n");
        }
        sb.append("        \n");
        sb.append("        // Example:\n");
        sb.append("        // var ").append(toLowerCamelCase(entity.name())).append(" = create").append(entity.name()).append("();\n");
        sb.append("        // assertTrue(/* verify: ").append(inv.expression()).append(" */);\n");
        sb.append("        \n");
        sb.append("        fail(\"Test not yet implemented\");\n");
        sb.append("    }\n\n");
    }
    
    private void generateGlobalInvariantTest(StringBuilder sb, InvariantDef inv) {
        String testName = "testGlobal_" + inv.name();
        
        sb.append("    @Test\n");
        sb.append("    void ").append(testName).append("() {\n");
        sb.append("        // TODO: Implement test for global invariant ").append(inv.name()).append("\n");
        sb.append("        // Scope: ").append(String.join(", ", inv.scope())).append("\n");
        sb.append("        // Expression: ").append(inv.expression()).append("\n");
        sb.append("        // Severity: ").append(inv.severity()).append("\n");
        if (inv.message().isPresent()) {
            sb.append("        // Message: ").append(inv.message().get()).append("\n");
        }
        sb.append("        \n");
        sb.append("        // Example:\n");
        for (String entityName : inv.scope()) {
            sb.append("        // var ").append(toLowerCamelCase(entityName)).append(" = create").append(entityName).append("();\n");
        }
        sb.append("        // assertTrue(/* verify: ").append(inv.expression()).append(" */);\n");
        sb.append("        \n");
        sb.append("        fail(\"Test not yet implemented\");\n");
        sb.append("    }\n\n");
    }

    private void generateDenyTest(StringBuilder sb, DenyDef deny) {
        String testName = "testDeny_" + deny.name();

        sb.append("    @Test\n");
        sb.append("    void ").append(testName).append("() {\n");
        sb.append("        // TODO: Implement negative test for deny ").append(deny.name()).append("\n");
        sb.append("        // Description: ").append(deny.description()).append("\n");
        sb.append("        // Scope: ").append(String.join(", ", deny.scope())).append("\n");
        sb.append("        // Severity: ").append(deny.severity()).append("\n");

        // Add compliance trait if present
        deny.traits().stream()
                .filter(t -> t.name().equals("compliance"))
                .findFirst()
                .ifPresent(t -> {
                    sb.append("        // Compliance: ").append(t.name()).append("\n");
                });

        sb.append("        \n");
        sb.append("        // This is a NEGATIVE test - assert that the prohibited condition NEVER holds\n");
        sb.append("        // Example:\n");
        for (String entityName : deny.scope()) {
            sb.append("        // var ").append(toLowerCamelCase(entityName)).append(" = create").append(entityName).append("();\n");
        }
        sb.append("        // assertFalse(/* verify prohibited condition does NOT occur */);\n");
        sb.append("        // or: assertThrows(/* verify operation is prevented */);\n");
        sb.append("        \n");
        sb.append("        fail(\"Test not yet implemented\");\n");
        sb.append("    }\n\n");
    }

    private String toClassName(String namespace) {
        String[] parts = namespace.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            sb.append(Character.toUpperCase(part.charAt(0)));
            sb.append(part.substring(1));
        }
        return sb.toString();
    }
    
    private String toLowerCamelCase(String name) {
        if (name.isEmpty()) return name;
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}

