package com.genairus.chronos.parser;

import com.genairus.chronos.model.ChronosModel;
import com.genairus.chronos.model.DenyDef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for parsing deny (negative requirements) blocks.
 */
class DenyParserTest {

    @Test
    void basicDenyWithAllFields() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                deny StorePlaintextPasswords {
                    description: "The system must never store passwords in plaintext"
                    scope: [UserCredential]
                    severity: critical
                }
                """);

        List<DenyDef> denies = model.denies();
        assertEquals(1, denies.size());
        
        DenyDef deny = denies.get(0);
        assertEquals("StorePlaintextPasswords", deny.name());
        assertEquals("The system must never store passwords in plaintext", deny.description());
        assertEquals(List.of("UserCredential"), deny.scope());
        assertEquals("critical", deny.severity());
        assertTrue(deny.traits().isEmpty());
        assertTrue(deny.docComments().isEmpty());
    }

    @Test
    void denyWithMultipleScopeEntities() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                deny ExposePIIInLogs {
                    description: "PII-annotated fields must never appear in application logs"
                    scope: [CustomerProfile, PaymentInfo, UserAccount]
                    severity: critical
                }
                """);

        DenyDef deny = model.denies().get(0);
        assertEquals("ExposePIIInLogs", deny.name());
        assertEquals(List.of("CustomerProfile", "PaymentInfo", "UserAccount"), deny.scope());
    }

    @Test
    void denyWithComplianceTrait() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                @compliance("GDPR")
                deny ExposePIIInLogs {
                    description: "PII-annotated fields must never appear in application logs"
                    scope: [CustomerProfile, PaymentInfo]
                    severity: critical
                }
                """);

        DenyDef deny = model.denies().get(0);
        assertEquals(1, deny.traits().size());
        assertEquals("compliance", deny.traits().get(0).name());
    }

    @Test
    void denyWithDocComments() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                /// This prohibition ensures GDPR compliance
                /// by preventing PII exposure in logs.
                deny ExposePIIInLogs {
                    description: "PII-annotated fields must never appear in application logs"
                    scope: [CustomerProfile]
                    severity: critical
                }
                """);

        DenyDef deny = model.denies().get(0);
        assertEquals(2, deny.docComments().size());
        assertTrue(deny.docComments().get(0).contains("GDPR compliance"));
    }

    @Test
    void denyWithDifferentSeverityLevels() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                deny CriticalDeny {
                    description: "Critical prohibition"
                    scope: [Entity1]
                    severity: critical
                }
                
                deny HighDeny {
                    description: "High severity prohibition"
                    scope: [Entity2]
                    severity: high
                }
                
                deny MediumDeny {
                    description: "Medium severity prohibition"
                    scope: [Entity3]
                    severity: medium
                }
                
                deny LowDeny {
                    description: "Low severity prohibition"
                    scope: [Entity4]
                    severity: low
                }
                """);

        List<DenyDef> denies = model.denies();
        assertEquals(4, denies.size());
        assertEquals("critical", denies.get(0).severity());
        assertEquals("high", denies.get(1).severity());
        assertEquals("medium", denies.get(2).severity());
        assertEquals("low", denies.get(3).severity());
    }

    @Test
    void denyFieldsCanAppearInAnyOrder() {
        ChronosModel model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                deny TestDeny {
                    severity: critical
                    scope: [Entity1]
                    description: "Test description"
                }
                """);

        DenyDef deny = model.denies().get(0);
        assertEquals("TestDeny", deny.name());
        assertEquals("Test description", deny.description());
        assertEquals(List.of("Entity1"), deny.scope());
        assertEquals("critical", deny.severity());
    }
}

