package com.emr.gds;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import javax.swing.SwingUtilities;

import com.emr.gds.input.FreqInputFrame;
import com.emr.gds.input.FxTextAreaManager;
import com.emr.gds.input.IttiaAppMain;
import com.emr.gds.main.IttiaAppTextArea;
import com.emr.gds.main.ListButtonAction;
import com.emr.gds.main.ListProblemAction;
import com.emr.gds.main.TextFormatUtil;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Main JavaFX Application for GDSEMR ITTIA - EMR Prototype
 * Handles UI layout, database initialization, and user interactions
 */
public class IttiaApp extends Application {
    // ================================
    // INSTANCE VARIABLES
    // ================================
    
    private ListProblemAction problemAction;
    private ListButtonAction buttonAction;
    private IttiaAppTextArea textAreaManager;
    
    private Connection dbConn;
    private final Map<String, String> abbrevMap = new HashMap<>();
    
    // Vital window management (singleton pattern for reuse)
    private FreqInputFrame freqStage;
    
    // Function key handler
    private IttiaAppFunctionkey functionKeyHandler;

    // ================================
    // DB HELPERS (Repo-tracked under app/db)
    // ================================
    
    private static Path repoRoot() {
        // Walk upward to find the repo root (dir containing 'gradlew' or '.git')
        Path p = Paths.get("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("gradlew")) && !Files.exists(p.resolve(".git"))) {
            p = p.getParent();
        }
        return (p != null) ? p : Paths.get("").toAbsolutePath();
    }

    private static Path dbPath(String fileName) {
        return repoRoot().resolve("app").resolve("db").resolve(fileName);
    }

    // ================================
    // APPLICATION LIFECYCLE
    // ================================
    
    public static void main(String[] args) {
        launch(args);
    }
    
    @Override
    public void start(Stage stage) {
        stage.setTitle("GDSEMR ITTIA – EMR Prototype (JavaFX)");
        
        // 1. Initialize data and managers
        initializeApplication();
        
        // 2. Build UI layout
        BorderPane root = buildRootLayout();
        Scene scene = new Scene(root, 1300, 1000);
        
        // 3. Display and configure
        stage.setScene(scene);
        stage.show();
        
        // 4. Post-show setup
        configurePostShow(scene);
    }
    
    // ================================
    // INITIALIZATION METHODS
    // ================================
    
    private void initializeApplication() {
        initAbbrevDatabase();
        ensureManagersInitialized();
        initializeFunctionKeyHandler();
    }
    
    private void initAbbrevDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            Path db = dbPath("abbreviations.db");
            Files.createDirectories(db.getParent());
            String url = "jdbc:sqlite:" + db.toAbsolutePath();
            System.out.println("[DB PATH] abbreviations -> " + db.toAbsolutePath());
            
            dbConn = DriverManager.getConnection(url);
            
