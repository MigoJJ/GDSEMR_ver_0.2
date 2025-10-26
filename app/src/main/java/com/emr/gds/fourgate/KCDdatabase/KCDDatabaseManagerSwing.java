package com.emr.gds.fourgate.KCDdatabase;

import com.emr.gds.input.IAIMain;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * A Swing-based GUI application for managing a KCD (Korean Classification of Diseases) database.
 * Provides functionalities for viewing, searching, adding, updating, and deleting records.
 */
public class KCDDatabaseManagerSwing extends JFrame {

    private static final String DB_PATH = "/home/migowj/git/GDSEMR_ver_0.2/app/src/main/resources/database/kcd_database.db";
    public static final String JDBC_URL = "jdbc:sqlite:" + DB_PATH;
    private static final DateTimeFormatter ISO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private JTable table;
    private DefaultTableModel tableModel;
    private TableRowSorter<DefaultTableModel> sorter;
    private JTextField searchField;
    private JComboBox<String> searchColumnCombo;
    private JButton addButton, updateButton, deleteButton, refreshButton, copyButton, saveToEmrButton;
    private JLabel statusLabel;

    private final String[] columnNames = {
            "Classification", "Disease Code", "Check Field",
            "Korean Name", "English Name", "Note"
    };

    public KCDDatabaseManagerSwing() {
        initializeFrame();
        initializeComponents();
        createLayout();
        setupEventHandlers();
        loadInitialData();
    }

