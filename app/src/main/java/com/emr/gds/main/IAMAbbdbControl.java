package com.emr.gds.main;

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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Controller for the Abbreviations Database Manager dialog.
 * This class provides a UI for adding, editing, deleting, and searching abbreviations
 * stored in the application's database.
 */
public class IAMAbbdbControl {

    private final Connection dbConn;
    private final Map<String, String> abbrevMap;
    private final Stage ownerStage;

    // UI Elements
    private final TextField shortField = new TextField();
    private final TextField fullField = new TextField();
    private final TextField searchField = new TextField();
    private final ListView<String> abbrevListView = new ListView<>();
    private final Button addButton = new Button("Add");
    private final Button updateButton = new Button("Update");
    private final Button deleteButton = new Button("Delete");
    private final Button clearButton = new Button("Clear");

    public IAMAbbdbControl(Connection dbConn, Map<String, String> abbrevMap, Stage ownerStage, IttiaApp parentApp) {
        this.dbConn = dbConn;
        this.abbrevMap = abbrevMap;
        this.ownerStage = ownerStage;
    }

    /**
     * Creates and displays the modal dialog for managing abbreviations.
     */
    public void showDbManagerDialog() {
        Stage dialogStage = createStage();
        VBox root = createLayout(dialogStage);
        setupEventHandlers(dialogStage);

        Scene scene = new Scene(root);
        dialogStage.setScene(scene);
        dialogStage.showAndWait();
    }

    private Stage createStage() {
        Stage stage = new Stage();
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(ownerStage);
        stage.setTitle("Abbreviations Database Manager");
        stage.setMinWidth(500);
        stage.setMinHeight(500);
        return stage;
    }

    private VBox createLayout(Stage stage) {
        shortField.setPromptText("Short Form (e.g., 'cp')");
        fullField.setPromptText("Full Expansion (e.g., 'chest pain')");
        searchField.setPromptText("Search abbreviations...");

        HBox inputFields = new HBox(10, shortField, fullField);
        HBox.setHgrow(fullField, Priority.ALWAYS);

        HBox actionButtons = new HBox(10, addButton, updateButton, deleteButton, new Separator(), clearButton);

        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.getChildren().addAll(
                new Label("Search:"),
                searchField,
                new Label("Abbreviations List:"),
                abbrevListView,
                new Label("Add/Edit Abbreviation:"),
                inputFields,
                actionButtons
        );

        updateListView("");
        updateDialogTitle(stage);
        return root;
    }

