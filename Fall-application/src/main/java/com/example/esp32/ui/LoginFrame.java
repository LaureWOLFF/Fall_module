package com.example.esp32.ui;

import com.example.esp32.auth.AuthService;
import com.example.esp32.ble.BluetoothService;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

public class LoginFrame extends JFrame {

    private final AuthService auth;
    private final BluetoothService ble;

    private final JTextField userField = new JTextField(15);
    private final JPasswordField passField = new JPasswordField(15);
    private final JLabel errorLabel = new JLabel(" ");

    public LoginFrame(AuthService auth, BluetoothService ble) {
        super("Connexion");
        this.auth = auth;
        this.ble = ble;
        buildUI();
    }

    private void buildUI() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; c.gridy = 0; form.add(new JLabel("Utilisateur :"), c);
        c.gridx = 1;              form.add(userField, c);
        c.gridx = 0; c.gridy = 1; form.add(new JLabel("Mot de passe :"), c);
        c.gridx = 1;              form.add(passField, c);

        errorLabel.setForeground(Color.RED);
        c.gridx = 0; c.gridy = 2; c.gridwidth = 2;
        form.add(errorLabel, c);

        JButton loginBtn = new JButton("Se connecter");
        c.gridy = 3; c.anchor = GridBagConstraints.CENTER;
        form.add(loginBtn, c);

        JButton registerBtn = new JButton("Créer un compte");
        c.gridy = 4;
        form.add(registerBtn, c);

        loginBtn.addActionListener(e -> attemptLogin());
        registerBtn.addActionListener(e -> openRegisterDialog());
        getRootPane().setDefaultButton(loginBtn);

        setContentPane(form);
        pack();
        setLocationRelativeTo(null);
    }

    private void attemptLogin() {
        String user = userField.getText().trim();
        char[] pass = passField.getPassword();

        boolean ok = auth.authenticate(user, pass);
        Arrays.fill(pass, '\0');

        if (ok) {
            new MainFrame(user, ble).setVisible(true);
            dispose();
        } else {
            errorLabel.setText("Identifiants incorrects.");
            passField.setText("");
        }
    }

    private void openRegisterDialog() {
        RegisterDialog dialog = new RegisterDialog(this, auth);
        dialog.setVisible(true); // bloque jusqu'à la fermeture (fenêtre modale)

        String created = dialog.getCreatedUsername();
        if (created != null) {
            userField.setText(created);
            passField.requestFocusInWindow();
            errorLabel.setText(" ");
        }
    }
}
