package com.genairus.chronos.parser;

import com.genairus.chronos.model.ActorDef;
import com.genairus.chronos.model.ChronosModel;
import com.genairus.chronos.model.EntityDef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for parsing entity and actor inheritance using the extends keyword.
 */
class InheritanceParserTest {

    @Test
    void parseEntityWithoutExtends() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example

                entity User {
                    id: String
                }
                """);
        assertEquals(1, model.entities().size());

        EntityDef user = model.entities().get(0);
        assertEquals("User", user.name());
        assertTrue(user.parentType().isEmpty());
        assertEquals(1, user.fields().size());
    }

    @Test
    void parseEntityWithExtends() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example

                entity User {
                    id: String
                    email: String
                }

                entity PremiumUser extends User {
                    tier: String
                    rewardsBalance: Integer
                }
                """);
        assertEquals(2, model.entities().size());

        EntityDef user = model.entities().get(0);
        assertEquals("User", user.name());
        assertTrue(user.parentType().isEmpty());
        assertEquals(2, user.fields().size());

        EntityDef premiumUser = model.entities().get(1);
        assertEquals("PremiumUser", premiumUser.name());
        assertTrue(premiumUser.parentType().isPresent());
        assertEquals("User", premiumUser.parentType().get());
        assertEquals(2, premiumUser.fields().size());
    }

    @Test
    void parseActorWithoutExtends() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example

                @description("A regular user")
                actor AuthenticatedUser
                """);
        assertEquals(1, model.actors().size());

        ActorDef actor = model.actors().get(0);
        assertEquals("AuthenticatedUser", actor.name());
        assertTrue(actor.parentType().isEmpty());
    }

    @Test
    void parseActorWithExtends() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example

                @description("A regular user")
                actor AuthenticatedUser

                @description("An admin user")
                actor AdminUser extends AuthenticatedUser
                """);
        assertEquals(2, model.actors().size());

        ActorDef authenticatedUser = model.actors().get(0);
        assertEquals("AuthenticatedUser", authenticatedUser.name());
        assertTrue(authenticatedUser.parentType().isEmpty());

        ActorDef adminUser = model.actors().get(1);
        assertEquals("AdminUser", adminUser.name());
        assertTrue(adminUser.parentType().isPresent());
        assertEquals("AuthenticatedUser", adminUser.parentType().get());
    }

    @Test
    void parseMultipleLevelsOfInheritance() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example

                entity Person {
                    name: String
                }

                entity User extends Person {
                    email: String
                }

                entity PremiumUser extends User {
                    tier: String
                }
                """);
        assertEquals(3, model.entities().size());

        EntityDef person = model.entities().get(0);
        assertEquals("Person", person.name());
        assertTrue(person.parentType().isEmpty());

        EntityDef user = model.entities().get(1);
        assertEquals("User", user.name());
        assertTrue(user.parentType().isPresent());
        assertEquals("Person", user.parentType().get());

        EntityDef premiumUser = model.entities().get(2);
        assertEquals("PremiumUser", premiumUser.name());
        assertTrue(premiumUser.parentType().isPresent());
        assertEquals("User", premiumUser.parentType().get());
    }
}

