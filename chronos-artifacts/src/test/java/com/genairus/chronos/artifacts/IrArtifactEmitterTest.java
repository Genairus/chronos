package com.genairus.chronos.artifacts;

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
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link IrArtifactEmitter}.
 */
class IrArtifactEmitterTest {

    // ── Fixture sources ───────────────────────────────────────────────────────

    private static final String NS_PAYMENTS = "dogfood.payments";
    private static final String NS_CHECKOUT = "dogfood.checkout";

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

            @kpi(metric: "Conv", target: ">80%")
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static IrCompilationUnit compileTwo() {
        var sources = List.of(
                new SourceUnit("payments/payments.chronos", SRC_PAYMENTS),
                new SourceUnit("checkout/checkout.chronos", SRC_CHECKOUT));
        var result = new ChronosCompiler().compileAll(sources);
        assertTrue(result.finalized(), "Fixtures must compile cleanly: " + result.diagnostics());
        return result.unitOrNull();
    }

    private static List<SourceUnit> twoSources() {
        return List.of(
                new SourceUnit("payments/payments.chronos", SRC_PAYMENTS),
                new SourceUnit("checkout/checkout.chronos", SRC_CHECKOUT));
    }

    // ── defaultOutputDir ─────────────────────────────────────────────────────

    @Test
    void defaultOutputDirIsUnderBuildChronosIr(@TempDir Path root) {
        Path expected = root.resolve("build").resolve("chronos").resolve("ir");
        assertEquals(expected, IrArtifactEmitter.defaultOutputDir(root));
    }

    // ── buildIrFileName ──────────────────────────────────────────────────────

    @Test
    void irFileNameCombinesNamespaceAndBasename() {
        assertEquals(
                "dogfood.payments__payments.ir.json",
                IrArtifactEmitter.buildIrFileName("dogfood.payments", "payments/payments.chronos"));
    }

    @Test
    void irFileNameWorksWithAbsolutePath() {
        assertEquals(
                "com.example__model.ir.json",
                IrArtifactEmitter.buildIrFileName("com.example", "/Users/me/project/model.chronos"));
    }

    @Test
    void irFileNameStripsChronosExtension() {
        String name = IrArtifactEmitter.buildIrFileName("ns", "file.chronos");
        assertFalse(name.contains(".chronos"), "Should strip .chronos extension");
        assertTrue(name.endsWith(".ir.json"));
    }

    // ── countShapes ──────────────────────────────────────────────────────────

    @Test
    void countShapesReturnsAlphabeticallySortedMap() {
        var unit = compileTwo();
        // payments model has entity + enum
        IrModel payments = unit.compiledSources().stream()
                .filter(cs -> cs.path().endsWith("payments.chronos"))
                .findFirst().orElseThrow()
                .model();

        TreeMap<String, Integer> counts = IrArtifactEmitter.countShapes(payments);
        assertEquals(2, counts.size());
        assertTrue(counts.containsKey("entity"), "Expected entity key");
        assertTrue(counts.containsKey("enum"),   "Expected enum key");
        assertEquals(1, counts.get("entity"));
        assertEquals(1, counts.get("enum"));

        // Keys must be in sorted order: "entity" < "enum" because 't' < 'u' at index 2
        var keys = counts.keySet().stream().toList();
        assertEquals("entity", keys.get(0));
        assertEquals("enum",   keys.get(1));
    }

    // ── emit ─────────────────────────────────────────────────────────────────

    @Test
    void emitCreatesOutputDirectory(@TempDir Path tmp) throws IOException {
        Path outDir = tmp.resolve("nested").resolve("ir");
        assertFalse(Files.exists(outDir));

        var options = new IrArtifactEmitter.EmitOptions(tmp, outDir, true);
        IrArtifactEmitter.emit(compileTwo(), twoSources(), options);

        assertTrue(Files.isDirectory(outDir), "emit() should create the output directory");
    }

    @Test
    void emitWritesOneIrFilePerSource(@TempDir Path tmp) throws IOException {
        var options = new IrArtifactEmitter.EmitOptions(tmp, tmp, true);
        var result  = IrArtifactEmitter.emit(compileTwo(), twoSources(), options);

        assertEquals(2, result.irFiles().size(), "Should produce one IR file per source");
        for (Path irFile : result.irFiles()) {
            assertTrue(Files.exists(irFile), "IR file should exist: " + irFile);
        }
    }

    @Test
    void emitWritesManifestJson(@TempDir Path tmp) throws IOException {
        var options = new IrArtifactEmitter.EmitOptions(tmp, tmp, true);
        var result  = IrArtifactEmitter.emit(compileTwo(), twoSources(), options);

        assertTrue(Files.exists(result.manifestPath()), "manifest.json should exist");
        assertEquals("manifest.json", result.manifestPath().getFileName().toString());
    }

