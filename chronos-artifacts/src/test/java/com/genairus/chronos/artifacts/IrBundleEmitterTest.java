package com.genairus.chronos.artifacts;

import com.genairus.chronos.compiler.ChronosCompiler;
import com.genairus.chronos.compiler.IrCompilationUnit;
import com.genairus.chronos.compiler.SourceUnit;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link IrBundleEmitter}.
 */
class IrBundleEmitterTest {

    // ── Fixture sources ───────────────────────────────────────────────────────

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

    // ── emit ──────────────────────────────────────────────────────────────────

    @Test
    void emitCreatesBundleFile(@TempDir Path tmp) throws IOException {
        Path bundlePath = IrBundleEmitter.emit(compileTwo(), tmp);

        assertTrue(Files.exists(bundlePath), "ir-bundle.json should exist");
        assertEquals("ir-bundle.json", bundlePath.getFileName().toString());
    }

    @Test
    void emitCreatesOutputDirectoryIfAbsent(@TempDir Path tmp) throws IOException {
        Path outDir = tmp.resolve("nested").resolve("ir");
        assertFalse(Files.exists(outDir));

        IrBundleEmitter.emit(compileTwo(), outDir);

        assertTrue(Files.isDirectory(outDir), "emit() should create the output directory");
    }

    @Test
    void bundleHasCorrectFormatAndVersion(@TempDir Path tmp) throws IOException {
        Path bundlePath = IrBundleEmitter.emit(compileTwo(), tmp);
        String json = Files.readString(bundlePath);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        assertEquals("chronos-ir-bundle", root.get("format").getAsString());
        assertEquals("1", root.get("version").getAsString());
    }

    @Test
    void bundleHasNoTimestamp(@TempDir Path tmp) throws IOException {
        Path bundlePath = IrBundleEmitter.emit(compileTwo(), tmp);
        String json = Files.readString(bundlePath);

        assertFalse(json.contains("generatedAt"),
                "Bundle must not contain a generatedAt timestamp (must be deterministic)");
    }

    @Test
    void bundleEntriesSortedBySourcePath(@TempDir Path tmp) throws IOException {
        Path bundlePath = IrBundleEmitter.emit(compileTwo(), tmp);
        String json = Files.readString(bundlePath);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray entries = root.getAsJsonArray("entries");

        assertEquals(2, entries.size());
        // "checkout/checkout.chronos" < "payments/payments.chronos"
        assertEquals("checkout/checkout.chronos",
                entries.get(0).getAsJsonObject().get("sourcePath").getAsString());
        assertEquals("payments/payments.chronos",
                entries.get(1).getAsJsonObject().get("sourcePath").getAsString());
    }

    @Test
    void eachEntryHasNamespaceSourcePathAndModel(@TempDir Path tmp) throws IOException {
        Path bundlePath = IrBundleEmitter.emit(compileTwo(), tmp);
        String json = Files.readString(bundlePath);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray entries = root.getAsJsonArray("entries");

        for (var elem : entries) {
            JsonObject entry = elem.getAsJsonObject();
            assertTrue(entry.has("namespace"), "entry missing 'namespace'");
            assertTrue(entry.has("sourcePath"), "entry missing 'sourcePath'");
            assertTrue(entry.has("model"), "entry missing 'model'");
            assertTrue(entry.get("model").isJsonObject(), "'model' must be a JSON object");
        }
    }

    @Test
    void modelObjectContainsExpectedKeys(@TempDir Path tmp) throws IOException {
        Path bundlePath = IrBundleEmitter.emit(compileTwo(), tmp);
        String json = Files.readString(bundlePath);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        for (var elem : root.getAsJsonArray("entries")) {
            JsonObject model = elem.getAsJsonObject().getAsJsonObject("model");
            assertTrue(model.has("namespace"), "model missing 'namespace'");
            assertTrue(model.has("imports"),   "model missing 'imports'");
            assertTrue(model.has("shapes"),    "model missing 'shapes'");
        }
    }

    @Test
    void namespaceMatchesModelNamespace(@TempDir Path tmp) throws IOException {
        Path bundlePath = IrBundleEmitter.emit(compileTwo(), tmp);
        String json = Files.readString(bundlePath);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        for (var elem : root.getAsJsonArray("entries")) {
            JsonObject entry = elem.getAsJsonObject();
            String entryNs = entry.get("namespace").getAsString();
            String modelNs = entry.getAsJsonObject("model").get("namespace").getAsString();
            assertEquals(entryNs, modelNs, "entry.namespace must match model.namespace");
        }
    }

    @Test
    void bundleIsDeterministic(@TempDir Path tmp) throws IOException {
        Path out1 = tmp.resolve("run1");
        Path out2 = tmp.resolve("run2");

        IrBundleEmitter.emit(compileTwo(), out1);
        IrBundleEmitter.emit(compileTwo(), out2);

        String bundle1 = Files.readString(out1.resolve("ir-bundle.json"));
        String bundle2 = Files.readString(out2.resolve("ir-bundle.json"));
        assertEquals(bundle1, bundle2, "Bundle must be byte-for-byte identical across runs");
    }

    @Test
    void singleSourceProducesOneEntry(@TempDir Path tmp) throws IOException {
        var result = new ChronosCompiler().compile(SRC_PAYMENTS, "payments.chronos");
        assertTrue(result.finalized(), "Single-file compile must succeed");

        var unit = new IrCompilationUnit(List.of(
                new IrCompilationUnit.CompiledSource("payments.chronos", result.modelOrNull())));

        Path bundlePath = IrBundleEmitter.emit(unit, tmp);
        String json = Files.readString(bundlePath);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray entries = root.getAsJsonArray("entries");

        assertEquals(1, entries.size());
        assertEquals("dogfood.payments",
                entries.get(0).getAsJsonObject().get("namespace").getAsString());
    }
}
