package com.genairus.chronos.generators;

import com.genairus.chronos.parser.ChronosModelParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TypeScriptTypesGeneratorTest {

    private final TypeScriptTypesGenerator generator = new TypeScriptTypesGenerator();

    @Test
    void entityGeneratesInterface() {
        var model = ChronosModelParser.parseString("test", """
            namespace com.example

            entity User {
                @required
                id: String
                @required
                name: String
                @required
                age: Integer
            }
            """);

        var output = generator.generate(model);
        var content = output.files().get("com-example.d.ts");

        assertNotNull(content);
        assertTrue(content.contains("export interface User {"));
        assertTrue(content.contains("id: string;"));
        assertTrue(content.contains("name: string;"));
        assertTrue(content.contains("age: number;"));
    }

    @Test
    void optionalFieldsHaveQuestionMark() {
        var model = ChronosModelParser.parseString("test", """
            namespace com.example

            entity User {
                @required
                id: String
                email: String
            }
            """);

        var output = generator.generate(model);
        var content = output.files().get("com-example.d.ts");

        assertTrue(content.contains("id: string;"));
        assertTrue(content.contains("email?: string;"));
    }

    @Test
    void enumGeneratesTypeScriptEnum() {
        var model = ChronosModelParser.parseString("test", """
            namespace com.example
            
            enum Status {
                ACTIVE
                INACTIVE
                PENDING
            }
            """);

        var output = generator.generate(model);
        var content = output.files().get("com-example.d.ts");

        assertTrue(content.contains("export enum Status {"));
        assertTrue(content.contains("ACTIVE = \"ACTIVE\""));
        assertTrue(content.contains("INACTIVE = \"INACTIVE\""));
        assertTrue(content.contains("PENDING = \"PENDING\""));
    }

    @Test
    void errorWithPayloadGeneratesSeparateInterface() {
        var model = ChronosModelParser.parseString("test", """
            namespace com.example

            error PaymentDeclinedError {
                code: "PAYMENT_DECLINED"
                severity: high
                recoverable: true
                message: "Payment was declined"
                payload: {
                    declineReason: String
                    retryAllowed: Boolean
                }
            }
            """);

        var output = generator.generate(model);
        var content = output.files().get("com-example.d.ts");

        // Should have payload interface
        assertTrue(content.contains("export interface PaymentDeclinedErrorPayload {"));
        assertTrue(content.contains("declineReason: string;"));
        assertTrue(content.contains("retryAllowed: boolean;"));

        // Should have main error interface
        assertTrue(content.contains("export interface PaymentDeclinedError {"));
        assertTrue(content.contains("code: \"PAYMENT_DECLINED\";"));
        assertTrue(content.contains("message: string;"));
        assertTrue(content.contains("payload: PaymentDeclinedErrorPayload;"));
    }

    @Test
    void errorWithoutPayloadHasNoPayloadField() {
        var model = ChronosModelParser.parseString("test", """
            namespace com.example
            
            error SystemFailureError {
                code: "SYS-001"
                severity: critical
                recoverable: false
                message: "Critical system failure"
            }
            """);

        var output = generator.generate(model);
        var content = output.files().get("com-example.d.ts");

        // Should not have payload interface
        assertFalse(content.contains("SystemFailureErrorPayload"));

        // Should have main error interface without payload
        assertTrue(content.contains("export interface SystemFailureError {"));
        assertTrue(content.contains("code: \"SYS-001\";"));
        assertTrue(content.contains("message: string;"));
        assertFalse(content.contains("payload:"));
    }

    @Test
    void listTypeRendersAsArray() {
        var model = ChronosModelParser.parseString("test", """
            namespace com.example

            entity Order {
                @required
                items: List<String>
            }
            """);

        var output = generator.generate(model);
        var content = output.files().get("com-example.d.ts");

        assertTrue(content.contains("items: string[];"));
    }

    @Test
    void mapTypeRendersAsRecord() {
        var model = ChronosModelParser.parseString("test", """
            namespace com.example

            entity Config {
                @required
                settings: Map<String, Integer>
            }
            """);

        var output = generator.generate(model);
        var content = output.files().get("com-example.d.ts");

        assertTrue(content.contains("settings: Record<string, number>;"));
    }
}

