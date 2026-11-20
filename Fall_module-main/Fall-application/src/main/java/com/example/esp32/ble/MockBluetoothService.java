package com.example.esp32.ble;

import java.util.List;

public class MockBluetoothService implements BluetoothService {
    private boolean connected;

    @Override
    public List<BleDevice> scan(int durationMs) throws Exception {
        Thread.sleep(Math.min(durationMs, 1500));
        return List.of(
                new BleDevice("ESP32C6-Demo", "AA:BB:CC:DD:EE:01", -45),
                new BleDevice("Autre-Appareil", "AA:BB:CC:DD:EE:02", -80));
    }

    @Override
    public void connect(BleDevice device) throws Exception {
        Thread.sleep(500);
        connected = true;
    }

    @Override
    public void send(String data) throws Exception {
        if (!connected) throw new IllegalStateException("Non connecté.");
        Thread.sleep(200);
        System.out.println("[MOCK] Envoi : " + data.trim());
    }

    @Override
    public void disconnect() { connected = false; }

    @Override
    public boolean isConnected() { return connected; }
}
