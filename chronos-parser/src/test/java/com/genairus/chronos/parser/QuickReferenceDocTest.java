package com.genairus.chronos.parser;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Parses every complete {@code ```chronos} code block in {@code docs/quick-reference.md}
 * and asserts that the Chronos parser accepts each one without throwing.
 *
 * <p>A block is considered <em>complete</em> if it contains a {@code namespace}
 * declaration and does not contain {@code ...} (which marks an illustrative fragment).
 *
 * <p>The test relies on the {@code chronos.rootDir} system property, which is set
 * by the Gradle {@code tasks.test} configuration in {@code chronos-parser/build.gradle.kts}.
 */
class QuickReferenceDocTest {

    private static final ChronosParserFacade FACADE = new ChronosParserFacade();

    @TestFactory
    Stream<DynamicTest> quickReferenceCodeBlocksParseWithoutError() throws Exception {
        String rootDir = System.getProperty("chronos.rootDir");
        if (rootDir == null) {
            // Running outside Gradle (e.g. IDE without system property) — skip gracefully.
            return Stream.of(dynamicTest("skipped — chronos.rootDir not set", () -> {}));
        }

        Path docPath = Path.of(rootDir, "docs", "quick-reference.md");
        String content = Files.readString(docPath);

        List<String> blocks = extractCompleteChronosBlocks(content);

        return blocks.stream().map(block -> {
            // Use the namespace line as the test display name
            String name = block.lines()
                    .filter(l -> l.startsWith("namespace "))
                    .findFirst()
                    .orElse("block")
                    .trim();
            return dynamicTest(name, () ->
                    assertDoesNotThrow(
                            () -> FACADE.parse(block, "<quick-reference.md>"),
                            "Code block starting with '" + name + "' failed to parse"));
        });
    }

    /**
     * Extracts all {@code ```chronos} fenced blocks from {@code markdown} that:
     * <ul>
     *   <li>contain a {@code namespace} declaration (i.e., are complete files, not fragments)</li>
     *   <li>do <em>not</em> contain {@code ...} (placeholder syntax used in illustrative fragments)</li>
     * </ul>
     */
    private static List<String> extractCompleteChronosBlocks(String markdown) {
        List<String> result = new ArrayList<>();
        String[] lines = markdown.split("\n", -1);
        boolean inBlock = false;
        StringBuilder current = new StringBuilder();

        for (String line : lines) {
            if (!inBlock && line.strip().equals("```chronos")) {
                inBlock = true;
                current = new StringBuilder();
            } else if (inBlock && line.strip().equals("```")) {
                inBlock = false;
                String block = current.toString();
                if (block.contains("namespace ") && !block.contains("...")) {
                    result.add(block);
                }
            } else if (inBlock) {
                current.append(line).append("\n");
            }
        }

        return result;
    }
}
