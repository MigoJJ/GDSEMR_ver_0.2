package com.emr.gds.fourgate.KCDdatabase;

import com.emr.gds.input.IAIMain;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class KCDDatabaseManagerJavaFX {

    private Stage stage;

    public Stage getStage() {
        return stage;
    }

    private static final String DB_PATH = "/home/migowj/git/GDSEMR_ver_0.2/app/src/main/resources/database/kcd_database.db";
    public static final String JDBC_URL = "jdbc:sqlite:" + DB_PATH;
    private static final DateTimeFormatter ISO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private TableView<KCDRecord> table;
    private ObservableList<KCDRecord> tableData = FXCollections.observableArrayList();
    private TextField searchField;
    private ComboBox<String> searchColumnCombo;
    private Button addButton, updateButton, deleteButton, refreshButton, copyButton, saveToEmrButton, quitButton; // Quit button declaration
    private Label statusLabel;

    private final String[] columnNames = {
            "Classification", "Disease Code", "Check Field",
            "Korean Name", "English Name", "Note"
    };


    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        // The stage title is now ideally set by the caller (IAMButtonAction)
        // initializeStage(primaryStage); // Removed: Title setting is handled by the caller.
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        root.setTop(createSearchPanel());
        root.setCenter(createTable());
        root.setBottom(createButtonPanel());

        Scene scene = new Scene(root, 1200, 800);
        primaryStage.setScene(scene);
        primaryStage.show();

        setupEventHandlers();
        loadInitialData();
    }

    // Removed: Title setting is handled by the caller (IAMButtonAction).
    // private void initializeStage(Stage stage) {
    //    stage.setTitle("KCD Database Manager");
    // }

    private TableView<KCDRecord> createTable() {
        table = new TableView<>();
        for (String colName : columnNames) {
            TableColumn<KCDRecord, String> column = new TableColumn<>(colName);
            column.setCellValueFactory(new PropertyValueFactory<>(toCamelCase(colName)));
            table.getColumns().add(column);
        }
        table.setItems(tableData);
        return table;
    }

    private FlowPane createSearchPanel() {
        FlowPane panel = new FlowPane(10, 10);
        panel.setPadding(new Insets(10));
        searchField = new TextField();
        searchField.setPromptText("Search...");
        searchColumnCombo = new ComboBox<>();
        searchColumnCombo.getItems().addAll("All Columns", "Classification", "Disease Code", "Check Field", "Korean Name", "English Name", "Note");
        searchColumnCombo.getSelectionModel().selectFirst();
        panel.getChildren().addAll(new Label("Search:"), searchField, searchColumnCombo);
        return panel;
    }

    private FlowPane createButtonPanel() {
        FlowPane panel = new FlowPane(10, 10);
        panel.setPadding(new Insets(10));
        addButton = new Button("Add");
        updateButton = new Button("Update");
        deleteButton = new Button("Delete");
        refreshButton = new Button("Refresh");
        copyButton = new Button("Copy");
        saveToEmrButton = new Button("Save to EMR");
        quitButton = new Button("Quit"); // Quit button creation
        statusLabel = new Label("Ready");
        panel.getChildren().addAll(addButton, updateButton, deleteButton, refreshButton, copyButton, saveToEmrButton, quitButton, statusLabel);
        return panel;
    }

    private void setupEventHandlers() {
        addButton.setOnAction(e -> showAddDialog());
        updateButton.setOnAction(e -> showUpdateDialog());
        deleteButton.setOnAction(e -> deleteSelectedRecord());
        refreshButton.setOnAction(e -> loadInitialData());
        copyButton.setOnAction(e -> copySelectedToClipboard());
        saveToEmrButton.setOnAction(e -> saveSelectedToEMR());
        quitButton.setOnAction(e -> {
            // Perform any specific cleanup for this manager before closing the stage
            // For example, if it had an open database connection unique to this instance:
            // if (this.localDbConnection != null) {
            //     try {
            //         this.localDbConnection.close();
            //     } catch (SQLException ex) {
            //         System.err.println("Error closing KCD manager's DB connection: " + ex.getMessage());
            //     }
            // }
            stage.close(); // Closes the primary stage of this manager
        });

        FilteredList<KCDRecord> filteredData = new FilteredList<>(tableData, p -> true);
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(record -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }
                String lowerCaseFilter = newValue.toLowerCase();
                int selectedIndex = searchColumnCombo.getSelectionModel().getSelectedIndex();
                if (selectedIndex <= 0) { // All columns
                    return record.toString().toLowerCase().contains(lowerCaseFilter);
                } else {
                    String property = toCamelCase(columnNames[selectedIndex - 1]);
                    try {
                        return ((String) record.getClass().getMethod("get" + property.substring(0, 1).toUpperCase() + property.substring(1)).invoke(record)).toLowerCase().contains(lowerCaseFilter);
                    } catch (Exception ex) {
                        System.err.println("Error during search filtering: " + ex.getMessage());
                        return false;
                    }
                }
            });
        });

        SortedList<KCDRecord> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sortedData);

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            boolean rowSelected = newSelection != null;
            updateButton.setDisable(!rowSelected);
            deleteButton.setDisable(!rowSelected);
            copyButton.setDisable(!rowSelected);
            saveToEmrButton.setDisable(!rowSelected);
        });
    }

    private void loadInitialData() {
        Task<List<KCDRecord>> task = new Task<>() {
            @Override
            protected List<KCDRecord> call() throws Exception {
                updateStatus("Loading data...");
                return DatabaseManager.getAllRecords();
            }
        };
        task.setOnSucceeded(e -> {
            tableData.setAll(task.getValue());
            updateStatus("Loaded " + tableData.size() + " records.");
        });
        task.setOnFailed(e -> {
            showErrorDialog("Database Error", "Failed to load data: " + task.getException().getMessage());
            updateStatus("Error loading data.");
            task.getException().printStackTrace();
        });
        new Thread(task).start();
    }

    private void showAddDialog() {
        KCDRecordDialog dialog = new KCDRecordDialog("Add New Record", null);
        dialog.showAndWait().ifPresent(record -> {
            try {
                DatabaseManager.addRecord(record);
                loadInitialData();
            } catch (SQLException e) {
                showErrorDialog("Database Error", "Could not add record: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private void showUpdateDialog() {
        KCDRecord selectedRecord = table.getSelectionModel().getSelectedItem();
        if (selectedRecord == null) return;

        KCDRecordDialog dialog = new KCDRecordDialog("Update Record", selectedRecord);
        dialog.showAndWait().ifPresent(record -> {
            try {
                DatabaseManager.updateRecord(selectedRecord.getDiseaseCode(), record);
                loadInitialData();
            } catch (SQLException e) {
                showErrorDialog("Database Error", "Could not update record: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private void deleteSelectedRecord() {
        KCDRecord selectedRecord = table.getSelectionModel().getSelectedItem();
        if (selectedRecord == null) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to delete this record?", ButtonType.YES, ButtonType.NO);
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                try {
                    DatabaseManager.deleteRecord(selectedRecord.getDiseaseCode());
                    loadInitialData();
                } catch (SQLException e) {
                    showErrorDialog("Database Error", "Could not delete record: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }

    private void copySelectedToClipboard() {
        KCDRecord selectedRecord = table.getSelectionModel().getSelectedItem();
        if (selectedRecord == null) return;

        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(selectedRecord.toFormattedString());
        clipboard.setContent(content);
        updateStatus("Record copied to clipboard.");
    }

    private void saveSelectedToEMR() {
        KCDRecord selectedRecord = table.getSelectionModel().getSelectedItem();
        if (selectedRecord == null) return;

        try {
            String timestamp = LocalDate.now().format(ISO_DATE_FORMAT);
            String emrEntry = String.format("\n< KCD > %s\n%s", timestamp, selectedRecord.toEMRFormat());

            IAIMain.getTextAreaManager().focusArea(7);
            IAIMain.getTextAreaManager().insertLineIntoFocusedArea("\t" + emrEntry);
            updateStatus("Record saved to EMR.");
        } catch (Exception e) {
            showErrorDialog("EMR Save Error", "Error saving to EMR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateStatus(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }

    private void showErrorDialog(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private String toCamelCase(String s) {
        String[] parts = s.split(" ");
        StringBuilder camelCaseString = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                camelCaseString.append(part.substring(0, 1).toUpperCase()).append(part.substring(1).toLowerCase());
            }
        }
        if (camelCaseString.length() > 0) {
            return camelCaseString.substring(0, 1).toLowerCase() + camelCaseString.substring(1);
        }
        return "";
    }
}