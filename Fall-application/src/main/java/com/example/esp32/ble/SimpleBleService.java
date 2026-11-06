package com.example.esp32.ble;

import com.example.esp32.Config;
import org.simplejavable.Adapter;
import org.simplejavable.BluetoothUUID;
import org.simplejavable.Peripheral;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class SimpleBleService implements BluetoothService {

    private static final BluetoothUUID SERVICE = new BluetoothUUID(Config.SERVICE_UUID);
    private static final BluetoothUUID CHARACTERISTIC = new BluetoothUUID(Config.CHAR_UUID);

    private Adapter adapter;
    private final Map<String, Peripheral> found = new HashMap<>();
    private Peripheral current;

    @Override
    public List<BleDevice> scan(int durationMs) throws Exception {
        if (adapter == null) {
            List<Adapter> adapters = Adapter.getAdapters();
            if (adapters.isEmpty()) {
                throw new IllegalStateException("Aucun adaptateur Bluetooth détecté.");
            }
            adapter = adapters.get(0);
        }

        found.clear();
        adapter.scanFor(durationMs);

        List<BleDevice> result = new ArrayList<>();
        for (Peripheral p : adapter.scanGetResults()) {
            String address = String.valueOf(p.getAddress());
            found.put(address, p);
            result.add(new BleDevice(p.getIdentifier(), address, (int) p.getRssi()));
        }

        // Signal le plus fort en premier (appareil le plus proche)
        result.sort(Comparator.comparingInt(BleDevice::rssi).reversed());
        return result;
    }

    @Override
    public void connect(BleDevice device) throws Exception {
        disconnect();
        Peripheral p = found.get(device.address());
        if (p == null) throw new IllegalStateException("Périphérique inconnu, refais un scan.");
        p.connect();
        current = p;
    }

    @Override
    public void send(String data) throws Exception {
        if (!isConnected()) throw new IllegalStateException("Non connecté.");

        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        int size = Config.BLE_CHUNK_SIZE;
        boolean useCommand = false;
        Exception requestError = null;

        for (int i = 0; i < bytes.length; i += size) {
            byte[] chunk = Arrays.copyOfRange(bytes, i, Math.min(i + size, bytes.length));

            if (!useCommand) {
                try {
                    // Écriture avec confirmation (préférée)
                    current.writeRequest(SERVICE, CHARACTERISTIC, chunk);
                    continue;
                } catch (Exception e) {
                    requestError = e;
                    useCommand = true; // bascule en écriture sans confirmation
                }
            }

            try {
                current.writeCommand(SERVICE, CHARACTERISTIC, chunk);
                Thread.sleep(20);
            } catch (Exception e) {
                String first = requestError == null ? "-" : requestError.getMessage();
                throw new Exception("writeRequest : " + first + "  |  writeCommand : " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void disconnect() {
        if (current != null) {
            try { current.disconnect(); } catch (Exception ignored) {}
            current = null;
        }
    }

    @Override
    public boolean isConnected() {
        try { return current != null && current.isConnected(); }
        catch (Exception e) { return false; }
    }
}
