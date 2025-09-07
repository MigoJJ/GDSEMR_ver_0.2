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
import java.util.Optional;
import java.util.StringJoiner;

import javax.swing.SwingUtilities;

import com.emr.gds.input.IAIFreqFrame;
import com.emr.gds.input.IAIFxTextAreaManager;
import com.emr.gds.input.IAIMain;
import com.emr.gds.input.IAITextAreaManager; // Import the interface
import com.emr.gds.main.IAMFunctionkey;
import com.emr.gds.main.IAMTextArea;
import com.emr.gds.main.IAMButtonAction;
import com.emr.gds.main.IAMProblemAction;
import com.emr.gds.main.IAMTextFormatUtil;

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
 * Main JavaFX Application for GDSEMR ITTIA - EMR Prototype.
 * Handles UI layout, database initialization, and user interactions.
 */
public class IttiaApp extends Application {

    // ================================
    // CONSTANTS
    // ================================
    private static final String APP_TITLE = "GDSEMR ITTIA – EMR Prototype (JavaFX)";
    private static final int SCENE_WIDTH = 1350;
    private static final int SCENE_HEIGHT = 1000;
    private static final String DB_FILENAME = "abbreviations.db";
    private static final String DB_TABLE_NAME = "abbreviations";
    private static final String DB_URL_PREFIX = "jdbc:sqlite:";
    private static final String DB_DRIVER = "org.sqlite.JDBC";
    private static final String DEFAULT_ABBREV_C = "hypercholesterolemia";
    private static final String DEFAULT_ABBREV_TO = "hypothyroidism";
    private static final int INITIAL_FOCUS_AREA = 0; // First text area

    // ================================
    // INSTANCE VARIABLES
    // ================================
    private IAMProblemAction problemAction;
    private IAMButtonAction buttonAction;
    private IAMTextArea textAreaManager; // Manages the UI text areas
    private Connection dbConn;
    private final Map<String, String> abbrevMap = new HashMap<>();
    private IAIFreqFrame freqStage; // Vital window management (singleton pattern for reuse)
    private IAMFunctionkey functionKeyHandler;

    // ================================
    // APPLICATION LIFECYCLE
    // ================================

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        stage.setTitle(APP_TITLE);

