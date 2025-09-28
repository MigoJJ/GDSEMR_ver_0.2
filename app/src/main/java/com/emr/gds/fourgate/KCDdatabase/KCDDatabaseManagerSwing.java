package com.emr.gds.fourgate.KCDdatabase;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import com.emr.gds.input.IAIMain;

import javax.swing.Timer;
import javax.swing.RowFilter;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class KCDDatabaseManagerSwing extends JFrame {
    private static final String DB_PATH = "/home/migowj/git/GDSEMR_ver_0.2/app/src/main/resources/database/kcd_database.db";
    private static final String JDBC_URL = "jdbc:sqlite:" + DB_PATH;
    private static final DateTimeFormatter ISO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int EMR_TARGET_AREA = 7; // Unused currently

    private static final Color PRIMARY_COLOR = new Color(0x4FC3F7);
    private static final Color PRIMARY_HOVER = new Color(0x29B6F6);
    private static final Color SUCCESS_COLOR = new Color(0x4CAF50);
    private static final Color INFO_COLOR = new Color(0x2196F3);
    private static final Color WARNING_COLOR = new Color(0xFF9800);
    private static final Color DANGER_COLOR = new Color(0xF44336);
    private static final Color SECONDARY_COLOR = new Color(0x37474F);

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

    private final int[] columnWidths = {100, 120, 90, 260, 260, 350};

    public KCDDatabaseManagerSwing() {
        initializeFrame();
        initializeComponents();
        createLayout();
        setupEventHandlers();
        installKeyboardShortcuts();
        loadInitialData();
        setVisible(true);
    }

    private void initializeFrame() {
        setTitle("KCD Database Manager - Electronic Medical Records Integration");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);

        // Uncomment below if you add an icon file path
        // try {
        //     setIconImage(Toolkit.getDefaultToolkit().getImage("icon.png"));
        // } catch (Exception e) {
        //     // Icon not found, continue without it
        // }
    }

    private void initializeComponents() {
        initializeTable();
        initializeSearchComponents();
        initializeButtons();
        initializeStatusBar();
    }

    private void initializeTable() {
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.getTableHeader().setReorderingAllowed(false);

        for (int i = 0; i < columnNames.length && i < columnWidths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(columnWidths[i]);
        }

        sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);
    }

    private void initializeSearchComponents() {
        searchField = new JTextField(20);
        searchField.setToolTipText("Enter search term and press Enter");

        String[] searchOptions = {
            "All Columns", "Classification", "Disease Code", "Check Field",
            "Korean Name", "English Name", "Note"
        };
        searchColumnCombo = new JComboBox<>(searchOptions);
        searchColumnCombo.setToolTipText("Select column to search in");
    }

    private void initializeButtons() {
        addButton = createStyledButton("Add Record", SUCCESS_COLOR, "Add new KCD record (Ctrl+N)");
        updateButton = createStyledButton("Update Record", INFO_COLOR, "Update selected record (Ctrl+U)");
        deleteButton = createStyledButton("Delete Record", DANGER_COLOR, "Delete selected record (Delete)");
        refreshButton = createStyledButton("Refresh", PRIMARY_COLOR, "Refresh data from database (F5)");

        copyButton = createStyledButton("Copy", SECONDARY_COLOR, "Copy selected row to clipboard (Ctrl+C)");
        saveToEmrButton = createStyledButton("Save to EMR", WARNING_COLOR, "Save selected record to EMR (Ctrl+S)");

        updateButton.setEnabled(false);
        deleteButton.setEnabled(false);
        copyButton.setEnabled(false);
        saveToEmrButton.setEnabled(false);
    }

    private void initializeStatusBar() {
        statusLabel = new JLabel("Ready - Database connection established");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 15, 5, 10));
        statusLabel.setFont(new Font("Malgun Gothic", Font.BOLD, 12));
        statusLabel.setForeground(new Color(0x333333));
    }

    private JButton createStyledButton(String text, Color backgroundColor, String tooltip) {
    	this.revalidate();
    	this.repaint();

        JButton button = new JButton(text);
        button.setFont(new Font("Malgun Gothic", Font.BOLD, 13));
        button.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        button.setFocusPainted(false);
        button.setToolTipText(tooltip);

//        button.setBackground(backgroundColor);
        button.setForeground(Color.WHITE);
//        button.setOpaque(true);
     // button.setBackground(backgroundColor);
     // button.setOpaque(true);
     button.setForeground(Color.BLACK);


        Color hoverColor = backgroundColor.darker();
        button.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { button.setBackground(hoverColor); }
            public void mouseExited(MouseEvent e) { button.setBackground(backgroundColor); }
        });

        return button;
    }

    private void createLayout() {
        setLayout(new BorderLayout(5, 5));
        add(createSearchPanel(), BorderLayout.NORTH);
        add(createTablePanel(), BorderLayout.CENTER);
        add(createButtonPanel(), BorderLayout.SOUTH);
    }

    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        panel.setBorder(BorderFactory.createTitledBorder("Search & Filter"));

        panel.add(new JLabel("Search in:"));
        panel.add(searchColumnCombo);
        panel.add(new JLabel("Term:"));
        panel.add(searchField);

        JButton clearButton = new JButton("Clear");
        clearButton.setToolTipText("Clear search filter");
        clearButton.addActionListener(e -> clearSearch());
        panel.add(clearButton);

        return panel;
    }

    private JScrollPane createTablePanel() {
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createTitledBorder("KCD Records Database"));
        scrollPane.setPreferredSize(new Dimension(1150, 600));
        return scrollPane;
    }

    private JPanel createButtonPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.add(statusLabel);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        buttonPanel.add(copyButton);
        buttonPanel.add(saveToEmrButton);
        buttonPanel.add(Box.createHorizontalStrut(15));
        buttonPanel.add(addButton);
        buttonPanel.add(updateButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(Box.createHorizontalStrut(10));
        buttonPanel.add(refreshButton);

        mainPanel.add(statusPanel, BorderLayout.WEST);
        mainPanel.add(buttonPanel, BorderLayout.EAST);

        return mainPanel;
    }

    private void setupEventHandlers() {
        setupTableEventHandlers();
        setupSearchEventHandlers();
        setupButtonEventHandlers();
    }

    private void setupTableEventHandlers() {
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                boolean hasSelection = table.getSelectedRow() != -1;
                updateButton.setEnabled(hasSelection);
                deleteButton.setEnabled(hasSelection);
                copyButton.setEnabled(hasSelection);
                saveToEmrButton.setEnabled(hasSelection);
            }
        });

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() != -1) {
                    showRecordDetails();
                }
            }
        });
    }

    private void setupSearchEventHandlers() {
        searchField.addActionListener(e -> performSearch());
        searchColumnCombo.addActionListener(e -> performSearch());

        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                performSearch();
            }
        });
    }

    private void setupButtonEventHandlers() {
        addButton.addActionListener(e -> showAddDialog());
        updateButton.addActionListener(e -> showUpdateDialog());
        deleteButton.addActionListener(e -> deleteSelectedRecord());
        refreshButton.addActionListener(e -> loadInitialData());
        copyButton.addActionListener(e -> copySelectedToClipboard());
        saveToEmrButton.addActionListener(e -> saveSelectedToEMR());
    }

    private void installKeyboardShortcuts() {
        JRootPane rootPane = getRootPane();
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPane.getActionMap();

        addShortcut(inputMap, actionMap, "ctrl N", "addRecord", e -> showAddDialog());
        addShortcut(inputMap, actionMap, "ctrl U", "updateRecord", e -> showUpdateDialog());
        addShortcut(inputMap, actionMap, "DELETE", "deleteRecord", e -> deleteSelectedRecord());
        addShortcut(inputMap, actionMap, "F5", "refresh", e -> loadInitialData());
        addShortcut(inputMap, actionMap, "ctrl C", "copyRow", e -> {
            if (copyButton.isEnabled()) copySelectedToClipboard();
        });
        addShortcut(inputMap, actionMap, "ctrl S", "saveToEmr", e -> {
            if (saveToEmrButton.isEnabled()) saveSelectedToEMR();
        });
    }

    private void addShortcut(InputMap inputMap, ActionMap actionMap, String keyStroke,
                             String actionName, ActionListener action) {
        inputMap.put(KeyStroke.getKeyStroke(keyStroke), actionName);
        actionMap.put(actionName, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.actionPerformed(e);
            }
        });
    }

    private void loadInitialData() {
        SwingUtilities.invokeLater(() -> {
            try {
                updateStatus("Loading data from database...");
                tableModel.setRowCount(0);
                List<KCDRecord> records = DatabaseManager.getAllRecords();

                for (KCDRecord record : records) {
                    tableModel.addRow(record.toArray());
                }

                updateStatus("Loaded " + records.size() + " records successfully");
            } catch (Exception e) {
                updateStatus("Error loading data: " + e.getMessage());
                showErrorDialog("Database Error", "Failed to load data: " + e.getMessage());
            }
        });
    }

    private void performSearch() {
        String searchTerm = searchField.getText().trim();
        String selectedColumn = (String) searchColumnCombo.getSelectedItem();

        try {
            if (searchTerm.isEmpty()) {
                sorter.setRowFilter(null);
                updateStatus("Showing all records");
                return;
            }

            RowFilter<DefaultTableModel, Object> filter;

            if ("All Columns".equals(selectedColumn)) {
                filter = RowFilter.regexFilter("(?i)" + searchTerm);
            } else {
                int columnIndex = getColumnIndex(selectedColumn);
                if (columnIndex != -1) {
                    filter = RowFilter.regexFilter("(?i)" + searchTerm, columnIndex);
                } else {
                    updateStatus("Invalid search column");
                    return;
                }
            }

            sorter.setRowFilter(filter);
            int matchCount = table.getRowCount();
            updateStatus("Found " + matchCount + " matching records for '" + searchTerm + "'");

        } catch (Exception e) {
            showErrorDialog("Search Error", "Error performing search: " + e.getMessage());
            updateStatus("Search error occurred");
        }
    }

    private void clearSearch() {
        searchField.setText("");
        searchColumnCombo.setSelectedIndex(0);
        sorter.setRowFilter(null);
        updateStatus("Search cleared - showing all records");
    }

    private int getColumnIndex(String columnName) {
        for (int i = 0; i < columnNames.length; i++) {
            if (columnNames[i].equals(columnName)) {
                return i;
            }
        }
        return -1;
    }

    private void showAddDialog() {
        KCDRecordDialog dialog = new KCDRecordDialog(this, "Add New KCD Record", null);
        dialog.setVisible(true);

        if (dialog.isConfirmed()) {
            try {
                KCDRecord record = dialog.getRecord();
                if (DatabaseManager.addRecord(record)) {
                    loadInitialData();
                    updateStatus("Record added successfully: " + record.getDiseaseCode());
                } else {
                    showErrorDialog("Add Failed", "Failed to add record to database");
                }
            } catch (Exception e) {
                showErrorDialog("Add Error", "Error adding record: " + e.getMessage());
            }
        }
    }

    private void showUpdateDialog() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) return;

        try {
            KCDRecord currentRecord = getSelectedRecord();
            if (currentRecord == null) return;

            KCDRecordDialog dialog = new KCDRecordDialog(this, "Update KCD Record", currentRecord);
            dialog.setVisible(true);

            if (dialog.isConfirmed()) {
                KCDRecord updatedRecord = dialog.getRecord();
                if (DatabaseManager.updateRecord(currentRecord.getDiseaseCode(), updatedRecord)) {
                    loadInitialData();
                    updateStatus("Record updated successfully: " + updatedRecord.getDiseaseCode());
                } else {
                    showErrorDialog("Update Failed", "Failed to update record in database");
                }
            }
        } catch (Exception e) {
            showErrorDialog("Update Error", "Error updating record: " + e.getMessage());
        }
    }

    private void deleteSelectedRecord() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) return;

        try {
            KCDRecord record = getSelectedRecord();
            if (record == null) return;

            String message = String.format(
                "Are you sure you want to delete this record?\n\n" +
                "Disease Code: %s\n" +
                "Korean Name: %s\n" +
                "English Name: %s\n\n" +
                "This action cannot be undone.",
                record.getDiseaseCode(),
                record.getKoreanName(),
                record.getEnglishName()
            );

            int result = JOptionPane.showConfirmDialog(
                this, message, "Confirm Delete",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE
            );

            if (result == JOptionPane.YES_OPTION) {
                if (DatabaseManager.deleteRecord(record.getDiseaseCode())) {
                    loadInitialData();
                    updateStatus("Record deleted successfully: " + record.getDiseaseCode());
                } else {
                    showErrorDialog("Delete Failed", "Failed to delete record from database");
                }
            }
        } catch (Exception e) {
            showErrorDialog("Delete Error", "Error deleting record: " + e.getMessage());
        }
    }

    private void copySelectedToClipboard() {
        try {
            KCDRecord record = getSelectedRecord();
            if (record == null) {
                showInfoDialog("Copy", "No record selected");
                return;
            }

            String formattedText = record.toFormattedString();
            StringSelection selection = new StringSelection(formattedText);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);

            updateStatus("Record copied to clipboard: " + record.getDiseaseCode());
        } catch (Exception e) {
            showErrorDialog("Copy Error", "Error copying to clipboard: " + e.getMessage());
        }
    }

    private void saveSelectedToEMR() {
        try {
            KCDRecord record = getSelectedRecord();
            if (record == null) {
                showInfoDialog("Save to EMR", "No record selected");
                return;
            }


            String timestamp = LocalDate.now().format(ISO_DATE_FORMAT);
            String emrEntry = String.format("\n< KCD > %s\n%s", timestamp, record.toEMRFormat());

            // Use the agreed-upon target index and insert the formatted text.
            IAIMain.getTextAreaManager().focusArea(7);
            IAIMain.getTextAreaManager().insertLineIntoFocusedArea("\t" + emrEntry);
            
            // Clear fields after a successful save.

        } catch (Exception e) {
            showErrorDialog("EMR Save Error", "Error saving to EMR: " + e.getMessage());
        }
    }


    private void saveToEMR(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        // Target O> index = 5
        IAIMain.getTextAreaManager().focusArea(7);
        IAIMain.getTextAreaManager().insertLineIntoFocusedArea("\t" + text);
    }
    
   

    private void showEMRPreview(String emrEntry, KCDRecord record) {
        JTextArea textArea = new JTextArea(emrEntry);
        textArea.setEditable(false);
        textArea.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        textArea.setRows(10);
        textArea.setColumns(50);

        JScrollPane scrollPane = new JScrollPane(textArea);

        int result = JOptionPane.showConfirmDialog(
            this,
            scrollPane,
            "Save to EMR - Preview",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.INFORMATION_MESSAGE
        );

        if (result == JOptionPane.OK_OPTION) {
            updateStatus("Would save to EMR: " + record.getDiseaseCode());
        }
    }

    private KCDRecord getSelectedRecord() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) return null;

        int modelRow = table.convertRowIndexToModel(selectedRow);
        return new KCDRecord(
            getTableValue(modelRow, 0),
            getTableValue(modelRow, 1),
            getTableValue(modelRow, 2),
            getTableValue(modelRow, 3),
            getTableValue(modelRow, 4),
            getTableValue(modelRow, 5)
        );
    }

    private String getTableValue(int row, int column) {
        Object value = tableModel.getValueAt(row, column);
        return value == null ? "" : value.toString().trim();
    }

    private void showRecordDetails() {
        KCDRecord record = getSelectedRecord();
        if (record == null) return;

        JTextArea textArea = new JTextArea(record.toDetailedString());
        textArea.setEditable(false);
        textArea.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(600, 300));

        JOptionPane.showMessageDialog(
            this, scrollPane, "Record Details - " + record.getDiseaseCode(),
            JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void updateStatus(String message) {
        statusLabel.setText(message);
        if (!message.toLowerCase().contains("error")) {
            Timer timer = new Timer(10000, e -> statusLabel.setText("Ready"));
            timer.setRepeats(false);
            timer.start();
        }
    }

    private void showErrorDialog(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    private void showInfoDialog(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.INFORMATION_MESSAGE);
    }


    private static class KCDRecord {
        private final String classification;
        private final String diseaseCode;
        private final String checkField;
        private final String koreanName;
        private final String englishName;
        private final String note;

        public KCDRecord(String classification, String diseaseCode, String checkField,
                         String koreanName, String englishName, String note) {
            this.classification = classification != null ? classification.trim() : "";
            this.diseaseCode = diseaseCode != null ? diseaseCode.trim() : "";
            this.checkField = checkField != null ? checkField.trim() : "";
            this.koreanName = koreanName != null ? koreanName.trim() : "";
            this.englishName = englishName != null ? englishName.trim() : "";
            this.note = note != null ? note.trim() : "";
        }

        public String getClassification() { return classification; }
        public String getDiseaseCode() { return diseaseCode; }
        public String getCheckField() { return checkField; }
        public String getKoreanName() { return koreanName; }
        public String getEnglishName() { return englishName; }
        public String getNote() { return note; }

        public String[] toArray() {
            return new String[]{classification, diseaseCode, checkField, koreanName, englishName, note};
        }

        public String toFormattedString() {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(diseaseCode).append("] ");
            sb.append(koreanName);
            if (!englishName.isEmpty()) {
                sb.append(" / ").append(englishName);
            }
            if (!classification.isEmpty()) {
                sb.append(" (").append(classification).append(")");
            }
            if (!checkField.isEmpty()) {
                sb.append(" {check: ").append(checkField).append("}");
            }
            if (!note.isEmpty()) {
                sb.append(" // ").append(note);
            }
            return sb.toString();
        }

        public String toEMRFormat() {
            return toFormattedString();
        }

        public String toDetailedString() {
            return String.format(
                "Classification: %s%n" +
                "Disease Code: %s%n" +
                "Check Field: %s%n" +
                "Korean Name: %s%n" +
                "English Name: %s%n" +
                "Note: %s%n",
                classification, diseaseCode, checkField, koreanName, englishName, note
            );
        }
    }

    private static class DatabaseManager {
        private static Connection getConnection() throws SQLException {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                throw new SQLException("SQLite JDBC driver not found", e);
            }
            return DriverManager.getConnection(JDBC_URL);
        }

        public static List<KCDRecord> getAllRecords() throws SQLException {
            List<KCDRecord> records = new ArrayList<>();
            String sql = "SELECT * FROM kcd_codes ORDER BY disease_code";

            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

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

        public static boolean addRecord(KCDRecord record) throws SQLException {
            String sql = "INSERT INTO kcd_codes(classification, disease_code, check_field, korean_name, english_name, note) VALUES(?, ?, ?, ?, ?, ?)";

            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, record.getClassification());
                pstmt.setString(2, record.getDiseaseCode());
                pstmt.setString(3, record.getCheckField());
                pstmt.setString(4, record.getKoreanName());
                pstmt.setString(5, record.getEnglishName());
                pstmt.setString(6, record.getNote());

                return pstmt.executeUpdate() > 0;
            }
        }

        public static boolean updateRecord(String originalDiseaseCode, KCDRecord record) throws SQLException {
            String sql = "UPDATE kcd_codes SET classification=?, disease_code=?, check_field=?, korean_name=?, english_name=?, note=? WHERE disease_code=?";

            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, record.getClassification());
                pstmt.setString(2, record.getDiseaseCode());
                pstmt.setString(3, record.getCheckField());
                pstmt.setString(4, record.getKoreanName());
                pstmt.setString(5, record.getEnglishName());
                pstmt.setString(6, record.getNote());
                pstmt.setString(7, originalDiseaseCode);

                return pstmt.executeUpdate() > 0;
            }
        }

        public static boolean deleteRecord(String diseaseCode) throws SQLException {
            String sql = "DELETE FROM kcd_codes WHERE disease_code = ?";

            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, diseaseCode);
                return pstmt.executeUpdate() > 0;
            }
        }
    }

    private static class KCDRecordDialog extends JDialog {
        private final JTextField[] fields;
        private final JTextArea noteTextArea;
        private boolean confirmed = false;

        public KCDRecordDialog(JFrame parent, String title, KCDRecord initialData) {
            super(parent, title, true);
            setSize(600, 500);
            setLocationRelativeTo(parent);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);

            String[] labels = {
                "Classification:", "Disease Code:", "Check Field:",
                "Korean Name:", "English Name:", "Note:"
            };

            fields = new JTextField[labels.length - 1];
            noteTextArea = new JTextArea(4, 30);
            noteTextArea.setLineWrap(true);
            noteTextArea.setWrapStyleWord(true);

            createDialogLayout(labels, initialData);
        }

        private void createDialogLayout(String[] labels, KCDRecord initialData) {
            JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
            mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

            JPanel formPanel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(8, 5, 8, 5);
            gbc.anchor = GridBagConstraints.WEST;

            for (int i = 0; i < labels.length; i++) {
                gbc.gridx = 0;
                gbc.gridy = i;
                gbc.weightx = 0;
                gbc.fill = GridBagConstraints.NONE;

                JLabel label = new JLabel(labels[i]);
                label.setFont(label.getFont().deriveFont(Font.BOLD));
                formPanel.add(label, gbc);

                gbc.gridx = 1;
                gbc.weightx = 1.0;
                gbc.fill = GridBagConstraints.HORIZONTAL;

                if (i == labels.length - 1) {
                    JScrollPane noteScrollPane = new JScrollPane(noteTextArea);
                    noteScrollPane.setPreferredSize(new Dimension(400, 100));
                    formPanel.add(noteScrollPane, gbc);

                    if (initialData != null) noteTextArea.setText(initialData.getNote());
                } else {
                    fields[i] = new JTextField(30);
                    fields[i].setFont(new Font("Malgun Gothic", Font.PLAIN, 12));

                    if (initialData != null) {
                        switch (i) {
                            case 0: fields[i].setText(initialData.getClassification()); break;
                            case 1: fields[i].setText(initialData.getDiseaseCode()); break;
                            case 2: fields[i].setText(initialData.getCheckField()); break;
                            case 3: fields[i].setText(initialData.getKoreanName()); break;
                            case 4: fields[i].setText(initialData.getEnglishName()); break;
                        }
                    }

                    formPanel.add(fields[i], gbc);
                }
            }

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
            JButton okButton = new JButton("Save");
            JButton cancelButton = new JButton("Cancel");

            okButton.setPreferredSize(new Dimension(80, 30));
            cancelButton.setPreferredSize(new Dimension(80, 30));

            okButton.addActionListener(e -> {
                if (validateInput()) {
                    confirmed = true;
                    setVisible(false);
                }
            });

            cancelButton.addActionListener(e -> {
                confirmed = false;
                setVisible(false);
            });

            buttonPanel.add(okButton);
            buttonPanel.add(cancelButton);

            mainPanel.add(formPanel, BorderLayout.CENTER);
            mainPanel.add(buttonPanel, BorderLayout.SOUTH);

            add(mainPanel);

            SwingUtilities.invokeLater(() -> {
                if (fields.length > 0) fields[0].requestFocusInWindow();
            });
        }

        private boolean validateInput() {
            if (fields[1].getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Disease Code is required.", "Validation Error", JOptionPane.ERROR_MESSAGE);
                fields[1].requestFocus();
                return false;
            }
            if (fields[3].getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Korean Name is required.", "Validation Error", JOptionPane.ERROR_MESSAGE);
                fields[3].requestFocus();
                return false;
            }
            return true;
        }

        public boolean isConfirmed() { return confirmed; }

        public KCDRecord getRecord() {
            return new KCDRecord(
                fields[0].getText().trim(),
                fields[1].getText().trim(),
                fields[2].getText().trim(),
                fields[3].getText().trim(),
                fields[4].getText().trim(),
                noteTextArea.getText().trim()
            );
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            KCDDatabaseManagerSwing frame = new KCDDatabaseManagerSwing();
            frame.setVisible(true);
        });
    }
}
