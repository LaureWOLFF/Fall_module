package com.example.esp32;

import com.example.esp32.auth.AuthService;
import com.example.esp32.ble.BluetoothService;
import com.example.esp32.ble.MockBluetoothService;
import com.example.esp32.ble.SimpleBleService;
import com.example.esp32.ui.LoginFrame;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            AuthService auth = new AuthService();
            BluetoothService ble = Config.USE_MOCK_BLUETOOTH
                    ? new MockBluetoothService()
                    : new SimpleBleService();

            new LoginFrame(auth, ble).setVisible(true);
        });
    }
}
