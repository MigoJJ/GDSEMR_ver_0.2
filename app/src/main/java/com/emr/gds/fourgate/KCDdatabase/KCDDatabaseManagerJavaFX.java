package com.emr.gds.fourgate.KCDdatabase;

import com.emr.gds.input.IAIMain;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class KCDDatabaseManagerJavaFX {

    private Stage stage;
    public Stage getStage() { return stage; }

    private static final String DB_PATH = "/home/migowj/git/GDSEMR_ver_0.2/app/src/main/resources/database/kcd_database.db";
    public static final String JDBC_URL = "jdbc:sqlite:" + DB_PATH;
    private static final DateTimeFormatter ISO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private TableView<KCDRecord> table;
    private final ObservableList<KCDRecord> tableData = FXCollections.observableArrayList();
    private TextField searchField;
    private ComboBox<String> searchColumnCombo;
    private Button addButton, editButton, deleteButton, refreshButton, copyButton, saveToEmrButton, quitButton;
    private Label statusLabel;

    private final String[] columnNames = {"Classification", "Disease Code", "Check Field", "Korean Name", "English Name", "Note"};
    private final double[] columnWidths = {100, 100, 80, 250, 250, 300};

    public void start(Stage primaryStage) {
        this.stage = primaryStage;
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

    private TableView<KCDRecord> createTable() {
        table = new TableView<>();
        for (int i = 0; i < columnNames.length; i++) {
            TableColumn<KCDRecord, String> column = new TableColumn<>(columnNames[i]);
            column.setCellValueFactory(new PropertyValueFactory<>(toCamelCase(columnNames[i])));
            column.setPrefWidth(columnWidths[i]);
            table.getColumns().add(column);
        }
        table.setItems(tableData);
        return table;
    }

    private HBox createSearchPanel() {
        HBox searchPanel = new HBox(10);
        searchPanel.setPadding(new Insets(10));
        searchPanel.setAlignment(Pos.CENTER_LEFT);
        searchField = new TextField();
        searchField.setPromptText("Search...");
        searchField.setPrefWidth(300);
        searchColumnCombo = new ComboBox<>();
        searchColumnCombo.getItems().addAll("All Columns", "Classification", "Disease Code", "Check Field", "Korean Name", "English Name", "Note");
        searchColumnCombo.getSelectionModel().selectFirst();
        searchPanel.getChildren().addAll(new Label("Search:"), searchField, searchColumnCombo);
        return searchPanel;
    }

    private VBox createButtonPanel() {
        HBox topButtons = new HBox(10);
        addButton = new Button("Add");
        editButton = new Button("Edit");
        deleteButton = new Button("Delete");
        refreshButton = new Button("Refresh");
        topButtons.getChildren().addAll(addButton, editButton, deleteButton, refreshButton);

        HBox bottomButtons = new HBox(10);
        copyButton = new Button("Copy to Clipboard");
        saveToEmrButton = new Button("Save to EMR");
        quitButton = new Button("Quit");
        bottomButtons.getChildren().addAll(copyButton, saveToEmrButton, quitButton);

        statusLabel = new Label("Ready");
        HBox statusPanel = new HBox(statusLabel);
        HBox.setHgrow(statusPanel, Priority.ALWAYS);
        statusPanel.setAlignment(Pos.CENTER_LEFT);

        VBox buttonLayout = new VBox(10, topButtons, bottomButtons, new Separator(), statusPanel);
        buttonLayout.setPadding(new Insets(10));
        return buttonLayout;
    }

    private void setupEventHandlers() {
        addButton.setOnAction(e -> showEditDialog(null));
        editButton.setOnAction(e -> showEditDialog(table.getSelectionModel().getSelectedItem()));
        deleteButton.setOnAction(e -> deleteSelectedRecord());
        refreshButton.setOnAction(e -> loadInitialData());
        copyButton.setOnAction(e -> copySelectedToClipboard());
        saveToEmrButton.setOnAction(e -> saveSelectedToEMR());
        quitButton.setOnAction(e -> stage.close());

        FilteredList<KCDRecord> filteredData = new FilteredList<>(tableData, p -> true);
        searchField.textProperty().addListener((obs, ov, nv) -> filteredData.setPredicate(createPredicate(nv)));
        searchColumnCombo.getSelectionModel().selectedItemProperty().addListener((obs, ov, nv) -> filteredData.setPredicate(createPredicate(searchField.getText())));

        SortedList<KCDRecord> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sortedData);

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            boolean rowSelected = newSelection != null;
            editButton.setDisable(!rowSelected);
            deleteButton.setDisable(!rowSelected);
            copyButton.setDisable(!rowSelected);
            saveToEmrButton.setDisable(!rowSelected);
        });
        editButton.setDisable(true);
        deleteButton.setDisable(true);
        copyButton.setDisable(true);
        saveToEmrButton.setDisable(true);
    }

    private java.util.function.Predicate<KCDRecord> createPredicate(String filterText) {
        return record -> {
            if (filterText == null || filterText.isEmpty()) return true;
            String lowerCaseFilter = filterText.toLowerCase();
            int selectedIndex = searchColumnCombo.getSelectionModel().getSelectedIndex();

            if (selectedIndex <= 0) { // All Columns
                return record.toString().toLowerCase().contains(lowerCaseFilter);
            } else {
                String property = toCamelCase(columnNames[selectedIndex - 1]);
                try {
                    String value = (String) record.getClass().getMethod("get" + property.substring(0, 1).toUpperCase() + property.substring(1)).invoke(record);
                    return value != null && value.toLowerCase().contains(lowerCaseFilter);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    return false;
                }
            }
        };
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

    private void showEditDialog(KCDRecord recordToEdit) {
        boolean isUpdate = recordToEdit != null;
        String title = isUpdate ? "Edit Record" : "Add New Record";
        KCDRecordDialog dialog = new KCDRecordDialog(title, recordToEdit);
        if (isUpdate) {
            dialog.setDiseaseCodeEditable(false);
        }

        Optional<KCDRecord> result = dialog.showAndWait();
        result.ifPresent(record -> {
            try {
                if (isUpdate) {
                    DatabaseManager.updateRecord(recordToEdit.getDiseaseCode(), record);
                } else {
                    DatabaseManager.addRecord(record);
                }
                loadInitialData();
            } catch (SQLException e) {
                showErrorDialog("Database Error", "Could not save record: " + e.getMessage());
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