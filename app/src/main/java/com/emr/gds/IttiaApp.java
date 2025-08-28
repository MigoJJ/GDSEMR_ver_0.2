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

import javax.swing.SwingUtilities;

import com.emr.gds.input.FreqInputFrame;
import com.emr.gds.input.IttiaAppMain;
import com.emr.gds.input.FxTextAreaManager;          // <-- NEW: bridge impl
import com.emr.gds.main.IttiaAppTextArea;
import com.emr.gds.main.ListButtonAction;
import com.emr.gds.main.ListProblemAction;
import com.emr.gds.main.TextFormatUtil;
import com.emr.gds.input.IttiaAppMain;
import com.emr.gds.input.FxTextAreaManager;
public class IttiaApp extends Application {
    // ---- Instance Variables ----
    private ListProblemAction problemAction;
    private ListButtonAction buttonAction;
    private Connection dbConn;
    private final Map<String, String> abbrevMap = new HashMap<>();
    private IttiaAppTextArea textAreaManager;

    // Vital 보조창(이미 열려 있으면 앞으로만 가져오도록)
    private FreqInputFrame freqStage;

    // ---- Main Application Entry Point ----
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        stage.setTitle("GDSEMR ITTIA – EMR Prototype (JavaFX)");

        initAppState();                   // 1) 데이터/매니저 초기화
        BorderPane root = buildRoot();    // 2) 레이아웃 구성

        Scene scene = new Scene(root, 1300, 1000);
        stage.setScene(scene);
        stage.show();

        postShow(scene);                  // 3) 표시 이후 포커싱/단축키
    }

    /* =============================== *
     *  1) 초기화 로직
     * =============================== */
    private void initAppState() {
        initAbbrevDatabase();
        problemAction   = new ListProblemAction(this);
        textAreaManager = new IttiaAppTextArea(abbrevMap, problemAction);
        buttonAction    = new ListButtonAction(this, dbConn, abbrevMap);

        // --- BRIDGE WIRING ---
        // FreqInputFrame -> IttiaAppMain -> FxTextAreaManager -> your 10 TextAreas
        // This is the key line that makes Save/Append work from FreqInputFrame.
        IttiaAppMain.setTextAreaManager(new FxTextAreaManager(textAreaManager.getTextAreas()));
    }

    /* =============================== *
     *  2) 루트 UI 구성
     * =============================== */
    private BorderPane buildRoot() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // TopBar
        ToolBar topBar = buildTopBar();
        root.setTop(topBar);

        // Left (Problem List)
        VBox leftPanel = problemAction.buildProblemPane();
        BorderPane.setMargin(leftPanel, new Insets(0, 10, 0, 0));
        root.setLeft(leftPanel);

        // Center (EMR 10칸 템플릿)
        GridPane centerPane = textAreaManager.buildCenterAreas();
        centerPane.setStyle("-fx-background-color: linear-gradient(to bottom right, #FFFACD, #FAFAD2);");
        root.setCenter(centerPane);

        // Bottom (주요 버튼)
        root.setBottom(buttonAction.buildBottomBar());

        return root;
    }

    /* =============================== *
     *  TopBar 구성 (템플릿 버튼 + Vital 버튼)
     * =============================== */
    private ToolBar buildTopBar() {
        ToolBar topBar = buttonAction.buildTopBar();

        // Template Button
        Button templateButton = new Button("Load Template");
        templateButton.setOnAction(e -> openTemplateEditor());
        topBar.getItems().addAll(new Separator(), templateButton);

        // Vital BP & HbA1c (JavaFX Stage로 열기)
        Button vitalButton = new Button("Vital BP & HbA1c");
        vitalButton.setOnAction(e -> openVitalWindow());
        topBar.getItems().addAll(new Separator(), vitalButton);

        return topBar;
    }

    /* =============================== *
     *  Vital 보조창 열기/포커스
     * =============================== */
    private void openVitalWindow() {
        if (!ensureBridgeReady()) {
            showToast("Text areas not ready yet. Please try again in a moment.");
            return;
        }

        if (freqStage == null || !freqStage.isShowing()) {
            freqStage = new FreqInputFrame();  // JavaFX 버전 Stage
        } else {
            freqStage.requestFocus();
            freqStage.toFront();
        }
    }

    private boolean ensureBridgeReady() {
        try {
            return IttiaAppMain.getTextAreaManager().isReady();
        } catch (Exception e) {
            return false;
        }
    }

    /* =============================== *
     *  3) 표시 이후 처리
     * =============================== */
    private void postShow(Scene scene) {
        Platform.runLater(() -> textAreaManager.focusArea(0));
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
            String uniqueText = TextFormatUtil.getUniqueLines(textAreaManager.getTextAreas().get(i).getText());
            if (!uniqueText.isEmpty()) {
                String title = (i < IttiaAppTextArea.TEXT_AREA_TITLES.length) ?
                        IttiaAppTextArea.TEXT_AREA_TITLES[i].replaceAll(">$", "") : "Area " + (i + 1);
                sj.add("# " + title + "\n" + uniqueText);
            }
        }

        // finalize for EMR
        String result = TextFormatUtil.finalizeForEMR(sj.toString());
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
            FU_edit editor = new FU_edit(templateContent ->
                Platform.runLater(() -> textAreaManager.parseAndAppendTemplate(templateContent))
            );
            editor.setVisible(true);
        });
    }

    // ---- Getter Methods ----
    public IttiaAppTextArea getTextAreaManager() {
        return textAreaManager;
    }

    public Connection getDbConnection() {
        return dbConn;
    }

    public Map<String, String> getAbbrevMap() {
        return abbrevMap;
    }
}
