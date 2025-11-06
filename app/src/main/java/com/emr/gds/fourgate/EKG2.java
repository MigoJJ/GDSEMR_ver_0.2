package com.emr.gds.fourgate;

import com.emr.gds.input.IAIMain;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A JFrame-based application for systematic EKG (Electrocardiogram) analysis.
 * This tool provides a structured form for documenting EKG findings and generating a summary report.
 */
public class EKG2 extends JFrame {

    // UI Components
    private JCheckBox[] leadCheckboxes;
    private JTextArea summaryArea;
    private final List<JTextField> textFields = new ArrayList<>();
    private final List<String> fieldLabels = new ArrayList<>();
    private final List<JCheckBox> checkBoxes = new ArrayList<>();
    private final Map<JComponent, String> componentToSectionMap = new LinkedHashMap<>();
    private final List<String> sectionTitles = new ArrayList<>();

    public EKG2() {
        setTitle("EMR EKG Analysis");
        setSize(1300, 850);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        initializeUI();
    }

    private void initializeUI() {
        // Main Panels
        JPanel southPanel = createSouthPanel();
        JPanel eastPanel = createEastPanel();
        JPanel westPanel = createWestPanel();
        JPanel centralPanel = createCentralPanel();

        // Layout main panels
        JPanel mainContentPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;

        gbc.gridx = 0;
        gbc.weightx = 0.22;
        mainContentPanel.add(westPanel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.60;
        mainContentPanel.add(centralPanel, gbc);

        add(southPanel, BorderLayout.SOUTH);
        add(eastPanel, BorderLayout.EAST);
        add(mainContentPanel, BorderLayout.CENTER);
    }

    private JPanel createSouthPanel() {
        JPanel panel = new JPanel();
        JButton clearButton = new JButton("Clear All");
        JButton saveButton = new JButton("Save to EMR");
        JButton quitButton = new JButton("Quit");
        JButton refButton = new JButton("EKG Reference");

        clearButton.addActionListener(e -> clearAllFields());
        saveButton.addActionListener(e -> saveDataToEMR());
        quitButton.addActionListener(e -> dispose());
        refButton.addActionListener(e -> openReferenceFile());

        panel.add(clearButton);
        panel.add(saveButton);
        panel.add(quitButton);
        panel.add(refButton);
        return panel;
    }

    private JPanel createEastPanel() {
        String[] leads = {
                "Normal ECG", "Sinus Bradycardia", "Sinus Tachycardia", "Atrial Fibrillation",
                "Premature Ventricular Contraction (PVC)", "Premature Atrial Contraction (PAC)",
                "Left Ventricular Hypertrophy (LVH) ECG pattern", "Non-specific ST-T changes",
                "Right Bundle Branch Block (RBBB)", "Left Bundle Branch Block (LBBB)",
                "Prolonged QT", "ST Elevation", "ST Depression", "Ventricular Tachycardia (VT)",
                "Wolff-Parkinson-White (WPW) syndrome", "Supraventricular Tachycardia (SVT: PSVT, etc.)",
                "Anterior Wall Ischemia / STEMI", "Atypical T wave changes",
                "LAE", "RAE", "LVH", "RVH", "PTFV1",
                "Junctional rhythm", "Supraventricular tachycardia (SVT)",
                "Poor R Wave Progression", "Atrial Flutter"
        };

        JPanel checkboxPanel = new JPanel(new GridLayout(0, 1));
        leadCheckboxes = new JCheckBox[leads.length];
        for (int i = 0; i < leads.length; i++) {
            leadCheckboxes[i] = new JCheckBox(leads[i]);
            leadCheckboxes[i].addItemListener(e -> updateSummary());
            checkboxPanel.add(leadCheckboxes[i]);
        }

        JScrollPane scrollPane = new JScrollPane(checkboxPanel);
        scrollPane.setPreferredSize(new Dimension(330, 0));
        return new JPanel(new BorderLayout()) {{ add(scrollPane, BorderLayout.CENTER); }};
    }

    private JPanel createWestPanel() {
        summaryArea = new JTextArea(10, 20);
        summaryArea.setLineWrap(true);
        summaryArea.setWrapStyleWord(true);
        summaryArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(new JLabel("Summary and Conclusion:"), BorderLayout.NORTH);
        panel.add(new JScrollPane(summaryArea), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createCentralPanel() {
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        int row = 0;
        row = addSection(formPanel, gbc, "🩺 Patient Information", row);
        row = addField(formPanel, gbc, "Name:", row, "🩺 Patient Information");
        // ... Add all other fields and sections in the same manner

        JScrollPane scrollPane = new JScrollPane(formPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JPanel centralPanel = new JPanel(new BorderLayout());
        centralPanel.setBorder(BorderFactory.createTitledBorder("EKG Interpretation Input"));
        centralPanel.add(scrollPane, BorderLayout.CENTER);
        return centralPanel;
    }

    private int addSection(JPanel panel, GridBagConstraints gbc, String section, int row) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        JLabel label = new JLabel(section);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 15f));
        panel.add(label, gbc);
        gbc.gridwidth = 1;
        sectionTitles.add(section);
        return row + 1;
    }

    private int addField(JPanel panel, GridBagConstraints gbc, String label, int row, String section) {
        gbc.gridx = 0;
        gbc.gridy = row;
        panel.add(new JLabel(label), gbc);

        gbc.gridx = 1;
        JTextField field = new JTextField(25);
        panel.add(field, gbc);

        textFields.add(field);
        fieldLabels.add(label);
        componentToSectionMap.put(field, section);
        field.getDocument().addDocumentListener(new SummaryUpdateListener());
        return row + 1;
    }

    private int addCheckGroup(JPanel panel, GridBagConstraints gbc, String[] labels, int row, String section) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        JPanel groupPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        for (String label : labels) {
            JCheckBox cb = new JCheckBox(label);
            groupPanel.add(cb);
            checkBoxes.add(cb);
            componentToSectionMap.put(cb, section);
            cb.addItemListener(e -> updateSummary());
        }
        panel.add(groupPanel, gbc);
        gbc.gridwidth = 1;
        return row + 1;
    }

