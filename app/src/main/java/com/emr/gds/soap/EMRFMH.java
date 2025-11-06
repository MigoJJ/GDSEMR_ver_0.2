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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Enhanced EMR Family Medical History (FMH) input frame with:
 * - Responsive layout
 * - Full scrolling (Swing + JavaFX)
 * - Clean organization
 * - Embedded JavaFX form in JFXPanel
 */
public class EMRFMH extends JFrame {

    private final JTextArea historyTextArea;
    private final IAITextAreaManager textAreaManager;
	private final Map<String, String> abbrevMap;


    private ObservableList<String> endocrineConditions;
    private ObservableList<String> cancerConditions;
    private ObservableList<String> cardiovascularConditions;
    private ObservableList<String> geneticConditions;

    private static final Path DATA_DIR = Paths.get("emr_fmh_data");
    private static final Path ENDOCRINE_FILE = DATA_DIR.resolve("endocrine.txt");
    private static final Path CANCER_FILE = DATA_DIR.resolve("cancer.txt");
    private static final Path CARDIO_FILE = DATA_DIR.resolve("cardiovascular.txt");
    private static final Path GENETIC_FILE = DATA_DIR.resolve("genetic.txt");

    // UI Components (to be reused)
    private ComboBox<String> relationshipComboBox;
    private TextArea notesTextArea;
    private GridPane conditionsGrid;

    public EMRFMH(IAITextAreaManager textAreaManager, Map<String, String> abbrevMap) {
        this.textAreaManager = textAreaManager;
		this.abbrevMap = (abbrevMap != null) ? abbrevMap : Collections.emptyMap();

        // -----------------------------------------------------------------
        // 1. Create the JTextArea **right here** so the final field is set
        // -----------------------------------------------------------------
        historyTextArea = new JTextArea(12, 80);
        historyTextArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        historyTextArea.setLineWrap(true);
        historyTextArea.setWrapStyleWord(true);
		addAbbreviationExpansionListener(historyTextArea);

        initializeData();   // load condition files
        initializeUI();     // build the rest of the UI (uses historyTextArea)
    }

    private void initializeData() {
        loadAllConditions();
    }

    private void initializeUI() {
        setTitle("Endocrinology - Family Medical History");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setPreferredSize(new Dimension(1000, 900));
        setMinimumSize(new Dimension(800, 600));

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ----- JavaFX panel ------------------------------------------------
        JFXPanel fxPanel = new JFXPanel();
        fxPanel.setPreferredSize(new Dimension(0, 650));
        Platform.runLater(() -> fxPanel.setScene(new Scene(createJavaFXForm())));

        JScrollPane fxScrollPane = new JScrollPane(fxPanel);
        fxScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        mainPanel.add(fxScrollPane, BorderLayout.NORTH);

        // ----- History text area (already created in ctor) ----------------
        JScrollPane textScrollPane = new JScrollPane(historyTextArea);
        textScrollPane.setBorder(BorderFactory.createTitledBorder("Family History Report"));
        mainPanel.add(textScrollPane, BorderLayout.CENTER);

        // ----- Buttons ----------------------------------------------------
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        buttonPanel.add(createButton("Clear", e -> historyTextArea.setText("")));
        buttonPanel.add(createButton("Save", e -> onSave()));
        buttonPanel.add(createButton("Quit", e -> dispose()));
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        pack();
        setLocationRelativeTo(null);
    }

    // ======================
    // JavaFX Form Creation
    // ======================
    private ScrollPane createJavaFXForm() {
        VBox root = new VBox(15);
        root.setPadding(new Insets(15));
        root.setStyle("-fx-font-size: 14px;");

        // Patient Info
        GridPane patientInfoGrid = createPatientInfoGrid();

        // Conditions Grid
        conditionsGrid = createConditionsGrid();

        // Management Tools
        HBox managementBox = createManagementBox();

        // Add Entry Button
        Button addHistoryButton = createAddHistoryButton();

        // Assemble
        root.getChildren().addAll(
                createSectionTitle("Patient & Relative Information"), patientInfoGrid,
                new Separator(),
                createSectionTitle("Medical Conditions by Category"), conditionsGrid,
                new Separator(),
                managementBox,
                addHistoryButton
        );

        // Wrap in ScrollPane
        ScrollPane scrollPane = new ScrollPane(root);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setStyle("-fx-background: white; -fx-border-color: lightgray;");

        return scrollPane;
    }

