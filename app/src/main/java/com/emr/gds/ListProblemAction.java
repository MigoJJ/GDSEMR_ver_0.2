package com.emr.gds;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

public class ListProblemAction {

    // ==== Layout tuning (adjust as you like) ====
    private static final double SIDEBAR_WIDTH_PX = 460;     // overall width of this left pane
    private static final int    SCRATCHPAD_ROWS   = 35;      // fewer rows = shorter TextArea
    private static final double PROBLIST_HEIGHT_PX = 180;    // shorter Problem List region
    private static final double SPACING_PX = 8;
    private static final double PADDING_RIGHT_PX = 8;

    private final IttiaApp app;
    private TextArea scratchpad;

    // Database connection for the problem list
    private Connection dbConn;

    // The problem list is now populated from the database
    private final ObservableList<String> problems = FXCollections.observableArrayList();

    private ListView<String> problemList;
    private TextArea scratchpadArea;
    private final LinkedHashMap<String, String> scratchpadEntries = new LinkedHashMap<>();

    public ListProblemAction(IttiaApp app) {
        this.app = app;
        initProblemListDatabase();
        loadProblemsFromDb();
    }

    /**
     * Initializes the SQLite database for the problem list.
     * Creates the database and table if they don't exist.
     * Populates with default data on the very first run.
     */
    private void initProblemListDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            // Define the path to the new database
            String dbPath = System.getProperty("user.dir") + "/src/main/resources/database/prolist.db";
            dbConn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

            Statement stmt = dbConn.createStatement();
            // Create the 'problems' table with a unique constraint to prevent duplicates
            stmt.execute("CREATE TABLE IF NOT EXISTS problems (id INTEGER PRIMARY KEY AUTOINCREMENT, problem_text TEXT NOT NULL UNIQUE)");

