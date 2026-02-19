package com.genairus.chronos.generators;

import com.genairus.chronos.model.ChronosModel;
import com.genairus.chronos.model.StateMachineDef;
import com.genairus.chronos.model.Transition;

/**
 * Generates transition-coverage test scaffolding for {@link StateMachineDef} declarations.
 *
 * <p>For each statemachine, this generator creates a test class with:
 * <ul>
 *   <li>A test method for each transition to verify it can be executed</li>
 *   <li>A test method for each guard condition to verify it's evaluated correctly</li>
 *   <li>A test method for each action to verify it's executed</li>
 *   <li>A test method to verify terminal states cannot transition further</li>
 *   <li>A test method to verify the initial state is set correctly</li>
 * </ul>
 *
 * <p>The generated tests are in JUnit 5 format and serve as a starting point
 * for developers to implement actual state machine transition tests.
 */
public final class StateMachineTestGenerator implements ChronosGenerator {

    @Override
    public GeneratorOutput generate(ChronosModel model) {
        var sb = new StringBuilder();
        
        String packageName = model.namespace();
        String className = toClassName(model.namespace()) + "StateMachineTests";
        
        // Package and imports
        sb.append("package ").append(packageName).append(".tests;\n\n");
        sb.append("import org.junit.jupiter.api.Test;\n");
        sb.append("import org.junit.jupiter.api.BeforeEach;\n");
        sb.append("import static org.junit.jupiter.api.Assertions.*;\n\n");
        
        // Class declaration
        sb.append("/**\n");
        sb.append(" * Test scaffolding for state machines defined in ").append(model.namespace()).append(".\n");
        sb.append(" *\n");
        sb.append(" * <p>Each test method corresponds to a state transition and contains a TODO stub\n");
        sb.append(" * that should be implemented to verify the transition can be executed correctly.\n");
        sb.append(" */\n");
        sb.append("public class ").append(className).append(" {\n\n");
        
        // Generate tests for each state machine
        for (StateMachineDef sm : model.stateMachines()) {
            generateStateMachineTests(sb, sm);
        }
        
        sb.append("}\n");
        
        String filename = className + ".java";
        return GeneratorOutput.of(filename, sb.toString());
    }
    
    private void generateStateMachineTests(StringBuilder sb, StateMachineDef sm) {
        sb.append("    // ── ").append(sm.name()).append(" (").append(sm.entityName()).append(".").append(sm.fieldName()).append(") ──────────────────────────────────\n\n");
        
        // Test for initial state
        generateInitialStateTest(sb, sm);
        
        // Test for each transition
        for (Transition t : sm.transitions()) {
            generateTransitionTest(sb, sm, t);
        }
        
        // Test for terminal states
        for (String terminalState : sm.terminalStates()) {
            generateTerminalStateTest(sb, sm, terminalState);
        }
        
        sb.append("\n");
    }
    
    private void generateInitialStateTest(StringBuilder sb, StateMachineDef sm) {
        String testName = "test" + sm.name() + "_InitialState";
        
        sb.append("    @Test\n");
        sb.append("    void ").append(testName).append("() {\n");
        sb.append("        // TODO: Verify that new ").append(sm.entityName()).append(" instances start in ").append(sm.initialState()).append(" state\n");
        sb.append("        \n");
        sb.append("        // Example:\n");
        sb.append("        // var ").append(toLowerCamelCase(sm.entityName())).append(" = new ").append(sm.entityName()).append("();\n");
        sb.append("        // assertEquals(").append(sm.entityName()).append("Status.").append(sm.initialState()).append(", ").append(toLowerCamelCase(sm.entityName())).append(".get").append(capitalize(sm.fieldName())).append("());\n");
        sb.append("        \n");
        sb.append("        fail(\"Test not yet implemented\");\n");
        sb.append("    }\n\n");
    }
    
    private void generateTransitionTest(StringBuilder sb, StateMachineDef sm, Transition t) {
        String testName = "test" + sm.name() + "_" + t.fromState() + "To" + t.toState();
        
        sb.append("    @Test\n");
        sb.append("    void ").append(testName).append("() {\n");
        sb.append("        // TODO: Verify transition from ").append(t.fromState()).append(" to ").append(t.toState()).append("\n");
        
        if (t.guard().isPresent()) {
            sb.append("        // Guard: ").append(t.guard().get()).append("\n");
        }
        if (t.action().isPresent()) {
            sb.append("        // Action: ").append(t.action().get()).append("\n");
        }
        
        sb.append("        \n");
        sb.append("        // Example:\n");
        sb.append("        // var ").append(toLowerCamelCase(sm.entityName())).append(" = create").append(sm.entityName()).append("InState(").append(sm.entityName()).append("Status.").append(t.fromState()).append(");\n");
        
        if (t.guard().isPresent()) {
            sb.append("        // // Ensure guard condition is met: ").append(t.guard().get()).append("\n");
            sb.append("        // setupGuardCondition(").append(toLowerCamelCase(sm.entityName())).append(");\n");
        }
        
        sb.append("        // ").append(toLowerCamelCase(sm.entityName())).append(".transitionTo").append(t.toState()).append("();\n");
        sb.append("        // assertEquals(").append(sm.entityName()).append("Status.").append(t.toState()).append(", ").append(toLowerCamelCase(sm.entityName())).append(".get").append(capitalize(sm.fieldName())).append("());\n");
        
        if (t.action().isPresent()) {
            sb.append("        // // Verify action was executed: ").append(t.action().get()).append("\n");
            sb.append("        // verifyActionExecuted();\n");
        }
        
        sb.append("        \n");
        sb.append("        fail(\"Test not yet implemented\");\n");
        sb.append("    }\n\n");
    }
    
    private void generateTerminalStateTest(StringBuilder sb, StateMachineDef sm, String terminalState) {
        String testName = "test" + sm.name() + "_" + terminalState + "IsTerminal";

        sb.append("    @Test\n");
        sb.append("    void ").append(testName).append("() {\n");
        sb.append("        // TODO: Verify that ").append(terminalState).append(" is a terminal state (no further transitions)\n");
        sb.append("        \n");
        sb.append("        // Example:\n");
        sb.append("        // var ").append(toLowerCamelCase(sm.entityName())).append(" = create").append(sm.entityName()).append("InState(").append(sm.entityName()).append("Status.").append(terminalState).append(");\n");
        sb.append("        // assertThrows(IllegalStateException.class, () -> ").append(toLowerCamelCase(sm.entityName())).append(".transitionToAnyState());\n");
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
    
    private String capitalize(String name) {
        if (name.isEmpty()) return name;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}

