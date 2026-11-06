package com.example.esp32.ble;

public record BleDevice(String name, String address, int rssi) {
    @Override
    public String toString() {
        String n = (name == null || name.isBlank()) ? "(sans nom)" : name;
        return n + "  [" + address + "]  " + rssi + " dBm";
    }
}
