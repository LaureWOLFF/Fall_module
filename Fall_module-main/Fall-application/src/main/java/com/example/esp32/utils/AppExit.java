package com.example.esp32.utils;

import java.util.concurrent.TimeUnit;

public final class AppExit {
    private AppExit() {}

    /* Arrête le programme immédiatement, même si un thread natif Bluetooth est bloqué. */
    public static void now() {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            try {
                Process p = new ProcessBuilder("taskkill", "/F", "/PID",
                        String.valueOf(ProcessHandle.current().pid()))
                        .redirectErrorStream(true)
                        .start();
                p.waitFor(3, TimeUnit.SECONDS); // on laisse taskkill agir avant le filet de sécurité
            } catch (Exception ignored) {
                // Si taskkill échoue, halt() prend le relais
            }
        }
        Runtime.getRuntime().halt(0);
    }
}