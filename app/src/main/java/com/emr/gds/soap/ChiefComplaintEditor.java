package com.emr.gds.soap;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

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
import java.util.Map;

/**
 * Specialized Editor for Chief Complaint (CC) TextArea with abbreviation expansion.
 */
public class ChiefComplaintEditor {
    private final TextArea sourceTextArea;
    private Stage editorStage;
    private TextArea editorTextArea;
    
    // Abbreviation database
    private final Map<String, String> abbrevMap = new HashMap<>();
    
    // Common chief complaint templates
    private final String[] ccTemplates = {
            "Chest pain", "Shortness of breath", "Abdominal pain",
            "Headache", "Back pain", "Nausea and vomiting",
            "Fever", "Cough", "Dizziness", "Fatigue",
             };
    
    public ChiefComplaintEditor(TextArea sourceTextArea) {
        this.sourceTextArea = sourceTextArea;
        initAbbrevDatabase();
        createEditorWindow();
    }
    
    // Abbreviation Database Initialization
    private void initAbbrevDatabase() {
        try {
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
        } catch (Exception e) {
            showError("Failed to initialize abbreviation database: " + e.getMessage());
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
    
    private void createEditorWindow() {
        editorStage = new Stage();
        editorStage.setTitle("Chief Complaint Editor");
        editorStage.initModality(Modality.APPLICATION_MODAL);
        editorStage.setMinWidth(700);
        editorStage.setMinHeight(600);
        
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));
        
        VBox topSection = createTopSection();
        root.setTop(topSection);
        
        VBox centerSection = createCenterSection();
        root.setCenter(centerSection);
        
        HBox bottomSection = createBottomSection();
        root.setBottom(bottomSection);
        
        Scene scene = new Scene(root, 700, 600);
        editorStage.setScene(scene);
    }
    
    private VBox createTopSection() {
        VBox topSection = new VBox(5);
        
        Label titleLabel = new Label("Chief Complaint Editor");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        
        Label instructionLabel = new Label("Edit the patient's chief complaint. Use templates below or type custom text.");
        instructionLabel.setStyle("-fx-text-fill: #666666;");
        
        // Abbreviation help label
        Label abbrevLabel = new Label("Tip: Use abbreviations like :cd for current date. They will expand when saving.");
        abbrevLabel.setStyle("-fx-text-fill: #0066cc; -fx-font-size: 11px; -fx-font-style: italic;");
        
        topSection.getChildren().addAll(titleLabel, instructionLabel, abbrevLabel);
        return topSection;
    }
    
    private VBox createCenterSection() {
        VBox centerSection = new VBox(10);
        
        Label templatesLabel = new Label("Quick Templates:");
        templatesLabel.setStyle("-fx-font-weight: bold;");
        
        GridPane templatesGrid = createTemplatesGrid();
        
        Label editorLabel = new Label("Chief Complaint Text:");
        editorLabel.setStyle("-fx-font-weight: bold;");
        
        editorTextArea = new TextArea();
        editorTextArea.setWrapText(true);
        editorTextArea.setPrefRowCount(10);
        editorTextArea.setText(sourceTextArea.getText());
        editorTextArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px;");
        
        // Preview area for expanded abbreviations
        Label previewLabel = new Label("Preview (with expanded abbreviations):");
        previewLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #666666;");
        
        TextArea previewArea = new TextArea();
        previewArea.setWrapText(true);
        previewArea.setPrefRowCount(4);
        previewArea.setEditable(false);
        previewArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px; -fx-background-color: #f5f5f5;");
        
        // Update preview when text changes
        editorTextArea.textProperty().addListener((obs, oldText, newText) -> {
            previewArea.setText(expandAbbreviations(newText));
        });
        
        // Initial preview
        previewArea.setText(expandAbbreviations(editorTextArea.getText()));
        
        centerSection.getChildren().addAll(
            templatesLabel, templatesGrid, 
            editorLabel, editorTextArea,
            previewLabel, previewArea
        );
        return centerSection;
    }
    
    private GridPane createTemplatesGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(5);
        grid.setVgap(5);
        grid.setPadding(new Insets(5));
        
        int cols = 3;
        for (int i = 0; i < ccTemplates.length; i++) {
            String template = ccTemplates[i];
            Button templateButton = new Button(template.trim());
            templateButton.setPrefWidth(180);
            templateButton.setMaxWidth(Double.MAX_VALUE);
            templateButton.setOnAction(e -> insertTemplate(template.trim()));
            
            // Style abbreviation templates differently
            if (template.contains(":")) {
                templateButton.setStyle("-fx-background-color: #e3f2fd; -fx-border-color: #2196f3;");
            }
            
            grid.add(templateButton, i % cols, i / cols);
        }
        
        return grid;
    }
    
    private HBox createBottomSection() {
        HBox bottomSection = new HBox(10);
        bottomSection.setPadding(new Insets(10, 0, 0, 0));
        
        Button applyButton = new Button("Apply Changes");
        applyButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        applyButton.setOnAction(e -> applyChanges());
        
        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> editorStage.close());
        
        Button clearButton = new Button("Clear");
        clearButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        clearButton.setOnAction(e -> editorTextArea.clear());
        
        // Add expand button for manual expansion preview
        Button expandButton = new Button("Test Expansion");
        expandButton.setStyle("-fx-background-color: #2196f3; -fx-text-fill: white;");
        expandButton.setOnAction(e -> showExpansionPreview());
        
        bottomSection.getChildren().addAll(applyButton, cancelButton, clearButton, expandButton);
        return bottomSection;
    }
    
    private void insertTemplate(String template) {
        // Remove explanatory text in parentheses for abbreviation templates
        String cleanTemplate = template.replaceAll("\\s*\\([^)]*\\)", "");
        editorTextArea.insertText(editorTextArea.getCaretPosition(), cleanTemplate + " ");
        editorTextArea.requestFocus();
    }
    
    private void applyChanges() {
        String originalText = editorTextArea.getText();
        String expandedText = expandAbbreviations(originalText);
        
        // Show confirmation if abbreviations were expanded
        if (!originalText.equals(expandedText)) {
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("Abbreviation Expansion");
            confirmAlert.setHeaderText("Abbreviations will be expanded:");
            confirmAlert.setContentText("Original: " + originalText + "\n\nExpanded: " + expandedText + "\n\nApply changes?");
            
            if (confirmAlert.showAndWait().orElse(null) == javafx.scene.control.ButtonType.OK) {
                sourceTextArea.setText(expandedText);
                editorStage.close();
            }
        } else {
            sourceTextArea.setText(originalText);
            editorStage.close();
        }
    }
    
    private void showExpansionPreview() {
        String originalText = editorTextArea.getText();
        String expandedText = expandAbbreviations(originalText);
        
        Alert previewAlert = new Alert(Alert.AlertType.INFORMATION);
        previewAlert.setTitle("Abbreviation Expansion Preview");
        previewAlert.setHeaderText("Preview of expanded text:");
        previewAlert.setContentText(expandedText.isEmpty() ? "(empty)" : expandedText);
        previewAlert.getDialogPane().setPrefWidth(500);
        previewAlert.showAndWait();
    }
    
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("An error occurred:");
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    public void showAndWait() {
        editorStage.showAndWait();
    }
}