    private void initializeFrame() {
        setTitle("KCD Database Manager");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);
    }

    private void initializeComponents() {
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        searchField = new JTextField(20);
        searchColumnCombo = new JComboBox<>(new String[]{"All Columns", "Classification", "Disease Code", "Check Field", "Korean Name", "English Name", "Note"});

        addButton = new JButton("Add");
        updateButton = new JButton("Update");
        deleteButton = new JButton("Delete");
        refreshButton = new JButton("Refresh");
        copyButton = new JButton("Copy");
        saveToEmrButton = new JButton("Save to EMR");

        statusLabel = new JLabel("Ready");
    }

    private void createLayout() {
        setLayout(new BorderLayout(5, 5));
        add(createSearchPanel(), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(createButtonPanel(), BorderLayout.SOUTH);
    }

    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(new JLabel("Search:"));
        panel.add(searchField);
        panel.add(searchColumnCombo);
        return panel;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(addButton);
        panel.add(updateButton);
        panel.add(deleteButton);
        panel.add(refreshButton);
        panel.add(copyButton);
        panel.add(saveToEmrButton);
        panel.add(statusLabel);
        return panel;
    }

    private void setupEventHandlers() {
        // Action listeners for buttons
        addButton.addActionListener(e -> showAddDialog());
        updateButton.addActionListener(e -> showUpdateDialog());
        deleteButton.addActionListener(e -> deleteSelectedRecord());
        refreshButton.addActionListener(e -> loadInitialData());
        copyButton.addActionListener(e -> copySelectedToClipboard());
        saveToEmrButton.addActionListener(e -> saveSelectedToEMR());

        // Search field listener
        searchField.addActionListener(e -> performSearch());

        // Table selection listener
        table.getSelectionModel().addListSelectionListener(e -> {
            boolean rowSelected = table.getSelectedRow() != -1;
            updateButton.setEnabled(rowSelected);
            deleteButton.setEnabled(rowSelected);
            copyButton.setEnabled(rowSelected);
            saveToEmrButton.setEnabled(rowSelected);
        });
    }

    private void loadInitialData() {
        SwingWorker<List<KCDRecord>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<KCDRecord> doInBackground() throws Exception {
                updateStatus("Loading data...");
                return DatabaseManager.getAllRecords();
            }

            @Override
            protected void done() {
                try {
                    List<KCDRecord> records = get();
                    tableModel.setRowCount(0);
                    for (KCDRecord record : records) {
                        tableModel.addRow(record.toArray());
                    }
                    updateStatus("Loaded " + records.size() + " records.");
                } catch (Exception e) {
                    showErrorDialog("Database Error", "Failed to load data: " + e.getMessage());
                    updateStatus("Error loading data.");
                }
            }
        };
        worker.execute();
    }

    private void performSearch() {
        String text = searchField.getText();
        int columnIndex = searchColumnCombo.getSelectedIndex() - 1;

        if (text.trim().length() == 0) {
            sorter.setRowFilter(null);
        } else {
            try {
                if (columnIndex < 0) { // Search all columns
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text));
                } else {
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text, columnIndex));
                }
            } catch (java.util.regex.PatternSyntaxException e) {
                updateStatus("Invalid search pattern.");
            }
        }
    }

    private void showAddDialog() {
        KCDRecordDialog dialog = new KCDRecordDialog(this, "Add New Record", null);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            try {
                DatabaseManager.addRecord(dialog.getRecord());
                loadInitialData();
            } catch (SQLException e) {
                showErrorDialog("Database Error", "Could not add record: " + e.getMessage());
            }
        }
    }

    private void showUpdateDialog() {
        KCDRecord selectedRecord = getSelectedRecord();
        if (selectedRecord == null) return;

        KCDRecordDialog dialog = new KCDRecordDialog(this, "Update Record", selectedRecord);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            try {
                DatabaseManager.updateRecord(selectedRecord.getDiseaseCode(), dialog.getRecord());
                loadInitialData();
            } catch (SQLException e) {
                showErrorDialog("Database Error", "Could not update record: " + e.getMessage());
            }
        }
    }

    private void deleteSelectedRecord() {
        KCDRecord selectedRecord = getSelectedRecord();
        if (selectedRecord == null) return;

        int response = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete this record?", "Confirm Deletion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (response == JOptionPane.YES_OPTION) {
            try {
                DatabaseManager.deleteRecord(selectedRecord.getDiseaseCode());
                loadInitialData();
            } catch (SQLException e) {
                showErrorDialog("Database Error", "Could not delete record: " + e.getMessage());
            }
        }
    }

    private void copySelectedToClipboard() {
        KCDRecord selectedRecord = getSelectedRecord();
        if (selectedRecord == null) return;

        StringSelection stringSelection = new StringSelection(selectedRecord.toFormattedString());
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
        updateStatus("Record copied to clipboard.");
    }

    private void saveSelectedToEMR() {
        KCDRecord selectedRecord = getSelectedRecord();
        if (selectedRecord == null) return;

        try {
            String timestamp = LocalDate.now().format(ISO_DATE_FORMAT);
            String emrEntry = String.format("\n< KCD > %s\n%s", timestamp, selectedRecord.toEMRFormat());

            IAIMain.getTextAreaManager().focusArea(7); // Target 'A>' area
            IAIMain.getTextAreaManager().insertLineIntoFocusedArea("\t" + emrEntry);
            updateStatus("Record saved to EMR.");
        } catch (Exception e) {
            showErrorDialog("EMR Save Error", "Error saving to EMR: " + e.getMessage());
        }
    }

    private KCDRecord getSelectedRecord() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) return null;

        int modelRow = table.convertRowIndexToModel(selectedRow);
        return new KCDRecord(
                (String) tableModel.getValueAt(modelRow, 0),
                (String) tableModel.getValueAt(modelRow, 1),
                (String) tableModel.getValueAt(modelRow, 2),
                (String) tableModel.getValueAt(modelRow, 3),
                (String) tableModel.getValueAt(modelRow, 4),
                (String) tableModel.getValueAt(modelRow, 5)
        );
    }

    private void updateStatus(String message) {
        statusLabel.setText(message);
    }

    private void showErrorDialog(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(KCDDatabaseManagerSwing::new);
    }
}

/**
 * A data class representing a single record in the KCD database.
 */
class KCDRecord {
    private final String classification, diseaseCode, checkField, koreanName, englishName, note;

    public KCDRecord(String classification, String diseaseCode, String checkField, String koreanName, String englishName, String note) {
        this.classification = classification;
        this.diseaseCode = diseaseCode;
        this.checkField = checkField;
        this.koreanName = koreanName;
        this.englishName = englishName;
        this.note = note;
    }

    public String getClassification() { return classification; }
    public String getDiseaseCode() { return diseaseCode; }
    public String getCheckField() { return checkField; }
    public String getKoreanName() { return koreanName; }
    public String getEnglishName() { return englishName; }
    public String getNote() { return note; }

