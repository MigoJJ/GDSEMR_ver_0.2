package com.emr.gds.soap;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Specialized Editor for Chief Complaint (CC>) TextArea
 * This is an example implementation that can be customized for each section
 */
public class ChiefComplaintEditor {
    
    private final TextArea sourceTextArea;
    private Stage editorStage;
    private TextArea editorTextArea;
    
    // Common chief complaint templates
    private final String[] ccTemplates = {
        "Chest pain",
        "Shortness of breath", 
        "Abdominal pain",
        "Headache",
        "Back pain",
        "Nausea and vomiting",
        "Fever",
        "Cough",
        "Dizziness",
        "Fatigue"
    };
    
    public ChiefComplaintEditor(TextArea sourceTextArea) {
        this.sourceTextArea = sourceTextArea;
        createEditorWindow();
    }
    
    private void createEditorWindow() {
        editorStage = new Stage();
        editorStage.setTitle("Chief Complaint Editor");
        editorStage.initModality(Modality.APPLICATION_MODAL);
        editorStage.setWidth(600);
        editorStage.setHeight(500);
        
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));
        
        // Top: Title and instructions
        VBox topSection = createTopSection();
        root.setTop(topSection);
        
        // Center: Main editing area
        VBox centerSection = createCenterSection();
        root.setCenter(centerSection);
        
        // Bottom: Action buttons
        HBox bottomSection = createBottomSection();
        root.setBottom(bottomSection);
        
        Scene scene = new Scene(root);
        editorStage.setScene(scene);
    }
    
    private VBox createTopSection() {
        VBox topSection = new VBox(5);
        
        Label titleLabel = new Label("Chief Complaint Editor");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        
        Label instructionLabel = new Label("Edit the patient's chief complaint. Use templates below or type custom text.");
        instructionLabel.setStyle("-fx-text-fill: #666666;");
        
        topSection.getChildren().addAll(titleLabel, instructionLabel);
        return topSection;
    }
    
    private VBox createCenterSection() {
        VBox centerSection = new VBox(10);
        
        // Template buttons section
        Label templatesLabel = new Label("Quick Templates:");
        templatesLabel.setStyle("-fx-font-weight: bold;");
        
        GridPane templatesGrid = createTemplatesGrid();
        
        // Editor text area
        Label editorLabel = new Label("Chief Complaint Text:");
        editorLabel.setStyle("-fx-font-weight: bold;");
        
        editorTextArea = new TextArea();
        editorTextArea.setWrapText(true);
        editorTextArea.setPrefRowCount(8);
        editorTextArea.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 12px;");
        
        // Initialize with current content from source
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
    
    private GridPane createTemplatesGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(5);
        grid.setVgap(5);
        grid.setPadding(new Insets(5));
        
        // Error checking for templates array
        if (ccTemplates == null || ccTemplates.length == 0) {
            Label noTemplatesLabel = new Label("No templates available");
            noTemplatesLabel.setStyle("-fx-text-fill: #999999; -fx-font-style: italic;");
            grid.add(noTemplatesLabel, 0, 0);
            return grid;
        }
        
        int cols = 3;
        for (int i = 0; i < ccTemplates.length; i++) {
            try {
                // Check for null or empty template
                String template = ccTemplates[i];
                if (template == null || template.trim().isEmpty()) {
                    System.err.println("Warning: Template at index " + i + " is null or empty, skipping");
                    continue;
                }
                
                Button templateButton = new Button(template.trim());
                templateButton.setPrefWidth(150);
                templateButton.setMaxWidth(Double.MAX_VALUE); // Allow button to expand
                
                // Capture the template value in a final variable for the lambda
                final String templateText = template.trim();
                templateButton.setOnAction(e -> {
                    try {
                        insertTemplate(templateText);
                    } catch (Exception ex) {
                        System.err.println("Error inserting template '" + templateText + "': " + ex.getMessage());
                        showErrorAlert("Template Error", "Failed to insert template: " + templateText);
                    }
                });
                
                // Calculate grid position
                int col = i % cols;
                int row = i / cols;
                grid.add(templateButton, col, row);
                
            } catch (Exception e) {
                System.err.println("Error creating template button at index " + i + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        // If no valid templates were added, show a message
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
        
        // Add some spacing
        bottomSection.getChildren().addAll(applyButton, cancelButton, clearButton);
        
        return bottomSection;
    }
    
    private void insertTemplate(String template) {
        try {
            // Validate input
            if (template == null || template.trim().isEmpty()) {
                showErrorAlert("Invalid Template", "Template text is empty or null");
                return;
            }
            
            // Ensure editor text area exists
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
                
                // Validate caret position
                if (caretPosition < 0) {
                    caretPosition = currentText.length();
                } else if (caretPosition > currentText.length()) {
                    caretPosition = currentText.length();
                }
                
                if (caretPosition == currentText.length()) {
                    // At end, add with separator
                    String separator = currentText.endsWith("\n") ? "" : "\n";
                    editorTextArea.appendText(separator + templateText);
                } else {
                    // Insert at current position
                    editorTextArea.insertText(caretPosition, templateText + " ");
                }
            }
            
            // Request focus safely
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
            // Validate components
            if (editorTextArea == null) {
                showErrorAlert("Editor Error", "Text editor is not initialized");
                return;
            }
            
            if (sourceTextArea == null) {
                showErrorAlert("Source Error", "Source text area is not available");
                return;
            }
            
            String newText = editorTextArea.getText();
            // Allow empty text (user might want to clear the field)
            if (newText == null) {
                newText = "";
            }
            
            sourceTextArea.setText(newText);
            
            // Show confirmation
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
            // If even the alert fails, log to console
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