        try {
            initializeApplicationComponents(); // Initialize data, managers, DB
            BorderPane root = buildRootLayout(); // Build UI
            Scene scene = new Scene(root, SCENE_WIDTH, SCENE_HEIGHT);
            stage.setScene(scene);
            stage.show();
            configurePostShow(scene); // Setup after showing stage
        } catch (Exception e) {
            showFatalError("Application Startup Error", "Failed to start the application: " + e.getMessage(), e);
        }
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        if (dbConn != null && !dbConn.isClosed()) {
            dbConn.close();
            System.out.println("Database connection closed.");
        }
    }

    // ================================
    // INITIALIZATION METHODS
    // ================================

    private void initializeApplicationComponents() throws SQLException, IOException, ClassNotFoundException {
        initAbbrevDatabase();
        // Initialize managers using 'this' after DB is ready
        problemAction = new IAMProblemAction(this);
        textAreaManager = new IAMTextArea(abbrevMap, problemAction);
        buttonAction = new IAMButtonAction(this, dbConn, abbrevMap);
        functionKeyHandler = new IAMFunctionkey(this);
    }

    private void initAbbrevDatabase() throws ClassNotFoundException, SQLException, IOException {
        Class.forName(DB_DRIVER);
        Path dbFile = getDbPath(DB_FILENAME);
        Files.createDirectories(dbFile.getParent());
        String url = DB_URL_PREFIX + dbFile.toAbsolutePath();
        System.out.println("[DB PATH] abbreviations -> " + dbFile.toAbsolutePath());

        dbConn = DriverManager.getConnection(url);
        createAbbreviationTable();
        loadAbbreviations();
    }

    private void createAbbreviationTable() throws SQLException {
        try (Statement stmt = dbConn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS " + DB_TABLE_NAME + " (short TEXT PRIMARY KEY, full TEXT)");
            stmt.execute("INSERT OR IGNORE INTO " + DB_TABLE_NAME + " (short, full) VALUES ('c', '" + DEFAULT_ABBREV_C + "')");
            stmt.execute("INSERT OR IGNORE INTO " + DB_TABLE_NAME + " (short, full) VALUES ('to', '" + DEFAULT_ABBREV_TO + "')");
        }
    }

    private void loadAbbreviations() throws SQLException {
        abbrevMap.clear();
        try (Statement stmt = dbConn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM " + DB_TABLE_NAME)) {
            while (rs.next()) {
                abbrevMap.put(rs.getString("short"), rs.getString("full"));
            }
        }
    }

    // ================================
    // UI LAYOUT METHODS
    // ================================

    private BorderPane buildRootLayout() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        root.setTop(buildTopToolBar());
        root.setLeft(buildLeftPanel());
        root.setCenter(buildCenterPanel());
        root.setBottom(buildBottomPanel());

        establishBridgeConnection(); // Setup bridge connection after UI is built

        return root;
    }

    private ToolBar buildTopToolBar() {
        ToolBar topBar = buttonAction.buildTopBar();

        Button templateButton = new Button("Load Template");
        templateButton.setOnAction(e -> openTemplateEditor());

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
            freqStage = new IAIFreqFrame();
        } else {
            freqStage.requestFocus();
            freqStage.toFront();
        }
    }

    private void openTemplateEditor() {
        SwingUtilities.invokeLater(() -> {
            IAFMainEdit editor = new IAFMainEdit(templateContent ->
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
            if (!isBridgeReady()) { // Re-check or ensure bridge after UI is shown
                establishBridgeConnection();
            }
            textAreaManager.focusArea(INITIAL_FOCUS_AREA);
        });
        installAllKeyboardShortcuts(scene);
    }

    private void establishBridgeConnection() {
        var areas = textAreaManager.getTextAreas();
        if (areas == null || areas.isEmpty()) { // Check for empty, not just size
            throw new IllegalStateException("EMR text areas not initialized. buildCenterAreas() must run first.");
        }
        // Set the global static manager for external access
        IAIMain.setTextAreaManager(new IAIFxTextAreaManager(areas));
    }

    private boolean isBridgeReady() {
        return Optional.ofNullable(IAIMain.getTextAreaManager()).map(IAITextAreaManager::isReady).orElse(false);
    }

    // ================================
    // KEYBOARD SHORTCUTS
    // ================================

    private void installAllKeyboardShortcuts(Scene scene) {
        installGlobalKeyboardShortcuts(scene);
        functionKeyHandler.installFunctionKeyShortcuts(scene); // Simplified call
    }

    private void installGlobalKeyboardShortcuts(Scene scene) {
        Map<KeyCombination, Runnable> shortcuts = new HashMap<>();

        shortcuts.put(new KeyCodeCombination(KeyCode.I, KeyCombination.CONTROL_DOWN), () -> insertTemplateIntoFocusedArea(IAMButtonAction.TemplateLibrary.HPI));
        shortcuts.put(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN), this::formatCurrentArea);
        shortcuts.put(new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN), this::copyAllToClipboard);

        addAreaFocusShortcuts(shortcuts);

        shortcuts.forEach((keyCombination, action) -> scene.getAccelerators().put(keyCombination, action));
    }

    private void addAreaFocusShortcuts(Map<KeyCombination, Runnable> shortcuts) {
        for (int i = 1; i <= 9; i++) {
            final int areaIndex = i - 1;
            shortcuts.put(new KeyCodeCombination(KeyCode.getKeyCode(String.valueOf(i)), KeyCombination.CONTROL_DOWN),
                          () -> textAreaManager.focusArea(areaIndex));
        }
        shortcuts.put(new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.CONTROL_DOWN), () -> textAreaManager.focusArea(9));
    }

    // ================================
    // TEXT MANIPULATION METHODS
    // ================================

    public void insertTemplateIntoFocusedArea(IAMButtonAction.TemplateLibrary template) {
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
        Optional.ofNullable(problemAction).ifPresent(IAMProblemAction::clearScratchpad);
        showToast("All text cleared");
    }

    // ================================
    // CLIPBOARD OPERATIONS
    // ================================

    public void copyAllToClipboard() {
        String compiledContent = compileAllContent();
        String finalizedContent = IAMTextFormatUtil.finalizeForEMR(compiledContent);

        ClipboardContent clipboardContent = new ClipboardContent();
        clipboardContent.putString(finalizedContent);
        Clipboard.getSystemClipboard().setContent(clipboardContent);

        showToast("Copied all content to clipboard");
    }

    private String compileAllContent() {
        StringJoiner contentJoiner = new StringJoiner("\n\n");
        addProblemListToContent(contentJoiner);
        addTextAreasToContent(contentJoiner);
        return contentJoiner.toString();
    }

    private void addProblemListToContent(StringJoiner contentJoiner) {
        ObservableList<String> problems = Optional.ofNullable(problemAction)
                                                  .map(IAMProblemAction::getProblems)
                                                  .orElse(null);
        if (problems != null && !problems.isEmpty()) {
            StringBuilder problemBuilder = new StringBuilder("# Problem List (as of ")
                    .append(LocalDate.now().format(DateTimeFormatter.ISO_DATE))
                    .append(")\n");
            problems.forEach(problem -> problemBuilder.append("- ").append(problem).append("\n"));
            contentJoiner.add(problemBuilder.toString().trim());
        }
    }

    private void addTextAreasToContent(StringJoiner contentJoiner) {
        List<TextArea> textAreas = Optional.ofNullable(textAreaManager)
                                           .map(IAMTextArea::getTextAreas)
                                           .orElse(List.of()); // Return empty list if manager is null

        for (int i = 0; i < textAreas.size(); i++) {
            String uniqueText = IAMTextFormatUtil.getUniqueLines(textAreas.get(i).getText());
            if (!uniqueText.isEmpty()) {
                String title = getAreaTitle(i);
                contentJoiner.add("# " + title + "\n" + uniqueText);
            }
        }
    }

    private String getAreaTitle(int areaIndex) {
        return (areaIndex < IAMTextArea.TEXT_AREA_TITLES.length) ?
                IAMTextArea.TEXT_AREA_TITLES[areaIndex].replaceAll(">$", "") :
                "Area " + (areaIndex + 1);
    }

    // ================================
    // UTILITY METHODS
    // ================================

    private Path getRepoRoot() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("gradlew")) && !Files.exists(p.resolve(".git"))) {
            p = p.getParent();
        }
        return (p != null) ? p : Paths.get("").toAbsolutePath();
    }

    private Path getDbPath(String fileName) {
        return getRepoRoot().resolve("app").resolve("db").resolve(fileName);
    }

    private void showToast(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setHeaderText(null);
        alert.setTitle("Info");
        alert.showAndWait();
    }

    private void showFatalError(String title, String message, Throwable cause) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText("A fatal error occurred.");
        alert.setContentText(message + "\n\nDetails: " + cause.getMessage());
        alert.getDialogPane().setExpandableContent(new TextArea(formatStackTrace(cause)));
        alert.showAndWait();
        Platform.exit(); // Exit the application on fatal error
    }

    private String formatStackTrace(Throwable t) {
        StringJoiner sj = new StringJoiner("\n");
        sj.add(t.toString());
        for (StackTraceElement element : t.getStackTrace()) {
            sj.add("\tat " + element);
        }
        return sj.toString();
    }

    // ================================
    // GETTER METHODS (public for manager access)
    // ================================

    public IAMTextArea getTextAreaManager() {
        return textAreaManager;
    }

    public Connection getDbConnection() {
        return dbConn;
    }

    public Map<String, String> getAbbrevMap() {
        return abbrevMap;
    }

    public IAMFunctionkey getFunctionKeyHandler() {
        return functionKeyHandler;
    }
}