package com.example.esp32;

import java.util.UUID;

public final class Config {
    private Config() {}

    // true = faux Bluetooth (test sans appareil), false = vrai Bluetooth
    public static final boolean USE_MOCK_BLUETOOTH = false;

    public static final String DEVICE_NAME_PREFIX = "ESP32C6";
    public static final String SERVICE_UUID = "4fafc201-1fb5-459e-8fcc-c5c9c331914b";
    public static final String CHAR_UUID    = "beb5483e-36e1-4688-b7f5-ea07361b26a8";
    public static final String TX_CHAR_UUID =  "6e400003-b5a3-f393-e0a9-e50e24dcca9e";// ESP32 → central


    public static final int SCAN_DURATION_MS = 5000;
    public static final int BLE_CHUNK_SIZE = 20;
}