    public Object[] toArray() {
        return new Object[]{classification, diseaseCode, checkField, koreanName, englishName, note};
    }

    public String toFormattedString() {
        return String.format("[%s] %s (%s)", diseaseCode, koreanName, englishName);
    }

    public String toEMRFormat() {
        return toFormattedString();
    }
}

/**
 * A manager class for handling all database operations for KCD records.
 */
class DatabaseManager {
    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(KCDDatabaseManagerSwing.JDBC_URL);
    }

    public static List<KCDRecord> getAllRecords() throws SQLException {
        List<KCDRecord> records = new ArrayList<>();
        String sql = "SELECT * FROM kcd_codes ORDER BY disease_code";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                records.add(new KCDRecord(
                        rs.getString("classification"),
                        rs.getString("disease_code"),
                        rs.getString("check_field"),
                        rs.getString("korean_name"),
                        rs.getString("english_name"),
                        rs.getString("note")
                ));
            }
        }
        return records;
    }

    public static void addRecord(KCDRecord record) throws SQLException {
        String sql = "INSERT INTO kcd_codes(classification, disease_code, check_field, korean_name, english_name, note) VALUES(?,?,?,?,?,?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, record.getClassification());
            pstmt.setString(2, record.getDiseaseCode());
            pstmt.setString(3, record.getCheckField());
            pstmt.setString(4, record.getKoreanName());
            pstmt.setString(5, record.getEnglishName());
            pstmt.setString(6, record.getNote());
            pstmt.executeUpdate();
        }
    }

    public static void updateRecord(String originalDiseaseCode, KCDRecord record) throws SQLException {
        String sql = "UPDATE kcd_codes SET classification=?, disease_code=?, check_field=?, korean_name=?, english_name=?, note=? WHERE disease_code=?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, record.getClassification());
            pstmt.setString(2, record.getDiseaseCode());
            pstmt.setString(3, record.getCheckField());
            pstmt.setString(4, record.getKoreanName());
            pstmt.setString(5, record.getEnglishName());
            pstmt.setString(6, record.getNote());
            pstmt.setString(7, originalDiseaseCode);
            pstmt.executeUpdate();
        }
    }

    public static void deleteRecord(String diseaseCode) throws SQLException {
        String sql = "DELETE FROM kcd_codes WHERE disease_code = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, diseaseCode);
            pstmt.executeUpdate();
        }
    }
}

/**
 * A JDialog for adding or updating a KCDRecord.
 */
class KCDRecordDialog extends JDialog {
    private final JTextField[] fields;
    private final JTextArea noteTextArea;
    private boolean confirmed = false;

    public KCDRecordDialog(Frame parent, String title, KCDRecord initialData) {
        super(parent, title, true);
        setSize(500, 400);
        setLocationRelativeTo(parent);

        String[] labels = {"Classification:", "Disease Code:", "Check Field:", "Korean Name:", "English Name:", "Note:"};
        fields = new JTextField[labels.length - 1];
        noteTextArea = new JTextArea(3, 20);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        for (int i = 0; i < labels.length; i++) {
            gbc.gridx = 0;
            gbc.gridy = i;
            panel.add(new JLabel(labels[i]), gbc);

            gbc.gridx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            if (i < fields.length) {
                fields[i] = new JTextField(20);
                panel.add(fields[i], gbc);
            } else {
                panel.add(new JScrollPane(noteTextArea), gbc);
            }
        }

        if (initialData != null) {
            fields[0].setText(initialData.getClassification());
            fields[1].setText(initialData.getDiseaseCode());
            fields[2].setText(initialData.getCheckField());
            fields[3].setText(initialData.getKoreanName());
            fields[4].setText(initialData.getEnglishName());
            noteTextArea.setText(initialData.getNote());
        }

        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> {
            confirmed = true;
            dispose();
        });

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        add(panel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public KCDRecord getRecord() {
        return new KCDRecord(
                fields[0].getText(),
                fields[1].getText(),
                fields[2].getText(),
                fields[3].getText(),
                fields[4].getText(),
                noteTextArea.getText()
        );
    }
}