    private void updateSummary() {
        StringBuilder sb = new StringBuilder();

        for (JCheckBox cb : leadCheckboxes) {
            if (cb.isSelected()) {
                sb.append("# ").append(cb.getText()).append("\n");
            }
        }

        Map<String, List<String>> sectionSummary = getStructuredSummary();
        for (Map.Entry<String, List<String>> entry : sectionSummary.entrySet()) {
            sb.append("# ").append(entry.getKey()).append("\n");
            for (String item : entry.getValue()) {
                sb.append("    - ").append(item).append("\n");
            }
        }

        summaryArea.setText(sb.toString());
    }

    private Map<String, List<String>> getStructuredSummary() {
        Map<String, List<String>> summary = new LinkedHashMap<>();
        sectionTitles.forEach(title -> summary.put(title, new ArrayList<>()));

        checkBoxes.stream().filter(JCheckBox::isSelected).forEach(cb -> {
            String section = componentToSectionMap.get(cb);
            if (section != null) summary.get(section).add(cb.getText());
        });

        for (int i = 0; i < textFields.size(); i++) {
            String text = textFields.get(i).getText().trim();
            if (!text.isEmpty()) {
                String section = componentToSectionMap.get(textFields.get(i));
                if (section != null) summary.get(section).add(fieldLabels.get(i) + " " + text);
            }
        }

        summary.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        return summary;
    }

    private void clearAllFields() {
        summaryArea.setText("");
        for (JCheckBox cb : leadCheckboxes) cb.setSelected(false);
        for (JTextField tf : textFields) tf.setText("");
        for (JCheckBox cb : checkBoxes) cb.setSelected(false);
    }

    private void saveDataToEMR() {
        String reportText = summaryArea.getText();
        if (reportText == null || reportText.trim().isEmpty()) {
            showError("No summary content to save.");
            return;
        }

        try {
            if (IAIMain.getTextAreaManager() == null || !IAIMain.getTextAreaManager().isReady()) {
                showError("Cannot save data: EMR connection is not ready.");
                return;
            }
            String stampedReport = String.format("\n< EKG Report - %s >\n%s", LocalDate.now().format(DateTimeFormatter.ISO_DATE), reportText.trim());
            IAIMain.getTextAreaManager().focusArea(5); // Target 'O>' area
            IAIMain.getTextAreaManager().insertLineIntoFocusedArea(stampedReport);
            clearAllFields();
        } catch (Exception e) {
            showError("An error occurred while saving to the EMR: " + e.getMessage());
        }
    }

    private void openReferenceFile() {
        try {
            File file = new File("src/main/resources/text/EKG_reference.odt").getAbsoluteFile();
            if (!file.exists()) {
                throw new IOException("Reference file not found at: " + file.getPath());
            }
            Desktop.getDesktop().open(file);
        } catch (Exception e) {
            showError("Could not open reference file: " + e.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Listener to update the summary whenever text fields are changed.
     */
    private class SummaryUpdateListener implements DocumentListener {
        @Override public void insertUpdate(DocumentEvent e) { updateSummary(); }
        @Override public void removeUpdate(DocumentEvent e) { updateSummary(); }
        @Override public void changedUpdate(DocumentEvent e) { updateSummary(); }
    }


}
