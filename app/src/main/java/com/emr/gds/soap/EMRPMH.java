package com.emr.gds.soap;

import javax.swing.*;	

import com.emr.gds.input.IAITextAreaManager;

import java.awt.*;
import java.awt.event.ItemEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Compact EMR Past Medical History (PMH) dialog.
 */
public class EMRPMH extends JFrame {
    // UI Components
    private final IAITextAreaManager textAreaManager;
    private final JTextArea selectedArea = createTextArea(6, 10, true);
    // MODIFIED: outputArea is now editable by changing the last parameter to 'true'.
    private final JTextArea outputArea = createTextArea(12, 50, true);
    private final JPanel checkBoxPanel = new JPanel(new GridLayout(0, 3, 6, 6));

    // Data and Constants
    private final Map<String, String> abbrevMap = new HashMap<>();
    private final Map<String, JCheckBox> boxes = new LinkedHashMap<>();
    private final Map<String, Boolean> selectionMap = new LinkedHashMap<>();
    private static final String[][] CONDITIONS = {
            {"Dyslipidemia", "Hypertension", "Diabetes Mellitus"},
            {"Cancer", "Operation", "Thyroid Disease"},
            {"Asthma", "Pneumonia", "Tuberculosis"},
            {"GERD", "Hepatitis A / B", "Gout"},
            {"AMI", "Angina Pectoris", "Arrhythmia"},
            {"CVA", "Depression", "Cognitive Disorder"},
            {"Anxiety", "Hearing Loss", "Parkinson's Disease"},
            {"Allergy", "All denied allergies..."},
            {"Food", "Injection", "Medication"}
    };

    // Constructor
    public EMRPMH(IAITextAreaManager manager) {
        this.textAreaManager = manager;
        initialize();
    }

    private void initialize() {
        try {
            initAbbrevDatabase();
        } catch (Exception e) {
            error("Failed to initialize abbreviation database: " + e.getMessage());
        }
        initFrame();
        buildCheckBoxes();
        layoutUI();
        refreshAll();
    }

    // UI Initialization
    private void initFrame() {
        setTitle("EMR Past Medical History");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(820, 820);
        setLocationRelativeTo(null);
        setFonts();
    }

    private void setFonts() {
        selectedArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        outputArea.setFont(new Font("Consolas", Font.PLAIN, 12));
    }

    private void buildCheckBoxes() {
        for (String[] row : CONDITIONS) {
            for (String item : row) {
                if (item == null || item.isBlank()) continue;
                JCheckBox cb = new JCheckBox(item);
                cb.addItemListener(this::onToggle);
                boxes.put(item, cb);
                selectionMap.put(item, false);
                checkBoxPanel.add(cb);
            }
        }
    }

