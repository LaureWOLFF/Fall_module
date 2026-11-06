package com.example.esp32.ui;

import com.example.esp32.Config;
import com.example.esp32.ble.BleDevice;
import com.example.esp32.ble.BluetoothService;
import com.example.esp32.model.PayloadBuilder;

import javax.swing.*;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class MainFrame extends JFrame {

    private final BluetoothService ble;

    private final DefaultListModel<BleDevice> deviceModel = new DefaultListModel<>();
    private final JList<BleDevice> deviceList = new JList<>(deviceModel);

    private final JButton scanBtn = new JButton("Scanner");
    private final JButton connectBtn = new JButton("Connecter");
    private final JButton disconnectBtn = new JButton("Déconnecter");
    private final JButton sendBtn = new JButton("Envoyer");

    private final JTextField nameField = new JTextField(15);
    private final JTextField valueField = new JTextField(15);
    private final JTextArea messageArea = new JTextArea(3, 15);
    private final JTextArea logArea = new JTextArea(8, 40);
    private final JLabel statusLabel = new JLabel("Non connecté");

    public MainFrame(String username, BluetoothService ble) {
        super("ESP32-C6 Controller - " + username);
        this.ble = ble;
        buildUI();
        updateButtons();
    }

    // ---------- Construction de l'interface ----------

    private void buildUI() {
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { shutdown(); }
        });

        setLayout(new BorderLayout(10, 10));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(buildDevicePanel(), BorderLayout.WEST);
        add(buildFormPanel(), BorderLayout.CENTER);
        add(buildLogPanel(), BorderLayout.SOUTH);

        scanBtn.addActionListener(e -> onScan());
        connectBtn.addActionListener(e -> onConnect());
        disconnectBtn.addActionListener(e -> onDisconnect());
        sendBtn.addActionListener(e -> onSend());
        deviceList.addListSelectionListener(e -> updateButtons());

        pack();
        setLocationRelativeTo(null);
    }

    private JPanel buildDevicePanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBorder(BorderFactory.createTitledBorder("Appareils Bluetooth"));
        deviceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = new JScrollPane(deviceList);
        scroll.setPreferredSize(new Dimension(260, 150));
        p.add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(3, 1, 5, 5));
        buttons.add(scanBtn);
        buttons.add(connectBtn);
        buttons.add(disconnectBtn);
        p.add(buttons, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildFormPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("Données à envoyer"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        c.gridx = 0; c.gridy = 0; c.weightx = 0; p.add(new JLabel("Nom :"), c);
        c.gridx = 1; c.weightx = 1;              p.add(nameField, c);
        c.gridx = 0; c.gridy = 1; c.weightx = 0; p.add(new JLabel("Valeur :"), c);
        c.gridx = 1; c.weightx = 1;              p.add(valueField, c);
        c.gridx = 0; c.gridy = 2; c.weightx = 0; p.add(new JLabel("Message :"), c);
        c.gridx = 1; c.weightx = 1;              p.add(new JScrollPane(messageArea), c);

        c.gridx = 0; c.gridy = 3; c.gridwidth = 2;
        p.add(sendBtn, c);
        c.gridy = 4;
        p.add(statusLabel, c);
        return p;
    }

    private JScrollPane buildLogPanel() {
        logArea.setEditable(false);
        JScrollPane s = new JScrollPane(logArea);
        s.setBorder(BorderFactory.createTitledBorder("Journal"));
        return s;
    }

    // ---------- Actions ----------

    private void onScan() {
        log("Scan en cours (" + Config.SCAN_DURATION_MS / 1000 + " s)...");
        setBusy(true);
        runAsync(() -> ble.scan(Config.SCAN_DURATION_MS), (List<BleDevice> devices) -> {
            deviceModel.clear();
            devices.forEach(deviceModel::addElement);
            log(devices.size() + " appareil(s) trouvé(s).");
        });
    }

    private void onConnect() {
        BleDevice d = deviceList.getSelectedValue();
        if (d == null) return;
        log("Connexion à " + d + "...");
        setBusy(true);
        runAsync(() -> { ble.connect(d); return d; }, dev -> {
            statusLabel.setText("Connecté à " + dev.name());
            log("Connecté.");
        });
    }

    private void onDisconnect() {
        ble.disconnect();
        statusLabel.setText("Non connecté");
        log("Déconnecté.");
        updateButtons();
    }

    private void onSend() {
        String name = nameField.getText();
        String value = valueField.getText();
        if (name.isBlank() && value.isBlank() && messageArea.getText().isBlank()) {
            log("Rien à envoyer.");
            return;
        }
        String payload = PayloadBuilder.build(name, value, messageArea.getText());
        log("Envoi : " + payload.trim());
        setBusy(true);
        runAsync(() -> { ble.send(payload); return payload; }, p -> log("Envoyé."));
    }

    private void shutdown() {
        ble.disconnect();
        dispose();
        System.exit(0);
    }

    // ---------- Utilitaires ----------

    /** Exécute une tâche bloquante hors du thread UI, puis rappelle onOk sur le thread UI. */
    private <T> void runAsync(Callable<T> task, Consumer<T> onOk) {
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return task.call(); }
            @Override protected void done() {
                try {
                    onOk.accept(get());
                } catch (Exception ex) {
                    Throwable root = ex.getCause() != null ? ex.getCause() : ex;
                    log("Erreur : " + root.getMessage());
                } finally {
                    setBusy(false);
                }
            }
        }.execute();
    }

    private void setBusy(boolean busy) {
        setCursor(busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
        if (busy) {
            scanBtn.setEnabled(false);
            connectBtn.setEnabled(false);
            disconnectBtn.setEnabled(false);
            sendBtn.setEnabled(false);
        } else {
            updateButtons();
        }
    }

    private void updateButtons() {
        boolean connected = ble.isConnected();
        scanBtn.setEnabled(true);
        connectBtn.setEnabled(!connected && deviceList.getSelectedValue() != null);
        disconnectBtn.setEnabled(connected);
        sendBtn.setEnabled(connected);
    }

    private void log(String msg) {
        String t = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        logArea.append("[" + t + "] " + msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
}
