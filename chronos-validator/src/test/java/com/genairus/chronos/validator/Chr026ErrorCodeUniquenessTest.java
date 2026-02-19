package com.genairus.chronos.validator;

import com.genairus.chronos.parser.ChronosModelParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHR-026: Error codes must be unique across the namespace.
 */
class Chr026ErrorCodeUniquenessTest {

    private final ChronosValidator validator = new ChronosValidator();

    @Test
    void uniqueErrorCodes_shouldPass() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                error PaymentDeclinedError {
                    code: "PAYMENT_DECLINED"
                    severity: high
                    recoverable: true
                    message: "Payment was declined"
                }
                
                error NetworkTimeoutError {
                    code: "NETWORK_TIMEOUT"
                    severity: medium
                    recoverable: true
                    message: "Network timeout occurred"
                }
                """);

        var result = validator.validate(model);
        var chr026Errors = result.errors().stream()
                .filter(e -> e.ruleCode().equals("CHR-026"))
                .toList();
        
        assertEquals(0, chr026Errors.size());
    }

    @Test
    void duplicateErrorCodes_shouldFail() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                error PaymentDeclinedError {
                    code: "PAYMENT_ERROR"
                    severity: high
                    recoverable: true
                    message: "Payment was declined"
                }
                
                error PaymentFailedError {
                    code: "PAYMENT_ERROR"
                    severity: high
                    recoverable: false
                    message: "Payment failed"
                }
                """);

        var result = validator.validate(model);
        var chr026Errors = result.errors().stream()
                .filter(e -> e.ruleCode().equals("CHR-026"))
                .toList();
        
        // Should have 2 errors: one for each occurrence
        assertEquals(2, chr026Errors.size());
        assertTrue(chr026Errors.get(0).message().contains("PAYMENT_ERROR"));
        assertTrue(chr026Errors.get(1).message().contains("PAYMENT_ERROR"));
    }

    @Test
    void triplicateErrorCodes_shouldReportAll() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                error Error1 {
                    code: "ERR-001"
                    severity: high
                    recoverable: true
                    message: "Error 1"
                }
                
                error Error2 {
                    code: "ERR-001"
                    severity: medium
                    recoverable: true
                    message: "Error 2"
                }
                
                error Error3 {
                    code: "ERR-001"
                    severity: low
                    recoverable: false
                    message: "Error 3"
                }
                """);

        var result = validator.validate(model);
        var chr026Errors = result.errors().stream()
                .filter(e -> e.ruleCode().equals("CHR-026"))
                .toList();
        
        // Should have 3 errors: one for each occurrence
        assertEquals(3, chr026Errors.size());
        assertTrue(chr026Errors.stream().allMatch(e -> e.message().contains("ERR-001")));
    }
}

