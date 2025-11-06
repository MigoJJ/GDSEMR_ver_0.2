// File: src/main/java/com/emr/gds/fourgate/SimpleEKGInterpreter.java
package com.emr.gds.fourgate;

import com.emr.gds.input.IAIMain;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * SimpleEKGInterpreter - Ultra-fast EKG reporting tool
 * Replaces old EKG.java - Doctor-approved, minimal clicks
 */
public class EKG extends JFrame {

    private final JTextArea summaryArea = new JTextArea();
    private final Map<String, JCheckBox> diagnosisCheckboxes = new HashMap<>();

    private final String[][] diagnosisGroups = {
            {"Normal ECG", "Sinus Rhythm", "Sinus Bradycardia", "Sinus Tachycardia"},
            {"Atrial Fibrillation", "Atrial Flutter", "SVT", "Junctional Rhythm"},
            {"PVCs", "PACs", "Ventricular Tachycardia", "Bigeminy"},
            {"LBBB", "RBBB", "LAFB", "LPFB"},
            {"LVH", "RVH", "LAE", "RAE"},
            {"ST Elevation (STEMI)", "ST Depression", "T Wave Inversion", "Poor R Wave Progression"},
            {"Prolonged QT", "WPW Pattern", "Brugada Pattern", "Low Voltage"},
            {"Paced Rhythm", "Artifact", "Poor Data Quality"}
    };

    private final JTextField patientField = new JTextField(20);
    private final JTextField additionalNotes = new JTextField(40);

