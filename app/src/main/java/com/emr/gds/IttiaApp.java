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
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
//import com.emr.gds.inputdata.VitalBPHbA1cFU;
import javax.swing.SwingUtilities;

public class IttiaApp extends Application {
    // ---- Instance Variables ----
    private ListProblemAction problemAction;
    private ListButtonAction buttonAction;
    private Connection dbConn;
    private final Map<String, String> abbrevMap = new HashMap<>();
    private IttiaAppTextArea textAreaManager;

 // ---- Main Application Entry Point ----
    @Override
    public void start(Stage stage) {
        stage.setTitle("GDSEMR ITTIA – EMR Prototype (JavaFX)");

        // --- 1. Initialization and Component Setup ---
        initAbbrevDatabase();
        problemAction = new ListProblemAction(this);
        textAreaManager = new IttiaAppTextArea(abbrevMap, problemAction);
        buttonAction = new ListButtonAction(this, dbConn, abbrevMap);
        
        // --- 2. UI Layout Creation ---
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));
        
        // --- 3. Build and Configure UI Components ---
        // Top Bar (Toolbar)
        ToolBar topBar = buttonAction.buildTopBar();
        Button templateButton = new Button("Load Template");
        templateButton.setOnAction(e -> openTemplateEditor());
        topBar.getItems().add(new Separator());
        topBar.getItems().add(templateButton);
        
        
     // Add Vital BP & HbA1c button to the top bar
        Button vitalButton = new Button("Vital BP & HbA1c");
        vitalButton.setOnAction(e -> openVitalBPHbA1cFU());
        topBar.getItems().add(new Separator());
        topBar.getItems().add(vitalButton);
        
        root.setTop(topBar);

        // Left Panel
        VBox leftPanel = problemAction.buildProblemPane();
        BorderPane.setMargin(leftPanel, new Insets(0, 10, 0, 0));
        root.setLeft(leftPanel);

        // Center Panel
        javafx.scene.layout.Pane centerPane = textAreaManager.buildCenterAreas();
        centerPane.setStyle("-fx-background-color: linear-gradient(to bottom right, #FFFACD, #FAFAD2);");
        root.setCenter(centerPane);
        
        // Bottom Bar
        root.setBottom(buttonAction.buildBottomBar());

        // --- 4. Scene Configuration and Display ---
        Scene scene = new Scene(root, 1200, 900);
        stage.setScene(scene);
        stage.show();

        // --- 5. Post-Display Actions ---
        Platform.runLater(() -> {
            textAreaManager.focusArea(0);
        });
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

    // ---- Text and Clipboard Actions ----
    public void insertTemplateIntoFocusedArea(ListButtonAction.TemplateLibrary t) {
        textAreaManager.insertTemplateIntoFocusedArea(t);
    }

    public void insertLineIntoFocusedArea(String line) {
        textAreaManager.insertLineIntoFocusedArea(line);
    }
    
    public void insertBlockIntoFocusedArea(String block) {
        textAreaManager.insertBlockIntoFocusedArea(block);
    }

    public void formatCurrentArea() {
        textAreaManager.formatCurrentArea();
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

        for (int i = 0; i < textAreaManager.getTextAreas().size(); i++) {
            String uniqueText = textAreaManager.getUniqueLines(textAreaManager.getTextAreas().get(i).getText());
            if (!uniqueText.isEmpty()) {
                String title = (i < IttiaAppTextArea.TEXT_AREA_TITLES.length) ?
                        IttiaAppTextArea.TEXT_AREA_TITLES[i].replaceAll(">$", "") : "Area " + (i + 1);
                sj.add("# " + title + "\n" + uniqueText);
            }
        }

        String result = IttiaAppTextArea.Formatter.finalizeForEMR(sj.toString());
        ClipboardContent cc = new ClipboardContent();
        cc.putString(result);
        Clipboard.getSystemClipboard().setContent(cc);
        showToast("Copied all content to clipboard");
    }

    public void clearAllText() {
        textAreaManager.clearAllTextAreas();
        if (problemAction != null) {
            problemAction.clearScratchpad();
        }
        showToast("All text cleared");
    }

    // ---- Helper and Utility Methods ----
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
                    () -> textAreaManager.focusArea(idx));
        }
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.CONTROL_DOWN),
                () -> textAreaManager.focusArea(9));
    }
    
    private void showToast(String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        a.setHeaderText(null);
        a.setTitle("Info");
        a.showAndWait();
    }
    
    private void openTemplateEditor() {
        // Swing UI operations must be done on the Swing Event Dispatch Thread (EDT).
        SwingUtilities.invokeLater(() -> {
            // Create the editor and pass it a callback function.
            // This function defines what to do when "Use Template" is clicked.
            FU_edit editor = new FU_edit(templateContent -> {
                // The callback is executed on the Swing EDT.
                // To update the JavaFX UI, we must switch back to the JavaFX Application Thread.
                Platform.runLater(() -> textAreaManager.parseAndAppendTemplate(templateContent));
            });
            editor.setVisible(true);
        });
    }
    
 // New method to open VitalBPHbA1cFU
    public void openVitalBPHbA1cFU() {
//        SwingUtilities.invokeLater(() -> new VitalBPHbA1cFU(this));
    }
    public static void main(String[] args) {
        launch(args);
    }
}