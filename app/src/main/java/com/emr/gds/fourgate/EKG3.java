package com.emr.gds.fourgate;

import com.emr.gds.input.IAIMain;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A simplified JFrame for rapid EKG (Electrocardiogram) analysis.
 * Features categorized diagnosis checkboxes for easy selection and a simple
 * notes area, which combine into a formatted report.
 */
public class EKG3 extends JFrame {

    private JTextArea interpretationNotesArea;
    private JTextArea reportArea;
    private final List<JCheckBox> allDiagnosisCheckboxes = new ArrayList<>();

    public EKG3() {
        setTitle("Simple EKG Interpretation");
        setSize(1000, 700); // Adjusted size for simpler layout
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        initializeUI();
        setLocationRelativeTo(null); // Center the frame
    }

    private void initializeUI() {
        // Main Panels
        JPanel diagnosisPanel = createDiagnosisPanel();
        JPanel mainPanel = createMainPanel();
        JPanel southPanel = createSouthPanel();

        // Add panels to frame
        add(diagnosisPanel, BorderLayout.EAST);
        add(mainPanel, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);
    }

    /**
     * Creates the panel with categorized diagnosis checkboxes for easy selection.
     */
    private JPanel createDiagnosisPanel() {
        // This panel holds all the groups, stacked vertically
        JPanel mainDiagnosisPanel = new JPanel();
        mainDiagnosisPanel.setLayout(new BoxLayout(mainDiagnosisPanel, BoxLayout.Y_AXIS));
        mainDiagnosisPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Add categorized groups
        mainDiagnosisPanel.add(createDiagnosisGroup("Rhythm", new String[]{
                "Normal Sinus Rhythm", "Sinus Bradycardia", "Sinus Tachycardia",
                "Atrial Fibrillation", "Atrial Flutter", "Supraventricular Tachycardia (SVT)",
                "Junctional Rhythm", "Ventricular Tachycardia (VT)"
        }));

        mainDiagnosisPanel.add(createDiagnosisGroup("Conduction", new String[]{
                "Right Bundle Branch Block (RBBB)", "Left Bundle Branch Block (LBBB)",
                "Prolonged QT", "Wolff-Parkinson-White (WPW)",
                "1st Degree AV Block", "2nd Degree AV Block (Mobitz I)",
                "2nd Degree AV Block (Mobitz II)", "3rd Degree AV Block"
        }));

        mainDiagnosisPanel.add(createDiagnosisGroup("Ischemia / Infarction", new String[]{
                "ST Elevation", "ST Depression", "Non-specific ST-T changes",
                "Anterior Wall Ischemia / STEMI", "Inferior Wall Ischemia / STEMI",
                "Lateral Wall Ischemia / STEMI", "Atypical T wave changes"
        }));

        mainDiagnosisPanel.add(createDiagnosisGroup("Hypertrophy & Other", new String[]{
                "Left Ventricular Hypertrophy (LVH)", "Right Ventricular Hypertrophy (RVH)",
                "Left Atrial Enlargement (LAE)", "Right Atrial Enlargement (RAE)",
                "Premature Ventricular Contraction (PVC)", "Premature Atrial Contraction (PAC)",
                "Poor R Wave Progression", "Normal ECG"
        }));

        // Put the stacked groups into a scroll pane
        JScrollPane scrollPane = new JScrollPane(mainDiagnosisPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        // Wrapper panel to set a preferred width and title
        JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.setPreferredSize(new Dimension(340, 0));
        wrapperPanel.setBorder(BorderFactory.createTitledBorder("EKG Findings"));
        wrapperPanel.add(scrollPane, BorderLayout.CENTER);

        return wrapperPanel;
    }

    /**
     * Helper method to create a single titled group of checkboxes.
     */
    private JPanel createDiagnosisGroup(String title, String[] items) {
        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 5)); // Vertical layout, 1 column
        panel.setBorder(BorderFactory.createTitledBorder(title));

