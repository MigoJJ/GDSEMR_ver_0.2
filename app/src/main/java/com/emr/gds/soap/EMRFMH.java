package com.emr.gds.soap;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.embed.swing.JFXPanel;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * An EMR Family Medical History (FMH) input form for an Endocrinologist.
 * This version supports multiple condition categories with add, edit, delete, find, and save functionality.
 */
public class EMRFMH extends JFrame {

    private final JTextArea historyTextArea;

    // Data lists for each category
    private ObservableList<String> endocrineConditions;
    private ObservableList<String> cancerConditions;
    private ObservableList<String> cardiovascularConditions;
    private ObservableList<String> geneticConditions;

    // File paths for saving/loading custom condition lists
    private static final Path DATA_DIR = Paths.get("emr_fmh_data");
    private static final Path ENDOCRINE_FILE = DATA_DIR.resolve("endocrine.txt");
    private static final Path CANCER_FILE = DATA_DIR.resolve("cancer.txt");
    private static final Path CARDIO_FILE = DATA_DIR.resolve("cardiovascular.txt");
    private static final Path GENETIC_FILE = DATA_DIR.resolve("genetic.txt");


    public EMRFMH() {
        setTitle("Endocrinology - Family Medical History");
        setSize(950, 850); // Increased size for the new layout
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Load all condition lists from files or defaults
        loadAllConditions();

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        historyTextArea = new JTextArea(15, 80);
        historyTextArea.setEditable(false);
        historyTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        historyTextArea.setBorder(BorderFactory.createTitledBorder("Family History Report"));
        mainPanel.add(new JScrollPane(historyTextArea), BorderLayout.CENTER);

        final JFXPanel fxPanel = new JFXPanel();
        mainPanel.add(fxPanel, BorderLayout.NORTH);

        setContentPane(mainPanel);

        Platform.runLater(() -> initFX(fxPanel));
    }

    private void initFX(JFXPanel fxPanel) {
        VBox root = createFamilyHistoryForm();
        Scene scene = new Scene(root);
        fxPanel.setScene(scene);
    }

    /**
     * Creates the entire JavaFX form, including controls and condition lists.
     */
    private VBox createFamilyHistoryForm() {
        VBox rootLayout = new VBox(20);
        rootLayout.setPadding(new Insets(20));
        rootLayout.setStyle("-fx-font-size: 14px;");

        // --- 1. Patient Info Section ---
        GridPane patientInfoGrid = new GridPane();
        patientInfoGrid.setHgap(10);
        patientInfoGrid.setVgap(10);

        Label relationshipLabel = new Label("Relationship:");
        ComboBox<String> relationshipComboBox = new ComboBox<>(FXCollections.observableArrayList(
                "Mother", "Father", "Sister", "Brother", "Maternal Grandmother", "Maternal Grandfather",
                "Paternal Grandmother", "Paternal Grandfather", "Aunt", "Uncle", "Cousin", "Child"
        ));
        relationshipComboBox.setPromptText("Select a relative");
        relationshipComboBox.setPrefWidth(300);

        Label notesLabel = new Label("General Notes:");
        TextArea notesTextArea = new TextArea();
        notesTextArea.setPromptText("e.g., Age of onset, lifestyle factors...");
        notesTextArea.setPrefRowCount(2);

        patientInfoGrid.add(relationshipLabel, 0, 0);
        patientInfoGrid.add(relationshipComboBox, 1, 0);
        patientInfoGrid.add(notesLabel, 0, 1);
        patientInfoGrid.add(notesTextArea, 1, 1);

        // --- 2. Condition Selection Section ---
        GridPane conditionsGrid = new GridPane();
        conditionsGrid.setHgap(20);
        conditionsGrid.setVgap(15);

        // Create the four condition list views
        ListView<String> endocrineListView = createConditionListView(endocrineConditions, "Endocrine");
        ListView<String> cancerListView = createConditionListView(cancerConditions, "Cancer (Most Frequent)");
        ListView<String> cardioListView = createConditionListView(cardiovascularConditions, "Cardiovascular Diseases");
        ListView<String> geneticListView = createConditionListView(geneticConditions, "Genetic Diseases");

        conditionsGrid.add(createTitledVBox("Endocrine", endocrineListView), 0, 0);
        conditionsGrid.add(createTitledVBox("Cancer", cancerListView), 1, 0);
        conditionsGrid.add(createTitledVBox("Cardiovascular", cardioListView), 0, 1);
        conditionsGrid.add(createTitledVBox("Genetic", geneticListView), 1, 1);

        // --- 3. Management and Search Section ---
        HBox managementBox = new HBox(10);
        TextField searchField = new TextField();
        searchField.setPromptText("Find condition...");
        searchField.setPrefWidth(250);

        Button addButton = new Button("Add Condition");
        Button editButton = new Button("Edit Selected");
        Button deleteButton = new Button("Delete Selected");
        Button saveButton = new Button("Save Lists");
        managementBox.getChildren().addAll(new Label("Manage Lists:"), searchField, addButton, editButton, deleteButton, saveButton);

        // --- 4. Main Action Button ---
        Button addHistoryButton = new Button("Add Entry to History Report");
        addHistoryButton.setMaxWidth(Double.MAX_VALUE);

        // --- Link Search Field to Lists ---
        setupSearchFilter(searchField, endocrineListView, endocrineConditions);
        setupSearchFilter(searchField, cancerListView, cancerConditions);
        setupSearchFilter(searchField, cardioListView, cardiovascularConditions);
        setupSearchFilter(searchField, geneticListView, geneticConditions);

        // --- Setup Button Actions ---
        addButton.setOnAction(e -> handleAddCondition(getActiveListView(endocrineListView, cancerListView, cardioListView, geneticListView)));
        editButton.setOnAction(e -> handleEditCondition(getActiveListView(endocrineListView, cancerListView, cardioListView, geneticListView)));
        deleteButton.setOnAction(e -> handleDeleteCondition(getActiveListView(endocrineListView, cancerListView, cardioListView, geneticListView)));
        saveButton.setOnAction(e -> {
            saveAllConditions();
            showAlert(Alert.AlertType.INFORMATION, "Success", "All condition lists have been saved.");
        });

        addHistoryButton.setOnAction(e -> {
            String relationship = relationshipComboBox.getValue();
            if (relationship == null || relationship.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Validation Error", "Please select a relationship.");
                return;
            }

            Map<String, List<String>> allSelections = new LinkedHashMap<>();
            allSelections.put("Endocrine", endocrineListView.getSelectionModel().getSelectedItems());
            allSelections.put("Cancer", cancerListView.getSelectionModel().getSelectedItems());
            allSelections.put("Cardiovascular", cardioListView.getSelectionModel().getSelectedItems());
            allSelections.put("Genetic", geneticListView.getSelectionModel().getSelectedItems());
            
            addHistoryEntry(relationship, allSelections, notesTextArea.getText());
            
            // Clear selections
            relationshipComboBox.getSelectionModel().clearSelection();
            notesTextArea.clear();
            endocrineListView.getSelectionModel().clearSelection();
            cancerListView.getSelectionModel().clearSelection();
            cardioListView.getSelectionModel().clearSelection();
            geneticListView.getSelectionModel().clearSelection();
        });


        rootLayout.getChildren().addAll(patientInfoGrid, new Separator(), conditionsGrid, new Separator(), managementBox, addHistoryButton);
        return rootLayout;
    }