    private void setupEventHandlers(Stage stage) {
        // Disable update/delete buttons when no item is selected
        updateButton.disableProperty().bind(abbrevListView.getSelectionModel().selectedItemProperty().isNull());
        deleteButton.disableProperty().bind(abbrevListView.getSelectionModel().selectedItemProperty().isNull());

        // Real-time search functionality
        searchField.textProperty().addListener((obs, oldVal, newVal) -> updateListView(newVal.trim().toLowerCase()));

        // Populate text fields when an item is selected from the list
        abbrevListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                String[] parts = newVal.split(" -> ", 2);
                if (parts.length == 2) {
                    shortField.setText(parts[0]);
                    fullField.setText(parts[1]);
                }
            } else {
                clearInputFields();
            }
        });

        // Keyboard shortcuts for input fields
        shortField.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) fullField.requestFocus(); });
        fullField.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) getEffectiveButton().fire(); });

        // Button actions
        addButton.setOnAction(e -> handleAddAction(stage));
        updateButton.setOnAction(e -> handleUpdateAction(stage));
        deleteButton.setOnAction(e -> handleDeleteAction(stage));
        clearButton.setOnAction(e -> handleClearAction());
    }

    // ================================
    // Action Handlers
    // ================================

    private void handleAddAction(Stage stage) {
        if (addEntry(shortField.getText().trim(), fullField.getText().trim())) {
            updateListView(searchField.getText().trim().toLowerCase());
            clearInputFields();
            updateDialogTitle(stage);
        }
    }

    private void handleUpdateAction(Stage stage) {
        String selectedItem = abbrevListView.getSelectionModel().getSelectedItem();
        if (selectedItem == null) return;

        String originalShortText = selectedItem.split(" -> ", 2)[0];
        if (updateEntry(originalShortText, shortField.getText().trim(), fullField.getText().trim())) {
            updateListView(searchField.getText().trim().toLowerCase());
            updateDialogTitle(stage);
        }
    }

    private void handleDeleteAction(Stage stage) {
        String shortText = shortField.getText().trim();
        if (!shortText.isEmpty() && deleteEntry(shortText)) {
            updateListView(searchField.getText().trim().toLowerCase());
            clearInputFields();
            updateDialogTitle(stage);
        }
    }

    private void handleClearAction() {
        clearInputFields();
        searchField.clear();
        abbrevListView.getSelectionModel().clearSelection();
    }

    // ================================
    // Database Operations
    // ================================

    private boolean addEntry(String shortText, String fullText) {
        if (shortText.isEmpty() || fullText.isEmpty()) {
            showAlert("Input Error", "Both short and full forms must be provided.", Alert.AlertType.ERROR);
            return false;
        }
        if (abbrevMap.containsKey(shortText)) {
            showAlert("Duplicate Entry", "The abbreviation '" + shortText + "' already exists.", Alert.AlertType.WARNING);
            return false;
        }

        String sql = "INSERT INTO abbreviations (short, full) VALUES (?, ?)";
        try (PreparedStatement pstmt = dbConn.prepareStatement(sql)) {
            pstmt.setString(1, shortText);
            pstmt.setString(2, fullText);
            pstmt.executeUpdate();
            abbrevMap.put(shortText, fullText); // Update in-memory map
            return true;
        } catch (SQLException e) {
            showAlert("Database Error", "Failed to add abbreviation: " + e.getMessage(), Alert.AlertType.ERROR);
            return false;
        }
    }

    private boolean updateEntry(String originalShort, String newShort, String newFull) {
        if (newShort.isEmpty() || newFull.isEmpty()) {
            showAlert("Input Error", "Both short and full forms must be provided.", Alert.AlertType.ERROR);
            return false;
        }
        if (!originalShort.equals(newShort) && abbrevMap.containsKey(newShort)) {
            showAlert("Duplicate Entry", "Cannot change short form to '" + newShort + "' as it already exists.", Alert.AlertType.ERROR);
            return false;
        }

        String sql = "UPDATE abbreviations SET short = ?, full = ? WHERE short = ?";
        try (PreparedStatement pstmt = dbConn.prepareStatement(sql)) {
            pstmt.setString(1, newShort);
            pstmt.setString(2, newFull);
            pstmt.setString(3, originalShort);
            pstmt.executeUpdate();
            abbrevMap.remove(originalShort);
            abbrevMap.put(newShort, newFull);
            return true;
        } catch (SQLException e) {
            showAlert("Database Error", "Failed to update abbreviation: " + e.getMessage(), Alert.AlertType.ERROR);
            return false;
        }
    }

    private boolean deleteEntry(String shortText) {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to delete '" + shortText + "'?", ButtonType.YES, ButtonType.NO);
        Optional<ButtonType> result = confirmAlert.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.YES) {
            String sql = "DELETE FROM abbreviations WHERE short = ?";
            try (PreparedStatement pstmt = dbConn.prepareStatement(sql)) {
                pstmt.setString(1, shortText);
                if (pstmt.executeUpdate() > 0) {
                    abbrevMap.remove(shortText);
                    return true;
                }
            } catch (SQLException e) {
                showAlert("Database Error", "Failed to delete abbreviation: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
        return false;
    }

    // ================================
    // UI Helper Methods
    // ================================

    private void updateListView(String filter) {
        ObservableList<String> items = abbrevMap.entrySet().stream()
                .filter(entry -> filter.isEmpty() || entry.getKey().toLowerCase().contains(filter) || entry.getValue().toLowerCase().contains(filter))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + " -> " + entry.getValue())
                .collect(Collectors.toCollection(FXCollections::observableArrayList));
        abbrevListView.setItems(items);
    }

    private void updateDialogTitle(Stage stage) {
        stage.setTitle(String.format("Abbreviations Manager (%d entries)", abbrevMap.size()));
    }

    private void clearInputFields() {
        shortField.clear();
        fullField.clear();
        shortField.requestFocus();
    }

    private Button getEffectiveButton() {
        return abbrevListView.getSelectionModel().isEmpty() ? addButton : updateButton;
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