            // Check if the table is empty to add initial default data
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS count FROM problems");
            if (rs.next() && rs.getInt("count") == 0) {
                System.out.println("Problem list database is empty. Populating with default data.");
                stmt.execute("INSERT INTO problems (problem_text) VALUES ('Hypercholesterolemia [F/U]')");
                stmt.execute("INSERT INTO problems (problem_text) VALUES ('Prediabetes (FBS 108 mg/dL)')");
                stmt.execute("INSERT INTO problems (problem_text) VALUES ('Thyroid nodule (small)')");
            }
            rs.close();
            stmt.close();

        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
            System.err.println("FATAL: Failed to initialize Problem List database: " + e.getMessage());
            // In a real app, you might show a disabling alert here
        }
    }

    /**
     * Loads all problems from the database into the ObservableList.
     */
    private void loadProblemsFromDb() {
        if (dbConn == null) return;
        problems.clear();
        String sql = "SELECT problem_text FROM problems ORDER BY id";
        try (Statement stmt = dbConn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                problems.add(rs.getString("problem_text"));
            }

        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("Failed to load problems from database: " + e.getMessage());
        }
    }

    /**
     * Adds a new problem to the database and then updates the UI.
     * @param problemText The problem to add.
     */
    private void addProblem(String problemText) {
        if (dbConn == null) return;
        String sql = "INSERT INTO problems(problem_text) VALUES(?)";
        try (PreparedStatement pstmt = dbConn.prepareStatement(sql)) {
            pstmt.setString(1, problemText);
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                // If DB insert is successful, update the UI list
                Platform.runLater(() -> problems.add(problemText));
            }
        } catch (SQLException e) {
            // This error is expected if the problem already exists due to the UNIQUE constraint.
            System.err.println("Failed to add problem '" + problemText + "'. It might already exist. Details: " + e.getMessage());
            // Optionally, show a user-friendly alert here.
        }
    }

    /**
     * Removes a selected problem from the database and then updates the UI.
     * @param problemText The problem to remove.
     */
    private void removeProblem(String problemText) {
        if (dbConn == null) return;
        String sql = "DELETE FROM problems WHERE problem_text = ?";
        try (PreparedStatement pstmt = dbConn.prepareStatement(sql)) {
            pstmt.setString(1, problemText);
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                // If DB delete is successful, update the UI list
                Platform.runLater(() -> problems.remove(problemText));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("Failed to remove problem '" + problemText + "': " + e.getMessage());
        }
    }

    public VBox buildProblemPane() {
        // --- Problem List Section ---
        problemList = new ListView<>(problems);
        problemList.setPrefWidth(SIDEBAR_WIDTH_PX);
        problemList.setMaxWidth(SIDEBAR_WIDTH_PX);
        problemList.setMinWidth(SIDEBAR_WIDTH_PX);
        // shorter visible height; will scroll when longer
        problemList.setPrefHeight(PROBLIST_HEIGHT_PX);

        problemList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String sel = problemList.getSelectionModel().getSelectedItem();
                if (sel != null) app.insertLineIntoFocusedArea("- " + sel);
            }
        });

        TextField input = new TextField();
        input.setPromptText("Add problem and press Enter");
        input.setOnAction(e -> {
            String text = IttiaAppTextArea.normalizeLine(input.getText());
            if (!text.isBlank()) {
                addProblem(text); // Persist to DB and update UI
                input.clear();
            }
        });

        Button remove = new Button("Remove Selected");
        remove.setOnAction(e -> {
            String selectedProblem = problemList.getSelectionModel().getSelectedItem();
            if (selectedProblem != null) {
                removeProblem(selectedProblem); // Remove from DB and update UI
            }
        });

        HBox problemControls = new HBox(SPACING_PX, input, remove);
        HBox.setHgrow(input, Priority.ALWAYS);
        problemControls.setPrefWidth(SIDEBAR_WIDTH_PX);
        problemControls.setMaxWidth(SIDEBAR_WIDTH_PX);

        // --- Scratchpad Section ---
        this.scratchpadArea = new TextArea();
        scratchpadArea.setPromptText("Scratchpad... (auto-updated from center areas)");
        scratchpadArea.setWrapText(true);
        scratchpadArea.setEditable(true);

        // Make it narrower & shorter (width and height)
        scratchpadArea.setPrefColumnCount(28); // controls width in many layouts
        scratchpadArea.setPrefRowCount(SCRATCHPAD_ROWS);
        scratchpadArea.setMaxWidth(SIDEBAR_WIDTH_PX);
        scratchpadArea.setMinWidth(SIDEBAR_WIDTH_PX);

        // --- Labels ---
        Label scratchpadLabel = new Label("Scratchpad");
        scratchpadLabel.setMaxWidth(SIDEBAR_WIDTH_PX);
        Label problemLabel = new Label("Problem List (Persistent)");
        problemLabel.setMaxWidth(SIDEBAR_WIDTH_PX);

        // --- Assemble the VBox ---
        VBox box = new VBox(
                SPACING_PX,
                scratchpadLabel,
                scratchpadArea,
                new Separator(Orientation.HORIZONTAL),
                problemLabel,
                problemList,
                problemControls
        );

        // Keep vertical growth modest for visibility
        VBox.setVgrow(problemList, Priority.NEVER);     // don't expand taller than prefHeight
        VBox.setVgrow(scratchpadArea, Priority.NEVER);  // keep compact

        box.setPadding(new Insets(0, PADDING_RIGHT_PX, 0, 0));
        box.setPrefWidth(SIDEBAR_WIDTH_PX);
        box.setMaxWidth(SIDEBAR_WIDTH_PX);
        box.setMinWidth(SIDEBAR_WIDTH_PX);

        return box;
    }

    public void updateAndRedrawScratchpad(String title, String newText) {
        String trimmedText = newText.trim();

        if (trimmedText.isEmpty()) {
            scratchpadEntries.remove(title);
        } else {
            String singleLineText = trimmedText.replaceAll("\\s*\\R\\s*", " \n\t ");
            scratchpadEntries.put(title, singleLineText);
        }

        redrawScratchpad();
    }

    public void redrawScratchpad() {
        if (scratchpadArea == null) return;

        List<String> orderedTitles = Arrays.asList(IttiaAppTextArea.TEXT_AREA_TITLES);
        StringJoiner sj = new StringJoiner("\n");
        for (String title : orderedTitles) {
            String value = scratchpadEntries.get(title);
            if (value != null && !value.isEmpty()) {
                sj.add(title + " " + value);
            }
        }

        if (!scratchpadArea.getText().equals(sj.toString())) {
            scratchpadArea.setText(sj.toString());
            scratchpadArea.positionCaret(scratchpadArea.getLength());
            scratchpadArea.setScrollTop(Double.MAX_VALUE);
        }
    }

    public void clearScratchpad() {
        if (scratchpadArea != null) {
            scratchpadArea.clear();
        }
    }

    public ObservableList<String> getProblems() {
        return problems;
    }
}
