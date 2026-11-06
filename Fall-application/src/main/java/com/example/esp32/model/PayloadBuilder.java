package com.example.esp32.model;

public final class PayloadBuilder {
    private PayloadBuilder() {}

    /** Format : NAME=...;VALUE=...;MSG=...\n */
    public static String build(String name, String value, String message) {
        return "NAME=" + clean(name)
                + ";VALUE=" + clean(value)
                + ";MSG=" + clean(message)
                + "\n";
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace(";", ",").replace("\n", " ").trim();
    }
}