    private Label createSectionTitle(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
        return label;
    }

    private GridPane createPatientInfoGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        grid.setStyle("-fx-background-color: #f9f9f9; -fx-border-color: #ddd; -fx-border-radius: 5;");

        relationshipComboBox = new ComboBox<>(
                FXCollections.observableArrayList("Mother", "Father", "Sister", "Brother",
                        "Grandmother", "Grandfather", "Aunt", "Uncle", "Cousin", "Child"));
        relationshipComboBox.setPromptText("Select relative");
        relationshipComboBox.setMaxWidth(Double.MAX_VALUE);

        notesTextArea = new TextArea();
        notesTextArea.setPromptText("e.g., Age of onset, lifestyle factors, treatment...");
        notesTextArea.setPrefRowCount(3);
        notesTextArea.setWrapText(true);
		addAbbreviationExpansionListener(notesTextArea);

        grid.add(new Label("Relationship:"), 0, 0);
        grid.add(relationshipComboBox, 1, 0);
        grid.add(new Label("Notes:"), 0, 1);
        GridPane.setHgrow(notesTextArea, Priority.ALWAYS);
        grid.add(notesTextArea, 1, 1);

        return grid;
    }

    private GridPane createConditionsGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(15);
        grid.setPadding(new Insets(10));

        grid.add(createConditionColumn("Endocrine", endocrineConditions), 0, 0);
        grid.add(createConditionColumn("Cancer", cancerConditions), 1, 0);
        grid.add(createConditionColumn("Cardiovascular", cardiovascularConditions), 2, 0);
        grid.add(createConditionColumn("Genetic", geneticConditions), 3, 0);

        return grid;
    }

    private VBox createConditionColumn(String title, ObservableList<String> items) {
        ListView<String> listView = new ListView<>(new FilteredList<>(items, p -> true));
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listView.setPrefHeight(180);

        VBox column = new VBox(8, new Label(title + ":"), listView);
        column.setStyle("-fx-border-color: #ccc; -fx-border-radius: 5; -fx-padding: 8;");
        VBox.setVgrow(listView, Priority.ALWAYS);
        return column;
    }

    private HBox createManagementBox() {
        TextField searchField = new TextField();
        searchField.setPromptText("Search conditions...");
        searchField.setPrefWidth(200);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        searchField.textProperty().addListener((obs, old, val) -> {
            String filter = val.toLowerCase();
            conditionsGrid.getChildren().forEach(node -> {
                if (node instanceof VBox) {
                    VBox col = (VBox) node;
                    ListView<String> lv = (ListView<String>) col.getChildren().get(1);
                    FilteredList<String> filtered = (FilteredList<String>) lv.getItems();
                    filtered.setPredicate(s -> s.toLowerCase().contains(filter));
                }
            });
        });

        Button addButton = new Button("Add Condition");
        addButton.setOnAction(e -> handleAddCondition(getFocusedListView()));

        Button saveListsButton = new Button("Save Lists");
        saveListsButton.setOnAction(e -> saveAllConditions());

        return new HBox(12, new Label("Manage:"), searchField, addButton, saveListsButton);
    }

    private Button createAddHistoryButton() {
        Button button = new Button("Add Entry to History Report");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        button.setOnAction(e -> addHistoryEntry());
        return button;
    }

    // ======================
    // Event Handlers
    // ======================
    private void addHistoryEntry() {
        String relationship = relationshipComboBox.getValue();
        if (relationship == null || relationship.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Input Required", "Please select a relationship.");
            return;
        }

        StringBuilder entry = new StringBuilder();
        entry.append(relationship).append(":\n");

        String notes = notesTextArea.getText().trim();
        if (!notes.isEmpty()) {
            entry.append("  Notes: ").append(notes).append("\n");
        }

        boolean hasCondition = false;
        for (javafx.scene.Node node : conditionsGrid.getChildren()) {
            if (node instanceof VBox) {
                VBox col = (VBox) node;
                ListView<String> lv = (ListView<String>) col.getChildren().get(1);
                ObservableList<String> selected = lv.getSelectionModel().getSelectedItems();
                if (!selected.isEmpty()) {
                    hasCondition = true;
                    Label titleLabel = (Label) col.getChildren().get(0);
                    entry.append("  ").append(titleLabel.getText().replace(":", "")).append(": ")
                            .append(String.join("; ", selected)).append("\n");
                }
            }
        }

        if (!hasCondition && notes.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Data", "Please select at least one condition or add notes.");
            return;
        }

        historyTextArea.append(entry.toString().trim() + "\n\n");
        clearFormInputs();
    }

    private void clearFormInputs() {
        relationshipComboBox.setValue(null);
        notesTextArea.clear();
        conditionsGrid.getChildren().forEach(node -> {
            if (node instanceof VBox) {
                ListView<String> lv = (ListView<String>) ((VBox) node).getChildren().get(1);
                lv.getSelectionModel().clearSelection();
            }
        });
    }

    private ListView<String> getFocusedListView() {
        for (javafx.scene.Node node : conditionsGrid.getChildren()) {
            if (node instanceof VBox) {
                ListView<String> lv = (ListView<String>) ((VBox) node).getChildren().get(1);
                if (lv.isFocused()) return lv;
            }
        }
        return null;
    }

    private void handleAddCondition(ListView<String> listView) {
        if (listView == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Click on a condition list first.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add New Condition");
        dialog.setHeaderText("Enter condition name:");
        dialog.setContentText("Condition:");

        dialog.showAndWait().ifPresent(name -> {
            if (!name.trim().isEmpty() && !listView.getItems().contains(name.trim())) {
                listView.getItems().add(name.trim());
            }
        });
    }

    private void onSave() {
        if (textAreaManager == null) {
            showErrorDialog("TextAreaManager is not available.");
            return;
        }
        try {
            String text = historyTextArea.getText();
            if (text.trim().isEmpty()) {
                int confirm = JOptionPane.showConfirmDialog(this,
                        "History is empty. Save anyway?", "Confirm Save",
                        JOptionPane.YES_NO_OPTION);
                if (confirm != JOptionPane.YES_OPTION) return;
            }

            textAreaManager.insertBlockIntoArea(IAITextAreaManager.AREA_PMH, text, true);
            JOptionPane.showMessageDialog(this, "Family History saved successfully.", "Success",
                    JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } catch (Exception ex) {
            showErrorDialog("Save failed: " + ex.getMessage());
        }
    }

    // ======================
    // Data Loading & Saving
    // ======================
    private void loadAllConditions() {
        endocrineConditions = loadConditions(ENDOCRINE_FILE, getDefaultEndocrine());
        cancerConditions = loadConditions(CANCER_FILE, getDefaultCancer());
        cardiovascularConditions = loadConditions(CARDIO_FILE, getDefaultCardiovascular());
        geneticConditions = loadConditions(GENETIC_FILE, getDefaultGenetic());
    }

    private ObservableList<String> loadConditions(Path file, List<String> defaults) {
        try {
            if (Files.exists(file)) {
                return FXCollections.observableArrayList(Files.readAllLines(file));
            }
        } catch (IOException e) {
            // Fall back to defaults
        }
        return FXCollections.observableArrayList(defaults);
    }

    private void saveAllConditions() {
        try {
            Files.createDirectories(DATA_DIR);
            Files.write(ENDOCRINE_FILE, endocrineConditions);
            Files.write(CANCER_FILE, cancerConditions);
            Files.write(CARDIO_FILE, cardiovascularConditions);
            Files.write(GENETIC_FILE, geneticConditions);
            showAlert(Alert.AlertType.INFORMATION, "Saved", "Condition lists saved successfully.");
        } catch (IOException ex) {
            showAlert(Alert.AlertType.ERROR, "Save Failed", "Could not save lists: " + ex.getMessage());
        }
    }

    // ======================
    // Defaults
    // ======================
    private List<String> getDefaultEndocrine() {
        return Arrays.asList("Type 1 Diabetes", "Type 2 Diabetes", "Hypothyroidism", "Hyperthyroidism", "Thyroid Cancer");
    }

    private List<String> getDefaultCancer() {
        return Arrays.asList("Breast Cancer", "Lung Cancer", "Prostate Cancer", "Colon Cancer", "Skin Cancer");
    }

    private List<String> getDefaultCardiovascular() {
        return Arrays.asList("Coronary Artery Disease", "Hypertension", "Heart Attack", "Stroke", "Arrhythmia");
    }

    private List<String> getDefaultGenetic() {
        return Arrays.asList("Cystic Fibrosis", "Huntington's Disease", "Down Syndrome", "Sickle Cell Anemia", "Hemophilia");
    }

    // ======================
    // UI Helpers
    // ======================
    private JButton createButton(String text, java.awt.event.ActionListener listener) {
        JButton btn = new JButton(text);
        btn.addActionListener(listener);
        return btn;
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type, message, ButtonType.OK);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.showAndWait();
        });
    }

    private void showErrorDialog(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    // ======================
    // Abbreviation Expansion
    // ======================
    private void addAbbreviationExpansionListener(JTextArea ta) {
        ta.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_SPACE) {
                    if (expandAbbreviationOnSpace(ta)) {
                        e.consume();
                    }
                }
            }
        });
    }

    private boolean expandAbbreviationOnSpace(JTextArea ta) {
        try {
            int caret = ta.getCaretPosition();
            javax.swing.text.Element root = ta.getDocument().getDefaultRootElement();
            int line = root.getElementIndex(caret);
            int start = root.getElement(line).getStartOffset();
            String lineText = ta.getDocument().getText(start, caret - start);

            int wordStart = Math.max(lineText.lastIndexOf(' '), lineText.lastIndexOf('\n')) + 1;
            String word = lineText.substring(wordStart).trim();

            if (!word.startsWith(":")) return false;

            String key = word.substring(1);
            String replacement = abbrevMap.get(key);
            if (replacement == null) return false;

            final int finalWordStart = start + wordStart;
            SwingUtilities.invokeLater(() -> {
                try {
                    ta.getDocument().remove(finalWordStart, caret - finalWordStart);
                    ta.getDocument().insertString(finalWordStart, replacement + " ", null);
                } catch (javax.swing.text.BadLocationException e) {
                    // ignore
                }
            });
            return true;
        } catch (javax.swing.text.BadLocationException e) {
            return false;
        }
    }

    private void addAbbreviationExpansionListener(TextArea ta) {
        ta.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.SPACE) {
                if (expandAbbreviationOnSpace(ta)) event.consume();
            }
        });
    }

    private boolean expandAbbreviationOnSpace(TextArea ta) {
        int caret = ta.getCaretPosition();
        String upToCaret = ta.getText(0, caret);
        int start = Math.max(upToCaret.lastIndexOf(' '), upToCaret.lastIndexOf('\n')) + 1;

        String word = upToCaret.substring(start).trim();
        if (!word.startsWith(":")) return false;

        String key = word.substring(1);
        String replacement = abbrevMap.get(key);
        if (replacement == null) return false;

        Platform.runLater(() -> {
            ta.deleteText(start, caret);
            ta.insertText(start, replacement + " ");
        });
        return true;
    }

    // ======================
    // Main
    // ======================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getLookAndFeel());
            } catch (Exception ignored) {}
            new EMRFMH(null, Collections.emptyMap()).setVisible(true);
        });
    }

}