        for (String item : items) {
            JCheckBox cb = new JCheckBox(item);
            cb.addItemListener(e -> updateReport()); // Add listener to update report
            allDiagnosisCheckboxes.add(cb); // Add to master list
            panel.add(cb);
        }
        return panel;
    }

    /**
     * Creates the central panel with the "Notes" input and "Report" output.
     */
    private JPanel createMainPanel() {
        // 1. Interpretation Notes (Simple Input)
        interpretationNotesArea = new JTextArea();
        interpretationNotesArea.setLineWrap(true);
        interpretationNotesArea.setWrapStyleWord(true);
        interpretationNotesArea.setFont(new Font("SansSerif", Font.PLAIN, 14));
        interpretationNotesArea.getDocument().addDocumentListener(new ReportUpdateListener());
        JScrollPane notesScrollPane = new JScrollPane(interpretationNotesArea);
        notesScrollPane.setBorder(BorderFactory.createTitledBorder("Interpretation Notes (Your Input)"));

        // 2. Generated Report (Output)
        reportArea = new JTextArea();
        reportArea.setLineWrap(true);
        reportArea.setWrapStyleWord(true);
        reportArea.setEditable(false); // Output only
        reportArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        reportArea.setBackground(new Color(245, 245, 245)); // Light gray background
        JScrollPane reportScrollPane = new JScrollPane(reportArea);
        reportScrollPane.setBorder(BorderFactory.createTitledBorder("Generated Report (Auto-updates)"));

        // 3. Split Pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                notesScrollPane, reportScrollPane);
        splitPane.setResizeWeight(0.45); // Give notes slightly less than 50%
        splitPane.setDividerLocation(300);
        splitPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        return new JPanel(new BorderLayout()) {{
            add(splitPane, BorderLayout.CENTER);
        }};
    }

    /**
     * Creates the bottom panel with action buttons.
     */
    private JPanel createSouthPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 10, 5, 10));

        JButton clearButton = new JButton("Clear All");
        JButton saveButton = new JButton("Save to EMR");
        JButton quitButton = new JButton("Quit");

        clearButton.addActionListener(e -> clearAllFields());
        saveButton.addActionListener(e -> saveDataToEMR());
        quitButton.addActionListener(e -> dispose());

        panel.add(clearButton);
        panel.add(saveButton);
        panel.add(quitButton);
        return panel;
    }

    /**
     * Updates the report area based on selected checkboxes and notes.
     */
    private void updateReport() {
        StringBuilder sb = new StringBuilder();

        // Get selected diagnoses
        List<String> selectedFindings = allDiagnosisCheckboxes.stream()
                .filter(JCheckBox::isSelected)
                .map(JCheckBox::getText)
                .collect(Collectors.toList());

        sb.append("EKG INTERPRETATION REPORT\n");
        sb.append("===========================\n\n");

        sb.append("FINDINGS:\n");
        if (selectedFindings.isEmpty()) {
            sb.append("- No significant findings selected.\n");
        } else {
            for (String finding : selectedFindings) {
                sb.append("- ").append(finding).append("\n");
            }
        }

        sb.append("\nINTERPRETATION NOTES:\n");
        String notes = interpretationNotesArea.getText().trim();
        if (notes.isEmpty()) {
            sb.append("N/A\n");
        } else {
            sb.append(notes).append("\n");
        }

        reportArea.setText(sb.toString());
        reportArea.setCaretPosition(0); // Scroll to top
    }

    /**
     * Clears all inputs and resets the form.
     */
    private void clearAllFields() {
        interpretationNotesArea.setText("");
        // Deselect all checkboxes, which will trigger updateReport via its listener
        for (JCheckBox cb : allDiagnosisCheckboxes) {
            cb.setSelected(false);
        }
        // updateReport() is called by the checkbox listeners,
        // but we call it again to ensure the notes area is cleared in the report.
        updateReport();
    }

    /**
     * Saves the generated report to the EMR.
     */
    private void saveDataToEMR() {
        String reportText = reportArea.getText();
        if (reportText == null || reportText.trim().isEmpty() ||
                reportText.contains("No significant findings selected")) {
            showError("No findings or notes to save. Please complete the interpretation.");
            return;
        }

        try {
            if (IAIMain.getTextAreaManager() == null || !IAIMain.getTextAreaManager().isReady()) {
                showError("Cannot save data: EMR connection is not ready.");
                return;
            }

            // Add a timestamp, similar to the original class
            String stampedReport = String.format("\n< EKG Report - %s >\n%s",
                    LocalDate.now().format(DateTimeFormatter.ISO_DATE),
                    reportText.trim());

            IAIMain.getTextAreaManager().focusArea(5); // Target 'O>' area (as per original)
            IAIMain.getTextAreaManager().insertLineIntoFocusedArea(stampedReport);

            JOptionPane.showMessageDialog(this, "EKG Report saved successfully.",
                    "Save Complete", JOptionPane.INFORMATION_MESSAGE);
            clearAllFields();
            dispose(); // Close window after successful save

        } catch (Exception e) {
            showError("An error occurred while saving to the EMR: " + e.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Listener to update the report whenever text notes are changed.
     */
    private class ReportUpdateListener implements DocumentListener {
        @Override
        public void insertUpdate(DocumentEvent e) {
            updateReport();
        }
        @Override
        public void removeUpdate(DocumentEvent e) {
            updateReport();
        }
        @Override
        public void changedUpdate(DocumentEvent e) {
            updateReport();
        }
    }

    // Main method for testing
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Set a modern Look and Feel
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new EKG3().setVisible(true);
        });
    }
}