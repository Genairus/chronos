package com.genairus.chronos.cli;

import com.genairus.chronos.model.*;

import java.util.List;

/**
 * Filters a {@link ChronosModel}'s shapes using the include/exclude patterns
 * defined in a {@link BuildTarget}.
 *
 * <h3>Pattern syntax</h3>
 * <ul>
 *   <li>{@code *}       — match every shape</li>
 *   <li>{@code type:name} — exact match on both type and name</li>
 *   <li>{@code type:*}  — all shapes of that type</li>
 *   <li>{@code *:name}  — any type, exact name match</li>
 * </ul>
 *
 * <p>An empty {@code include} list means "include everything". Exclusion is
 * checked after inclusion — a shape that passes include but matches any exclude
 * pattern is dropped.
 */
public final class ModelProjection {

    private ModelProjection() {}

    /**
     * Returns a new {@link ChronosModel} containing only the shapes that pass
     * the target's include/exclude filters. The namespace and imports are unchanged.
     */
    public static ChronosModel apply(ChronosModel model, BuildTarget target) {
        List<ShapeDefinition> filtered = model.shapes().stream()
                .filter(s -> included(s, target.include()))
                .filter(s -> !excluded(s, target.exclude()))
                .toList();
        return new ChronosModel(model.namespace(), model.imports(), filtered);
    }

    // ── Include / exclude logic ────────────────────────────────────────────────

    private static boolean included(ShapeDefinition shape, List<String> include) {
        if (include.isEmpty()) return true; // empty = include all
        for (String pattern : include) {
            if (matches(shape, pattern)) return true;
        }
        return false;
    }

    private static boolean excluded(ShapeDefinition shape, List<String> exclude) {
        for (String pattern : exclude) {
            if (matches(shape, pattern)) return true;
        }
        return false;
    }

    private static boolean matches(ShapeDefinition shape, String pattern) {
        if ("*".equals(pattern)) return true;

        int colon = pattern.indexOf(':');
        if (colon < 0) return false; // unrecognised pattern — no match

        String typePattern = pattern.substring(0, colon);
        String namePattern = pattern.substring(colon + 1);

        boolean typeOk = "*".equals(typePattern) || typePattern.equals(typeName(shape));
        boolean nameOk = "*".equals(namePattern) || namePattern.equals(shape.name());
        return typeOk && nameOk;
    }

    // ── Type name helpers ──────────────────────────────────────────────────────

    static String typeName(ShapeDefinition shape) {
        return switch (shape) {
            case EntityDef      e  -> "entity";
            case ShapeStructDef s  -> "shape";
            case ListDef        l  -> "list";
            case MapDef         m  -> "map";
            case EnumDef        en -> "enum";
            case ActorDef       a  -> "actor";
            case PolicyDef      p  -> "policy";
            case JourneyDef     j  -> "journey";
        };
    }
}