            createAbbreviationTable();
            loadAbbreviations();
            
        } catch (ClassNotFoundException | SQLException | IOException e) {
            e.printStackTrace();
            System.err.println("Failed to initialize database: " + e.getMessage());
            throw new RuntimeException("Failed to initialize abbreviations.db", e);
        }
    }
    
    private void createAbbreviationTable() throws SQLException {
        Statement stmt = dbConn.createStatement();
        stmt.execute("CREATE TABLE IF NOT EXISTS abbreviations (short TEXT PRIMARY KEY, full TEXT)");
        
        // Insert default abbreviations
        stmt.execute("INSERT OR IGNORE INTO abbreviations (short, full) VALUES ('c', 'hypercholesterolemia')");
        stmt.execute("INSERT OR IGNORE INTO abbreviations (short, full) VALUES ('to', 'hypothyroidism')");
        
        stmt.close();
    }
    
    private void loadAbbreviations() throws SQLException {
        abbrevMap.clear();
        Statement stmt = dbConn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT * FROM abbreviations");
        
        while (rs.next()) {
            abbrevMap.put(rs.getString("short"), rs.getString("full"));
        }
        
        rs.close();
        stmt.close();
    }
    
    private void ensureManagersInitialized() {
        if (problemAction == null) {
            problemAction = new ListProblemAction(this);
        }
        if (textAreaManager == null) {
            textAreaManager = new IttiaAppTextArea(abbrevMap, problemAction);
        }
        if (buttonAction == null) {
            buttonAction = new ListButtonAction(this, dbConn, abbrevMap);
        }
    }
    
    private void initializeFunctionKeyHandler() {
        functionKeyHandler = new IttiaAppFunctionkey(this);
    }
    
    // ================================
    // UI LAYOUT METHODS
    // ================================
    
    private BorderPane buildRootLayout() {
        ensureManagersInitialized();
        
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));
        
        // Configure layout sections
        root.setTop(buildTopToolBar());
        root.setLeft(buildLeftPanel());
        root.setCenter(buildCenterPanel());
        root.setBottom(buildBottomPanel());
        
        // Setup bridge connection after UI is built
        establishBridgeConnection();
        
        return root;
    }
    
    private ToolBar buildTopToolBar() {
        ToolBar topBar = buttonAction.buildTopBar();
        
        // Add template button
        Button templateButton = new Button("Load Template");
        templateButton.setOnAction(e -> openTemplateEditor());
        
        // Add vital button
        Button vitalButton = new Button("Vital BP & HbA1c");
        vitalButton.setOnAction(e -> openVitalWindow());
        
        topBar.getItems().addAll(
            new Separator(), templateButton,
            new Separator(), vitalButton
        );
        
        return topBar;
    }
    
    private VBox buildLeftPanel() {
        VBox leftPanel = problemAction.buildProblemPane();
        BorderPane.setMargin(leftPanel, new Insets(0, 10, 0, 0));
        return leftPanel;
    }
    
    private GridPane buildCenterPanel() {
        GridPane centerPane = textAreaManager.buildCenterAreas();
        centerPane.setStyle("-fx-background-color: linear-gradient(to bottom right, #FFFACD, #FAFAD2);");
        return centerPane;
    }
    
    private ToolBar buildBottomPanel() {
        try {
            return buttonAction.buildBottomBar();
        } catch (Exception e) {
            System.err.println("Error building bottom panel: " + e.getMessage());
            e.printStackTrace();
            
            // Return a fallback ToolBar
            ToolBar fallbackToolBar = new ToolBar();
            Label errorLabel = new Label("Error loading bottom panel");
            errorLabel.setStyle("-fx-text-fill: red; -fx-font-size: 12px;");
            fallbackToolBar.getItems().add(errorLabel);
            
            return fallbackToolBar;
        }
    }
    
    // ================================
    // WINDOW MANAGEMENT
    // ================================
    
    public void openVitalWindow() {
        if (!isBridgeReady()) {
            showToast("Text areas not ready yet. Please try again in a moment.");
            return;
        }
        
        if (freqStage == null || !freqStage.isShowing()) {
            freqStage = new FreqInputFrame();
        } else {
            freqStage.requestFocus();
            freqStage.toFront();
        }
    }
    
    private void openTemplateEditor() {
        SwingUtilities.invokeLater(() -> {
            FU_edit editor = new FU_edit(templateContent ->
                Platform.runLater(() -> textAreaManager.parseAndAppendTemplate(templateContent))
            );
            editor.setVisible(true);
        });
    }
    
    // ================================
    // POST-INITIALIZATION SETUP
    // ================================
    
    private void configurePostShow(Scene scene) {
        Platform.runLater(() -> {
            try {
                IttiaAppMain.getTextAreaManager();
            } catch (Exception ignored) {
                establishBridgeConnection(); // Retry if connection failed
            }
            textAreaManager.focusArea(0);
        });
        
        installAllKeyboardShortcuts(scene);
    }
    
    private void establishBridgeConnection() {
        var areas = textAreaManager.getTextAreas();
        if (areas == null || areas.size() < 10) {
            throw new IllegalStateException("EMR text areas not initialized. buildCenterAreas() must run first.");
        }
        
        IttiaAppMain.setTextAreaManager(new FxTextAreaManager(areas));
    }
    
    private boolean isBridgeReady() {
        try {
            return IttiaAppMain.getTextAreaManager().isReady();
        } catch (Exception e) {
            return false;
        }
    }
    
    // ================================
    // KEYBOARD SHORTCUTS
    // ================================
    
    private void installAllKeyboardShortcuts(Scene scene) {
        installGlobalKeyboardShortcuts(scene);
        installFunctionKeyShortcuts(scene);
    }
    
    private void installGlobalKeyboardShortcuts(Scene scene) {
        Map<KeyCombination, Runnable> shortcuts = createShortcutMap();
        
        shortcuts.forEach((keyCombination, action) -> 
            scene.getAccelerators().put(keyCombination, action)
        );
    }
    
    private void installFunctionKeyShortcuts(Scene scene) {
        if (functionKeyHandler != null) {
            functionKeyHandler.installFunctionKeyShortcuts(scene);
        }
    }
    
    private Map<KeyCombination, Runnable> createShortcutMap() {
        Map<KeyCombination, Runnable> shortcuts = new HashMap<>();
        
        // Template and formatting shortcuts
        shortcuts.put(
            new KeyCodeCombination(KeyCode.I, KeyCombination.CONTROL_DOWN),
            () -> insertTemplateIntoFocusedArea(ListButtonAction.TemplateLibrary.HPI)
        );
        shortcuts.put(
            new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
            this::formatCurrentArea
        );
        shortcuts.put(
            new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
            this::copyAllToClipboard
        );
        
        // Area focus shortcuts (Ctrl+1 through Ctrl+9, Ctrl+0)
        addAreaFocusShortcuts(shortcuts);
        
        return shortcuts;
    }
    
    private void addAreaFocusShortcuts(Map<KeyCombination, Runnable> shortcuts) {
        // Ctrl+1 through Ctrl+9
        for (int i = 1; i <= 9; i++) {
            final int areaIndex = i - 1;
            shortcuts.put(
                new KeyCodeCombination(KeyCode.getKeyCode(String.valueOf(i)), KeyCombination.CONTROL_DOWN),
                () -> textAreaManager.focusArea(areaIndex)
            );
        }
        
        // Ctrl+0 for area 10
        shortcuts.put(
            new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.CONTROL_DOWN),
            () -> textAreaManager.focusArea(9)
        );
    }
    
    // ================================
    // TEXT MANIPULATION METHODS
    // ================================
    
    public void insertTemplateIntoFocusedArea(ListButtonAction.TemplateLibrary template) {
        textAreaManager.insertTemplateIntoFocusedArea(template);
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
    
    public void clearAllText() {
        textAreaManager.clearAllTextAreas();
        if (problemAction != null) {
            problemAction.clearScratchpad();
        }
        showToast("All text cleared");
    }
    
    // ================================
    // CLIPBOARD OPERATIONS
    // ================================
    
    public void copyAllToClipboard() {
        String compiledContent = compileAllContent();
        String finalizedContent = TextFormatUtil.finalizeForEMR(compiledContent);
        
        ClipboardContent clipboardContent = new ClipboardContent();
        clipboardContent.putString(finalizedContent);
        Clipboard.getSystemClipboard().setContent(clipboardContent);
        
        showToast("Copied all content to clipboard");
    }
    
    private String compileAllContent() {
        StringJoiner contentJoiner = new StringJoiner("\n\n");
        
        // Add problem list
        addProblemListToContent(contentJoiner);
        
        // Add text areas content
        addTextAreasToContent(contentJoiner);
        
        return contentJoiner.toString();
    }
    
    private void addProblemListToContent(StringJoiner contentJoiner) {
        ObservableList<String> problems = problemAction.getProblems();
        if (!problems.isEmpty()) {
            StringBuilder problemBuilder = new StringBuilder("# Problem List (as of ")
                    .append(LocalDate.now().format(DateTimeFormatter.ISO_DATE))
                    .append(")\n");
            
            problems.forEach(problem -> problemBuilder.append("- ").append(problem).append("\n"));
            contentJoiner.add(problemBuilder.toString().trim());
        }
    }
    
    private void addTextAreasToContent(StringJoiner contentJoiner) {
        List<TextArea> textAreas = textAreaManager.getTextAreas();
        
        for (int i = 0; i < textAreas.size(); i++) {
            String uniqueText = TextFormatUtil.getUniqueLines(textAreas.get(i).getText());
            if (!uniqueText.isEmpty()) {
                String title = getAreaTitle(i);
                contentJoiner.add("# " + title + "\n" + uniqueText);
            }
        }
    }
    
    private String getAreaTitle(int areaIndex) {
        if (areaIndex < IttiaAppTextArea.TEXT_AREA_TITLES.length) {
            return IttiaAppTextArea.TEXT_AREA_TITLES[areaIndex].replaceAll(">$", "");
        }
        return "Area " + (areaIndex + 1);
    }
    
    // ================================
    // UTILITY METHODS
    // ================================
    
    private void showToast(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setHeaderText(null);
        alert.setTitle("Info");
        alert.showAndWait();
    }
    
    // ================================
    // GETTER METHODS
    // ================================
    
    public IttiaAppTextArea getTextAreaManager() {
        return textAreaManager;
    }
    
    public Connection getDbConnection() {
        return dbConn;
    }
    
    public Map<String, String> getAbbrevMap() {
        return abbrevMap;
    }
    
    public IttiaAppFunctionkey getFunctionKeyHandler() {
        return functionKeyHandler;
    }
}