    private void layoutUI() {
        JPanel topText = new JPanel(new GridLayout(2, 1, 6, 6));
        topText.add(new JScrollPane(selectedArea));
        topText.add(new JScrollPane(outputArea));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(new JButton("Clear") {{ addActionListener(e -> onClear()); }});
        buttons.add(new JButton("Append Selection") {{ addActionListener(e -> onAppendSelection()); }});
        buttons.add(new JButton("Generate Report") {{ addActionListener(e -> onGenerateReport()); }});
        // MODIFIED: Added a "Save & Quit" button.
        buttons.add(new JButton("Save & Quit") {{ addActionListener(e -> onSaveAndQuit()); }});
        buttons.add(new JButton("Quit") {{ addActionListener(e -> dispose()); }});

        setLayout(new BorderLayout(8, 8));
        add(topText, BorderLayout.NORTH);
        add(new JScrollPane(checkBoxPanel), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
    }

    // Event Handlers
    private void onToggle(ItemEvent e) {
        JCheckBox src = (JCheckBox) e.getItemSelectable();
        selectionMap.put(src.getText(), e.getStateChange() == ItemEvent.SELECTED);
        refreshAll();
    }

    private void onClear() {
        selectionMap.keySet().forEach(k -> selectionMap.put(k, false));
        boxes.values().forEach(b -> b.setSelected(false));
        outputArea.setText("");
        refreshAll();
    }

    private void onAppendSelection() {
        String summary = selectedSummary();
        if (!summary.isBlank()) {
            outputArea.append("--- Selection Summary ---\n");
            outputArea.append(summary);
            outputArea.append("\n");
        }
    }

    /**
     * MODIFIED: This method now clears the outputArea before generating the report.
     * The button text has also been updated to "Generate Report" to be more descriptive.
     */
    private void onGenerateReport() {
        // MODIFIED: Clear the text area before generating new content.
        outputArea.setText("");

        final String originalText = getCombinedText();
        if (originalText.isBlank()) {
            return; // Nothing to generate
        }

        final String expandedText = expandAbbreviations(originalText).trim();

        if (!expandedText.isEmpty()) {
            // Set the text of the now-empty area.
            outputArea.setText(expandedText);
        }
    }
    
    /**
     * MODIFIED: New method to save the content of the editable outputArea and close the dialog.
     * This logic is based on your provided example and the original save functionality.
     */
    private void onSaveAndQuit() {
        if (textAreaManager == null) {
            error("TextAreaManager not available. Cannot save.");
            return;
        }

        final String originalText = outputArea.getText();
        if (originalText.isBlank()) {
            error("No text in the output area to save.");
            return;
        }

        final String expandedTextRaw = expandAbbreviations(originalText);
        final String expandedText = expandedTextRaw.trim();

        boolean expansionOccurred = !originalText.equals(expandedTextRaw);
        boolean proceed = !expansionOccurred;

        if (expandedText.isEmpty()) {
            error("Nothing to save after processing.");
            return;
        }

        if (expansionOccurred) {
            String message = "Original:\n" + originalText + "\n\nExpanded:\n" + expandedText + "\n\nApply changes and save?";
            int response = JOptionPane.showConfirmDialog(this, message, "Abbreviation Expansion", JOptionPane.OK_CANCEL_OPTION);
            proceed = (response == JOptionPane.OK_OPTION);
        }

        if (!proceed) return;

        try {
            textAreaManager.insertBlockIntoArea(IAITextAreaManager.AREA_PMH, expandedText, true);
            JOptionPane.showMessageDialog(this, "Past Medical History saved successfully.");
            dispose(); // The "Quit" part of the button name
        } catch (Exception ex) {
            error("Failed to save: " + ex.getMessage());
        }
    }


    // Helper Methods
    private static JTextArea createTextArea(int rows, int cols, boolean editable) {
        JTextArea ta = new JTextArea(rows, cols);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setEditable(editable);
        return ta;
    }

    private void refreshAll() {
        selectedArea.setText(selectedSummary());
    }

    private String selectedSummary() {
        StringBuilder sb = new StringBuilder();
        selectionMap.forEach((k, v) -> {
            if (v) sb.append("    ▣ ").append(k).append('\n');
        });
        return sb.toString();
    }

    private String getCombinedText() {
        StringBuilder block = new StringBuilder();
        String selectedText = selectedArea.getText().trim();
        if (!selectedText.isEmpty()) {
            block.append(selectedText).append("\n");
        }
        block.append("---------------------------\n");
        block.append(renderGrid(true));
        block.append("---------------------------\n");
        return block.toString();
    }

    private String renderGrid(boolean withMarks) {
        StringBuilder sb = new StringBuilder();
        for (String[] row : CONDITIONS) {
            for (String item : row) {
                if (item == null || item.isBlank()) continue;
                String mark = withMarks ? (isSelected(item) ? "▣" : "□") : "□";
                sb.append("    ").append(mark).append(' ').append(item);
                sb.append("\t");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private boolean isSelected(String item) {
        return Boolean.TRUE.equals(selectionMap.get(item));
    }

    private void error(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    // Abbreviation and Database Logic (Unchanged)
    private void initAbbrevDatabase() throws ClassNotFoundException, SQLException, IOException {
        Class.forName("org.sqlite.JDBC");
        Path dbFile = getDbPath("abbreviations.db");
        Files.createDirectories(dbFile.getParent());
        String url = "jdbc:sqlite:" + dbFile.toAbsolutePath();
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM abbreviations")) {
            while (rs.next()) {
                abbrevMap.put(rs.getString("short"), rs.getString("full"));
            }
        }
    }

    private Path getDbPath(String fileName) {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("gradlew")) && !Files.exists(p.resolve(".git"))) {
            p = p.getParent();
        }
        return (p != null) ? p.resolve("app").resolve("db").resolve(fileName) : Paths.get("").toAbsolutePath();
    }

    private String expandAbbreviations(String text) {
        StringBuilder sb = new StringBuilder();
        String[] words = text.split("(?<=\\s)|(?=\\s)");
        for (String word : words) {
            String cleanWord = word.trim();
            if (cleanWord.startsWith(":") && abbrevMap.containsKey(cleanWord.substring(1))) {
                sb.append(abbrevMap.get(cleanWord.substring(1)));
            } else if (":cd".equals(cleanWord)) {
                sb.append(LocalDate.now().format(DateTimeFormatter.ISO_DATE));
            } else {
                sb.append(word);
            }
        }
        return sb.toString();
    }

    // Main Method
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new EMRPMH(null).setVisible(true));
    }
}