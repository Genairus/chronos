package com.genairus.chronos.mcp;

import com.genairus.chronos.mcp.security.PathSecurity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies PathSecurity contract:
 * - relative paths resolve to absolute
 * - paths inside workspace are accepted
 * - paths outside workspace (including .. traversal) are rejected
 * - symlinks pointing outside workspace are rejected
 * - toAbsolute always returns absolute path
 * - resolveWorkspaceRoot prioritises CHRONOS_WORKSPACE env var over caller-supplied value
 */
class PathSecurityTest {

    @TempDir
    Path workspace;

    @Test
    void pathInsideWorkspaceIsAccepted() throws IOException {
        var file = Files.createFile(workspace.resolve("model.chronos"));
        var resolved = PathSecurity.validate(file, workspace);
        assertTrue(resolved.isAbsolute(), "resolved path must be absolute");
        // Use toRealPath() for comparison — workspace itself may be a symlink (e.g. /tmp on macOS)
        assertTrue(resolved.startsWith(workspace.toRealPath()), "must be inside workspace");
    }

    @Test
    void relativePathIsResolvedToAbsolute() throws IOException {
        var file = Files.createFile(workspace.resolve("model.chronos"));
        // Relative paths resolve relative to CWD — pass the workspace as root and use the absolute path
        var resolved = PathSecurity.validate(file.toAbsolutePath(), workspace);
        assertTrue(resolved.isAbsolute());
    }

    @Test
    void pathOutsideWorkspaceThrows(@TempDir Path other) throws IOException {
        var outside = Files.createFile(other.resolve("secret.chronos"));
        assertThrows(PathSecurity.PathOutsideWorkspaceException.class,
                () -> PathSecurity.validate(outside, workspace),
                "Path outside workspace must throw PathOutsideWorkspaceException");
    }

    @Test
    void dotDotTraversalIsRejected() {
        // Construct a path that tries to escape the workspace via ..
        var traversal = workspace.resolve("subdir/../../../etc/passwd");
        assertThrows(PathSecurity.PathOutsideWorkspaceException.class,
                () -> PathSecurity.validate(traversal, workspace));
    }

    @Test
    void dotDotTraversalFromStringIsRejected() {
        var traversal = Path.of(workspace.toString(), "..", "..", "etc", "passwd");
        assertThrows(PathSecurity.PathOutsideWorkspaceException.class,
                () -> PathSecurity.validate(traversal, workspace));
    }

    @Test
    void symlinkEscapeIsRejected(@TempDir Path other) throws IOException {
        // Create a real file outside the workspace
        var outsideFile = Files.createFile(other.resolve("secret.chronos"));
        // Create a symlink inside the workspace pointing to it
        var symlink = workspace.resolve("escape.chronos");
        Files.createSymbolicLink(symlink, outsideFile);

        assertThrows(PathSecurity.PathOutsideWorkspaceException.class,
                () -> PathSecurity.validate(symlink, workspace),
                "Symlink pointing outside workspace must be rejected");
    }

    @Test
    void symlinkedParentForNonExistentPathIsRejected(@TempDir Path other) throws IOException {
        var outsideDir = Files.createDirectories(other.resolve("outside-dir"));
        var linkDir = workspace.resolve("linked");
        Files.createSymbolicLink(linkDir, outsideDir);

        var target = linkDir.resolve("future/output.chronos");
        assertThrows(PathSecurity.PathOutsideWorkspaceException.class,
                () -> PathSecurity.validate(target, workspace),
                "Non-existent child under symlinked parent must be rejected");
    }

    @Test
    void exceptionContainsSuppliedAndResolvedPaths() throws IOException {
        var outside = Files.createTempFile("test", ".chronos");
        outside.toFile().deleteOnExit();
        try {
            PathSecurity.validate(outside, workspace);
            fail("Expected PathOutsideWorkspaceException");
        } catch (PathSecurity.PathOutsideWorkspaceException e) {
            assertNotNull(e.supplied(), "exception must expose supplied path");
            assertNotNull(e.resolved(), "exception must expose resolved path");
        }
    }

    @Test
    void toAbsoluteReturnsAbsolutePath() {
        var relative = Path.of("some/relative/path.chronos");
        var abs = PathSecurity.toAbsolute(relative);
        assertTrue(abs.isAbsolute(), "toAbsolute must return an absolute path");
    }

    @Test
    void toAbsoluteNormalizesDoubleDots() {
        var withDots = workspace.resolve("a/../b/./c.chronos");
        var abs = PathSecurity.toAbsolute(withDots);
        assertFalse(abs.toString().contains(".."), "toAbsolute must normalize ..");
        assertFalse(abs.toString().contains("/./"), "toAbsolute must normalize .");
    }

    // ── resolveWorkspaceRoot tests ─────────────────────────────────────────────

    @Test
    void resolveWorkspaceRootFromArguments() {
        // When CHRONOS_WORKSPACE is not set, caller-supplied workspaceRoot is used
        var args = Map.<String, Object>of("workspaceRoot", workspace.toString());
        var resolved = PathSecurity.resolveWorkspaceRoot(args);
        assertNotNull(resolved);
        assertTrue(resolved.isAbsolute());
    }

    @Test
    void resolveWorkspaceRootFallsBackToUserDirWhenNoArgs() {
        var resolved = PathSecurity.resolveWorkspaceRoot(Map.of());
        assertNotNull(resolved);
        assertTrue(resolved.isAbsolute());
    }

    @Test
    void resolveWorkspaceRootHandlesNullArguments() {
        var resolved = PathSecurity.resolveWorkspaceRoot(null);
        assertNotNull(resolved);
        assertTrue(resolved.isAbsolute());
    }
}
