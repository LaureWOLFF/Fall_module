package com.example.esp32.utils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Recolle les paquets Bluetooth (20 octets au plus) en messages complets.
 * Un message se termine par '\n'. Si plus rien n'arrive pendant 300 ms, ce qui a été
 * reçu est considéré comme un message complet (au cas où le '\n' aurait été retiré en amont).
 */
public class LineAssembler {

    private static final int IDLE_FLUSH_MS = 300;
    private static final int MAX_LENGTH = 1024;

    private final Consumer<String> onLine;
    private final StringBuilder buffer = new StringBuilder();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "line-assembler");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> pendingFlush;

    /** onLine est appelé avec chaque message complet (depuis un thread de la bibliothèque, pas le thread Swing). */
    public LineAssembler(Consumer<String> onLine) {
        this.onLine = onLine;
    }

    /** À appeler avec chaque paquet reçu. */
    public synchronized void feed(byte[] data) {
        buffer.append(new String(data, StandardCharsets.UTF_8));

        int nl;
        while ((nl = buffer.indexOf("\n")) >= 0) {
            String line = buffer.substring(0, nl).trim();
            buffer.delete(0, nl + 1);
            if (!line.isEmpty()) onLine.accept(line);
        }

        if (pendingFlush != null) pendingFlush.cancel(false);
        if (buffer.length() > MAX_LENGTH) {
            flush();
        } else if (buffer.length() > 0) {
            pendingFlush = timer.schedule(this::flush, IDLE_FLUSH_MS, TimeUnit.MILLISECONDS);
        }
    }

    private synchronized void flush() {
        String line = buffer.toString().trim();
        buffer.setLength(0);
        if (!line.isEmpty()) onLine.accept(line);
    }
}