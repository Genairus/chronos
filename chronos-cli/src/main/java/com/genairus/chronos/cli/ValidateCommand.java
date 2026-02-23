package com.genairus.chronos.cli;

import com.genairus.chronos.compiler.ChronosCompiler;
import com.genairus.chronos.compiler.SourceUnit;
import com.genairus.chronos.core.diagnostics.Diagnostic;
import com.genairus.chronos.core.diagnostics.DiagnosticSeverity;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Validates a single {@code .chronos} file or all {@code .chronos} files
 * found recursively under a directory without generating any output.
 *
 * <p>When given a directory, files are collected in lexicographic path order
 * (stable ordering), hidden directories are skipped, and symbolic links are
 * not followed. All collected files are compiled together as a single
 * compilation unit via {@link ChronosCompiler#compileAll}, so cross-file
 * references are resolved correctly.
 *
 * <p>Diagnostics are printed in {@code file:line:col [SEVERITY CODE] message}
 * format, sorted by file path then source position. Exits {@code 0} only when
 * every file is fully parsed and finalized without errors.
 */
@Command(
    name = "validate",
    description = "Validate a .chronos file or directory without generating output",
    mixinStandardHelpOptions = true
)
public class ValidateCommand implements Callable<Integer> {

    @ParentCommand
    private ChronosCli parent;

    @Spec
    private CommandSpec spec;

    @Parameters(
        index = "0",
        description = "Source .chronos file or directory to scan recursively",
        paramLabel = "<input>"
    )
    private Path input;

    @Option(names = {"-v", "--verbose"}, description = "Print a success message when the model is clean")
    private boolean verbose = false;

    @Override
    public Integer call() {
        var console = parent.console(spec.commandLine().getOut(), spec.commandLine().getErr());

        // ── Collect source files ───────────────────────────────────────────────
        List<SourceUnit> sources;
        try {
            if (Files.isRegularFile(input)) {
                sources = List.of(new SourceUnit(input.toString(), Files.readString(input)));
            } else if (Files.isDirectory(input)) {
                try (var walk = Files.walk(input)) {
                    sources = walk
                            .filter(p -> !hasHiddenComponent(p, input))
                            .filter(p -> p.toString().endsWith(".chronos"))
                            .filter(Files::isRegularFile)
                            .sorted()
                            .map(p -> {
                                try {
                                    return new SourceUnit(p.toString(), Files.readString(p));
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            })
                            .toList();
                }
                if (sources.isEmpty()) {
                    console.error("No .chronos files found under: " + input);
                    return 1;
                }
            } else {
                console.error("Input not found: " + input);
                return 1;
            }
        } catch (IOException e) {
            console.error("Error reading input: " + e.getMessage());
            return 1;
        } catch (UncheckedIOException e) {
            console.error("Error reading file: " + e.getCause().getMessage());
            return 1;
        }

        // ── Compile ────────────────────────────────────────────────────────────
        var result = new ChronosCompiler().compileAll(sources);

        // ── Print diagnostics sorted by: path → line → col → code ─────────────
        result.diagnostics().stream()
                .sorted(Comparator
                        .comparing((Diagnostic d) -> d.pathOrNull() != null ? d.pathOrNull() : "")
                        .thenComparingInt(d -> d.span().startLine())
                        .thenComparingInt(d -> d.span().startCol())
                        .thenComparing(Diagnostic::code))
                .forEach(d -> {
                    String tag = "[" + d.severity().name() + " " + d.code() + "]";
                    String msg = d.span() + " " + tag + " " + d.message();
                    if (d.severity() == DiagnosticSeverity.ERROR) {
                        console.error(msg);
                    } else {
                        console.warning(msg);
                    }
                });

        if (!result.parsed() || !result.finalized()) {
            return 1;
        }

        if (verbose) {
            int n = sources.size();
            console.success("✓ No issues found in " + n + (n == 1 ? " file." : " files."));
        }
        return 0;
    }

    /**
     * Returns {@code true} if any path component of {@code path} relative to
     * {@code root} starts with {@code "."} (hidden file/directory convention).
     */
    private static boolean hasHiddenComponent(Path path, Path root) {
        Path rel = root.relativize(path);
        for (Path component : rel) {
            if (component.toString().startsWith(".")) return true;
        }
        return false;
    }
}
