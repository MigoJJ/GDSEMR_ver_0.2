package com.emr.gds.soap;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Specialized Editor for Chief Complaint (CC) TextArea with Database Integration
 */
public class ChiefComplaintEditor {
    private final TextArea sourceTextArea;
    private Stage editorStage;
    private TextArea editorTextArea;
    private Map<String, String> abbrevMap;
    private Connection dbConnection;
    
    // Database path relative to the project
    private static final String DB_PATH = "src/main/resources/database/abbreviations.db";
    
    // Common chief complaint templates
    private final String[] ccTemplates = {
            "Chest pain", "Shortness of breath", "Abdominal pain",
            "Headache", "Back pain", "Nausea and vomiting",
            "Fever", "Cough", "Dizziness", "Fatigue"
    };

    // Constructor with database integration
    public ChiefComplaintEditor(TextArea sourceTextArea) {
        this.sourceTextArea = sourceTextArea;
        this.abbrevMap = new HashMap<>();
        initializeDatabase();
        loadAbbreviationsFromDatabase();
        createEditorWindow();
    }

    /**
     * Initialize database connection
     */
    private void initializeDatabase() {
        try {
            String dbUrl = "jdbc:sqlite:" + DB_PATH;
            dbConnection = DriverManager.getConnection(dbUrl);
            createAbbreviationsTableIfNotExists();
        } catch (SQLException e) {
            System.err.println("Failed to initialize database: " + e.getMessage());
            // Fallback to default abbreviations if database fails
            loadDefaultAbbreviations();
        }
    }

