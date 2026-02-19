package com.genairus.chronos.cli;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads and validates {@code chronos-build.json} configuration files.
 *
 * <p>Environment variable placeholders of the form {@code ${VAR}} or
 * {@code ${VAR:-default}} are expanded in every String field of the
 * resulting {@link BuildConfig}.
 */
public final class BuildConfigLoader {

    private BuildConfigLoader() {}

    private static final Gson GSON = new Gson();

    /**
     * Pattern matching {@code ${VAR_NAME}} and {@code ${VAR_NAME:-default}}.
     * Group 1 = variable name, group 2 = default value (null when absent).
     */
    private static final Pattern ENV_PATTERN =
            Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)(?::-(.*?))?\\}");

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Loads {@code chronos-build.json} from {@code configFile}, expanding all
     * {@code ${VAR}} placeholders using {@link System#getenv}.
     *
     * @throws BuildConfigException on missing file, malformed JSON, or unresolvable variable
     */
    public static BuildConfig load(Path configFile) {
        return load(configFile, System::getenv);
    }

    /**
     * Package-private overload that accepts a custom environment resolver —
     * used by tests to inject mock env variables without relying on the OS.
     */
    static BuildConfig load(Path configFile, Function<String, String> env) {
        if (!Files.exists(configFile)) {
            throw new BuildConfigException("Config file not found: " + configFile);
        }

        String json;
        try {
            json = Files.readString(configFile);
        } catch (IOException e) {
            throw new BuildConfigException("Failed to read config file: " + e.getMessage(), e);
        }

        BuildConfig raw;
        try {
            raw = GSON.fromJson(json, BuildConfig.class);
            if (raw == null) {
                raw = new BuildConfig(null, null, null);
            }
        } catch (JsonSyntaxException e) {
            throw new BuildConfigException(
                    "Malformed JSON in " + configFile + ": " + e.getMessage(), e);
        }

        return expandConfig(raw, env);
    }

    /**
     * Resolves source glob patterns against {@code baseDir}, returning all matching
     * {@code .chronos} files in sorted absolute-path order.
     *
     * <p>Patterns are interpreted as {@link FileSystem#getPathMatcher(String) glob} patterns
     * relative to {@code baseDir}. Both flat ({@code *.chronos}) and recursive
     * ({@code **}{@code /*.chronos}) patterns are supported.
     */
    public static List<Path> resolveSourceFiles(Path baseDir, List<String> patterns) {
        var result = new TreeSet<Path>();
        FileSystem fs = FileSystems.getDefault();

        for (String pattern : patterns) {
            PathMatcher matcher = fs.getPathMatcher("glob:" + pattern);
            try {
                Files.walkFileTree(baseDir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (file.getFileName().toString().endsWith(".chronos")) {
                            Path relative = baseDir.relativize(file);
                            if (matcher.matches(relative)) {
                                result.add(file.toAbsolutePath().normalize());
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                throw new BuildConfigException(
                        "Error walking filesystem for pattern '" + pattern + "': " + e.getMessage(), e);
            }
        }

        return List.copyOf(result);
    }

    // ── Env-var expansion ──────────────────────────────────────────────────────

    private static BuildConfig expandConfig(BuildConfig config, Function<String, String> env) {
        List<String> sources = expandList(config.sources(), env);
        List<BuildTarget> targets = config.targets().stream()
                .map(t -> expandTarget(t, env))
                .toList();
        Map<String, String> creds = expandMap(config.credentials(), env);
        return new BuildConfig(sources, targets, creds);
    }

    private static BuildTarget expandTarget(BuildTarget t, Function<String, String> env) {
        return new BuildTarget(
                expand(t.name(),      env),
                expand(t.generator(), env),
                expand(t.output(),    env),
                expandList(t.include(), env),
                expandList(t.exclude(), env));
    }

    private static List<String> expandList(List<String> list, Function<String, String> env) {
        return list.stream().map(s -> expand(s, env)).toList();
    }

    private static Map<String, String> expandMap(Map<String, String> map,
                                                  Function<String, String> env) {
        var result = new LinkedHashMap<String, String>();
        for (var entry : map.entrySet()) {
            result.put(expand(entry.getKey(), env), expand(entry.getValue(), env));
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Expands {@code ${VAR}} and {@code ${VAR:-default}} placeholders in {@code value}.
     * Package-private for unit testing.
     *
     * @throws BuildConfigException if a variable has no definition and no default
     */
    static String expand(String value, Function<String, String> env) {
        if (value == null || !value.contains("${")) return value;
        var sb = new StringBuffer();
        Matcher m = ENV_PATTERN.matcher(value);
        while (m.find()) {
            String varName    = m.group(1);
            String defaultVal = m.group(2); // null when no :- present
            String envVal     = env.apply(varName);
            if (envVal != null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(envVal));
            } else if (defaultVal != null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(defaultVal));
            } else {
                throw new BuildConfigException(
                        "Undefined environment variable '" + varName + "'");
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
