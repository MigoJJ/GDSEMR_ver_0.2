// ListProblemAction.java
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
import javafx.scene.control.TextArea;

public class ListProblemAction {

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
        problemList.setPrefWidth(320);
        problemList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String sel = problemList.getSelectionModel().getSelectedItem();
                if (sel != null) app.insertLineIntoFocusedArea("- " + sel);
            }
        });

        TextField input = new TextField();
        input.setPromptText("Add problem and press Enter");
        input.setOnAction(e -> {
            String text = IttiaApp.normalizeLine(input.getText());
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

        HBox problemControls = new HBox(8, input, remove);
        HBox.setHgrow(input, Priority.ALWAYS);

        // --- Scratchpad Section ---
        this.scratchpadArea = new TextArea();
        scratchpadArea.setPromptText("Scratchpad... (auto-updated from center areas)");
        scratchpadArea.setWrapText(true);
        scratchpadArea.setPrefRowCount(16);
        scratchpadArea.setEditable(true);

        // --- Assemble the VBox ---
        VBox box = new VBox(8,
                new Label("Scratchpad"),
                scratchpadArea,
                new Separator(Orientation.HORIZONTAL),
                new Label("Problem List (Persistent)"),
                problemList,
                problemControls
        );

        VBox.setVgrow(problemList, Priority.ALWAYS);
        VBox.setVgrow(scratchpadArea, Priority.ALWAYS);
        box.setPadding(new Insets(0, 10, 0, 0));
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

        List<String> orderedTitles = Arrays.asList(IttiaApp.TEXT_AREA_TITLES);
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
        if (scratchpad != null) {
            scratchpad.clear();
        }
    }

    
    
    public ObservableList<String> getProblems() {
        return problems;
    }
}