package com.genairus.chronos.parser;

import com.genairus.chronos.model.ChronosModel;
import com.genairus.chronos.model.ErrorDef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ErrorParserTest {

    @Test
    void errorWithAllFields() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                error PaymentDeclinedError {
                    code: "PAY-001"
                    severity: high
                    recoverable: true
                    message: "Payment was declined by the gateway"
                    payload: {
                        gatewayCode: String
                        retryable: Boolean
                    }
                }
                """);

        assertEquals(1, model.errors().size());
        ErrorDef error = model.errors().get(0);
        assertEquals("PaymentDeclinedError", error.name());
        assertEquals("PAY-001", error.code());
        assertEquals("high", error.severity());
        assertTrue(error.recoverable());
        assertEquals("Payment was declined by the gateway", error.message());
        assertEquals(2, error.payload().size());
        assertEquals("gatewayCode", error.payload().get(0).name());
        assertEquals("retryable", error.payload().get(1).name());
    }

    @Test
    void errorWithoutPayload() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                error SystemFailureError {
                    code: "SYS-001"
                    severity: critical
                    recoverable: false
                    message: "Critical system failure occurred"
                }
                """);

        ErrorDef error = model.errors().get(0);
        assertEquals("SystemFailureError", error.name());
        assertEquals("SYS-001", error.code());
        assertEquals("critical", error.severity());
        assertFalse(error.recoverable());
        assertEquals("Critical system failure occurred", error.message());
        assertTrue(error.payload().isEmpty());
    }

    @Test
    void errorWithDocComments() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                /// Payment declined by gateway
                /// This error is recoverable
                error PaymentDeclinedError {
                    code: "PAY-001"
                    severity: high
                    recoverable: true
                    message: "Payment was declined"
                }
                """);

        ErrorDef error = model.errors().get(0);
        assertEquals(2, error.docComments().size());
        assertEquals("Payment declined by gateway", error.docComments().get(0));
        assertEquals("This error is recoverable", error.docComments().get(1));
    }

    @Test
    void errorWithTrait() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                @description("Payment processing error")
                error PaymentDeclinedError {
                    code: "PAY-001"
                    severity: high
                    recoverable: true
                    message: "Payment was declined"
                }
                """);

        ErrorDef error = model.errors().get(0);
        assertEquals(1, error.traits().size());
        assertEquals("description", error.traits().get(0).name());
    }

    @Test
    void multipleErrors() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                error Error1 {
                    code: "E1"
                    severity: low
                    recoverable: true
                    message: "Error 1"
                }
                
                error Error2 {
                    code: "E2"
                    severity: high
                    recoverable: false
                    message: "Error 2"
                }
                """);

        assertEquals(2, model.errors().size());
        assertEquals("Error1", model.errors().get(0).name());
        assertEquals("Error2", model.errors().get(1).name());
    }

    @Test
    void errorWithComplexPayload() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                error NetworkError {
                    code: "NET-001"
                    severity: medium
                    recoverable: true
                    message: "Network error"
                    payload: {
                        attemptCount: Integer
                        lastAttemptTime: String
                        retryAfter: Integer
                        errorDetails: String
                    }
                }
                """);

        ErrorDef error = model.errors().get(0);
        assertEquals(4, error.payload().size());
        assertEquals("attemptCount", error.payload().get(0).name());
        assertEquals("lastAttemptTime", error.payload().get(1).name());
        assertEquals("retryAfter", error.payload().get(2).name());
        assertEquals("errorDetails", error.payload().get(3).name());
    }
}

