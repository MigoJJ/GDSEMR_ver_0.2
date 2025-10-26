package com.emr.gds.main;

import com.emr.gds.IttiaApp;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.StringJoiner;

/**
 * Manages the Problem List and Scratchpad sections of the UI.
 * This class handles:
 * - Displaying and managing a persistent list of patient problems.
 * - Storing and retrieving problem data from a dedicated SQLite database.
 * - Providing a scratchpad area that mirrors content from the main EMR text areas.
 */
public class IAMProblemAction {

    // ================================ 
    // UI Layout Constants
    // ================================ 
    private static final double SIDEBAR_WIDTH_PX = 460;
    private static final int SCRATCHPAD_ROWS = 35;
    private static final double PROBLIST_HEIGHT_PX = 180;
    private static final double SPACING_PX = 8;
    private static final double PADDING_RIGHT_PX = 8;

    // ================================ 
    // Instance Variables
    // ================================ 
    private final IttiaApp app;
    private Connection dbConn;
    private final ObservableList<String> problems = FXCollections.observableArrayList();
    private ListView<String> problemList;
    private TextArea scratchpadArea;
    private final LinkedHashMap<String, String> scratchpadEntries = new LinkedHashMap<>();

    // ================================ 
    // Constructor
    // ================================ 
    public IAMProblemAction(IttiaApp app) {
        this.app = app;
        initProblemListDatabase();
        loadProblemsFromDb();
    }

    // ================================ 
    // Database Initialization and Operations
    // ================================ 

