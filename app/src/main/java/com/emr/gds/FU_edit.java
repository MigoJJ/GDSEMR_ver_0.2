package com.emr.gds;

// Save this as FU_edit.java
// Requires SQLite JDBC in the classpath (e.g., org.xerial:sqlite-jdbc).

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FU_edit extends JFrame {
    // === Canonical section titles (labels) ===
    public static final String[] TEXT_AREA_TITLES = {
            "CC>", "PI>", "ROS>", "PMH>", "S>",
            "O>", "Physical Exam>", "A>", "P>", "Comment>"
    };

    // Reordered output placement: place ROS just after S.
    private static final List<String> OUTPUT_ORDER = Arrays.asList(
            "CC>", "PI>", "PMH>", "S>", "ROS>", "O>", "Physical Exam>", "A>", "P>", "Comment>"
    );

    // Regex to detect a header line; tolerant to leading spaces
    private static final Pattern HEADER_PATTERN = Pattern.compile(
            "^\\s*(CC>|PI>|ROS>|PMH>|S>|O>|Physical Exam>|A>|P>|Comment>)\\s*(.*)$"
    );

    // === GUI Components ===
    private JTable templateTable;
    private DefaultTableModel tableModel;
    private JTextField templateNameField;
    private JTextArea templateContentArea;
    private JButton newButton;
    private JButton saveButton;
    private JButton deleteButton;
    private JButton useTemplateButton;

    // === Data / Logic ===
    private final DatabaseManager dbManager;
    private int selectedTemplateId = -1;
    private final Consumer<String> onTemplateSelectedCallback;

    // --- DB Helpers (Repo-tracked under app/db) ---
    private static Path repoRoot() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("gradlew")) && !Files.exists(p.resolve(".git"))) {
            p = p.getParent();
        }
        return (p != null) ? p : Paths.get("").toAbsolutePath();
    }
    
    private static Path dbPath() {
        return repoRoot().resolve("app").resolve("db").resolve("emr_templates.db");
    }

    public FU_edit(Consumer<String> onTemplateSelectedCallback) {
        this.onTemplateSelectedCallback = onTemplateSelectedCallback;
        dbManager = new DatabaseManager();
        dbManager.ensureDbDirectory();
        dbManager.createTableIfNotExists();

        setTitle("EMR Template Editor");
        setSize(900, 620);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        initComponents();
        layoutComponents();
        attachListeners();
        loadTemplatesIntoTable();

        SwingUtilities.invokeLater(() -> templateNameField.requestFocusInWindow());
    }

    // Optional local test harness
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() ->
                new FU_edit(content -> {
                    JTextArea preview = new JTextArea(content, 25, 80);
                    preview.setFont(new Font("Monospaced", Font.PLAIN, 12));
                    preview.setLineWrap(true);
                    preview.setWrapStyleWord(true);
                    JOptionPane.showMessageDialog(null, new JScrollPane(preview),
                            "Selected Template Output", JOptionPane.INFORMATION_MESSAGE);
                }).setVisible(true)
        );
    }

    // === UI setup ===
    private void initComponents() {
        String[] columnNames = {"ID", "Template Name"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
            @Override public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 0 ? Integer.class : String.class;
            }
        };

        templateTable = new JTable(tableModel);
        templateTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        templateTable.setRowHeight(22);
        // Hide the ID column visually
        templateTable.removeColumn(templateTable.getColumnModel().getColumn(0));

        templateNameField = new JTextField(28);

        templateContentArea = new JTextArea();
        templateContentArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        templateContentArea.setLineWrap(true);
        templateContentArea.setWrapStyleWord(true);

        newButton = new JButton("New");
        saveButton = new JButton("Save");
        deleteButton = new JButton("Delete");
        useTemplateButton = new JButton("Use Template");
        useTemplateButton.setFont(new Font("SansSerif", Font.BOLD, 12));

        newButton.setMnemonic('N');
        saveButton.setMnemonic('S');
        deleteButton.setMnemonic('D');
        useTemplateButton.setMnemonic('U');

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { /* add dirty-check if needed */ }
        });
    }

    private void layoutComponents() {
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createTitledBorder("Templates"));
        leftPanel.add(new JScrollPane(templateTable), BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout(6, 6));
        rightPanel.setBorder(BorderFactory.createTitledBorder("Editor"));

        JPanel namePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        namePanel.add(new JLabel("Name:"));
        namePanel.add(templateNameField);
        rightPanel.add(namePanel, BorderLayout.NORTH);
        rightPanel.add(new JScrollPane(templateContentArea), BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(300);
        add(splitPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttonPanel.add(newButton);
        buttonPanel.add(saveButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(new JSeparator(SwingConstants.VERTICAL));
        buttonPanel.add(useTemplateButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void attachListeners() {
        templateTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) handleTableSelection();
        });

        newButton.addActionListener(e -> clearEditor());
        saveButton.addActionListener(e -> saveTemplate());
        deleteButton.addActionListener(e -> deleteTemplate());
        useTemplateButton.addActionListener(e -> useTemplate());
    }

    // === Core behavior: parse & assemble ===
    /**
     * Parse the free text into section buckets. Lines with a header start a new section.
     * Anything not under a recognized header is appended to "Comment>" by default.
     */
    private static LinkedHashMap<String, List<String>> parseSections(String content) {
        // Use LinkedHashMap to preserve insertion order for any non-specified sections
        LinkedHashMap<String, List<String>> sections = new LinkedHashMap<>();
        Set<String> valid = new LinkedHashSet<>(Arrays.asList(TEXT_AREA_TITLES));

        // Initialize all known sections (empty lists)
        for (String title : TEXT_AREA_TITLES) {
            sections.put(title, new ArrayList<>());
        }

        String current = null;
        String[] lines = content.split("\\r?\\n", -1); // keep trailing empty

        for (String rawLine : lines) {
            Matcher m = HEADER_PATTERN.matcher(rawLine);
            if (m.matches()) {
                // New section header
                String header = m.group(1);             // the matched label e.g., "S>"
                String after = m.group(2);              // content on the same line (if any)
                current = header;
                if (!sections.containsKey(current)) {
                    // If somehow new header (shouldn't happen), track it
                    sections.put(current, new ArrayList<>());
                }
                if (!after.isEmpty()) {
                    sections.get(current).add(after);
                }
            } else {
                // Continuation or unheaded line
                if (current == null) {
                    // No header encountered yet—dump into Comment>
                    sections.get("Comment>").add(rawLine.trim());
                } else {
                    sections.get(current).add(rawLine);
                }
            }
        }
        return sections;
    }

    /**
     * Assemble output using the clinical order:
     * CC, PI, PMH, S, ROS, O, Physical Exam, A, P, Comment
     * - Omit sections with no content.
     * - ROS is printed as its own labeled line directly under S (if both exist).
     */
    private static String buildOrderedOutput(LinkedHashMap<String, List<String>> sections) {
        StringBuilder out = new StringBuilder();

        // Helper: append a labeled block with proper first line + clean continuations
        final java.util.function.BiConsumer<String, List<String>> appendBlock = (label, lines) -> {
            if (lines == null || lines.isEmpty()) return;

            // Trim trailing blank-only lines
            int last = lines.size() - 1;
            while (last >= 0 && lines.get(last).trim().isEmpty()) last--;
            if (last < 0) return;

            boolean first = true;
            for (int i = 0; i <= last; i++) {
                String ln = lines.get(i);
                if (first) {
                    String body = ln == null ? "" : ln.trim();
                    out.append(label);
                    if (!body.isEmpty()) out.append(' ').append(body);
                    out.append('\n');
                    first = false;
                } else {
                    // Keep continuation on its own line (no accidental merging with header)
                    out.append("\t").append(ln == null ? "" : ln).append('\n');
                }
            }
        };

        List<String> sLines   = sections.getOrDefault("S>",   Collections.emptyList());
        List<String> rosLines = sections.getOrDefault("ROS>", Collections.emptyList());

        // Fixed clinical order with ROS immediately after S
        List<String> ORDER = Arrays.asList("CC>", "PI>", "PMH>", "S>", "ROS>", "O>", "Physical Exam>", "A>", "P>", "Comment>");

        for (String label : ORDER) {
            if ("S>".equals(label)) {
                appendBlock.accept("S>", sLines);
                if (!sLines.isEmpty() && !rosLines.isEmpty()) {
                    appendBlock.accept("ROS>", rosLines); // labeled ROS directly under S
                }
            } else if ("ROS>".equals(label)) {
                // Only print here if S was empty
                if (sLines.isEmpty()) appendBlock.accept("ROS>", rosLines);
            } else {
                appendBlock.accept(label, sections.getOrDefault(label, Collections.emptyList()));
            }
        }

        // Trim final newline
        int len = out.length();
        if (len > 0 && out.charAt(len - 1) == '\n') out.setLength(len - 1);
        return out.toString();
    }

    /**
     * End-to-end: parse + assemble + deliver
     */
    private void processAndDeliver(String raw) {
        LinkedHashMap<String, List<String>> sections = parseSections(raw);
        String finalOutput = buildOrderedOutput(sections);
        deliverAndClose(finalOutput);
    }

    // === Actions ===
    private void useTemplate() {
        // If nothing selected, use editor content directly
        if (selectedTemplateId == -1) {
            processAndDeliver(templateContentArea.getText());
            return;
        }

        String content = dbManager.getTemplateContent(selectedTemplateId);
        if (content == null) content = "";
        processAndDeliver(content);
    }

    private void loadTemplatesIntoTable() {
        tableModel.setRowCount(0);
        List<Object[]> templates = dbManager.getAllTemplates();
        for (Object[] t : templates) {
            tableModel.addRow(t); // [id, name]
        }
        if (tableModel.getRowCount() > 0) {
            templateTable.setRowSelectionInterval(0, 0);
        }
    }

    private void handleTableSelection() {
        int selectedRow = templateTable.getSelectedRow();
        if (selectedRow != -1) {
            int modelRow = templateTable.convertRowIndexToModel(selectedRow);
            selectedTemplateId = (int) tableModel.getValueAt(modelRow, 0);
            String name = (String) tableModel.getValueAt(modelRow, 1);
            String content = dbManager.getTemplateContent(selectedTemplateId);

            templateNameField.setText(name);
            templateContentArea.setText(content != null ? content : "");
        }
    }

    private void clearEditor() {
        templateTable.clearSelection();
        selectedTemplateId = -1;
        templateNameField.setText("");
        templateContentArea.setText("");
        templateNameField.requestFocus();
    }

    private void saveTemplate() {
        String name = templateNameField.getText().trim();
        String content = templateContentArea.getText();

        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Template name cannot be empty.", "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (selectedTemplateId == -1) {
            dbManager.createTemplate(name, content);
        } else {
            dbManager.updateTemplate(selectedTemplateId, name, content);
        }

        loadTemplatesIntoTable();
    }

    private void deleteTemplate() {
        if (selectedTemplateId == -1) {
            JOptionPane.showMessageDialog(this, "Please select a template to delete.", "Warning",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int response = JOptionPane.showConfirmDialog(this, "Delete this template?", "Confirm Deletion",
                JOptionPane.YES_NO_OPTION);
        if (response == JOptionPane.YES_OPTION) {
            dbManager.deleteTemplate(selectedTemplateId);
            loadTemplatesIntoTable();
            clearEditor();
        }
    }

    private void deliverAndClose(String text) {
        if (onTemplateSelectedCallback != null) {
            onTemplateSelectedCallback.accept(text);
        } else {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(text), null);
            JOptionPane.showMessageDialog(this, "Content copied to clipboard.", "Info",
                    JOptionPane.INFORMATION_MESSAGE);
        }
        dispose();
    }

    // === Database helper ===
    private static class DatabaseManager {
        private Connection conn;

        DatabaseManager() {
            try {
                Class.forName("org.sqlite.JDBC");
                Path db = dbPath();
                Files.createDirectories(db.getParent());
                String url = "jdbc:sqlite:" + db.toAbsolutePath();
                System.out.println("[DB PATH] emr_templates -> " + db.toAbsolutePath());
                this.conn = DriverManager.getConnection(url);
            } catch (Exception e) {
                throw new RuntimeException("Failed to open emr_templates.db", e);
            }
        }

        void ensureDbDirectory() {
            try {
                Path db = dbPath();
                Path parent = db.getParent();
                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectories(parent);
                }
            } catch (Exception e) {
                System.err.println("Failed to ensure DB directory: " + e.getMessage());
            }
        }

        private Connection connect() {
            return conn; // Use the existing connection
        }

        void createTableIfNotExists() {
            String sql = "CREATE TABLE IF NOT EXISTS templates (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT NOT NULL," +
                    "content TEXT" +
                    ");";
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            } catch (SQLException e) {
                System.err.println("Failed to create templates table: " + e.getMessage());
            }
        }

        List<Object[]> getAllTemplates() {
            String sql = "SELECT id, name FROM templates ORDER BY name;";
            List<Object[]> list = new ArrayList<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                
                while (rs.next()) {
                    list.add(new Object[]{rs.getInt("id"), rs.getString("name")});
                }
                
            } catch (SQLException e) {
                System.err.println("Failed to load templates: " + e.getMessage());
            }
            return list;
        }

        String getTemplateContent(int id) {
            String sql = "SELECT content FROM templates WHERE id = ?;";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getString("content");
                }
            } catch (SQLException e) {
                System.err.println("Failed to get template content: " + e.getMessage());
            }
            return "";
        }

        void createTemplate(String name, String content) {
            String sql = "INSERT INTO templates (name, content) VALUES (?, ?);";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.setString(2, content);
                ps.executeUpdate();
            } catch (SQLException e) {
                System.err.println("Failed to create template: " + e.getMessage());
            }
        }

        void updateTemplate(int id, String name, String content) {
            String sql = "UPDATE templates SET name = ?, content = ? WHERE id = ?;";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.setString(2, content);
                ps.setInt(3, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                System.err.println("Failed to update template: " + e.getMessage());
            }
        }

        void deleteTemplate(int id) {
            String sql = "DELETE FROM templates WHERE id = ?;";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                System.err.println("Failed to delete template: " + e.getMessage());
            }
        }

        void closeConnection() {
            if (conn != null) {
                try {
                    conn.close();
                    System.out.println("Template database connection closed.");
                } catch (SQLException e) {
                    System.err.println("Error closing template database: " + e.getMessage());
                }
            }
        }
    }
}