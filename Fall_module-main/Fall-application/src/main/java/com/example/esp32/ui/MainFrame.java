package com.example.esp32.ui;

import com.example.esp32.Config;
import com.example.esp32.ble.BleDevice;
import com.example.esp32.ble.BluetoothService;
import com.example.esp32.model.PayloadBuilder;
import com.example.esp32.utils.AppExit;
import com.example.esp32.auth.AuthService;

import javax.swing.*;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainFrame extends JFrame {

    private final BluetoothService ble;

    private final DefaultListModel<BleDevice> deviceModel = new DefaultListModel<>();
    private final JList<BleDevice> deviceList = new JList<>(deviceModel);

    private final AuthService auth;
    private final String username;

    private final JButton scanBtn = new JButton("Scanner");
    private final JButton connectBtn = new JButton("Connecter");
    private final JButton disconnectBtn = new JButton("Déconnecter");
    private final JButton sendBtn = new JButton("Envoyer");

    private final JTextField nameField = new JTextField(15);
    private final JTextField valueField = new JTextField(15);
    private final JTextArea messageArea = new JTextArea(3, 15);
    private final JTextArea logArea = new JTextArea(8, 40);
    private final JLabel statusLabel = new JLabel("Non connecté");
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ble-watchdog");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean wasConnected = false;

    public MainFrame(String username, AuthService auth, BluetoothService ble) {
        super("ESP32-C6 Controller - " + username);
        this.username = username;
        this.auth = auth;
        this.ble = ble;
        buildUI();
        updateButtons();
        watchdog.scheduleWithFixedDelay(this::checkConnection, 1500, 1500, TimeUnit.MILLISECONDS);
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
        add(buildTopBar(), BorderLayout.NORTH);

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
            wasConnected = true;
            String label = (dev.name() == null || dev.name().isBlank()) ? dev.address() : dev.name();
            statusLabel.setText("Connecté à " + label);
            log("Connecté.");
        });
    }

    private void onDisconnect() {
        wasConnected = false;
        ble.disconnect();
        statusLabel.setText("Non connecté");
        log("Déconnecté.");
        updateButtons();
    }

    private JPanel buildTopBar() {
        JLabel who = new JLabel("Utilisateur : " + username);

        JButton logoutBtn = new JButton("Fermer la session");
        logoutBtn.addActionListener(e -> onLogout());

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        right.add(logoutBtn);

        JPanel bar = new JPanel(new BorderLayout());
        bar.add(who, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    /** Ferme la session : coupe le Bluetooth puis retourne à l'écran de connexion. */
    private void onLogout() {
        if (ble.isConnected()) {
            int answer = JOptionPane.showConfirmDialog(this,
                    "La connexion Bluetooth sera coupée. Continuer ?",
                    "Fermer la session", JOptionPane.YES_NO_OPTION);
            if (answer != JOptionPane.YES_OPTION) return;
        }

        wasConnected = false;
        watchdog.shutdownNow();
        setVisible(false);

        // Déconnexion dans un thread à part, 1,5 s maximum
        Thread closer = new Thread(() -> {
            try { ble.disconnect(); } catch (Exception ignored) {}
        }, "ble-close");
        closer.setDaemon(true);
        closer.start();
        try { closer.join(1500); } catch (InterruptedException ignored) {}

        dispose();
        new LoginFrame(auth, ble).setVisible(true);
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
        watchdog.shutdownNow();
        setVisible(false);

        Thread closer = new Thread(() -> {
            try { ble.disconnect(); } catch (Exception ignored) {}
        }, "ble-close");
        closer.setDaemon(true);
        closer.start();
        try { closer.join(1500); } catch (InterruptedException ignored) {}

        dispose();
        AppExit.now();
    }

    // ---------- Utilitaires ----------

    /* Exécute une tâche bloquante hors du thread UI, puis rappelle onOk sur le thread UI. */
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
        if (!connected && !statusLabel.getText().equals("Non connecté")) {
            statusLabel.setText("Non connecté");
            wasConnected = false;
        }
        scanBtn.setEnabled(true);
        connectBtn.setEnabled(!connected && deviceList.getSelectedValue() != null);
        disconnectBtn.setEnabled(connected);
        sendBtn.setEnabled(connected);
    }

    /* Tourne en arrière-plan : détecte une connexion rompue par l'appareil. */
    private volatile int missed = 0;

    private void checkConnection() {
        if (!wasConnected || ble.isConnected()) {
            missed = 0;
            return;
        }
        if (++missed >= 2) {
            missed = 0;
            wasConnected = false;
            SwingUtilities.invokeLater(() -> {
                log("Connexion perdue : l'appareil s'est déconnecté.");
                updateButtons();
            });
        }
    }

    private void log(String msg) {
        String t = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        logArea.append("[" + t + "] " + msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }


}
