package com.emr.gds;

//Save this as FU_edit.java
//Make sure you have the SQLite JDBC driver in your project's classpath.

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer; // Import Consumer for the callback

/**
* FU_edit.java
* A MODIFIED JFrame for creating and selecting EMR templates.
* This version is designed to be launched from another application and uses a
* callback to return the selected template content.
*/
public class FU_edit extends JFrame {

 // --- GUI Components ---
 private JTable templateTable;
 private DefaultTableModel tableModel;
 private JTextField templateNameField;
 private JTextArea templateContentArea;
 private JButton newButton;
 private JButton saveButton;
 private JButton deleteButton;
 private JButton useTemplateButton; // New button

 // --- Data and Logic ---
 private final DatabaseManager dbManager;
 private int selectedTemplateId = -1;
 private final Consumer<String> onTemplateSelectedCallback; // Callback function

 /**
  * Constructor that accepts a callback function.
  * @param onTemplateSelectedCallback A function to be executed when "Use Template" is clicked.
  *                                   It receives the full content of the selected template.
  */
 public FU_edit(Consumer<String> onTemplateSelectedCallback) {
     this.onTemplateSelectedCallback = onTemplateSelectedCallback;

     // Initialize the database manager
     dbManager = new DatabaseManager();
     dbManager.createTableIfNotExists();

     // --- Frame Setup ---
     setTitle("EMR Template Editor");
     setSize(800, 600);
     // DISPOSE_ON_CLOSE just closes this window, not the whole application
     setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
     setLocationRelativeTo(null);
     setLayout(new BorderLayout());

     // --- Initialize Components ---
     initComponents();
     layoutComponents();
     attachListeners();

     // Load initial data from the database
     loadTemplatesIntoTable();
 }

 private void initComponents() {
     // Table setup
     String[] columnNames = {"ID", "Template Name"};
     tableModel = new DefaultTableModel(columnNames, 0) {
         @Override
         public boolean isCellEditable(int row, int column) {
             return false;
         }
     };
     templateTable = new JTable(tableModel);
     templateTable.removeColumn(templateTable.getColumnModel().getColumn(0));

     // Editor components
     templateNameField = new JTextField(20);
     templateContentArea = new JTextArea();
     templateContentArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
     templateContentArea.setLineWrap(true);
     templateContentArea.setWrapStyleWord(true);

     // Buttons
     newButton = new JButton("New");
     saveButton = new JButton("Save");
     deleteButton = new JButton("Delete");
     useTemplateButton = new JButton("Use Template"); // Initialize new button
     useTemplateButton.setFont(new Font("SansSerif", Font.BOLD, 12));
 }

 private void layoutComponents() {
     JPanel leftPanel = new JPanel(new BorderLayout());
     leftPanel.setBorder(BorderFactory.createTitledBorder("Templates"));
     leftPanel.add(new JScrollPane(templateTable), BorderLayout.CENTER);

     JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
     rightPanel.setBorder(BorderFactory.createTitledBorder("Editor"));
     JPanel namePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
     namePanel.add(new JLabel("Name:"));
     namePanel.add(templateNameField);
     rightPanel.add(namePanel, BorderLayout.NORTH);
     rightPanel.add(new JScrollPane(templateContentArea), BorderLayout.CENTER);

     JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
     splitPane.setDividerLocation(250);
     add(splitPane, BorderLayout.CENTER);

     // --- Bottom Panel (Buttons) ---
     JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
     buttonPanel.add(newButton);
     buttonPanel.add(saveButton);
     buttonPanel.add(deleteButton);
     buttonPanel.add(new JSeparator(SwingConstants.VERTICAL));
     buttonPanel.add(useTemplateButton); // Add new button to layout
     add(buttonPanel, BorderLayout.SOUTH);
 }

 private void attachListeners() {
     templateTable.getSelectionModel().addListSelectionListener(e -> {
         if (!e.getValueIsAdjusting()) {
             handleTableSelection();
         }
     });

     newButton.addActionListener(e -> clearEditor());
     saveButton.addActionListener(e -> saveTemplate());
     deleteButton.addActionListener(e -> deleteTemplate());

     // Listener for the "Use Template" button
     useTemplateButton.addActionListener(e -> useTemplate());
 }
 
