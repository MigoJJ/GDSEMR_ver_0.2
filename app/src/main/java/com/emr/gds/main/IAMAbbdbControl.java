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
import javafx.scene.control.*;
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

    // UI Elements
    private final TextField shortField = new TextField();
    private final TextField fullField = new TextField();
    private final TextField searchField = new TextField();
    private final ListView<String> abbrevListView = new ListView<>();
    private final Button addButton = new Button("Add");
    private final Button editButton = new Button("Update");
    private final Button deleteButton = new Button("Delete");
    private final Button clearButton = new Button("Clear");

    public IAMAbbdbControl(Connection dbConn, Map<String, String> abbrevMap, Stage ownerStage, IttiaApp parentApp) {
        this.dbConn = dbConn;
        this.abbrevMap = abbrevMap;
        this.ownerStage = ownerStage;
        this.parentApp = parentApp;
    }

    public void showDbManagerDialog() {
        Stage stage = createStage();
        VBox root = createLayout();
        setupEventHandlers(stage);
        
        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.showAndWait();
    }
    
    // Explanation: The main entry point `showDbManagerDialog()` is now more
    // concise by delegating the responsibilities of stage creation, layout,
    // and event handling to dedicated private methods.

    private Stage createStage() {
        Stage stage = new Stage();
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(ownerStage);
        stage.setTitle("Abbreviations Database Manager");
        stage.setMinWidth(500);
        stage.setMinHeight(500);
        return stage;
    }

    private VBox createLayout() {
        shortField.setPromptText("Short Form (e.g., 'cp')");
        shortField.setPrefWidth(120);
        fullField.setPromptText("Full Expansion (e.g., 'chest pain')");
        searchField.setPromptText("Search abbreviations...");
        abbrevListView.setPrefHeight(250);
        updateListView("");

        HBox inputFields = new HBox(10, shortField, fullField);
        HBox.setHgrow(fullField, Priority.ALWAYS);

        HBox actionButtons = new HBox(10, addButton, editButton, deleteButton, new Label("|"), clearButton);

        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.getChildren().addAll(
            new Label("Search:"), searchField,
            new Label("Abbreviations List (" + abbrevMap.size() + " entries):"),
            abbrevListView,
            new Label("Add/Edit Abbreviation:"), inputFields,
            actionButtons
        );
        return root;
    }
    
    // Explanation: UI component creation and layout are now consolidated into
    // `createLayout()`. Unnecessary buttons like "Find" and "Refresh" are
    // removed because their functionality is now handled by the real-time search
    // and list view updates.

    private void setupEventHandlers(Stage stage) {
        editButton.disableProperty().bind(abbrevListView.getSelectionModel().selectedItemProperty().isNull());
        deleteButton.disableProperty().bind(abbrevListView.getSelectionModel().selectedItemProperty().isNull());

        searchField.textProperty().addListener((obs, oldVal, newVal) -> updateListView(newVal.trim().toLowerCase()));

        abbrevListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                clearFields();
            } else {
                String[] parts = newVal.split(" -> ", 2);
                if (parts.length == 2) {
                    shortField.setText(parts[0]);
                    fullField.setText(parts[1]);
                }
            }
        });

        shortField.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) fullField.requestFocus(); });
        fullField.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) getEffectiveButton().fire(); });
        
        // Explanation: The logic for keyboard shortcuts is now more compact.
        // A new method `getEffectiveButton()` is introduced to decide whether
        // to fire the Add or Update action based on the current selection.

        addButton.setOnAction(e -> {
            if (add(shortField.getText().trim(), fullField.getText().trim())) {
                updateListView(searchField.getText().trim().toLowerCase());
                clearFields();
                updateListLabel(stage);
            }
        });

        editButton.setOnAction(e -> {
            String selectedItem = abbrevListView.getSelectionModel().getSelectedItem();
            if (selectedItem == null) return;
            String originalShortText = selectedItem.split(" -> ", 2)[0];
            if (edit(originalShortText, shortField.getText().trim(), fullField.getText().trim())) {
                updateListView(searchField.getText().trim().toLowerCase());
                updateListLabel(stage);
            }
        });

        deleteButton.setOnAction(e -> {
            String shortText = shortField.getText().trim();
            if (!shortText.isEmpty() && delete(shortText)) {
                updateListView(searchField.getText().trim().toLowerCase());
                clearFields();
                updateListLabel(stage);
            }
        });

        clearButton.setOnAction(e -> {
            clearFields();
            searchField.clear();
            abbrevListView.getSelectionModel().clearSelection();
        });

        stage.setOnHidden(e -> {
            if (parentApp != null) {
                System.out.println("Dialog closed. Notifying main application to refresh.");
                // parentApp.refreshAbbreviations();
            }
        });
    }

    private Button getEffectiveButton() {
        return abbrevListView.getSelectionModel().isEmpty() ? addButton : editButton;
    }
    
    private boolean add(String shortText, String fullText) {
        if (shortText.isEmpty() || fullText.isEmpty()) {
            showAlert("Error", "Both fields must be filled.", Alert.AlertType.ERROR);
            return false;
        }
        if (abbrevMap.containsKey(shortText)) {
            showAlert("Warning", "Abbreviation already exists.", Alert.AlertType.WARNING);
            return false;
        }
        String sql = "INSERT INTO abbreviations (short, full) VALUES (?, ?)";
        try (PreparedStatement pstmt = dbConn.prepareStatement(sql)) {
            pstmt.setString(1, shortText);
            pstmt.setString(2, fullText);
            abbrevMap.put(shortText, fullText);
            showAlert("Success", "Abbreviation added.", Alert.AlertType.INFORMATION);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            showAlert("Database Error", "Failed to add: " + e.getMessage(), Alert.AlertType.ERROR);
            return false;
        }
    }

    private boolean edit(String originalShort, String newShort, String newFull) {
        if (newShort.isEmpty() || newFull.isEmpty()) {
            showAlert("Error", "Both fields must be filled.", Alert.AlertType.ERROR);
            return false;
        }
        if (!originalShort.equals(newShort) && abbrevMap.containsKey(newShort)) {
            showAlert("Error", "Cannot change short form to one that already exists.", Alert.AlertType.ERROR);
            return false;
        }
        String sql = "UPDATE abbreviations SET short = ?, full = ? WHERE short = ?";
        try (PreparedStatement pstmt = dbConn.prepareStatement(sql)) {
            pstmt.setString(1, newShort);
            pstmt.setString(2, newFull);
            pstmt.setString(3, originalShort);
            abbrevMap.remove(originalShort);
            abbrevMap.put(newShort, newFull);
            showAlert("Success", "Abbreviation updated.", Alert.AlertType.INFORMATION);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            showAlert("Database Error", "Failed to update: " + e.getMessage(), Alert.AlertType.ERROR);
            return false;
        }
    }

    private boolean delete(String shortText) {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to delete '" + shortText + "'?", ButtonType.YES, ButtonType.NO);
        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            String sql = "DELETE FROM abbreviations WHERE short = ?";
            try (PreparedStatement pstmt = dbConn.prepareStatement(sql)) {
                pstmt.setString(1, shortText);
                abbrevMap.remove(shortText);
                showAlert("Success", "Abbreviation deleted.", Alert.AlertType.INFORMATION);
                return pstmt.executeUpdate() > 0;
            } catch (SQLException e) {
                showAlert("Database Error", "Failed to delete: " + e.getMessage(), Alert.AlertType.ERROR);
                return false;
            }
        }
        return false;
    }
    
    // Explanation: The add, edit, and delete methods are simplified by moving
    // the UI-related alert calls and map updates into the database logic. The
    // confirmation dialog is also made more concise.

    private void updateListView(String filter) {
        ObservableList<String> items = abbrevMap.entrySet().stream()
                .filter(entry -> filter.isEmpty() || entry.getKey().toLowerCase().contains(filter) || entry.getValue().toLowerCase().contains(filter))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + " -> " + entry.getValue())
                .collect(Collectors.toCollection(FXCollections::observableArrayList));
        abbrevListView.setItems(items);
    }
    
    private void updateListLabel(Stage stage) {
        stage.setTitle(String.format("Abbreviations Database Manager (%d total, %d shown)", abbrevMap.size(), abbrevListView.getItems().size()));
    }

    private void clearFields() {
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