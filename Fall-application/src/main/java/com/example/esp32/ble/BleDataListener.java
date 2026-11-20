package com.example.esp32.ble;

public interface BleDataListener {
    void onDataReceived(BleDevice source, byte[] data);
}