    // --- Helper Methods for UI and Logic ---

    private ListView<String> createConditionListView(ObservableList<String> items, String category) {
        ListView<String> listView = new ListView<>(items);
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listView.setPrefHeight(150); // Set a consistent height
        return listView;
    }

    private VBox createTitledVBox(String title, ListView<String> listView) {
        Label label = new Label(title);
        label.setStyle("-fx-font-weight: bold;");
        return new VBox(5, label, listView);
    }
    
    private void setupSearchFilter(TextField searchField, ListView<String> listView, ObservableList<String> originalList) {
        FilteredList<String> filteredData = new FilteredList<>(originalList, p -> true);
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(condition -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }
                String lowerCaseFilter = newValue.toLowerCase();
                return condition.toLowerCase().contains(lowerCaseFilter);
            });
        });
        listView.setItems(filteredData);
    }
    
    private ListView<String> getActiveListView(ListView<String>... listViews) {
        for (ListView<String> lv : listViews) {
            if (lv.getSelectionModel().getSelectedIndex() != -1) {
                return lv;
            }
        }
        return null; // No list has a selection
    }
    
    private void handleAddCondition(ListView<String> activeListView) {
        if (activeListView == null) {
            showAlert(Alert.AlertType.WARNING, "Action Needed", "Please select a list by clicking on it before adding.");
            return;
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add New Condition");
        dialog.setHeaderText("Enter the name of the new condition.");
        dialog.setContentText("Condition:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                ((ObservableList<String>)((FilteredList<String>)activeListView.getItems()).getSource()).add(name);
            }
        });
    }

    private void handleEditCondition(ListView<String> activeListView) {
        if (activeListView == null || activeListView.getSelectionModel().getSelectedItem() == null) {
            showAlert(Alert.AlertType.WARNING, "Action Needed", "Please select a condition to edit.");
            return;
        }
        String selected = activeListView.getSelectionModel().getSelectedItem();
        TextInputDialog dialog = new TextInputDialog(selected);
        dialog.setTitle("Edit Condition");
        dialog.setHeaderText("Edit the name of the selected condition.");
        dialog.setContentText("New name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                ObservableList<String> originalList = (ObservableList<String>)((FilteredList<String>)activeListView.getItems()).getSource();
                int index = originalList.indexOf(selected);
                if (index != -1) {
                    originalList.set(index, name);
                }
            }
        });
    }
    
    private void handleDeleteCondition(ListView<String> activeListView) {
        if (activeListView == null || activeListView.getSelectionModel().getSelectedItem() == null) {
            showAlert(Alert.AlertType.WARNING, "Action Needed", "Please select a condition to delete.");
            return;
        }
        String selected = activeListView.getSelectionModel().getSelectedItem();
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Deletion");
        confirm.setHeaderText("Delete '" + selected + "'?");
        confirm.setContentText("Are you sure you want to permanently delete this condition from the list?");
        
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            ((ObservableList<String>)((FilteredList<String>)activeListView.getItems()).getSource()).remove(selected);
        }
    }

    private void addHistoryEntry(String relationship, Map<String, List<String>> allSelections, String notes) {
        StringBuilder sb = new StringBuilder();
        sb.append("--------------------------------------------------------------------------------\n");
        sb.append(String.format("%-15s: %s\n", "RELATIONSHIP", relationship));
        
        for (Map.Entry<String, List<String>> entry : allSelections.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                String conditionsStr = String.join(", ", entry.getValue());
                sb.append(String.format("%-15s: %s\n", entry.getKey().toUpperCase(), conditionsStr));
            }
        }
        
        if (notes != null && !notes.trim().isEmpty()) {
            sb.append(String.format("%-15s: %s\n", "NOTES", notes));
        }
        sb.append("--------------------------------------------------------------------------------\n\n");

        SwingUtilities.invokeLater(() -> historyTextArea.append(sb.toString()));
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // --- File Persistence Methods ---

    private void loadAllConditions() {
        endocrineConditions = loadConditionsFromFile(ENDOCRINE_FILE, getDefaultEndocrine());
        cancerConditions = loadConditionsFromFile(CANCER_FILE, getDefaultCancer());
        cardiovascularConditions = loadConditionsFromFile(CARDIO_FILE, getDefaultCardiovascular());
        geneticConditions = loadConditionsFromFile(GENETIC_FILE, getDefaultGenetic());
    }

    private void saveAllConditions() {
        try {
            if (!Files.exists(DATA_DIR)) {
                Files.createDirectories(DATA_DIR);
            }
            Files.write(ENDOCRINE_FILE, endocrineConditions);
            Files.write(CANCER_FILE, cancerConditions);
            Files.write(CARDIO_FILE, cardiovascularConditions);
            Files.write(GENETIC_FILE, geneticConditions);
        } catch (IOException e) {
            System.err.println("Error saving condition lists: " + e.getMessage());
            Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Save Error", "Could not save condition lists to file."));
        }
    }

    private ObservableList<String> loadConditionsFromFile(Path file, List<String> defaultList) {
        if (Files.exists(file)) {
            try {
                return FXCollections.observableArrayList(Files.readAllLines(file));
            } catch (IOException e) {
                System.err.println("Error loading " + file + ", using defaults. " + e.getMessage());
            }
        }
        return FXCollections.observableArrayList(defaultList);
    }

    // --- Default Data ---
    private List<String> getDefaultEndocrine() { return Arrays.asList("Type 1 Diabetes", "Type 2 Diabetes", "Gestational Diabetes", "Thyroid Cancer", "Hypothyroidism (Hashimoto's)", "Hyperthyroidism (Graves')", "Goiter", "Addison's Disease", "Cushing's Syndrome", "Polycystic Ovary Syndrome (PCOS)", "Hyperparathyroidism", "Pituitary Adenoma"); }
    private List<String> getDefaultCancer() { return Arrays.asList("Breast Cancer", "Lung Cancer", "Prostate Cancer", "Colorectal Cancer", "Melanoma (Skin)", "Leukemia", "Lymphoma", "Ovarian Cancer", "Pancreatic Cancer", "Stomach Cancer", "Liver Cancer", "Brain Tumor"); }
    private List<String> getDefaultCardiovascular() { return Arrays.asList("Coronary Artery Disease", "Hypertension", "Myocardial Infarction", "Stroke", "Heart Failure", "Atrial Fibrillation", "Arrhythmia", "Hyperlipidemia", "Peripheral Artery Disease", "Aortic Aneurysm", "Deep Vein Thrombosis", "Congenital Heart Defect"); }
    private List<String> getDefaultGenetic() { return Arrays.asList("Cystic Fibrosis", "Huntington's Disease", "Down Syndrome", "Sickle Cell Anemia", "Thalassemia", "Marfan Syndrome", "Phenylketonuria (PKU)", "Hemophilia A", "Duchenne Muscular Dystrophy", "Tay-Sachs Disease", "Fragile X Syndrome", "Neurofibromatosis"); }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            EMRFMH frame = new EMRFMH();
            frame.setVisible(true);
        });
    }
}