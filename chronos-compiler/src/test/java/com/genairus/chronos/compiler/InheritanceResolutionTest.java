package com.genairus.chronos.compiler;

import com.genairus.chronos.compiler.util.IrRefWalker;
import com.genairus.chronos.ir.types.ActorDef;
import com.genairus.chronos.ir.types.EntityDef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for inheritance parent resolution via {@link com.genairus.chronos.core.refs.SymbolRef}.
 *
 * <p>Covers:
 * <ul>
 *   <li>Valid entity inheritance: {@code parentRef} resolved, {@code finalized=true}</li>
 *   <li>Missing parent entity: CHR-008 + CHR-012, {@code finalized=false}</li>
 *   <li>Valid actor inheritance: {@code parentRef} resolved, {@code finalized=true}</li>
 *   <li>Missing parent actor: CHR-008 + CHR-012, {@code finalized=false}</li>
 *   <li>Circular entity inheritance: CHR-015 still detected end-to-end</li>
 * </ul>
 */
class InheritanceResolutionTest {

    // ── Entity inheritance ────────────────────────────────────────────────────

    @Test
    void validEntityParent_finalizedTrue_parentRefResolved() {
        String source = """
                namespace com.example.test

                entity User {
                    id: String
                }

                entity PremiumUser extends User {
                    tier: String
                }
                """;

        var result = new ChronosCompiler().compile(source, "<test>");

        assertTrue(result.parsed(), "should parse successfully");
        assertTrue(result.finalized(),
                "all refs resolved → should be finalized; errors: " + result.errors());
        assertNotNull(result.modelOrNull());

        // No unresolved refs anywhere in the IR
        assertTrue(IrRefWalker.findUnresolvedRefs(result.modelOrNull()).isEmpty(),
                "finalized model must have zero unresolved SymbolRefs");

        // parentRef on PremiumUser must be resolved to 'User'
        EntityDef premiumUser = result.modelOrNull().shapes().stream()
                .filter(s -> s instanceof EntityDef e && "PremiumUser".equals(e.name()))
                .map(s -> (EntityDef) s)
                .findFirst()
                .orElseThrow(() -> new AssertionError("PremiumUser not found in model"));

        assertTrue(premiumUser.parentRef().isPresent(), "PremiumUser must have a parentRef");
        assertTrue(premiumUser.parentRef().get().isResolved(),
                "parentRef must be resolved, got: " + premiumUser.parentRef().get());
        assertEquals("User", premiumUser.parentRef().get().id().name(),
                "parentRef must resolve to 'User'");
    }

    @Test
    void missingParentEntity_emitsChr008AndChr012_finalizedFalse() {
        String source = """
                namespace com.example.test

                entity PremiumUser extends Ghost {
                    tier: String
                }
                """;

        var result = new ChronosCompiler().compile(source, "<test>");

        assertTrue(result.parsed(), "should parse successfully");
        assertFalse(result.finalized(), "unresolved parent must prevent finalization");

        assertTrue(result.errors().stream().anyMatch(d -> "CHR-008".equals(d.code())),
                "expected CHR-008 (undefined parent entity) but got: " + result.errors());
        assertTrue(result.errors().stream().anyMatch(d -> "CHR-012".equals(d.code())),
                "expected CHR-012 (unresolved ref remains) but got: " + result.errors());
    }

    // ── Actor inheritance ─────────────────────────────────────────────────────

    @Test
    void validActorParent_finalizedTrue_parentRefResolved() {
        String source = """
                namespace com.example.test

                actor BaseUser

                actor AdminUser extends BaseUser
                """;

        var result = new ChronosCompiler().compile(source, "<test>");

        assertTrue(result.parsed(), "should parse successfully");
        assertTrue(result.finalized(),
                "all refs resolved → should be finalized; errors: " + result.errors());

        // No unresolved refs anywhere in the IR
        assertTrue(IrRefWalker.findUnresolvedRefs(result.modelOrNull()).isEmpty(),
                "finalized model must have zero unresolved SymbolRefs");

        // parentRef on AdminUser must be resolved to 'BaseUser'
        ActorDef adminUser = result.modelOrNull().shapes().stream()
                .filter(s -> s instanceof ActorDef a && "AdminUser".equals(a.name()))
                .map(s -> (ActorDef) s)
                .findFirst()
                .orElseThrow(() -> new AssertionError("AdminUser not found in model"));

        assertTrue(adminUser.parentRef().isPresent(), "AdminUser must have a parentRef");
        assertTrue(adminUser.parentRef().get().isResolved(),
                "parentRef must be resolved, got: " + adminUser.parentRef().get());
        assertEquals("BaseUser", adminUser.parentRef().get().id().name(),
                "parentRef must resolve to 'BaseUser'");
    }

    @Test
    void missingParentActor_emitsChr008AndChr012_finalizedFalse() {
        String source = """
                namespace com.example.test

                actor AdminUser extends GhostRole
                """;

        var result = new ChronosCompiler().compile(source, "<test>");

        assertTrue(result.parsed(), "should parse successfully");
        assertFalse(result.finalized(), "unresolved parent must prevent finalization");

        assertTrue(result.errors().stream().anyMatch(d -> "CHR-008".equals(d.code())),
                "expected CHR-008 (undefined parent actor) but got: " + result.errors());
        assertTrue(result.errors().stream().anyMatch(d -> "CHR-012".equals(d.code())),
                "expected CHR-012 (unresolved ref remains) but got: " + result.errors());
    }

    // ── Circular inheritance still detected end-to-end ───────────────────────

    @Test
    void circularEntityInheritance_emitsChr015() {
        String source = """
                namespace com.example.test

                entity A extends B {
                    id: String
                }

                entity B extends A {
                    name: String
                }
                """;

        var result = new ChronosCompiler().compile(source, "<test>");

        assertTrue(result.parsed(), "should parse successfully");

        assertTrue(result.errors().stream().anyMatch(d -> "CHR-015".equals(d.code())),
                "expected CHR-015 (circular inheritance) but got: " + result.errors());
    }
}
