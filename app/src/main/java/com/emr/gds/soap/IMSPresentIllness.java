package com.emr.gds.soap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class IMSPresentIllness {

    private final TextArea sourceTextArea;
    private Stage editorStage;
    private TextArea editorTextArea;
    private final Map<String, String> abbrevMap = new HashMap<>();

    // --- Data Fields ---
    private final String[] piTemplates = {
        	"Acute", "Chronic", "Severe", "Persistent", "Intermittent", "Localized", 
        	"Radiating", "Progressive", "Recurrent", "Generalized", "Exacerbated", 
        	"Associated", "Episodic", "Subacute", "Relieved",
            "[ :cd ]", "-day ago onset","-week ago onset", "-month ago onset", "-year ago onset"
    };

    private final String[] clinicalPhrases = {
    		"Acute chest pain radiating to left arm with shortness of breath",
        	"Chronic lower back pain, worse with sitting/standing, with leg numbness", 
        	"Persistent right lower quadrant abdominal pain with nausea and fever", 
        	"Recurrent throbbing headaches with photophobia and nausea", 
        	"Subacute shortness of breath, worse on exertion, with productive cough", 
        	"Episodic epigastric burning pain, relieved by antacids, worse after meals", 
        	"Progressive generalized fatigue and weakness over two weeks", 
        	"Intermittent knee pain and swelling, worse mornings with stiffness", 
        	"Persistent sore throat with difficulty swallowing and hoarseness", 
        	"Recurrent urinary frequency with burning and occasional hematuria", 
        	"Itchy skin rash, worse with heat, spreading across torso", 
        	"Acute dizziness and vertigo with nausea and balance issues", 
        	"Chronic cough with wheezing, worse at night and in cold air", 
        	"Progressive upper extremity weakness with muscle cramps", 
        	"Recurrent palpitations with chest discomfort, triggered by stress"

    };

    /**
     * Data model for the phrase table.
     */
    public static class Phrase {
        private final SimpleStringProperty text;
        public Phrase(String text) {
            this.text = new SimpleStringProperty(text);
        }
        public String getText() { return text.get(); }
        public StringProperty textProperty() { return text; }
    }


    public IMSPresentIllness(TextArea sourceTextArea) {
        this.sourceTextArea = sourceTextArea;
        initAbbrevDatabase();
        createEditorWindow();
    }

    private void initAbbrevDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            Path dbFile = getDbPath("abbreviations.db");
            Files.createDirectories(dbFile.getParent());
            String url = "jdbc:sqlite:" + dbFile.toAbsolutePath();

            try (Connection conn = DriverManager.getConnection(url);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM abbreviations")) {
                while (rs.next()) {
                    abbrevMap.put(rs.getString("short"), rs.getString("full"));
                }
            }
        } catch (Exception e) {
            showError("Failed to initialize abbreviation database: " + e.getMessage());
        }
    }

    private Path getDbPath(String fileName) {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("gradlew")) && !Files.exists(p.resolve(".git"))) {
            p = p.getParent();
        }
        return (p != null) ? p.resolve("app").resolve("db").resolve(fileName) : Paths.get("").toAbsolutePath();
    }

    private String expandAbbreviations(String text) {
        StringBuilder sb = new StringBuilder();
        for (String word : text.split("(?<=\\s)|(?=\\s)")) {
            String cleanWord = word.trim();
            if (":cd".equals(cleanWord)) {
                sb.append(LocalDate.now().format(DateTimeFormatter.ISO_DATE));
            } else if (cleanWord.startsWith(":")) {
                sb.append(abbrevMap.getOrDefault(cleanWord.substring(1), word));
            } else {
                sb.append(word);
            }
        }
        return sb.toString();
    }

    private void createEditorWindow() {
        editorStage = new Stage();
        editorStage.setTitle("Chief Complaint Editor");
        editorStage.initModality(Modality.APPLICATION_MODAL);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));
        root.setTop(createTopSection());
        root.setCenter(createCenterSection());
        root.setBottom(createBottomSection());

        // Calculate new West Panel width
        double originalWestPanelWidth = 280;
        double newWestPanelWidth = originalWestPanelWidth * 1.15; // Increase by 15%

        // Original total width was 1000, which includes 280 for West Panel.
        // So the remaining width was 1000 - 280 = 720.
        // New total width will be newWestPanelWidth + 720.
        double newTotalWidth = newWestPanelWidth + (1000 - originalWestPanelWidth);

        VBox westPanel = createWestPanel(newWestPanelWidth); // Pass the new width
        root.setLeft(westPanel);

        // Increased width to accommodate the new panel
        editorStage.setScene(new Scene(root, newTotalWidth, 600));
        editorStage.setWidth(newTotalWidth);
        editorStage.setHeight(600);
        editorStage.setMinWidth(newTotalWidth);
        editorStage.setMinHeight(600);
    }
    
    // --- UI Creation Helper Methods ---

    private VBox createWestPanel(double width) { // Accept width as a parameter
        Label title = createStyledLabel("Phrase Bank", "-fx-font-weight: bold;");

        TableView<Phrase> table = new TableView<>();
        table.setPrefWidth(width + 5); // Set the calculated width for the panel
        
        TableColumn<Phrase, String> phraseColumn = new TableColumn<>("Clinical Phrases");
        phraseColumn.setCellValueFactory(new PropertyValueFactory<>("text"));
        phraseColumn.prefWidthProperty().bind(table.widthProperty().subtract(2)); // Column fills table width
        phraseColumn.setResizable(false);
        
        table.getColumns().add(phraseColumn);

        // Populate the table with data
        ObservableList<Phrase> phraseData = Arrays.stream(clinicalPhrases)
                .map(Phrase::new)
                .collect(Collectors.toCollection(FXCollections::observableArrayList));
        table.setItems(phraseData);

        // Add listener to append text on click
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                editorTextArea.appendText(newSelection.getText() + " ");
                editorTextArea.requestFocus();
            }
        });
        
        VBox westBox = new VBox(5, title, table);
        westBox.setPadding(new Insets(0, 10, 0, 0)); // Add right padding
        return westBox;
    }

    private VBox createTopSection() {
        return new VBox(5,
            createStyledLabel("Present Illness Editor", "-fx-font-size: 16px; -fx-font-weight: bold;"),
            createStyledLabel("Edit the patient's chief complaint. Use templates below or type custom text.", "-fx-text-fill: #666666;"),
            createStyledLabel("Tip: Use abbreviations like :cd for current date. They will expand when saving.", "-fx-text-fill: #0066cc; -fx-font-size: 11px; -fx-font-style: italic;")
        );
    }

    private VBox createCenterSection() {
        editorTextArea = new TextArea(sourceTextArea.getText());
        editorTextArea.setWrapText(true);
        editorTextArea.setPrefRowCount(10);
        editorTextArea.setStyle("-fx-font-family: 'Malgun Gothic', 'Apple SD Gothic Neo', 'Nanum Gothic', 'Source Han Sans', 'Consolas', 'Monaco', monospace; -fx-font-size: 12px;");
        
        TextArea previewArea = new TextArea();
        previewArea.setWrapText(true);
        previewArea.setPrefRowCount(4);
        previewArea.setEditable(false);
        previewArea.setStyle("-fx-font-family: 'Malgun Gothic', 'Apple SD Gothic Neo', 'Nanum Gothic', 'Source Han Sans', 'Consolas', 'Monaco', monospace; -fx-font-size: 12px; -fx-background-color: #f5f5f5;");

        editorTextArea.textProperty().addListener((obs, oldText, newText) -> previewArea.setText(expandAbbreviations(newText)));
        previewArea.setText(expandAbbreviations(editorTextArea.getText())); // Initial preview

        return new VBox(10,
            createStyledLabel("Quick Templates:", "-fx-font-weight: bold;"),
            createTemplatesGrid(),
            createStyledLabel("Chief Complaint Text:", "-fx-font-weight: bold;"),
            editorTextArea,
            createStyledLabel("Preview (with expanded abbreviations):", "-fx-font-weight: bold; -fx-text-fill: #666666;"),
            previewArea
        );
    }

    private GridPane createTemplatesGrid() {
        GridPane grid = new GridPane(5, 5);
        grid.setPadding(new Insets(5));
        final int cols = 3;

        IntStream.range(0, piTemplates.length).forEach(i -> {
            String template = piTemplates[i].trim();
            Button btn = new Button(template);
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setOnAction(e -> insertTemplate(template.replaceAll("\\s*\\([^)]*\\)", "")));
            grid.add(btn, i % cols, i / cols);
        });
        return grid;
    }

    private HBox createBottomSection() {
        HBox bottomBar = new HBox(10,
            createStyledButton("Apply Changes", "-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;", e -> applyChanges()),
            new Button("Cancel") {{ setOnAction(e -> editorStage.close()); }},
            createStyledButton("Clear", "-fx-background-color: #f44336; -fx-text-fill: white;", e -> editorTextArea.clear())
        );
        bottomBar.setPadding(new Insets(10, 0, 0, 0));
        return bottomBar;
    }
    
    // --- Action and Logic Methods ---

    private void insertTemplate(String template) {
        editorTextArea.appendText(template + " ");
        editorTextArea.requestFocus();
    }

    private void applyChanges() {
        final String originalText = editorTextArea.getText();
        final String expandedTextRaw = expandAbbreviations(originalText);
        final String expandedText = expandedTextRaw == null ? "" : expandedTextRaw.trim();

        boolean expansionOccurred = !java.util.Objects.equals(originalText, expandedTextRaw);
        boolean proceed = !expansionOccurred;

        if (expandedText.isEmpty()) {
            showError("Nothing to apply. Please enter a value.");
            return;
        }

        if (expansionOccurred) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Abbreviation Expansion");
            confirm.setHeaderText("Abbreviations will be expanded:");
            confirm.setContentText("Original:\n" + originalText + "\n\nExpanded:\n" + expandedText + "\n\nApply changes?");
            proceed = confirm.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
        }

        if (proceed) {
            sourceTextArea.setText(expandedText);
            editorStage.close();
        }
    }

    private void showError(String message) {
        new Alert(Alert.AlertType.ERROR, "An error occurred:\n" + message) {{
            setTitle("Error");
            showAndWait();
        }};
    }
    
    // --- Component Factory Helpers ---
    
    private Label createStyledLabel(String text, String style) {
        Label label = new Label(text);
        label.setStyle(style);
        return label;
    }

    private Button createStyledButton(String text, String style, EventHandler<ActionEvent> handler) {
        Button button = new Button(text);
        button.setStyle(style);
        button.setOnAction(handler);
        return button;
    }

    public void showAndWait() {
        editorStage.showAndWait();
    }
}