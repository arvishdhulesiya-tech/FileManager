package com.filemanager.api;

/** Reads FileManager configuration from Java system properties or environment variables. */
final class Config {
    private Config() {}

    static String value(String key, String defaultValue) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) v = System.getenv(key);
        return (v == null || v.isBlank()) ? defaultValue : v.trim();
    }

    static boolean bool(String key, boolean defaultValue) {
        return Boolean.parseBoolean(value(key, Boolean.toString(defaultValue)));
    }

    static long longValue(String key, long defaultValue) {
        try { return Long.parseLong(value(key, Long.toString(defaultValue))); }
        catch (NumberFormatException e) { return defaultValue; }
    }
}
