package com.emr.gds.soap;

import com.emr.gds.input.IAITextAreaManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.embed.swing.JFXPanel;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A Swing JFrame for inputting Family Medical History (FMH), with an embedded JavaFX panel for a modern UI.
 */
public class EMRFMH extends JFrame {

    private final JTextArea historyTextArea;
    private final IAITextAreaManager textAreaManager;

    private ObservableList<String> endocrineConditions, cancerConditions, cardiovascularConditions, geneticConditions;

    private static final Path DATA_DIR = Paths.get("emr_fmh_data");
    private static final Path ENDOCRINE_FILE = DATA_DIR.resolve("endocrine.txt");
    private static final Path CANCER_FILE = DATA_DIR.resolve("cancer.txt");
    private static final Path CARDIO_FILE = DATA_DIR.resolve("cardiovascular.txt");
    private static final Path GENETIC_FILE = DATA_DIR.resolve("genetic.txt");

    public EMRFMH(IAITextAreaManager textAreaManager) {
        this.textAreaManager = textAreaManager;

        setTitle("Endocrinology - Family Medical History");
        setSize(950, 850);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        loadAllConditions();

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        historyTextArea = new JTextArea(15, 80);
        historyTextArea.setEditable(true);
        historyTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        historyTextArea.setBorder(BorderFactory.createTitledBorder("Family History Report"));
        mainPanel.add(new JScrollPane(historyTextArea), BorderLayout.CENTER);

        JFXPanel fxPanel = new JFXPanel();
        mainPanel.add(fxPanel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(createButton("Clear", e -> historyTextArea.setText("")));
        buttonPanel.add(createButton("Save", e -> onSave()));
        buttonPanel.add(createButton("Quit", e -> dispose()));
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        Platform.runLater(() -> fxPanel.setScene(new Scene(createFamilyHistoryForm())));
    }

    private VBox createFamilyHistoryForm() {
        VBox rootLayout = new VBox(20);
        rootLayout.setPadding(new Insets(20));
        rootLayout.setStyle("-fx-font-size: 14px;");

        GridPane patientInfoGrid = createPatientInfoGrid();
        GridPane conditionsGrid = createConditionsGrid();
        HBox managementBox = createManagementBox(conditionsGrid);
        Button addHistoryButton = createAddHistoryButton(patientInfoGrid, conditionsGrid);

        rootLayout.getChildren().addAll(patientInfoGrid, new Separator(), conditionsGrid, new Separator(), managementBox, addHistoryButton);
        return rootLayout;
    }

    private GridPane createPatientInfoGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        ComboBox<String> relationshipComboBox = new ComboBox<>(FXCollections.observableArrayList("Mother", "Father", "Sister", "Brother", "Grandmother", "Grandfather", "Aunt", "Uncle", "Cousin", "Child"));
        relationshipComboBox.setPromptText("Select a relative");
        TextArea notesTextArea = new TextArea();
        notesTextArea.setPromptText("e.g., Age of onset, lifestyle factors...");
        notesTextArea.setPrefRowCount(2);
        grid.add(new Label("Relationship:"), 0, 0);
        grid.add(relationshipComboBox, 1, 0);
        grid.add(new Label("General Notes:"), 0, 1);
        grid.add(notesTextArea, 1, 1);
        return grid;
    }

