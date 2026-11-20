package com.example.esp32.ui;

import javax.swing.*;
import java.awt.*;

public class FallHistoryFrame extends JFrame {

    public FallHistoryFrame(JFrame parentFrame, DefaultListModel<String> historyModel) {
        super("Historique des chutes");
        setSize(620, 380);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        JList<String> list = new JList<>(historyModel);
        list.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        add(new JScrollPane(list), BorderLayout.CENTER);

        JButton backButton = new JButton("Retour à la fenêtre principale");
        backButton.addActionListener(e -> {
            if (parentFrame != null) {
                parentFrame.toFront();
                parentFrame.requestFocus();
            }
            dispose();
        });

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.add(backButton);
        add(bottomPanel, BorderLayout.SOUTH);
    }
}