    /**
     * Create abbreviations table if it doesn't exist
     */
    private void createAbbreviationsTableIfNotExists() {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS abbreviations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                short TEXT UNIQUE NOT NULL,
                full TEXT NOT NULL,
                created_date DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """;
        
        try (PreparedStatement stmt = dbConnection.prepareStatement(createTableSQL)) {
            stmt.executeUpdate();
            
            // Insert some default abbreviations if table is empty
            insertDefaultAbbreviationsIfEmpty();
            
        } catch (SQLException e) {
            System.err.println("Failed to create abbreviations table: " + e.getMessage());
        }
    }

    /**
     * Insert default medical abbreviations if table is empty
     */
    private void insertDefaultAbbreviationsIfEmpty() {
        try {
            // Check if table has any data
            String countSQL = "SELECT COUNT(*) FROM abbreviations";
            try (PreparedStatement countStmt = dbConnection.prepareStatement(countSQL);
                 ResultSet rs = countStmt.executeQuery()) {
                
                if (rs.next() && rs.getInt(1) == 0) {
                    // Table is empty, insert defaults
                    String insertSQL = "INSERT INTO abbreviations (short, full) VALUES (?, ?)";
                    try (PreparedStatement insertStmt = dbConnection.prepareStatement(insertSQL)) {
                        
                        String[][] defaultAbbrevs = {
                            {"cp", "chest pain"},
                            {"sob", "shortness of breath"},
                            {"n/v", "nausea and vomiting"},
                            {"ha", "headache"},
                            {"abd", "abdominal"},
                            {"c/o", "complains of"},
                            {"w/", "with"},
                            {"w/o", "without"},
                            {"pt", "patient"},
                            {"hx", "history"},
                            {"rx", "prescription"},
                            {"dx", "diagnosis"},
                            {"sx", "symptoms"},
                            {"tx", "treatment"},
                            {"f/u", "follow up"},
                            {"rta", "road traffic accident"},
                            {"loe", "loss of energy"},
                            {"loc", "loss of consciousness"},
                            {"nkda", "no known drug allergies"},
                            {"r/o", "rule out"}
                        };
                        
                        for (String[] abbrev : defaultAbbrevs) {
                            insertStmt.setString(1, abbrev[0]);
                            insertStmt.setString(2, abbrev[1]);
                            insertStmt.executeUpdate();
                        }
                        
                        System.out.println("Inserted default abbreviations into database.");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to insert default abbreviations: " + e.getMessage());
        }
    }

    /**
     * Load abbreviations from database into memory map
     */
    private void loadAbbreviationsFromDatabase() {
        if (dbConnection == null) {
            loadDefaultAbbreviations();
            return;
        }
        
        String selectSQL = "SELECT short, full FROM abbreviations ORDER BY short";
        try (PreparedStatement stmt = dbConnection.prepareStatement(selectSQL);
             ResultSet rs = stmt.executeQuery()) {
            
            abbrevMap.clear();
            while (rs.next()) {
                String shortForm = rs.getString("short");
                String fullForm = rs.getString("full");
                abbrevMap.put(shortForm, fullForm);
            }
            
            System.out.println("Loaded " + abbrevMap.size() + " abbreviations from database.");
            
        } catch (SQLException e) {
            System.err.println("Failed to load abbreviations from database: " + e.getMessage());
            loadDefaultAbbreviations();
        }
    }

    /**
     * Fallback method to load default abbreviations if database fails
     */
    private void loadDefaultAbbreviations() {
        abbrevMap.clear();
        abbrevMap.put("cp", "chest pain");
        abbrevMap.put("sob", "shortness of breath");
        abbrevMap.put("n/v", "nausea and vomiting");
        abbrevMap.put("ha", "headache");
        abbrevMap.put("abd", "abdominal");
        abbrevMap.put("c/o", "complains of");
        abbrevMap.put("w/", "with");
        abbrevMap.put("w/o", "without");
        abbrevMap.put("pt", "patient");
        abbrevMap.put("hx", "history");
        
        System.out.println("Loaded default abbreviations (fallback mode).");
    }

    /**
     * Clean up database connection
     */
    private void closeDatabase() {
        if (dbConnection != null) {
            try {
                dbConnection.close();
                System.out.println("Database connection closed.");
            } catch (SQLException e) {
                System.err.println("Error closing database: " + e.getMessage());
            }
        }
    }

    // Rest of your existing methods remain the same...
    private void createEditorWindow() {
        editorStage = new Stage();
        editorStage.setTitle("Chief Complaint Editor");
        editorStage.initModality(Modality.APPLICATION_MODAL);
        editorStage.setMinWidth(600);
        editorStage.setMinHeight(500);

        // Close database when window closes
        editorStage.setOnCloseRequest(e -> closeDatabase());

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        VBox topSection = createTopSection();
        root.setTop(topSection);

        VBox centerSection = createCenterSection();
        root.setCenter(centerSection);

        HBox bottomSection = createBottomSection();
        root.setBottom(bottomSection);

        Scene scene = new Scene(root, 600, 500);
        editorStage.setScene(scene);
    }

    // Your existing createCenterSection method with the abbreviation handler
    private VBox createCenterSection() {
        VBox centerSection = new VBox(10);

        Label templatesLabel = new Label("Quick Templates:");
        templatesLabel.setStyle("-fx-font-weight: bold;");

        GridPane templatesGrid = createTemplatesGrid();

        Label editorLabel = new Label("Chief Complaint Text (supports abbreviations - " + 
                                    abbrevMap.size() + " loaded):");
        editorLabel.setStyle("-fx-font-weight: bold;");

        editorTextArea = new TextArea();
        editorTextArea.setWrapText(true);
        editorTextArea.setPrefRowCount(8);
        editorTextArea.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 12px;");

        // Add the key listener for abbreviation expansion
        editorTextArea.setOnKeyReleased(this::handleAbbreviationExpansion);

        if (sourceTextArea.getText() != null && !sourceTextArea.getText().trim().isEmpty()) {
            editorTextArea.setText(sourceTextArea.getText());
        }

        centerSection.getChildren().addAll(
                templatesLabel, templatesGrid,
                new Separator(),
                editorLabel, editorTextArea
        );

        return centerSection;
    }

    /**
     * Enhanced abbreviation expansion handler
     */
    private void handleAbbreviationExpansion(KeyEvent event) {
        if (abbrevMap == null || abbrevMap.isEmpty()) {
            return;
        }

        if (event.getCode() != KeyCode.SPACE && event.getCode() != KeyCode.ENTER) {
            return;
        }

        int caretPosition = editorTextArea.getCaretPosition();
        String text = editorTextArea.getText();

        if (caretPosition <= 0 || text == null || text.isEmpty()) {
            return;
        }

        int lastSpace = text.lastIndexOf(' ', caretPosition - 2);
        int lastNewline = text.lastIndexOf('\n', caretPosition - 2);
        int wordStart = Math.max(lastSpace, lastNewline) + 1;

        if (wordStart >= caretPosition - 1 || wordStart < 0) {
            return;
        }

        String word = text.substring(wordStart, caretPosition - 1).trim().toLowerCase();

        if (!word.isEmpty() && abbrevMap.containsKey(word)) {
            String expansion = abbrevMap.get(word);

            if (expansion == null || expansion.trim().isEmpty()) {
                return;
            }

            Platform.runLater(() -> {
                try {
                    editorTextArea.replaceText(wordStart, caretPosition, expansion.trim() + " ");
                } catch (Exception e) {
                    System.err.println("Error expanding abbreviation '" + word + "': " + e.getMessage());
                }
            });
        }
    }

    // All your other existing methods remain unchanged:
    // createTopSection(), createTemplatesGrid(), createBottomSection(), 
    // insertTemplate(), applyChanges(), showErrorAlert(), show(), showAndWait()
    
    // [Include all your existing methods here - they don't need changes]


    // ... (rest of your ChiefComplaintEditor class is unchanged) ...
    // createTopSection(), createTemplatesGrid(), createBottomSection(), etc.
    // ... all the other methods remain the same ...
    private VBox createTopSection() {
        VBox topSection = new VBox(5);

        Label titleLabel = new Label("Chief Complaint Editor");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label instructionLabel = new Label("Edit the patient's chief complaint. Use templates below or type custom text.");
        instructionLabel.setStyle("-fx-text-fill: #666666;");

        topSection.getChildren().addAll(titleLabel, instructionLabel);
        return topSection;
    }

    private GridPane createTemplatesGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(5);
        grid.setVgap(5);
        grid.setPadding(new Insets(5));

        if (ccTemplates == null || ccTemplates.length == 0) {
            Label noTemplatesLabel = new Label("No templates available");
            noTemplatesLabel.setStyle("-fx-text-fill: #999999; -fx-font-style: italic;");
            grid.add(noTemplatesLabel, 0, 0);
            return grid;
        }

        int cols = 3;
        for (int i = 0; i < ccTemplates.length; i++) {
            try {
                String template = ccTemplates[i];
                if (template == null || template.trim().isEmpty()) {
                    System.err.println("Warning: Template at index " + i + " is null or empty, skipping");
                    continue;
                }

                Button templateButton = new Button(template.trim());
                templateButton.setPrefWidth(150);
                templateButton.setMaxWidth(Double.MAX_VALUE);

                final String templateText = template.trim();
                templateButton.setOnAction(e -> {
                    try {
                        insertTemplate(templateText);
                    } catch (Exception ex) {
                        System.err.println("Error inserting template '" + templateText + "': " + ex.getMessage());
                        showErrorAlert("Template Error", "Failed to insert template: " + templateText);
                    }
                });

                int col = i % cols;
                int row = i / cols;
                grid.add(templateButton, col, row);

            } catch (Exception e) {
                System.err.println("Error creating template button at index " + i + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        if (grid.getChildren().isEmpty()) {
            Label noValidTemplatesLabel = new Label("No valid templates available");
            noValidTemplatesLabel.setStyle("-fx-text-fill: #ff6666; -fx-font-style: italic;");
            grid.add(noValidTemplatesLabel, 0, 0);
        }

        return grid;
    }

    private HBox createBottomSection() {
        HBox bottomSection = new HBox(10);
        bottomSection.setPadding(new Insets(10, 0, 0, 0));

        Button applyButton = new Button("Apply Changes");
        applyButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        applyButton.setOnAction(e -> applyChanges());

        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> editorStage.close());

        Button clearButton = new Button("Clear");
        clearButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        clearButton.setOnAction(e -> editorTextArea.clear());

        bottomSection.getChildren().addAll(applyButton, cancelButton, clearButton);

        return bottomSection;
    }

    private void insertTemplate(String template) {
        try {
            if (template == null || template.trim().isEmpty()) {
                showErrorAlert("Invalid Template", "Template text is empty or null");
                return;
            }

            if (editorTextArea == null) {
                showErrorAlert("Editor Error", "Text editor is not initialized");
                return;
            }

            String currentText = editorTextArea.getText();
            String templateText = template.trim();

            if (currentText == null || currentText.trim().isEmpty()) {
                editorTextArea.setText(templateText);
            } else {
                int caretPosition = editorTextArea.getCaretPosition();

                if (caretPosition < 0 || caretPosition > currentText.length()) {
                    caretPosition = currentText.length();
                }

                if (caretPosition == currentText.length()) {
                    String separator = currentText.endsWith("\n") ? "" : "\n";
                    editorTextArea.appendText(separator + templateText);
                } else {
                    editorTextArea.insertText(caretPosition, templateText + " ");
                }
            }
            Platform.runLater(() -> {
                if (editorTextArea != null) {
                    editorTextArea.requestFocus();
                }
            });

        } catch (Exception e) {
            System.err.println("Error in insertTemplate: " + e.getMessage());
            e.printStackTrace();
            showErrorAlert("Template Insert Error", "Failed to insert template: " + e.getMessage());
        }
    }

    private void applyChanges() {
        try {
            if (editorTextArea == null) {
                showErrorAlert("Editor Error", "Text editor is not initialized");
                return;
            }

            if (sourceTextArea == null) {
                showErrorAlert("Source Error", "Source text area is not available");
                return;
            }

            String newText = editorTextArea.getText();
            if (newText == null) {
                newText = "";
            }

            sourceTextArea.setText(newText);

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Changes Applied");
            alert.setHeaderText(null);
            alert.setContentText("Chief complaint has been updated successfully.");
            alert.showAndWait();

            if (editorStage != null) {
                editorStage.close();
            }

        } catch (Exception e) {
            System.err.println("Error applying changes: " + e.getMessage());
            e.printStackTrace();
            showErrorAlert("Apply Changes Error", "Failed to apply changes: " + e.getMessage());
        }
    }

    private void showErrorAlert(String title, String message) {
        try {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        } catch (Exception e) {
            System.err.println("Failed to show error alert - " + title + ": " + message);
            e.printStackTrace();
        }
    }

    public void show() {
        editorStage.show();
    }

    public void showAndWait() {
        editorStage.showAndWait();
    }
}
