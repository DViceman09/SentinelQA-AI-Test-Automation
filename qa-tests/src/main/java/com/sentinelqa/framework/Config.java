package com.sentinelqa.framework;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * One place for settings. Lookup order: -Dbase.url=... → env BASE_URL → .env file → default.
 * The same .env drives docker compose, so a demo is configured once.
 */
public final class Config {

    private static final Map<String, String> DOT_ENV = loadDotEnv();

    private Config() {}

    public static String get(String key, String defaultValue) {
        String envName = key.toUpperCase().replace('.', '_');
        for (String value : new String[] {System.getProperty(key), System.getenv(envName), DOT_ENV.get(envName)}) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return defaultValue;
    }

    public static int getInt(String key, int defaultValue) {
        return Integer.parseInt(get(key, String.valueOf(defaultValue)));
    }

    public static boolean getBool(String key, boolean defaultValue) {
        return Boolean.parseBoolean(get(key, String.valueOf(defaultValue)));
    }

    /** Where API and DB-side tests reach the app. */
    public static String baseUrl() {
        return get("base.url", "http://localhost:8080");
    }

    /** Where the browser reaches the app; differs from baseUrl when browsers run inside Selenium Grid containers. */
    public static String uiBaseUrl() {
        return get("ui.base.url", baseUrl());
    }

    // Tests run from the module folder, so also look for .env one level up (repo root).
    private static Map<String, String> loadDotEnv() {
        for (Path path : List.of(Path.of(".env"), Path.of("..", ".env"))) {
            if (!Files.isRegularFile(path)) continue;
            try {
                return Files.readAllLines(path).stream().map(String::trim)
                        .filter(line -> !line.startsWith("#") && line.contains("="))
                        .collect(Collectors.toMap(
                                line -> line.substring(0, line.indexOf('=')).trim(),
                                line -> line.substring(line.indexOf('=') + 1).trim().replaceAll("^\"|\"$", ""),
                                (first, second) -> second));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return Map.of();
    }
}
