package com.example.esp32.ui;

import com.example.esp32.auth.AuthService;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

public class RegisterDialog extends JDialog {

    private final AuthService auth;

    private final JTextField userField = new JTextField(15);
    private final JPasswordField passField = new JPasswordField(15);
    private final JPasswordField confirmField = new JPasswordField(15);
    private final JLabel messageLabel = new JLabel(" ");

    private String createdUsername; // null tant qu'aucun compte n'est créé

    public RegisterDialog(Frame owner, AuthService auth) {
        super(owner, "Créer un compte", true);
        this.auth = auth;
        buildUI();
    }

    /** Nom du compte créé, ou null si l'utilisateur a annulé. */
    public String getCreatedUsername() {
        return createdUsername;
    }

    private void buildUI() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; c.gridy = 0; form.add(new JLabel("Utilisateur :"), c);
        c.gridx = 1;              form.add(userField, c);
        c.gridx = 0; c.gridy = 1; form.add(new JLabel("Mot de passe :"), c);
        c.gridx = 1;              form.add(passField, c);
        c.gridx = 0; c.gridy = 2; form.add(new JLabel("Confirmation :"), c);
        c.gridx = 1;              form.add(confirmField, c);

        messageLabel.setForeground(Color.RED);
        c.gridx = 0; c.gridy = 3; c.gridwidth = 2;
        form.add(messageLabel, c);

        JButton createBtn = new JButton("Créer le compte");
        JButton cancelBtn = new JButton("Annuler");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttons.add(createBtn);
        buttons.add(cancelBtn);
        c.gridy = 4; c.anchor = GridBagConstraints.CENTER;
        form.add(buttons, c);

        createBtn.addActionListener(e -> onCreate());
        cancelBtn.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(createBtn);

        setContentPane(form);
        pack();
        setLocationRelativeTo(getOwner());
    }

    private void onCreate() {
        String username = userField.getText().trim();
        char[] pass = passField.getPassword();
        char[] confirm = confirmField.getPassword();

        try {
            if (!Arrays.equals(pass, confirm)) {
                showError("Les mots de passe ne correspondent pas.");
                return;
            }
            auth.register(username, pass);

            createdUsername = username;
            JOptionPane.showMessageDialog(this,
                    "Compte « " + username + " » créé avec succès.",
                    "Compte créé", JOptionPane.INFORMATION_MESSAGE);
            dispose();

        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        } catch (RuntimeException ex) {
            showError("Erreur : " + ex.getMessage());
        } finally {
            Arrays.fill(pass, '\0');
            Arrays.fill(confirm, '\0');
        }
    }

    private void showError(String msg) {
        messageLabel.setText(msg);
        pack();
    }
}