    /**
     * Initializes the connection to the 'prolist.db' SQLite database.
     * Creates the database and table if they don't exist.
     */
    private void initProblemListDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            Path db = dbPath();
            Files.createDirectories(db.getParent());
            String url = "jdbc:sqlite:" + db.toAbsolutePath();
            System.out.println("[DB PATH] prolist -> " + db.toAbsolutePath());
            this.dbConn = DriverManager.getConnection(url);
            createProblemTable();
        } catch (Exception e) {
            System.err.println("FATAL: Failed to initialize Problem List database: " + e.getMessage());
            throw new RuntimeException("Failed to open prolist.db", e);
        }
    }

    /**
     * Creates the 'problems' table if it doesn't exist and populates it with default data on first run.
     */
    private void createProblemTable() throws SQLException {
        try (Statement stmt = dbConn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS problems (id INTEGER PRIMARY KEY AUTOINCREMENT, problem_text TEXT NOT NULL UNIQUE)");

            // Check if the table is empty to add initial default data
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS count FROM problems")) {
                if (rs.next() && rs.getInt("count") == 0) {
                    System.out.println("Problem list database is empty. Populating with default data.");
                    stmt.execute("INSERT INTO problems (problem_text) VALUES ('Hypercholesterolemia [F/U]')");
                    stmt.execute("INSERT INTO problems (problem_text) VALUES ('Prediabetes (FBS 108 mg/dL)')");
                    stmt.execute("INSERT INTO problems (problem_text) VALUES ('Thyroid nodule (small)')");
                }
            }
        }
    }

    /**
     * Loads all problems from the database into the UI's ObservableList.
     */
    private void loadProblemsFromDb() {
        if (dbConn == null) return;

        problems.clear();
        String sql = "SELECT problem_text FROM problems ORDER BY id";

        try (Statement stmt = dbConn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                problems.add(rs.getString("problem_text"));
            }
        } catch (SQLException e) {
            System.err.println("Failed to load problems from database: " + e.getMessage());
        }
    }

    /**
     * Adds a new problem to the database and updates the UI.
     * @param problemText The problem to add.
     */
    private void addProblem(String problemText) {
        if (dbConn == null || problemText == null || problemText.isBlank()) return;

        String sql = "INSERT INTO problems(problem_text) VALUES(?)";
        try (PreparedStatement pstmt = dbConn.prepareStatement(sql)) {
            pstmt.setString(1, problemText);
            if (pstmt.executeUpdate() > 0) {
                Platform.runLater(() -> problems.add(problemText));
            }
        } catch (SQLException e) {
            // This error is expected if the problem already exists due to the UNIQUE constraint.
            System.err.println("Failed to add problem '" + problemText + "'. It might already exist. Details: " + e.getMessage());
        }
    }

    /**
     * Removes a selected problem from the database and updates the UI.
     * @param problemText The problem to remove.
     */
    private void removeProblem(String problemText) {
        if (dbConn == null || problemText == null) return;

        String sql = "DELETE FROM problems WHERE problem_text = ?";
        try (PreparedStatement pstmt = dbConn.prepareStatement(sql)) {
            pstmt.setString(1, problemText);
            if (pstmt.executeUpdate() > 0) {
                Platform.runLater(() -> problems.remove(problemText));
            }
        } catch (SQLException e) {
            System.err.println("Failed to remove problem '" + problemText + "': " + e.getMessage());
        }
    }

    // ================================ 
    // UI Building
    // ================================ 

    /**
     * Constructs the entire problem pane, including the problem list and scratchpad.
     * @return A VBox containing the configured UI components.
     */
    public VBox buildProblemPane() {
        // --- Problem List Section ---
        problemList = createProblemListView();
        TextField input = createProblemInputTextField();
        Button removeButton = createRemoveProblemButton();
        HBox problemControls = new HBox(SPACING_PX, input, removeButton);
        HBox.setHgrow(input, Priority.ALWAYS);

        // --- Scratchpad Section ---
        scratchpadArea = createScratchpadTextArea();

        // --- Assemble the VBox ---
        VBox box = new VBox(
                SPACING_PX,
                new Label("Scratchpad"),
                scratchpadArea,
                new Separator(Orientation.HORIZONTAL),
                new Label("Problem List (Persistent)"),
                problemList,
                problemControls
        );

        // Configure layout properties
        VBox.setVgrow(problemList, Priority.NEVER);
        VBox.setVgrow(scratchpadArea, Priority.NEVER);
        box.setPadding(new Insets(0, PADDING_RIGHT_PX, 0, 0));
        box.setPrefWidth(SIDEBAR_WIDTH_PX);
        box.setMaxWidth(SIDEBAR_WIDTH_PX);
        box.setMinWidth(SIDEBAR_WIDTH_PX);

        return box;
    }

    private ListView<String> createProblemListView() {
        ListView<String> listView = new ListView<>(problems);
        listView.setPrefHeight(PROBLIST_HEIGHT_PX);
        listView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String selectedItem = listView.getSelectionModel().getSelectedItem();
                if (selectedItem != null) {
                    app.insertLineIntoFocusedArea("- " + selectedItem);
                }
            }
        });
        return listView;
    }

    private TextField createProblemInputTextField() {
        TextField input = new TextField();
        input.setPromptText("Add problem and press Enter");
        input.setOnAction(e -> {
            String text = IAMTextFormatUtil.normalizeLine(input.getText());
            if (!text.isBlank()) {
                addProblem(text); // Persist to DB and update UI
                input.clear();
            }
        });
        return input;
    }

    private Button createRemoveProblemButton() {
        Button remove = new Button("Remove Selected");
        remove.setOnAction(e -> {
            String selectedProblem = problemList.getSelectionModel().getSelectedItem();
            if (selectedProblem != null) {
                removeProblem(selectedProblem);
            }
        });
        return remove;
    }

    private TextArea createScratchpadTextArea() {
        TextArea textArea = new TextArea();
        textArea.setPromptText("Scratchpad... (auto-updated from center areas)");
        textArea.setWrapText(true);
        textArea.setEditable(true);
        textArea.setPrefRowCount(SCRATCHPAD_ROWS);
        return textArea;
    }

    // ================================ 
    // Scratchpad Logic
    // ================================ 

    /**
     * Updates the scratchpad content based on changes in the main text areas.
     * @param title The title of the text area that changed.
     * @param newText The new text content.
     */
    public void updateAndRedrawScratchpad(String title, String newText) {
        String trimmedText = newText.trim();
        if (trimmedText.isEmpty()) {
            scratchpadEntries.remove(title);
        } else {
            // Replace newlines with a space and a tab for a more compact view
            String singleLineText = trimmedText.replaceAll("\\s*\\R\\s*", " \n\t ");
            scratchpadEntries.put(title, singleLineText);
        }
        redrawScratchpad();
    }

    /**
     * Redraws the scratchpad with the latest content from all mirrored text areas.
     */
    public void redrawScratchpad() {
        if (scratchpadArea == null) return;

        List<String> orderedTitles = Arrays.asList(IAMTextArea.TEXT_AREA_TITLES);
        StringJoiner sj = new StringJoiner("\n");

        for (String title : orderedTitles) {
            String value = scratchpadEntries.get(title);
            if (value != null && !value.isEmpty()) {
                sj.add(title + " " + value);
            }
        }

        String newContent = sj.toString();
        if (!scratchpadArea.getText().equals(newContent)) {
            scratchpadArea.setText(newContent);
            scratchpadArea.positionCaret(scratchpadArea.getLength());
            scratchpadArea.setScrollTop(Double.MAX_VALUE);
        }
    }

    public void clearScratchpad() {
        if (scratchpadArea != null) {
            scratchpadArea.clear();
        }
    }

    // ================================ 
    // Public Getters and Cleanup
    // ================================ 

    public ObservableList<String> getProblems() {
        return problems;
    }

    /**
     * Closes the database connection when the application shuts down.
     */
    public void closeDatabase() {
        if (dbConn != null) {
            try {
                dbConn.close();
                System.out.println("Problem list database connection closed.");
            } catch (SQLException e) {
                System.err.println("Error closing problem list database: " + e.getMessage());
            }
        }
    }

    // --- DB Helpers (Repo-tracked under app/db) ---
    private static Path repoRoot() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("gradlew")) && !Files.exists(p.resolve(".git"))) {
            p = p.getParent();
        }
        return (p != null) ? p : Paths.get("").toAbsolutePath();
    }
    
    private static Path dbPath() {
        return repoRoot().resolve("app").resolve("db").resolve("prolist.db");
    }
}
