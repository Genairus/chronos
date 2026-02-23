package com.genairus.chronos.cli;

import com.genairus.chronos.ir.model.IrModel;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads an {@code ir-bundle.json} file produced by
 * {@link com.genairus.chronos.artifacts.IrBundleEmitter} and reconstructs
 * the compiled {@link IrModel} objects without recompiling {@code .chronos}
 * sources.
 *
 * <p>The loader validates the bundle {@code "format"} and {@code "version"}
 * fields before deserializing entries, so callers receive a clear error if
 * they accidentally supply a file produced by a different tool or a future
 * incompatible bundle version.
 */
final class IrBundleLoader {

    static final String EXPECTED_FORMAT  = "chronos-ir-bundle";
    static final String EXPECTED_VERSION = "1";

    private IrBundleLoader() {}

    /**
     * Loads an IR bundle from the given path and returns the list of compiled
     * models in bundle order (entries are sorted by {@code sourcePath}).
     *
     * @param bundlePath path to the {@code ir-bundle.json} file
     * @return list of reconstructed {@link IrModel} objects
     * @throws IOException              if the file cannot be read
     * @throws IllegalArgumentException if the bundle has an unexpected
     *                                  {@code "format"} or {@code "version"}
     */
    static List<IrModel> load(Path bundlePath) throws IOException {
        String json = Files.readString(bundlePath);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        // ── Validate format ────────────────────────────────────────────────────
        JsonElement formatElem = root.get("format");
        String actualFormat = formatElem != null ? formatElem.getAsString() : "(missing)";
        if (!EXPECTED_FORMAT.equals(actualFormat)) {
            throw new IllegalArgumentException(
                    "Unexpected bundle format '" + actualFormat
                            + "'; expected '" + EXPECTED_FORMAT + "'");
        }

        // ── Validate version ───────────────────────────────────────────────────
        JsonElement versionElem = root.get("version");
        String actualVersion = versionElem != null ? versionElem.getAsString() : "(missing)";
        if (!EXPECTED_VERSION.equals(actualVersion)) {
            throw new IllegalArgumentException(
                    "Unsupported bundle version '" + actualVersion
                            + "'; expected '" + EXPECTED_VERSION + "'");
        }

        // ── Deserialize entries ────────────────────────────────────────────────
        JsonArray entries = root.getAsJsonArray("entries");
        if (entries == null) {
            throw new IllegalArgumentException("Bundle is missing 'entries' array");
        }

        var deserializer = new IrModelDeserializer();
        List<IrModel> models = new ArrayList<>(entries.size());
        for (JsonElement elem : entries) {
            JsonObject entry = elem.getAsJsonObject();
            JsonObject modelObj = entry.getAsJsonObject("model");
            if (modelObj == null) {
                throw new IllegalArgumentException(
                        "Bundle entry is missing 'model' object");
            }
            models.add(deserializer.fromJson(modelObj));
        }

        return List.copyOf(models);
    }
}