    public EKG() {
        setTitle("Quick EKG Interpreter - EMR Ready");
        setSize(960, 740);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        initUI();
        updateSummary();
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));

        // === TOP: Patient Info ===
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        topPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Patient", TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Arial", Font.BOLD, 14), Color.BLUE.darker()
        ));
        topPanel.add(new JLabel("Name/ID:"));
        topPanel.add(patientField);
        patientField.getDocument().addDocumentListener(new SimpleDocumentListener(this::updateSummary));

        // === CENTER: Diagnosis Selection ===
        JPanel centerPanel = createDiagnosisPanel();

        // === RIGHT: Live Preview ===
        summaryArea.setFont(new Font("Consolas", Font.PLAIN, 15));
        summaryArea.setEditable(false);
        summaryArea.setBackground(new Color(252, 252, 248));
        summaryArea.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane summaryScroll = new JScrollPane(summaryArea);
        summaryScroll.setPreferredSize(new Dimension(400, 0));
        summaryScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GREEN.darker(), 2),
                "EMR Report (Copy-Paste Ready)",
                TitledBorder.CENTER, TitledBorder.TOP,
                new Font("Arial", Font.BOLD, 16), Color.GREEN.darker()
        ));

        // === BOTTOM: Buttons ===
        JPanel bottomPanel = createActionPanel();

        // === Layout ===
        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(summaryScroll, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JPanel createDiagnosisPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.BLUE.darker(), 3),
                "Click to Select Diagnoses",
                TitledBorder.CENTER, TitledBorder.TOP,
                new Font("Arial", Font.BOLD, 18), Color.BLUE.darker()
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 10, 8, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        for (String[] group : diagnosisGroups) {
            gbc.gridx = 0;
            gbc.gridy = row++;
            gbc.gridwidth = 2;

            JPanel groupPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 6));
            groupPanel.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createEtchedBorder(), group[0].split(" ")[0] + "…"
            ));

            for (String diag : group) {
                JCheckBox cb = new JCheckBox(diag);
                cb.setFont(new Font("Arial", Font.PLAIN, 15));
                cb.addActionListener(e -> updateSummary());
                groupPanel.add(cb);
                diagnosisCheckboxes.put(diag, cb);
            }
            panel.add(groupPanel, gbc);
        }

        // Free text impression
        gbc.gridy = row++;
        gbc.gridwidth = 1;
        panel.add(new JLabel("Impression / Notes:"), gbc);
        gbc.gridx = 1;
        additionalNotes.setPreferredSize(new Dimension(300, 32));
        additionalNotes.getDocument().addDocumentListener(new SimpleDocumentListener(this::updateSummary));
        panel.add(additionalNotes, gbc);

        JScrollPane scroll = new JScrollPane(panel);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(scroll, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel createActionPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 25, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 15, 0));

        JButton normalBtn = createStyledButton("Normal ECG", new Color(0, 120, 215));
        JButton clearBtn = createStyledButton("Clear All", new Color(180, 180, 180));
        JButton saveBtn = createStyledButton("Save to EMR", new Color(0, 140, 0));

        normalBtn.addActionListener(e -> setNormalECG());
        clearBtn.addActionListener(e -> clearAll());
        saveBtn.addActionListener(e -> saveToEMR());

        panel.add(normalBtn);
        panel.add(clearBtn);
        panel.add(saveBtn);
        return panel;
    }

    private JButton createStyledButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Arial", Font.BOLD, 16));
        btn.setForeground(Color.WHITE);
        btn.setBackground(bg);
        btn.setPreferredSize(new Dimension(160, 48));
        btn.setFocusPainted(false);
        return btn;
    }

    private void updateSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("EKG INTERPRETATION\n");
        sb.append("Date: ").append(LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy"))).append("\n");
        sb.append("══════════════════════════════════════\n\n");

        String patient = patientField.getText().trim();
        if (!patient.isEmpty()) {
            sb.append("Patient: ").append(patient).append("\n\n");
        }

        boolean hasFinding = false;
        for (Map.Entry<String, JCheckBox> entry : diagnosisCheckboxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                sb.append("• ").append(entry.getKey()).append("\n");
                hasFinding = true;
            }
        }

        String notes = additionalNotes.getText().trim();
        if (!notes.isEmpty()) {
            sb.append("\nImpression: ").append(notes).append("\n");
            hasFinding = true;
        }

        if (!hasFinding) {
            sb.append("No acute abnormalities identified.\n");
            sb.append("Normal sinus rhythm.\n");
        }

        sb.append("\n— End of EKG Report —");
        summaryArea.setText(sb.toString());
    }

    private void clearAll() {
        diagnosisCheckboxes.values().forEach(cb -> cb.setSelected(false));
        patientField.setText("");
        additionalNotes.setText("");
        updateSummary();
    }

    private void setNormalECG() {
        clearAll();
        diagnosisCheckboxes.get("Normal ECG").setSelected(true);
        diagnosisCheckboxes.get("Sinus Rhythm").setSelected(true);
        updateSummary();
    }

    private void saveToEMR() {
        String report = summaryArea.getText();
        if (report.length() < 50) {
            JOptionPane.showMessageDialog(this, "Nothing to save!", "Empty Report", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            if (IAIMain.getTextAreaManager() == null || !IAIMain.getTextAreaManager().isReady()) {
                JOptionPane.showMessageDialog(this, "EMR connection not ready!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String finalReport = "\n< EKG Report >\n" + report;
            IAIMain.getTextAreaManager().focusArea(5);
            IAIMain.getTextAreaManager().insertLineIntoFocusedArea(finalReport);

            JOptionPane.showMessageDialog(this, "EKG report saved to EMR!", "Success", JOptionPane.INFORMATION_MESSAGE);
            clearAll();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Save failed:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Helper: Document listener
    private static class SimpleDocumentListener implements javax.swing.event.DocumentListener {
        private final Runnable action;
        SimpleDocumentListener(Runnable action) { this.action = action; }
        public void insertUpdate(javax.swing.event.DocumentEvent e) { action.run(); }
        public void removeUpdate(javax.swing.event.DocumentEvent e) { action.run(); }
        public void changedUpdate(javax.swing.event.DocumentEvent e) { action.run(); }
    }

    // === MAIN LAUNCHER ===
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Works perfectly on Java 21 Linux (your exact setup)
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {}
            new EKG().setVisible(true);
        });
    }
}