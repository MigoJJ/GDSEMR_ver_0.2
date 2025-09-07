package com.emr.gds.main;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.emr.gds.IttiaApp;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Enhanced Abbreviations Database Control with improved UX and functionality
 */
public class IAMAbbdbControl {
    private final Connection dbConn;
    private final Map<String, String> abbrevMap;
    private final Stage ownerStage;
    private final IttiaApp parentApp;
    
    public IAMAbbdbControl(Connection dbConn, Map<String, String> abbrevMap, Stage ownerStage, IttiaApp parentApp) {
        this.dbConn = dbConn;
        this.abbrevMap = abbrevMap;
        this.ownerStage = ownerStage;
        this.parentApp = parentApp;
    }

    public void showDbManagerDialog() {
        Stage stage = new Stage();
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(ownerStage);
        stage.setTitle("Abbreviations Database Manager");
        stage.setMinWidth(500);
        stage.setMinHeight(500);

        // UI Elements
        TextField shortField = new TextField();
        shortField.setPromptText("Short Form (e.g., 'cp')");
        shortField.setPrefWidth(120);
        
        TextField fullField = new TextField();
        fullField.setPromptText("Full Expansion (e.g., 'chest pain')");
        
        // ENHANCEMENT: Search field for filtering
        TextField searchField = new TextField();
        searchField.setPromptText("Search abbreviations...");
        
        Button findButton = new Button("Find");
        Button addButton = new Button("Add");
        Button editButton = new Button("Update");
        Button deleteButton = new Button("Delete");
        Button refreshButton = new Button("Refresh");
        Button clearButton = new Button("Clear");

        ListView<String> abbrevListView = new ListView<>();
        abbrevListView.setPrefHeight(250);
        updateListView(abbrevListView, ""); // Show all initially

        // Layout
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        
        // Search section
        VBox searchSection = new VBox(5);
        searchSection.getChildren().addAll(
            new Label("Search:"),
            searchField
        );
        
        // Input section
        VBox inputSection = new VBox(5);
        HBox inputFields = new HBox(10);
        inputFields.getChildren().addAll(shortField, fullField);
        HBox.setHgrow(fullField, Priority.ALWAYS); // Make full field expand
        
        inputSection.getChildren().addAll(
            new Label("Add/Edit Abbreviation:"),
            inputFields
        );

        // Button section
        HBox actionButtons = new HBox(10);
        actionButtons.getChildren().addAll(
            addButton, editButton, deleteButton, 
            new Label("|"), // Visual separator
            findButton, clearButton, refreshButton
        );

        root.getChildren().addAll(
            searchSection,
            new Label("Abbreviations List (" + abbrevMap.size() + " entries):"),
            abbrevListView,
            inputSection,
            actionButtons
        );

        // Event Handlers
        setupEventHandlers(stage, shortField, fullField, searchField, abbrevListView, 
                          findButton, addButton, editButton, deleteButton, refreshButton, clearButton);

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.showAndWait();
    }

