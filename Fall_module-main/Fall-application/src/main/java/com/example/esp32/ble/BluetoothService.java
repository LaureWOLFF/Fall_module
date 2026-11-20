package com.example.esp32.ble;

import java.util.List;

public interface BluetoothService {
    List<BleDevice> scan(int durationMs) throws Exception;
    void connect(BleDevice device) throws Exception;
    void send(String data) throws Exception;
    void disconnect();
    boolean isConnected();
}