    @Test
    void irFilesFollowNamingConvention(@TempDir Path tmp) throws IOException {
        var options = new IrArtifactEmitter.EmitOptions(tmp, tmp, true);
        var result  = IrArtifactEmitter.emit(compileTwo(), twoSources(), options);

        var names = result.irFiles().stream()
                .map(p -> p.getFileName().toString())
                .toList();
        assertTrue(names.contains("dogfood.payments__payments.ir.json"),
                "Missing payments IR file; got: " + names);
        assertTrue(names.contains("dogfood.checkout__checkout.ir.json"),
                "Missing checkout IR file; got: " + names);
    }

    @Test
    void irFilesContainValidJson(@TempDir Path tmp) throws IOException {
        var options = new IrArtifactEmitter.EmitOptions(tmp, tmp, true);
        var result  = IrArtifactEmitter.emit(compileTwo(), twoSources(), options);

        for (Path irFile : result.irFiles()) {
            String json = Files.readString(irFile);
            assertTrue(json.startsWith("{"),            irFile + ": must start with {");
            assertTrue(json.endsWith("}"),              irFile + ": must end with }");
            assertTrue(json.contains("\"namespace\""),  irFile + ": must contain namespace");
            assertTrue(json.contains("\"shapes\""),     irFile + ": must contain shapes");
            assertTrue(json.contains("\"imports\""),    irFile + ": must contain imports");
        }
    }

    @Test
    void manifestContainsExpectedKeys(@TempDir Path tmp) throws IOException {
        var options = new IrArtifactEmitter.EmitOptions(tmp, tmp, true);
        var result  = IrArtifactEmitter.emit(compileTwo(), twoSources(), options);

        String manifest = Files.readString(result.manifestPath());
        assertTrue(manifest.contains("\"format\""),      "manifest missing 'format'");
        assertTrue(manifest.contains("\"version\""),     "manifest missing 'version'");
        assertTrue(manifest.contains("\"generatedAt\""), "manifest missing 'generatedAt'");
        assertTrue(manifest.contains("\"entries\""),     "manifest missing 'entries'");
        assertTrue(manifest.contains("chronos-ir-manifest"), "manifest missing format value");
        assertTrue(manifest.contains("\"1\""),           "manifest missing version value");
    }

    @Test
    void manifestContainsBothEntries(@TempDir Path tmp) throws IOException {
        var options = new IrArtifactEmitter.EmitOptions(tmp, tmp, true);
        var result  = IrArtifactEmitter.emit(compileTwo(), twoSources(), options);

        String manifest = Files.readString(result.manifestPath());
        assertTrue(manifest.contains("dogfood.payments"), "manifest missing payments namespace");
        assertTrue(manifest.contains("dogfood.checkout"), "manifest missing checkout namespace");
        assertTrue(manifest.contains("\"sourcePath\""),   "manifest missing sourcePath field");
        assertTrue(manifest.contains("\"irFile\""),       "manifest missing irFile field");
        assertTrue(manifest.contains("\"shapeCounts\""),  "manifest missing shapeCounts field");
    }

    @Test
    void irFilesAreDeterministic(@TempDir Path tmp) throws IOException {
        var outDir1 = tmp.resolve("run1");
        var outDir2 = tmp.resolve("run2");

        var options1 = new IrArtifactEmitter.EmitOptions(tmp, outDir1, true);
        var options2 = new IrArtifactEmitter.EmitOptions(tmp, outDir2, true);

        IrArtifactEmitter.emit(compileTwo(), twoSources(), options1);
        IrArtifactEmitter.emit(compileTwo(), twoSources(), options2);

        // IR files (not manifest) must be byte-for-byte identical
        var unit = compileTwo();
        for (IrCompilationUnit.CompiledSource cs : unit.compiledSources()) {
            String fileName = IrArtifactEmitter.buildIrFileName(cs.model().namespace(), cs.path());
            String content1 = Files.readString(outDir1.resolve(fileName));
            String content2 = Files.readString(outDir2.resolve(fileName));
            assertEquals(content1, content2, "IR file not deterministic: " + fileName);
        }
    }

    @Test
    void singleSourceEmitProducesOneIrFile(@TempDir Path tmp) throws IOException {
        var source = new SourceUnit("payments.chronos", SRC_PAYMENTS);
        var compileResult = new ChronosCompiler().compile(SRC_PAYMENTS, "payments.chronos");
        assertTrue(compileResult.finalized(), "Single-file compile must succeed");

        // Wrap the single-file result in an IrCompilationUnit for emit
        var unit = new IrCompilationUnit(List.of(
                new IrCompilationUnit.CompiledSource("payments.chronos", compileResult.modelOrNull())));
        var options = new IrArtifactEmitter.EmitOptions(tmp, tmp, true);
        var result  = IrArtifactEmitter.emit(unit, List.of(source), options);

        assertEquals(1, result.irFiles().size());
        assertTrue(Files.readString(result.manifestPath()).contains("dogfood.payments"));
    }
}
