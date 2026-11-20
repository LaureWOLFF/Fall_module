package com.example.esp32.ble;
import com.example.esp32.model.PayloadBuilder;
import java.util.concurrent.CopyOnWriteArrayList;
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

    // Service UART-like standard : à aligner avec le firmware ESP32
    private static final UUID SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID TX_CHARACTERISTIC_UUID =  UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");// ESP32 → central
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final CopyOnWriteArrayList<BleDataListener> listeners = new CopyOnWriteArrayList<>();

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

        Exception last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                p.connect();
                current = p;
                return;
            } catch (Exception e) {
                last = e;
                try { p.disconnect(); } catch (Exception ignored) {}
                Thread.sleep(1000);
            }
        }
        throw last;
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

    @Override
    public void listen(BleDataListener listener) {
        if (listener != null) listeners.addIfAbsent(listener);
        connectAndSubscribe();
    }

    private void connectAndSubscribe() {
        // 1. Scanner jusqu'à trouver l'ESP32 (nom ou SERVICE_UUID annoncé)
        // 2. Se connecter (GATT connect)
        // 3. Après onServicesDiscovered :
        //    BluetoothGattCharacteristic tx =
        //        gatt.getService(SERVICE_UUID).getCharacteristic(TX_CHARACTERISTIC_UUID);
        //    gatt.setCharacteristicNotification(tx, true);   // active la réception locale
        //
        //    BluetoothGattDescriptor cccd = tx.getDescriptor(CCCD_UUID);
        //    cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        //    gatt.writeDescriptor(cccd);                     // dit à l'ESP32 "envoie-moi tout"
    }

    /** Callback invoqué à chaque notification de l'ESP32. */
    private void onCharacteristicChanged(BleDevice source, byte[] data) {
        String payload = new String(data, java.nio.charset.StandardCharsets.UTF_8);
        String[] decoded = PayloadBuilder.parse(payload);
        for (BleDataListener l : listeners) {
            l.onDataReceived(source, data);
        }
    }
}
