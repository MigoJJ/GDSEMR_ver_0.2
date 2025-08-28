package com.emr.gds;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Handles the UI and logic for managing the abbreviations database.
 * This class provides a modal dialog for adding, editing, and deleting
 * abbreviations, and for viewing the full list.
 */
public class AbbdbControl {

    private final Connection dbConn;
    private final Map<String, String> abbrevMap;
    private final Stage ownerStage;
    private final IttiaApp parentApp; // Reference to the main app for UI updates

    public AbbdbControl(Connection dbConn, Map<String, String> abbrevMap, Stage ownerStage, IttiaApp parentApp) {
        this.dbConn = dbConn;
        this.abbrevMap = abbrevMap;
        this.ownerStage = ownerStage;
        this.parentApp = parentApp;
    }

    /**
     * Shows a modal dialog for database management.
     */
    public void showDbManagerDialog() {
        Stage stage = new Stage();
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(ownerStage);
        stage.setTitle("Abbreviations Database Manager");
        stage.setMinWidth(450);
        stage.setMinHeight(450);

        // --- UI Elements ---
        TextField shortField = new TextField();
        shortField.setPromptText("Short Form");
        TextField fullField = new TextField();
        fullField.setPromptText("Full Expansion");

        Button findButton = new Button("Find");
        Button addButton = new Button("Add");
        Button editButton = new Button("Edit");
        Button deleteButton = new Button("Delete");
        Button refreshButton = new Button("Refresh");

        ListView<String> abbrevListView = new ListView<>();
        updateListView(abbrevListView);

        // --- Layout using VBox for simplicity and better alignment ---
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));

        HBox inputFields = new HBox(10, shortField, fullField);
        HBox actionButtons = new HBox(10, findButton, addButton, editButton, deleteButton, refreshButton);

        root.getChildren().addAll(
                new Label("Abbreviations List:"),
                abbrevListView,
                inputFields,
                actionButtons
        );
        
        // --- Logic and Event Handlers ---

        // **IMPROVEMENT**: Disable Edit/Delete buttons when nothing is selected.
        editButton.disableProperty().bind(abbrevListView.getSelectionModel().selectedItemProperty().isNull());
        deleteButton.disableProperty().bind(abbrevListView.getSelectionModel().selectedItemProperty().isNull());

        // Populate text fields when an item is selected from the list.
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

        addButton.setOnAction(e -> {
            add(shortField.getText().trim(), fullField.getText().trim());
            updateListView(abbrevListView);
            clearFields(shortField, fullField);
        });

        // **CRITICAL FIX**: Edit logic is now based on the selected item's original key.
        editButton.setOnAction(e -> {
            String selectedItem = abbrevListView.getSelectionModel().getSelectedItem();
            if (selectedItem == null) return;

            String originalShortText = selectedItem.split(" -> ", 2)[0];
            String newShortText = shortField.getText().trim();
            String newFullText = fullField.getText().trim();
            
            // This updated 'edit' call can now handle changes to the key itself.
            edit(originalShortText, newShortText, newFullText);
            updateListView(abbrevListView);
        });

        deleteButton.setOnAction(e -> {
            String shortText = shortField.getText().trim();
            if (!shortText.isEmpty()) {
                delete(shortText);
                updateListView(abbrevListView);
                clearFields(shortField, fullField);
            }
        });

        findButton.setOnAction(e -> {
            String shortText = shortField.getText().trim();
            if (shortText.isEmpty()) return;

            for (String item : abbrevListView.getItems()) {
                if (item.startsWith(shortText + " ->")) {
                    abbrevListView.getSelectionModel().select(item);
                    abbrevListView.scrollTo(item);
                    break;
                }
            }
        });

        refreshButton.setOnAction(e -> {
            updateListView(abbrevListView);
            clearFields(shortField, fullField);
        });
        
        // **IMPROVEMENT**: Notify the main application when the dialog is closed.
        stage.setOnHidden(e -> {
            if (parentApp != null) {
                // Assuming your IttiaApp class has a method to refresh its data/UI.
                // parentApp.refreshData(); 
                System.out.println("Dialog closed. Notifying main application to refresh.");
            }
        });

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.showAndWait(); // Use showAndWait to block owner until this dialog is closed.
    }

    /**
     * Adds a new abbreviation to the database and map.
     */
    private void add(String shortText, String fullText) {
        if (shortText.isEmpty() || fullText.isEmpty()) {
            showAlert("Error", "Both fields must be filled.", Alert.AlertType.ERROR);
            return;
        }

        String sql = "INSERT OR IGNORE INTO abbreviations (short, full) VALUES (?, ?)";
        try (PreparedStatement pstmt = dbConn.prepareStatement(sql)) {
            pstmt.setString(1, shortText);
            pstmt.setString(2, fullText);
            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                abbrevMap.put(shortText, fullText);
                showAlert("Success", "Abbreviation added.", Alert.AlertType.INFORMATION);
            } else {
                showAlert("Warning", "Abbreviation '" + shortText + "' already exists.", Alert.AlertType.WARNING);
            }
        } catch (SQLException e) {
            showAlert("Database Error", "Failed to add abbreviation: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    /**
     * Edits an existing abbreviation in the database and map.
     * This version can handle changes to the primary key ('short' text).
     */
    private void edit(String originalShort, String newShort, String newFull) {
        if (newShort.isEmpty() || newFull.isEmpty()) {
            showAlert("Error", "Both fields must be filled.", Alert.AlertType.ERROR);
            return;
        }

        String sql = "UPDATE abbreviations SET short = ?, full = ? WHERE short = ?";
        try (PreparedStatement pstmt = dbConn.prepareStatement(sql)) {
            pstmt.setString(1, newShort);
            pstmt.setString(2, newFull);
            pstmt.setString(3, originalShort);
            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                // Update the in-memory map
                abbrevMap.remove(originalShort);
                abbrevMap.put(newShort, newFull);
                showAlert("Success", "Abbreviation updated.", Alert.AlertType.INFORMATION);
            } else {
                showAlert("Warning", "Original abbreviation '" + originalShort + "' not found.", Alert.AlertType.WARNING);
            }
        } catch (SQLException e) {
            showAlert("Database Error", "Failed to edit abbreviation: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    /**
     * Deletes an abbreviation from the database and map.
     */
    private void delete(String shortText) {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirmation");
        confirmAlert.setHeaderText("Delete Abbreviation");
        confirmAlert.setContentText("Are you sure you want to delete '" + shortText + "'?");
        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            String sql = "DELETE FROM abbreviations WHERE short = ?";
            try (PreparedStatement pstmt = dbConn.prepareStatement(sql)) {
                pstmt.setString(1, shortText);
                int rowsAffected = pstmt.executeUpdate();
                if (rowsAffected > 0) {
                    abbrevMap.remove(shortText);
                    showAlert("Success", "Abbreviation deleted.", Alert.AlertType.INFORMATION);
                } else {
                    showAlert("Warning", "Abbreviation not found.", Alert.AlertType.WARNING);
                }
            } catch (SQLException e) {
                showAlert("Database Error", "Failed to delete abbreviation: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }

    /**
     * Populates the ListView with all abbreviations, sorted by key.
     */
    private void updateListView(ListView<String> listView) {
        ObservableList<String> items = abbrevMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + " -> " + entry.getValue())
                .collect(Collectors.toCollection(FXCollections::observableArrayList));

        listView.setItems(items);
    }

    /**
     * Clears the input fields and requests focus on the first field.
     */
    private void clearFields(TextField shortField, TextField fullField) {
        shortField.clear();
        fullField.clear();
        shortField.requestFocus();
    }

    /**
     * Helper method to show a user-facing alert.
     */
    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}