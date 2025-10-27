package com.emr.gds.soap;

import com.emr.gds.input.IAIMain;
import com.emr.gds.input.IAITextAreaManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Arrays;

/**
 * A Swing-based dialog for inputting and managing a patient's Past Medical History (PMH).
 */
public class EMRPMH extends JFrame {

    private final IAITextAreaManager textAreaManager;
    private final JTextArea selectedArea = createTextArea(6, 10);
    private final JTextArea outputArea = createTextArea(12, 50);
    private final JPanel checkBoxPanel = new JPanel(new GridLayout(0, 3, 6, 6));

    private final Map<String, String> abbrevMap = new HashMap<>();
    private final Map<String, JCheckBox> checkBoxes = new LinkedHashMap<>();
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

    public EMRPMH(IAITextAreaManager manager) {
        this.textAreaManager = manager;
        initialize();
    }

    private void initialize() {
        initAbbrevDatabase();
        initFrame();
        buildCheckBoxes();
        layoutUI();
        refreshSelectionSummary();
    }

    private void initFrame() {
        setTitle("EMR Past Medical History");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(820, 820);
        setLocationRelativeTo(null);
        selectedArea.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        outputArea.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
    }

    private void buildCheckBoxes() {
        for (String[] row : CONDITIONS) {
            for (String item : row) {
                if (item == null || item.isBlank()) continue;
                JCheckBox cb = new JCheckBox(item);
                cb.addItemListener(this::onCheckboxToggle);
                checkBoxes.put(item, cb);
                selectionMap.put(item, false);
                checkBoxPanel.add(cb);
            }
        }
    }

    private void layoutUI() {
        JPanel topPanel = new JPanel(new GridLayout(2, 1, 6, 6));
        topPanel.add(new JScrollPane(selectedArea));
        topPanel.add(new JScrollPane(outputArea));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(createButton("Family History", e -> openFamilyHistoryForm()));
        buttonPanel.add(createButton("Clear", e -> onClear()));
        buttonPanel.add(createButton("Generate Report", e -> onGenerateReport()));
        buttonPanel.add(createButton("Save & Quit", e -> onSaveAndQuit()));
        buttonPanel.add(createButton("Quit", e -> dispose()));

        setLayout(new BorderLayout(8, 8));
        add(topPanel, BorderLayout.NORTH);
        add(new JScrollPane(checkBoxPanel), BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void onCheckboxToggle(ItemEvent e) {
        JCheckBox source = (JCheckBox) e.getItemSelectable();
        selectionMap.put(source.getText(), e.getStateChange() == ItemEvent.SELECTED);
        refreshSelectionSummary();
    }

    private void openFamilyHistoryForm() {
        try {
            if (IAIMain.getTextAreaManager() != null && IAIMain.getTextAreaManager().isReady()) {
                SwingUtilities.invokeLater(() -> new EMRFMH(IAIMain.getTextAreaManager()).setVisible(true));
            } else {
                showError("The text area manager is not ready.");
            }
        } catch (Exception e) {
            showError("Failed to open Family History form: " + e.getMessage());
        }
    }

    private void onClear() {
        selectionMap.keySet().forEach(key -> selectionMap.put(key, false));
        checkBoxes.values().forEach(cb -> cb.setSelected(false));
        outputArea.setText("");
        refreshSelectionSummary();
    }

    private void onGenerateReport() {
        outputArea.setText(getCombinedText(true));
    }

    private void onSaveAndQuit() {
        if (textAreaManager == null) {
            showError("Cannot save: EMR connection not available.");
            return;
        }
        String report = expandAbbreviations(outputArea.getText());
        if (report.isBlank()) {
            showError("Nothing to save.");
            return;
        }
        textAreaManager.insertBlockIntoArea(IAITextAreaManager.AREA_PMH, report, true);
        dispose();
    }

    private void refreshSelectionSummary() {
        StringBuilder sb = new StringBuilder();
        selectionMap.forEach((key, value) -> {
            if (value) sb.append("    ▣ ").append(key).append('\n');
        });
        selectedArea.setText(sb.toString());
    }

    private String getCombinedText(boolean withMarks) {
        StringBuilder sb = new StringBuilder();
        for (String[] row : CONDITIONS) {
            for (String item : row) {
                if (item == null || item.isBlank()) continue;
                String mark = withMarks ? (selectionMap.get(item) ? "▣" : "□") : "";
                sb.append(String.format("    %-20s", mark + " " + item));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private void initAbbrevDatabase() {
        try {
            Path dbFile = Paths.get("app/db/abbreviations.db").toAbsolutePath();
            if (!Files.exists(dbFile)) return;
            String url = "jdbc:sqlite:" + dbFile;
            try (Connection conn = DriverManager.getConnection(url); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM abbreviations")) {
                while (rs.next()) {
                    abbrevMap.put(rs.getString("short"), rs.getString("full"));
                }
            }
        } catch (SQLException | HeadlessException e) {
            showError("Failed to initialize abbreviation database: " + e.getMessage());
        }
    }

    private String expandAbbreviations(String text) {
        return Arrays.stream(text.split("((?<= )|(?= ))"))
                .map(word -> {
                    String cleanWord = word.trim();
                    if (":cd".equals(cleanWord)) return LocalDate.now().format(DateTimeFormatter.ISO_DATE);
                    return cleanWord.startsWith(":") ? abbrevMap.getOrDefault(cleanWord.substring(1), word) : word;
                })
                .collect(java.util.stream.Collectors.joining());
    }

    private JTextArea createTextArea(int rows, int cols) {
        JTextArea ta = new JTextArea(rows, cols);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        return ta;
    }

    private JButton createButton(String text, java.awt.event.ActionListener listener) {
        JButton button = new JButton(text);
        button.addActionListener(listener);
        return button;
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new EMRPMH(null).setVisible(true));
    }
}
