package com.genairus.chronos.cli;

import com.genairus.chronos.artifacts.IrBundleEmitter;
import com.genairus.chronos.compiler.ChronosCompiler;
import com.genairus.chronos.compiler.IrCompilationUnit;
import com.genairus.chronos.compiler.SourceUnit;
import com.genairus.chronos.ir.model.IrModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link IrBundleLoader}.
 *
 * <p>Covers round-trip correctness (compile → emit → load → verify) and
 * error cases (missing file, wrong format, wrong version).
 */
class IrBundleLoaderTest {

    // ── Fixtures ───────────────────────────────────────────────────────────────

    private static final String SRC_PAYMENTS = """
            namespace dogfood.payments

            enum PaymentMethod { CARD = 1 PAYPAL = 2 }

            entity PaymentInstrument {
                id: String
                method: PaymentMethod
            }
            """;

    private static final String SRC_CHECKOUT = """
            namespace dogfood.checkout

            use dogfood.payments#PaymentMethod

            @description("A buyer")
            actor Buyer

            journey PlaceOrder {
                actor: Buyer
                steps: [
                    step Pay {
                        action: "Buyer submits payment"
                        expectation: "Payment processed"
                    }
                ]
                outcomes: { success: "Order placed" }
            }
            """;

    private static IrCompilationUnit compileTwo() {
        var sources = List.of(
                new SourceUnit("checkout/checkout.chronos", SRC_CHECKOUT),
                new SourceUnit("payments/payments.chronos", SRC_PAYMENTS));
        var result = new ChronosCompiler().compileAll(sources);
        assertTrue(result.finalized(), "Fixtures must compile cleanly: " + result.diagnostics());
        return result.unitOrNull();
    }

    // ── Round-trip ─────────────────────────────────────────────────────────────

    @Test
    void roundTrip_returnsTwoModels(@TempDir Path tmp) throws IOException {
        IrBundleEmitter.emit(compileTwo(), tmp);
        Path bundlePath = tmp.resolve("ir-bundle.json");

        List<IrModel> models = IrBundleLoader.load(bundlePath);

        assertEquals(2, models.size(), "Expected two models");
    }

    @Test
    void roundTrip_namespacesPreserved(@TempDir Path tmp) throws IOException {
        IrBundleEmitter.emit(compileTwo(), tmp);
        Path bundlePath = tmp.resolve("ir-bundle.json");

        List<IrModel> models = IrBundleLoader.load(bundlePath);

        // Bundle entries sorted by source path: checkout/... < payments/...
        assertEquals("dogfood.checkout", models.get(0).namespace());
        assertEquals("dogfood.payments", models.get(1).namespace());
    }

    @Test
    void roundTrip_shapeCountsPreserved(@TempDir Path tmp) throws IOException {
        IrBundleEmitter.emit(compileTwo(), tmp);
        Path bundlePath = tmp.resolve("ir-bundle.json");

        List<IrModel> models = IrBundleLoader.load(bundlePath);

        // dogfood.checkout: Buyer (actor) + PlaceOrder (journey) = 2 shapes
        // dogfood.payments: PaymentMethod (enum) + PaymentInstrument (entity) = 2 shapes
        for (IrModel model : models) {
            assertEquals(2, model.shapes().size(),
                    "Expected 2 shapes in " + model.namespace());
        }
    }

    @Test
    void roundTrip_entityNamePreserved(@TempDir Path tmp) throws IOException {
        IrBundleEmitter.emit(compileTwo(), tmp);
        Path bundlePath = tmp.resolve("ir-bundle.json");

        List<IrModel> models = IrBundleLoader.load(bundlePath);

        // dogfood.payments (index 1): entities include PaymentInstrument
        IrModel paymentsModel = models.get(1);
        boolean found = paymentsModel.entities().stream()
                .anyMatch(e -> "PaymentInstrument".equals(e.name()));
        assertTrue(found, "Expected PaymentInstrument entity in dogfood.payments");
    }

    @Test
    void roundTrip_importsPreserved(@TempDir Path tmp) throws IOException {
        IrBundleEmitter.emit(compileTwo(), tmp);
        Path bundlePath = tmp.resolve("ir-bundle.json");

        List<IrModel> models = IrBundleLoader.load(bundlePath);

        // dogfood.checkout (index 0) imports PaymentMethod from dogfood.payments
        IrModel checkoutModel = models.get(0);
        assertEquals(1, checkoutModel.imports().size(),
                "Expected one import in dogfood.checkout");
        assertEquals("PaymentMethod", checkoutModel.imports().get(0).name());
    }

    // ── Error cases ────────────────────────────────────────────────────────────

    @Test
    void missingFile_throwsIOException(@TempDir Path tmp) {
        Path missing = tmp.resolve("no-bundle.json");
        assertThrows(IOException.class, () -> IrBundleLoader.load(missing));
    }

    @Test
    void invalidFormat_throwsIllegalArgumentException(@TempDir Path tmp) throws Exception {
        Path bad = tmp.resolve("bad.json");
        Files.writeString(bad,
                "{\"format\":\"wrong-format\",\"version\":\"1\",\"entries\":[]}");

        var ex = assertThrows(IllegalArgumentException.class,
                () -> IrBundleLoader.load(bad));
        assertTrue(ex.getMessage().contains("wrong-format"),
                "Expected wrong format name in message: " + ex.getMessage());
    }

    @Test
    void invalidVersion_throwsIllegalArgumentException(@TempDir Path tmp) throws Exception {
        Path bad = tmp.resolve("bad.json");
        Files.writeString(bad,
                "{\"format\":\"chronos-ir-bundle\",\"version\":\"99\",\"entries\":[]}");

        var ex = assertThrows(IllegalArgumentException.class,
                () -> IrBundleLoader.load(bad));
        assertTrue(ex.getMessage().contains("99"),
                "Expected wrong version in message: " + ex.getMessage());
    }
}
