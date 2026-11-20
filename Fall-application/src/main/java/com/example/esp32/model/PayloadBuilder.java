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

    /** Décode "NAME=...;VALUE=...;MSG=...\n" reçu de l'ESP32. */
    public static String[] parse(String payload) {
        String name = "", value = "", msg = "";
        for (String part : payload.split(";")) {
            int eq = part.indexOf('=');
            if (eq < 0) continue;
            String key = part.substring(0, eq).trim();
            String val = part.substring(eq + 1).trim();
            switch (key) {
                case "NAME"  -> name  = val;
                case "VALUE" -> value = val;
                case "MSG"   -> msg   = val;
            }
        }
        return new String[]{ name, value, msg };
    }
}
