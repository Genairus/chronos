package com.genairus.chronos.generators;

import com.genairus.chronos.ir.model.IrModel;
import com.genairus.chronos.ir.types.StateMachineDef;
import com.genairus.chronos.ir.types.Transition;

import java.util.HashMap;
import java.util.Map;

/**
 * Generates Mermaid state diagrams from {@link StateMachineDef} declarations.
 *
 * <p>Each statemachine in the model produces a separate {@code .mmd} file
 * containing a Mermaid state diagram with:
 * <ul>
 *   <li>Initial state marked with {@code [*]}</li>
 *   <li>Terminal states marked with {@code [*]}</li>
 *   <li>Transitions with optional guard conditions and actions</li>
 * </ul>
 *
 * <p>Example output:
 * <pre>
 * stateDiagram-v2
 *     [*] --> PENDING
 *     PENDING --> PAID : payment received
 *     PAID --> SHIPPED : fulfillment dispatched
 *     SHIPPED --> [*]
 * </pre>
 */
public final class MermaidStateDiagramGenerator implements ChronosGenerator {

    @Override
    public GeneratorOutput generate(IrModel model) {
        Map<String, String> files = new HashMap<>();
        
        for (StateMachineDef sm : model.stateMachines()) {
            String diagram = generateDiagram(sm);
            String filename = sm.name() + ".mmd";
            files.put(filename, diagram);
        }
        
        return new GeneratorOutput(files);
    }

    private String generateDiagram(StateMachineDef sm) {
        var sb = new StringBuilder();
        
        // Header
        sb.append("stateDiagram-v2\n");
        
        // Add note with entity and field information
        sb.append("    note right of ").append(sm.initialState())
          .append(" : ").append(sm.entityName()).append(".").append(sm.fieldName())
          .append("\n");
        
        // Initial state transition
        sb.append("    [*] --> ").append(sm.initialState()).append("\n");
        
        // Add all transitions
        for (Transition t : sm.transitions()) {
            sb.append("    ").append(t.fromState())
              .append(" --> ").append(t.toState());
            
            // Add label if guard or action is present
            String label = buildTransitionLabel(t);
            if (!label.isEmpty()) {
                sb.append(" : ").append(label);
            }
            
            sb.append("\n");
        }
        
        // Terminal state transitions
        for (String terminalState : sm.terminalStates()) {
            sb.append("    ").append(terminalState).append(" --> [*]\n");
        }
        
        return sb.toString();
    }

    private String buildTransitionLabel(Transition t) {
        if (t.guard().isPresent() && t.action().isPresent()) {
            return t.guard().get() + " / " + t.action().get();
        } else if (t.guard().isPresent()) {
            return t.guard().get();
        } else if (t.action().isPresent()) {
            return t.action().get();
        }
        return "";
    }
}