 private void useTemplate() {
     if (selectedTemplateId == -1) {
         JOptionPane.showMessageDialog(this, "Please select a template from the list first.", "No Template Selected", JOptionPane.WARNING_MESSAGE);
         return;
     }

     String content = dbManager.getTemplateContent(selectedTemplateId);
     if (onTemplateSelectedCallback != null) {
         onTemplateSelectedCallback.accept(content); // Execute the callback
     }
     this.dispose(); // Close the window
 }

 // ... (rest of the methods: loadTemplatesIntoTable, handleTableSelection, clearEditor, saveTemplate, deleteTemplate)
 // ... The DatabaseManager inner class remains exactly the same as before.
 // --- Paste the unchanged methods and the DatabaseManager inner class here ---
 private void loadTemplatesIntoTable() {
     tableModel.setRowCount(0); // Clear existing rows
     List<Object[]> templates = dbManager.getAllTemplates();
     for (Object[] template : templates) {
         tableModel.addRow(template);
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
         templateContentArea.setText(content);
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
         JOptionPane.showMessageDialog(this, "Template name cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
         return;
     }
     if (selectedTemplateId == -1) {
         dbManager.createTemplate(name, content);
     } else {
         dbManager.updateTemplate(selectedTemplateId, name, content);
     }
     loadTemplatesIntoTable();
     clearEditor();
 }
 private void deleteTemplate() {
     if (selectedTemplateId == -1) {
         JOptionPane.showMessageDialog(this, "Please select a template to delete.", "Warning", JOptionPane.WARNING_MESSAGE);
         return;
     }
     int response = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete this template?", "Confirm Deletion", JOptionPane.YES_NO_OPTION);
     if (response == JOptionPane.YES_OPTION) {
         dbManager.deleteTemplate(selectedTemplateId);
         loadTemplatesIntoTable();
         clearEditor();
     }
 }
 private static class DatabaseManager {

	 private static final String DB_URL = "jdbc:sqlite:" + System.getProperty("user.dir") + "/src/main/resources/database/emr_templates.db";     private Connection connect() {
         try {
             return DriverManager.getConnection(DB_URL);
         } catch (SQLException e) { System.err.println(e.getMessage()); return null; }
     }
     public void createTableIfNotExists() {
         String sql = "CREATE TABLE IF NOT EXISTS templates (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, content TEXT);";
         try (Connection conn = this.connect(); Statement stmt = conn.createStatement()) {
             stmt.execute(sql);
         } catch (SQLException e) { System.err.println(e.getMessage()); }
     }
     public List<Object[]> getAllTemplates() {
         String sql = "SELECT id, name FROM templates ORDER BY name";
         List<Object[]> templates = new ArrayList<>();
         try (Connection conn = this.connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
             while (rs.next()) {
                 templates.add(new Object[]{rs.getInt("id"), rs.getString("name")});
             }
         } catch (SQLException e) { System.err.println(e.getMessage()); }
         return templates;
     }
     public String getTemplateContent(int id) {
         String sql = "SELECT content FROM templates WHERE id = ?";
         try (Connection conn = this.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
             pstmt.setInt(1, id);
             ResultSet rs = pstmt.executeQuery();
             if (rs.next()) return rs.getString("content");
         } catch (SQLException e) { System.err.println(e.getMessage()); }
         return "";
     }
     public void createTemplate(String name, String content) {
         String sql = "INSERT INTO templates(name, content) VALUES(?,?)";
         try (Connection conn = this.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
             pstmt.setString(1, name);
             pstmt.setString(2, content);
             pstmt.executeUpdate();
         } catch (SQLException e) { System.err.println(e.getMessage()); }
     }
     public void updateTemplate(int id, String name, String content) {
         String sql = "UPDATE templates SET name = ?, content = ? WHERE id = ?";
         try (Connection conn = this.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
             pstmt.setString(1, name);
             pstmt.setString(2, content);
             pstmt.setInt(3, id);
             pstmt.executeUpdate();
         } catch (SQLException e) { System.err.println(e.getMessage()); }
     }
     public void deleteTemplate(int id) {
         String sql = "DELETE FROM templates WHERE id = ?";
         try (Connection conn = this.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
             pstmt.setInt(1, id);
             pstmt.executeUpdate();
         } catch (SQLException e) { System.err.println(e.getMessage()); }
     }
 }
}