    private void setupEventHandlers(Stage stage, TextField shortField, TextField fullField, 
                                  TextField searchField, ListView<String> abbrevListView,
                                  Button findButton, Button addButton, Button editButton, 
                                  Button deleteButton, Button refreshButton, Button clearButton) {
        
        // Disable Edit/Delete when nothing selected
        editButton.disableProperty().bind(abbrevListView.getSelectionModel().selectedItemProperty().isNull());
        deleteButton.disableProperty().bind(abbrevListView.getSelectionModel().selectedItemProperty().isNull());

        // ENHANCEMENT: Real-time search filtering
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            updateListView(abbrevListView, newVal.trim().toLowerCase());
        });

        // Selection handler
        abbrevListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                clearFields(shortField, fullField);
            } else {
                String[] parts = newVal.split(" -> ", 2);
                if (parts.length == 2) {
                    shortField.setText(parts[0]);
                    fullField.setText(parts[1]);
                }
            }
        });

        // ENHANCEMENT: Keyboard shortcuts
        shortField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                fullField.requestFocus();
            }
        });
        
        fullField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                if (editButton.isDisabled()) {
                    addButton.fire(); // Add new if nothing selected
                } else {
                    editButton.fire(); // Update existing
                }
            }
        });

        // Button handlers
        addButton.setOnAction(e -> {
            if (add(shortField.getText().trim(), fullField.getText().trim())) {
                updateListView(abbrevListView, searchField.getText().trim().toLowerCase());
                clearFields(shortField, fullField);
                updateListLabel(stage, abbrevListView);
            }
        });

        editButton.setOnAction(e -> {
            String selectedItem = abbrevListView.getSelectionModel().getSelectedItem();
            if (selectedItem == null) return;
            
            String originalShortText = selectedItem.split(" -> ", 2)[0];
            String newShortText = shortField.getText().trim();
            String newFullText = fullField.getText().trim();
            
            if (edit(originalShortText, newShortText, newFullText)) {
                updateListView(abbrevListView, searchField.getText().trim().toLowerCase());
                updateListLabel(stage, abbrevListView);
            }
        });

        deleteButton.setOnAction(e -> {
            String shortText = shortField.getText().trim();
            if (!shortText.isEmpty() && delete(shortText)) {
                updateListView(abbrevListView, searchField.getText().trim().toLowerCase());
                clearFields(shortField, fullField);
                updateListLabel(stage, abbrevListView);
            }
        });

        findButton.setOnAction(e -> {
            String shortText = shortField.getText().trim();
            if (shortText.isEmpty()) return;
            
            for (String item : abbrevListView.getItems()) {
                if (item.toLowerCase().startsWith(shortText.toLowerCase() + " ->")) {
                    abbrevListView.getSelectionModel().select(item);
                    abbrevListView.scrollTo(item);
                    break;
                }
            }
        });

        clearButton.setOnAction(e -> {
            clearFields(shortField, fullField);
            searchField.clear();
            abbrevListView.getSelectionModel().clearSelection();
        });

        refreshButton.setOnAction(e -> {
            updateListView(abbrevListView, searchField.getText().trim().toLowerCase());
            clearFields(shortField, fullField);
            updateListLabel(stage, abbrevListView);
        });

        // Dialog close handler
        stage.setOnHidden(e -> {
            if (parentApp != null) {
                System.out.println("Dialog closed. Notifying main application to refresh.");
                // parentApp.refreshAbbreviations(); // Uncomment when method exists
            }
        });
    }

    private boolean add(String shortText, String fullText) {
        if (shortText.isEmpty() || fullText.isEmpty()) {
            showAlert("Error", "Both fields must be filled.", Alert.AlertType.ERROR);
            return false;
        }
        
        if (abbrevMap.containsKey(shortText)) {
            showAlert("Warning", "Abbreviation '" + shortText + "' already exists. Use Update to modify.", Alert.AlertType.WARNING);
            return false;
        }

        String sql = "INSERT INTO abbreviations (short, full) VALUES (?, ?)";
        try (PreparedStatement pstmt = dbConn.prepareStatement(sql)) {
            pstmt.setString(1, shortText);
            pstmt.setString(2, fullText);
            int rowsAffected = pstmt.executeUpdate();
            
            if (rowsAffected > 0) {
                abbrevMap.put(shortText, fullText);
                showAlert("Success", "Abbreviation '" + shortText + "' added successfully.", Alert.AlertType.INFORMATION);
                return true;
            }
        } catch (SQLException e) {
            showAlert("Database Error", "Failed to add abbreviation: " + e.getMessage(), Alert.AlertType.ERROR);
        }
        return false;
    }

    private boolean edit(String originalShort, String newShort, String newFull) {
        if (newShort.isEmpty() || newFull.isEmpty()) {
            showAlert("Error", "Both fields must be filled.", Alert.AlertType.ERROR);
            return false;
        }

        // Check if new short form conflicts with existing (and it's not the same entry)
        if (!originalShort.equals(newShort) && abbrevMap.containsKey(newShort)) {
            showAlert("Error", "Cannot change to '" + newShort + "' - it already exists.", Alert.AlertType.ERROR);
            return false;
        }

        String sql = "UPDATE abbreviations SET short = ?, full = ? WHERE short = ?";
        try (PreparedStatement pstmt = dbConn.prepareStatement(sql)) {
            pstmt.setString(1, newShort);
            pstmt.setString(2, newFull);
            pstmt.setString(3, originalShort);
            int rowsAffected = pstmt.executeUpdate();
            
            if (rowsAffected > 0) {
                abbrevMap.remove(originalShort);
                abbrevMap.put(newShort, newFull);
                showAlert("Success", "Abbreviation updated successfully.", Alert.AlertType.INFORMATION);
                return true;
            } else {
                showAlert("Warning", "Original abbreviation '" + originalShort + "' not found.", Alert.AlertType.WARNING);
            }
        } catch (SQLException e) {
            showAlert("Database Error", "Failed to update abbreviation: " + e.getMessage(), Alert.AlertType.ERROR);
        }
        return false;
    }

    private boolean delete(String shortText) {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirm Deletion");
        confirmAlert.setHeaderText("Delete Abbreviation");
        confirmAlert.setContentText("Are you sure you want to delete '" + shortText + "' -> '" + 
                                   abbrevMap.get(shortText) + "'?");
        
        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            String sql = "DELETE FROM abbreviations WHERE short = ?";
            try (PreparedStatement pstmt = dbConn.prepareStatement(sql)) {
                pstmt.setString(1, shortText);
                int rowsAffected = pstmt.executeUpdate();
                
                if (rowsAffected > 0) {
                    abbrevMap.remove(shortText);
                    showAlert("Success", "Abbreviation '" + shortText + "' deleted successfully.", Alert.AlertType.INFORMATION);
                    return true;
                } else {
                    showAlert("Warning", "Abbreviation not found in database.", Alert.AlertType.WARNING);
                }
            } catch (SQLException e) {
                showAlert("Database Error", "Failed to delete abbreviation: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
        return false;
    }

    // ENHANCEMENT: Filtered list view with search capability
    private void updateListView(ListView<String> listView, String filter) {
        ObservableList<String> items = abbrevMap.entrySet().stream()
                .filter(entry -> filter.isEmpty() || 
                        entry.getKey().toLowerCase().contains(filter) ||
                        entry.getValue().toLowerCase().contains(filter))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + " -> " + entry.getValue())
                .collect(Collectors.toCollection(FXCollections::observableArrayList));
        listView.setItems(items);
    }

    private void updateListLabel(Stage stage, ListView<String> listView) {
        // Update window title with count
        stage.setTitle("Abbreviations Database Manager (" + abbrevMap.size() + " total, " + 
                      listView.getItems().size() + " shown)");
    }

    private void clearFields(TextField shortField, TextField fullField) {
        shortField.clear();
        fullField.clear();
        shortField.requestFocus();
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}