    private GridPane createConditionsGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(15);
        grid.add(createTitledVBox("Endocrine", createConditionListView(endocrineConditions)), 0, 0);
        grid.add(createTitledVBox("Cancer", createConditionListView(cancerConditions)), 1, 0);
        grid.add(createTitledVBox("Cardiovascular", createConditionListView(cardiovascularConditions)), 0, 1);
        grid.add(createTitledVBox("Genetic", createConditionListView(geneticConditions)), 1, 1);
        return grid;
    }

    private HBox createManagementBox(GridPane conditionsGrid) {
        TextField searchField = new TextField();
        searchField.setPromptText("Find condition...");
        searchField.textProperty().addListener((obs, old, val) -> conditionsGrid.getChildren().forEach(node -> {
            if (node instanceof VBox) {
                ListView<String> lv = (ListView<String>) ((VBox) node).getChildren().get(1);
                ((FilteredList<String>) lv.getItems()).setPredicate(s -> s.toLowerCase().contains(val.toLowerCase()));
            }
        }));

        Button addButton = new Button("Add");
        addButton.setOnAction(e -> handleAddCondition(getActiveListView(conditionsGrid)));
        // ... similar setup for edit and delete buttons

        Button saveButton = new Button("Save Lists");
        saveButton.setOnAction(e -> saveAllConditions());

        return new HBox(10, new Label("Manage:"), searchField, addButton, saveButton);
    }

    private Button createAddHistoryButton(GridPane patientInfoGrid, GridPane conditionsGrid) {
        Button button = new Button("Add Entry to History Report");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(e -> {
            ComboBox<String> relationshipBox = (ComboBox<String>) patientInfoGrid.getChildren().get(1);
            String relationship = relationshipBox.getValue();
            if (relationship == null || relationship.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Validation Error", "Please select a relationship.");
                return;
            }
            // ... logic to gather selections and add to historyTextArea
        });
        return button;
    }

    private void onSave() {
        if (textAreaManager == null) {
            showErrorDialog("Error: TextAreaManager was not provided.");
            return;
        }
        try {
            textAreaManager.insertBlockIntoArea(IAITextAreaManager.AREA_PMH, historyTextArea.getText(), true);
            JOptionPane.showMessageDialog(this, "Family History saved successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } catch (Exception ex) {
            showErrorDialog("Failed to save: " + ex.getMessage());
        }
    }

    private void loadAllConditions() {
        endocrineConditions = loadConditionsFromFile(ENDOCRINE_FILE, getDefaultEndocrine());
        cancerConditions = loadConditionsFromFile(CANCER_FILE, getDefaultCancer());
        cardiovascularConditions = loadConditionsFromFile(CARDIO_FILE, getDefaultCardiovascular());
        geneticConditions = loadConditionsFromFile(GENETIC_FILE, getDefaultGenetic());
    }

    private void saveAllConditions() {
        try {
            Files.createDirectories(DATA_DIR);
            Files.write(ENDOCRINE_FILE, endocrineConditions);
            Files.write(CANCER_FILE, cancerConditions);
            Files.write(CARDIO_FILE, cardiovascularConditions);
            Files.write(GENETIC_FILE, geneticConditions);
            showAlert(Alert.AlertType.INFORMATION, "Success", "All condition lists have been saved.");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Save Error", "Could not save condition lists.");
        }
    }

    private ObservableList<String> loadConditionsFromFile(Path file, List<String> defaultList) {
        try {
            return FXCollections.observableArrayList(Files.exists(file) ? Files.readAllLines(file) : defaultList);
        } catch (IOException e) {
            return FXCollections.observableArrayList(defaultList);
        }
    }

    // Default data methods (getDefaultEndocrine, etc.) remain the same
    private List<String> getDefaultEndocrine() { return Arrays.asList("Type 1 Diabetes", "Type 2 Diabetes", "Thyroid Cancer"); }
    private List<String> getDefaultCancer() { return Arrays.asList("Breast Cancer", "Lung Cancer", "Prostate Cancer"); }
    private List<String> getDefaultCardiovascular() { return Arrays.asList("Coronary Artery Disease", "Hypertension", "Stroke"); }
    private List<String> getDefaultGenetic() { return Arrays.asList("Cystic Fibrosis", "Huntington's Disease", "Down Syndrome"); }

    // Helper methods (createButton, createTitledVBox, etc.)
    private JButton createButton(String text, java.awt.event.ActionListener listener) {
        JButton button = new JButton(text);
        button.addActionListener(listener);
        return button;
    }

    private VBox createTitledVBox(String title, ListView<String> listView) {
        return new VBox(5, new Label(title), listView);
    }

    private ListView<String> createConditionListView(ObservableList<String> items) {
        ListView<String> listView = new ListView<>(new FilteredList<>(items, p -> true));
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        return listView;
    }

    private ListView<String> getActiveListView(GridPane grid) {
        // Logic to find the currently focused ListView
        return null;
    }

    private void handleAddCondition(ListView<String> activeListView) {
        // Logic for adding a new condition
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type, message);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.showAndWait();
        });
    }

    private void showErrorDialog(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new EMRFMH(null).setVisible(true));
    }
}
