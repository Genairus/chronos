package com.genairus.chronos.mcp.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;

/**
 * Workspace path security utilities.
 *
 * <p>All caller-supplied paths MUST be validated through {@link #validate} before use.
 * Any path that does not resolve to a location inside {@code workspaceRoot} is rejected
 * with a {@link PathOutsideWorkspaceException}.
 *
 * <p>This guard applies to every path argument in every MCP tool, not only
 * {@code chronos.discover}.
 */
public final class PathSecurity {

    private PathSecurity() {}

    /**
     * Exception thrown when a caller-supplied path resolves outside the workspace root.
     * Maps to error code {@code PATH_OUTSIDE_WORKSPACE, retryable: false} in the envelope.
     */
    public static final class PathOutsideWorkspaceException extends RuntimeException {
        private final Path supplied;
        private final Path resolved;

        public PathOutsideWorkspaceException(Path supplied, Path resolved, Path workspaceRoot) {
            super("Path '" + supplied + "' resolves to '" + resolved
                    + "' which is outside workspace root '" + workspaceRoot + "'");
            this.supplied = supplied;
            this.resolved = resolved;
        }

        public Path supplied() { return supplied; }
        public Path resolved() { return resolved; }
    }

    /**
     * Resolves {@code path} to an absolute, real form and verifies it is inside
     * {@code workspaceRoot}.
     *
     * <p>Uses {@link Path#toRealPath()} when the path exists so that symlinks pointing
     * outside the workspace are detected and rejected. Falls back to
     * {@link Path#toAbsolutePath()}/{@link Path#normalize()} for paths that do not yet
     * exist (e.g. output directories that haven't been created yet).
     *
     * @param path          the caller-supplied path (may be relative or contain {@code ..})
     * @param workspaceRoot the absolute workspace root (must already be absolute)
     * @return              the absolute, real (symlink-resolved) path
     * @throws PathOutsideWorkspaceException if the resolved path is outside the workspace
     */
    public static Path validate(Path path, Path workspaceRoot) {
        Path absolute = path.toAbsolutePath().normalize();

        // Resolve supplied path with symlink awareness:
        // - existing paths: toRealPath()
        // - non-existing paths: resolve nearest existing ancestor to real path, then append remainder
        // This blocks symlink-parent escapes like <workspace>/link-to-/etc/new-file.
        Path resolved = resolveWithExistingAncestor(absolute);

        // Resolve workspace root in real form when possible for consistent comparison.
        Path root;
        try {
            root = workspaceRoot.toRealPath();
        } catch (IOException e) {
            root = workspaceRoot.toAbsolutePath().normalize();
        }

        if (!resolved.startsWith(root)) {
            throw new PathOutsideWorkspaceException(path, resolved, root);
        }
        return resolved;
    }

    private static Path resolveWithExistingAncestor(Path absolutePath) {
        try {
            return absolutePath.toRealPath();
        } catch (IOException ignored) {
            // Non-existent target: resolve via nearest existing ancestor.
        }

        Path ancestor = absolutePath;
        while (ancestor != null && !Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) {
            ancestor = ancestor.getParent();
        }
        if (ancestor == null) {
            return absolutePath;
        }

        try {
            Path ancestorReal = ancestor.toRealPath();
            Path remainder = ancestor.relativize(absolutePath);
            return ancestorReal.resolve(remainder).normalize();
        } catch (IOException e) {
            return absolutePath;
        }
    }

    /**
     * Resolves the workspace root from the tool's argument map, with the
     * {@code CHRONOS_WORKSPACE} environment variable always taking precedence.
     *
     * <p>Priority order:
     * <ol>
     *   <li>{@code CHRONOS_WORKSPACE} env var — set by the server operator; callers cannot override</li>
     *   <li>{@code workspaceRoot} argument from the request — used only when no env var is set</li>
     *   <li>{@code user.dir} system property — last resort fallback</li>
     * </ol>
     *
     * @param arguments  raw tool arguments map (may be null or empty)
     * @return           the resolved, normalized workspace root path
     */
    public static Path resolveWorkspaceRoot(Map<String, Object> arguments) {
        // Env var always takes precedence — never let callers widen their own workspace root
        var envRoot = System.getenv("CHRONOS_WORKSPACE");
        if (envRoot != null && !envRoot.isBlank()) {
            return Path.of(envRoot).toAbsolutePath().normalize();
        }
        if (arguments != null && arguments.containsKey("workspaceRoot")
                && arguments.get("workspaceRoot") != null) {
            return Path.of(arguments.get("workspaceRoot").toString()).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    /**
     * Converts any path to its absolute, normalized form without workspace validation.
     * Use this only for output paths that the server controls, never for caller-supplied paths.
     */
    public static Path toAbsolute(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
