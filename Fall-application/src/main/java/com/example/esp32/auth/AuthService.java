package com.example.esp32.auth;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;

public class AuthService {

    private static final int MIN_USERNAME_LENGTH = 3;
    private static final int MIN_PASSWORD_LENGTH = 6;

    private record Credentials(String salt, String hash) {}

    private final Map<String, Credentials> users = new HashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Path storage = Path.of(
            System.getProperty("user.home"), ".esp32-controller", "users.properties");

    public AuthService() {
        load();
        if (users.isEmpty()) {
            addUser("admin", "1234");
            save();
        }
    }

    public synchronized void register(String username, char[] password) {
        String name = username == null ? "" : username.trim();

        if (name.length() < MIN_USERNAME_LENGTH) {
            throw new IllegalArgumentException(
                    "Le nom d'utilisateur doit contenir au moins " + MIN_USERNAME_LENGTH + " caractères.");
        }
        if (password == null || password.length < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "Le mot de passe doit contenir au moins " + MIN_PASSWORD_LENGTH + " caractères.");
        }
        if (users.containsKey(name)) {
            throw new IllegalArgumentException("Ce nom d'utilisateur existe déjà.");
        }

        addUser(name, new String(password));
        save();
    }

    public synchronized boolean authenticate(String username, char[] password) {
        Credentials c = users.get(username);
        if (c == null) return false;
        String candidate = hash(c.salt(), new String(password));
        return MessageDigest.isEqual(
                candidate.getBytes(StandardCharsets.UTF_8),
                c.hash().getBytes(StandardCharsets.UTF_8));
    }

    public synchronized boolean userExists(String username) {
        return users.containsKey(username);
    }

    private void addUser(String username, String password) {
        byte[] saltBytes = new byte[16];
        random.nextBytes(saltBytes);
        String salt = HexFormat.of().formatHex(saltBytes);
        users.put(username, new Credentials(salt, hash(salt, password)));
    }

    private void load() {
        if (!Files.exists(storage)) return;
        Properties props = new Properties();
        try (var reader = Files.newBufferedReader(storage, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture des comptes impossible", e);
        }
        for (String name : props.stringPropertyNames()) {
            String[] parts = props.getProperty(name).split(":", 2);
            if (parts.length == 2) {
                users.put(name, new Credentials(parts[0], parts[1]));
            }
        }
    }

    private void save() {
        Properties props = new Properties();
        users.forEach((name, c) -> props.setProperty(name, c.salt() + ":" + c.hash()));
        try {
            Files.createDirectories(storage.getParent());
            try (var writer = Files.newBufferedWriter(storage, StandardCharsets.UTF_8)) {
                props.store(writer, "Comptes ESP32 Controller (mots de passe hachés)");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Sauvegarde des comptes impossible", e);
        }
    }

    private static String hash(String salt, String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest((salt + password).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
