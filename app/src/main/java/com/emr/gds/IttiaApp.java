// IttiaApp.java
package com.emr.gds;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.UnaryOperator;

public class IttiaApp extends Application {
    // ---- Instance Variables ----
    private final List<TextArea> areas = new ArrayList<>(10);
    private TextArea lastFocusedArea = null;
    private ListProblemAction problemAction;
    private ListButtonAction buttonAction;
    private Connection dbConn;
    private final Map<String, String> abbrevMap = new HashMap<>();

    // ---- Constants ----
    public static final String[] TEXT_AREA_TITLES = {
            "CC>", "PI>", "ROS>", "PMH>", "S>",
            "O>", "Physical Exam>", "A>", "P>", "Comment>"
    };

    // ---- Main Application Entry Point ----
    @Override
    public void start(Stage stage) {
        stage.setTitle("GDSEMR ITTIA – EMR Prototype (JavaFX)");

        // Initialize components and data
        initAbbrevDatabase();
        problemAction = new ListProblemAction(this);
        buttonAction = new ListButtonAction(this, dbConn, abbrevMap);
        
        // Build the main UI layout
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));
        root.setTop(buttonAction.buildTopBar());
        root.setLeft(problemAction.buildProblemPane());
        root.setCenter(buildCenterAreas());
        root.setBottom(buttonAction.buildBottomBar());

        // Configure and show the scene
        Scene scene = new Scene(root, 1400, 800);
        stage.setScene(scene);
        stage.show();

        // Set initial focus
        Platform.runLater(() -> {
            if (!areas.isEmpty()) {
                areas.get(0).requestFocus();
                lastFocusedArea = areas.get(0);
            }
        });

        // Install global keyboard shortcuts
        installGlobalShortcuts(scene);
    }

    // ---- Database & Data Initialization ----
    private void initAbbrevDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            String dbPath = System.getProperty("user.dir") + "/src/main/resources/database/abbreviations.db";
            dbConn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            
            Statement stmt = dbConn.createStatement();
            stmt.execute("CREATE TABLE IF NOT EXISTS abbreviations (short TEXT PRIMARY KEY, full TEXT)");
            stmt.execute("INSERT OR IGNORE INTO abbreviations (short, full) VALUES ('c', 'hypercholesterolemia')");
            stmt.execute("INSERT OR IGNORE INTO abbreviations (short, full) VALUES ('to', 'hypothyroidism')");

            abbrevMap.clear();
            ResultSet rs = stmt.executeQuery("SELECT * FROM abbreviations");
            while (rs.next()) {
                abbrevMap.put(rs.getString("short"), rs.getString("full"));
            }
            rs.close();
            stmt.close();
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
            System.err.println("Failed to initialize database: " + e.getMessage());
        }
    }

    // ---- UI Component Builders ----
    private GridPane buildCenterAreas() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        int rows = 5, cols = 2;

        for (int i = 0; i < rows * cols; i++) {
            TextArea ta = new TextArea();
            ta.setWrapText(true);
            ta.setFont(Font.font("Monospaced", 13));
            ta.setPrefRowCount(8);
            ta.setPrefColumnCount(40);
            
            String title = (i < TEXT_AREA_TITLES.length) ? TEXT_AREA_TITLES[i] : "Area " + (i + 1);
            ta.setPromptText(title);
            
            // Add listeners for focus and text changes
            ta.focusedProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) {
                    lastFocusedArea = ta;
                }
            });
            final int idx = i;
            if (idx < TEXT_AREA_TITLES.length) {
                ta.textProperty().addListener((obs, oldVal, newVal) ->
                        problemAction.updateAndRedrawScratchpad(TEXT_AREA_TITLES[idx], newVal));
            }
            
            // Handle abbreviation expansion on space key press
            ta.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.SPACE) {
                    int caret = ta.getCaretPosition();
                    String text = ta.getText(0, caret);
                    int start = Math.max(text.lastIndexOf(' '), text.lastIndexOf('\n')) + 1;
                    String word = text.substring(start);
                    
                    if (word.startsWith(":")) {
                        String key = word.substring(1);
                        String replacement = "cd".equals(key) ?
                                LocalDate.now().format(DateTimeFormatter.ISO_DATE) :
                                abbrevMap.get(key);
                        if (replacement != null) {
                            ta.deleteText(start, caret);
                            ta.insertText(start, replacement + " ");
                            event.consume();
                        }
                    }
                }
            });

            ta.setTextFormatter(new TextFormatter<>(filterControlChars()));
            grid.add(ta, i % cols, i / cols);
            areas.add(ta);
        }
        return grid;
    }

    // ---- Text and Clipboard Actions ----
    public void insertTemplateIntoFocusedArea(ListButtonAction.TemplateLibrary t) {
        insertBlockIntoFocusedArea(t.body());
    }

    public void insertLineIntoFocusedArea(String line) {
        String insert = line.endsWith("\n") ? line : line + "\n";
        insertBlockIntoFocusedArea(insert);
    }
    
    public void insertBlockIntoFocusedArea(String block) {
        TextArea ta = getFocusedArea();
        if (ta == null) return;
        int caret = ta.getCaretPosition();
        ta.insertText(caret, block);
        Platform.runLater(ta::requestFocus);
    }

    public void formatCurrentArea() {
        TextArea ta = getFocusedArea();
        if (ta == null) return;
        ta.setText(Formatter.autoFormat(ta.getText()));
    }
    
    public void copyAllToClipboard() {
        StringJoiner sj = new StringJoiner("\n\n");
        
        ObservableList<String> problems = problemAction.getProblems();
        if (!problems.isEmpty()) {
            StringBuilder pb = new StringBuilder("# Problem List (as of ")
                .append(LocalDate.now().format(DateTimeFormatter.ISO_DATE))
                .append(")\n");
            problems.forEach(p -> pb.append("- ").append(p).append("\n"));
            sj.add(pb.toString().trim());
        }

        for (int i = 0; i < areas.size(); i++) {
            String uniqueText = getUniqueLines(areas.get(i).getText());
            if (!uniqueText.isEmpty()) {
                String title = (i < TEXT_AREA_TITLES.length) ?
                        TEXT_AREA_TITLES[i].replaceAll(">$", "") : "Area " + (i + 1);
                sj.add("# " + title + "\n" + uniqueText);
            }
        }

        String result = Formatter.finalizeForEMR(sj.toString());
        ClipboardContent cc = new ClipboardContent();
        cc.putString(result);
        Clipboard.getSystemClipboard().setContent(cc);
        showToast("Copied all content to clipboard");
    }

    private String getUniqueLines(String text) {
        if (text == null || text.isBlank()) return "";
        Set<String> uniqueLines = new LinkedHashSet<>();
        text.lines().map(String::trim).filter(line -> !line.isEmpty()).forEach(uniqueLines::add);
        return String.join("\n", uniqueLines);
    }

    public void clearAllText() {
        areas.forEach(TextArea::clear);
        if (problemAction != null) {
            problemAction.clearScratchpad();
        }
        showToast("All text cleared");
    }

    // ---- Helper and Utility Methods ----
    private TextArea getFocusedArea() {
        for (TextArea ta : areas) {
            if (ta.isFocused()) {
                return ta;
            }
        }
        return lastFocusedArea != null ? lastFocusedArea : (areas.isEmpty() ? null : areas.get(0));
    }
    
    private void installGlobalShortcuts(Scene scene) {
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.I, KeyCombination.CONTROL_DOWN),
                () -> insertTemplateIntoFocusedArea(ListButtonAction.TemplateLibrary.HPI));
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                this::formatCurrentArea);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                this::copyAllToClipboard);
        
        // Ctrl+1...9 and Ctrl+0 shortcuts
        for (int i = 1; i <= 9; i++) {
            final int idx = i - 1;
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.getKeyCode(String.valueOf(i)), KeyCombination.CONTROL_DOWN),
                    () -> focusArea(idx));
        }
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.CONTROL_DOWN),
                () -> focusArea(9));
    }
    
    private void focusArea(int idx) {
        if (idx >= 0 && idx < areas.size()) {
            areas.get(idx).requestFocus();
            lastFocusedArea = areas.get(idx);
        }
    }

    private void showToast(String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        a.setHeaderText(null);
        a.setTitle("Info");
        a.showAndWait();
    }
    
    private static UnaryOperator<TextFormatter.Change> filterControlChars() {
        return change -> {
            if (change.getText() == null || change.getText().isEmpty()) {
                return change;
            }
            String filtered = change.getText().replaceAll("[\\u0000-\\u001F\\u007F]", "\n");
            if (!filtered.equals(change.getText())) {
                // Optional: Log or alert user about filtered characters
                System.out.println("Filtered control characters from input: " + change.getText());
            }
            change.setText(filtered);
            return change;
        };
    }
    
    public List<TextArea> getTextAreas() {
        return this.areas;
    }
    
    public static class Formatter {
        static String autoFormat(String raw) {
            if (raw == null || raw.isBlank()) return "";
            String[] lines = raw.replace("\r", "").split("\n", -1);
            StringBuilder out = new StringBuilder();
            boolean lastBlank = false;
            
            for (String line : lines) {
                String t = line.strip().replaceAll("^[•·→▶▷‣⦿∘*]+\\s*|^[-]{1,2}\\s*", "- ");
                if (t.isEmpty()) {
                    if (!lastBlank) {
                        out.append("\n");
                        lastBlank = true;
                    }
                } else {
                    out.append(t).append("\n");
                    lastBlank = false;
                }
            }
            return out.toString().strip();
        }
        
        static String finalizeForEMR(String raw) {
            String s = autoFormat(raw);
            s = s.replaceAll("^(#+)([^#\\n])", "$1 $2");
            s = s.replaceAll("\\n{3,}", "\n\n");
            return s.trim();
        }
    }
 // IttiaApp.java
    public static String normalizeLine(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }
    public static void main(String[] args) {
        launch(args);
    }
}
