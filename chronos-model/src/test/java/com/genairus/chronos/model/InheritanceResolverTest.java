package com.genairus.chronos.model;

import com.genairus.chronos.parser.ChronosModelParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InheritanceResolverTest {

    @Test
    void resolveAllFields_noInheritance() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example
                
                entity User {
                    id: String
                    email: String
                }
                """);

        var entity = model.entities().get(0);
        var resolver = new InheritanceResolver(model);
        var fields = resolver.resolveAllFields(entity);

        assertEquals(2, fields.size());
        assertEquals("id", fields.get(0).name());
        assertEquals("email", fields.get(1).name());
    }

    @Test
    void hasCircularInheritance_noCircle() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example

                entity User {
                    id: String
                }

                entity PremiumUser extends User {
                    tier: String
                }
                """);

        var resolver = new InheritanceResolver(model);
        assertFalse(resolver.hasCircularInheritance(model.entities().get(0)));
        assertFalse(resolver.hasCircularInheritance(model.entities().get(1)));
    }

    @Test
    void resolveAllTraits_entityNoInheritance() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example

                @description("A user entity")
                @required
                entity User {
                    id: String
                }
                """);

        var entity = model.entities().get(0);
        var resolver = new InheritanceResolver(model);
        var traits = resolver.resolveAllTraits(entity);

        assertEquals(2, traits.size());
        assertEquals("description", traits.get(0).name());
        assertEquals("required", traits.get(1).name());
    }

    @Test
    void resolveAllTraits_entityInheritsParentTraits() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example

                @description("Base user")
                @required
                entity User {
                    id: String
                }

                @kpi
                entity PremiumUser extends User {
                    tier: String
                }
                """);

        var child = model.entities().get(1);
        var resolver = new InheritanceResolver(model);
        var traits = resolver.resolveAllTraits(child);

        assertEquals(3, traits.size());
        assertEquals("description", traits.get(0).name());
        assertEquals("required", traits.get(1).name());
        assertEquals("kpi", traits.get(2).name());
    }

    @Test
    void resolveAllTraits_entityOverridesParentTrait() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example

                @description("Base user")
                @required
                entity User {
                    id: String
                }

                @description("Premium user with benefits")
                @kpi
                entity PremiumUser extends User {
                    tier: String
                }
                """);

        var child = model.entities().get(1);
        var resolver = new InheritanceResolver(model);
        var traits = resolver.resolveAllTraits(child);

        assertEquals(3, traits.size());
        assertEquals("description", traits.get(0).name());
        assertEquals("required", traits.get(1).name());
        assertEquals("kpi", traits.get(2).name());

        // The description trait should be the child's version
        var descTrait = traits.get(0);
        var descValue = (TraitValue.StringValue) descTrait.args().get(0).value();
        assertEquals("Premium user with benefits", descValue.value());
    }

    @Test
    void resolveAllTraits_actorNoInheritance() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example

                @description("A customer actor")
                actor Customer
                """);

        var actor = model.actors().get(0);
        var resolver = new InheritanceResolver(model);
        var traits = resolver.resolveAllTraits(actor);

        assertEquals(1, traits.size());
        assertEquals("description", traits.get(0).name());
    }

    @Test
    void resolveAllTraits_actorInheritsParentTraits() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example

                @description("Base user")
                actor User

                @kpi
                actor AdminUser extends User
                """);

        var child = model.actors().get(1);
        var resolver = new InheritanceResolver(model);
        var traits = resolver.resolveAllTraits(child);

        assertEquals(2, traits.size());
        assertEquals("description", traits.get(0).name());
        assertEquals("kpi", traits.get(1).name());
    }

    @Test
    void resolveAllTraits_actorOverridesParentTrait() {
        var model = ChronosModelParser.parseString("test", """
                namespace com.example

                @description("Base user")
                actor User

                @description("Admin with elevated privileges")
                @kpi
                actor AdminUser extends User
                """);

        var child = model.actors().get(1);
        var resolver = new InheritanceResolver(model);
        var traits = resolver.resolveAllTraits(child);

        assertEquals(2, traits.size());
        assertEquals("description", traits.get(0).name());
        assertEquals("kpi", traits.get(1).name());

        // The description trait should be the child's version
        var descTrait = traits.get(0);
        var descValue = (TraitValue.StringValue) descTrait.args().get(0).value();
        assertEquals("Admin with elevated privileges", descValue.value());
    }
}