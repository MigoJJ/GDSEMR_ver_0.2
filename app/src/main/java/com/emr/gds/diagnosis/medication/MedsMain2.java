package com.emr.gds.diagnosis.medication;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/**
 * MedsMain (JavaFX 25, single file)
 * - Package: com.emr.gds.diagnosis.medication
 * - Creates ~/.emr/meds.db (SQLite) if absent, seeds with EMR medication data
 * - UI: Left = Selected items + Copy/Clear; Center = Category Tabs -> Subcategory Accordion -> ListView
 */
public class MedsMain2 extends Application {

    private TextArea outputTextArea;
    private DatabaseManager dbManager;


    @Override
    public void init() {
        dbManager = new DatabaseManager();
        dbManager.createTables();
        dbManager.ensureSeedData();
    }

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();
        root.setRight(createWestPanel());
        root.setCenter(createCenterTabPane());

        Scene scene = new Scene(root, 1100, 700);
        primaryStage.setTitle("EMR Medication Helper");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private VBox createWestPanel() {
        VBox westPanel = new VBox(10);
        westPanel.setPadding(new Insets(10));
        westPanel.setPrefWidth(320);

        Label title = new Label("Selected Items:");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        outputTextArea = new TextArea();
        outputTextArea.setPromptText("Clicked medications will appear here...");
        outputTextArea.setWrapText(true);
        VBox.setVgrow(outputTextArea, Priority.ALWAYS);

        Button copyAllButton = new Button("Copy All to Clipboard");
        copyAllButton.setMaxWidth(Double.MAX_VALUE);
        copyAllButton.setOnAction(e -> {
            String allText = outputTextArea.getText();
            if (allText != null && !allText.isEmpty()) {
                Clipboard cb = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();
                content.putString(allText);
                cb.setContent(content);
            }
        });

        Button clearButton = new Button("Clear Selections");
        clearButton.setMaxWidth(Double.MAX_VALUE);
        clearButton.setOnAction(e -> outputTextArea.clear());

        westPanel.getChildren().addAll(title, outputTextArea, copyAllButton, clearButton);
        return westPanel;
    }

    private TabPane createCenterTabPane() {
        TabPane mainTabPane = new TabPane();
        mainTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Map<String, List<MedicationGroup>> data = dbManager.getMedicationData();
        List<String> categories = dbManager.getOrderedCategories();

        for (String category : categories) {
            Accordion accordion = new Accordion();
            for (MedicationGroup group : data.getOrDefault(category, Collections.emptyList())) {
                ListView<String> medListView = new ListView<>();
                medListView.getItems().addAll(group.medications());

                medListView.setOnMouseClicked(event -> {
                    String selectedMed = medListView.getSelectionModel().getSelectedItem();
                    if (selectedMed != null && !isSeparator(selectedMed)) {
                        Clipboard cb = Clipboard.getSystemClipboard();
                        ClipboardContent content = new ClipboardContent();
                        content.putString(selectedMed);
                        cb.setContent(content);
                        outputTextArea.appendText(selectedMed + "\n");
                    }
                });

                TitledPane pane = new TitledPane(group.title(), medListView);
                accordion.getPanes().add(pane);
            }
            mainTabPane.getTabs().add(new Tab(category, accordion));
        }
        return mainTabPane;
    }

    private boolean isSeparator(String text) {
        return text.trim().matches("^-{3,}$");
    }

    public static void openInNewWindow() {
        Runnable show = () -> {
            try {
                MedsMain2 app = new MedsMain2();
                // init()은 Application.launch 때만 자동 호출되므로 수동 초기화
                app.dbManager = new DatabaseManager();
                app.dbManager.createTables();
                app.dbManager.ensureSeedData();

                Stage stage = new Stage();
                BorderPane root = new BorderPane();
                root.setRight(app.createWestPanel());
                root.setCenter(app.createCenterTabPane());

                stage.setTitle("EMR Medication Helper");
                stage.setScene(new Scene(root, 1100, 700));
                stage.show();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        };
        if (Platform.isFxApplicationThread()) show.run();
        else Platform.runLater(show);
    }

    // ----------------- Data types & DB manager (embedded) -----------------

    public static record MedicationGroup(String title, List<String> medications) {}

    public static class DatabaseManager {
        private static final String DB_DIR = System.getProperty("user.home") + "/.emr";
        private static final String DB_PATH = DB_DIR + "/meds.db";
        private static final String JDBC_URL = "jdbc:sqlite:" + DB_PATH;

        public DatabaseManager() {
            try { Files.createDirectories(Path.of(DB_DIR)); } catch (Exception ignored) {}
        }

        private Connection conn() throws SQLException {
            return DriverManager.getConnection(JDBC_URL);
        }

        public void createTables() {
            final String createCategories =
                "CREATE TABLE IF NOT EXISTS Categories (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL UNIQUE," +
                "sort_order INTEGER);";
            final String createSubCategories =
                "CREATE TABLE IF NOT EXISTS SubCategories (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "category_id INTEGER NOT NULL," +
                "name TEXT NOT NULL," +
                "sort_order INTEGER," +
                "FOREIGN KEY (category_id) REFERENCES Categories(id));";
            final String createMedications =
                "CREATE TABLE IF NOT EXISTS Medications (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "subcategory_id INTEGER NOT NULL," +
                "med_text TEXT NOT NULL," +
                "sort_order INTEGER," +
                "FOREIGN KEY (subcategory_id) REFERENCES SubCategories(id));";
            try (Connection c = conn(); Statement st = c.createStatement()) {
                st.execute(createCategories);
                st.execute(createSubCategories);
                st.execute(createMedications);
            } catch (SQLException e) {
                throw new RuntimeException("DB createTables failed", e);
            }
        }

        public boolean hasAnyData() {
            try (Connection c = conn();
                 Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM Medications")) {
                return rs.next() && rs.getInt(1) > 0;
            } catch (SQLException e) {
                return false;
            }
        }

        public void ensureSeedData() {
            if (hasAnyData()) return;
            executeSqlScript(SEED_SQL);
        }

        private void executeSqlScript(String script) {
            try (Connection c = conn()) {
                c.setAutoCommit(false);
                try (Statement st = c.createStatement();
                     BufferedReader br = new BufferedReader(new StringReader(script))) {

                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("--")) continue;
                        sb.append(line).append('\n');
                        if (trimmed.endsWith(";")) {
                            String stmt = sb.toString().trim();
                            sb.setLength(0);
                            if (stmt.endsWith(";")) stmt = stmt.substring(0, stmt.length() - 1);
                            st.execute(stmt);
                        }
                    }
                    c.commit();
                } catch (SQLException ex) {
                    c.rollback();
                    throw ex;
                } finally {
                    c.setAutoCommit(true);
                }
            } catch (Exception e) {
                throw new RuntimeException("Seeding DB failed", e);
            }
        }

        public List<String> getOrderedCategories() {
            String sql = "SELECT name FROM Categories ORDER BY sort_order, id";
            List<String> out = new ArrayList<>();
            try (Connection c = conn();
                 PreparedStatement ps = c.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            } catch (SQLException e) { throw new RuntimeException(e); }
            return out;
        }

        /** Map<Category, List<MedicationGroup>> */
        public Map<String, List<MedicationGroup>> getMedicationData() {
            String q = """
                SELECT c.name AS category, sc.id AS sub_id, sc.name AS sub_name
                FROM SubCategories sc
                JOIN Categories c ON c.id = sc.category_id
                ORDER BY c.sort_order, c.id, sc.sort_order, sc.id
                """;
            Map<String, List<MedicationGroup>> result = new LinkedHashMap<>();
            try (Connection c = conn();
                 PreparedStatement ps = c.prepareStatement(q);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String category = rs.getString("category");
                    int subId = rs.getInt("sub_id");
                    String subName = rs.getString("sub_name");
                    List<String> meds = loadMedsForSub(subId);
                    result.computeIfAbsent(category, k -> new ArrayList<>())
                          .add(new MedicationGroup(subName, meds));
                }
            } catch (SQLException e) { throw new RuntimeException(e); }
            return result;
        }

        private List<String> loadMedsForSub(int subId) {
            String sql = """
                SELECT med_text FROM Medications
                WHERE subcategory_id = ?
                ORDER BY sort_order, id
                """;
            List<String> items = new ArrayList<>();
            try (Connection c = conn();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, subId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) items.add(rs.getString(1));
                }
            } catch (SQLException e) { throw new RuntimeException(e); }
            return items;
        }

        // Embedded seed SQL (typo fixed for SubCategory 7)
        private static final String SEED_SQL = """
            PRAGMA foreign_keys = ON;

            CREATE TABLE IF NOT EXISTS Categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                sort_order INTEGER
            );
            CREATE TABLE IF NOT EXISTS SubCategories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                category_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                sort_order INTEGER,
                FOREIGN KEY (category_id) REFERENCES Categories (id)
            );
            CREATE TABLE IF NOT EXISTS Medications (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                subcategory_id INTEGER NOT NULL,
                med_text TEXT NOT NULL,
                sort_order INTEGER,
                FOREIGN KEY (subcategory_id) REFERENCES SubCategories (id)
            );

            INSERT INTO Categories (name, sort_order) VALUES
            ('Diabetes Mellitus', 1),
            ('Hypertension', 2),
            ('Lipids (Statins)', 3),
            ('Follow-up', 4);

            INSERT INTO SubCategories (category_id, name, sort_order) VALUES
            (1, 'SGLT2-i / TZD', 1),
            (1, 'Sulfonylurea', 2),
            (1, 'DPP4-i', 3),
            (1, 'Insulin', 4),
            (1, 'Metformin / Gliclazide', 5),
            (2, 'ARB / CCB', 1),
            (2, 'ARB/CCB Combo', 2),
            (3, 'Pitavastatin', 1),
            (3, 'Statin/Ezetimibe Combo', 2),
            (3, 'Rosuvastatin', 3),
            (3, 'Atorvastatin', 4),
            (4, 'Plans & Actions', 1);

            INSERT INTO Medications (subcategory_id, med_text, sort_order) VALUES
            (1, 'Jadian [ 10 ] mg 1 tab p.o. q.d.', 1),
            (1, 'Jadian [ 25 ] mg 1 tab p.o. q.d.', 2),
            (1, '...', 3),
            (1, 'Exiglu [ 10 ] mg 1 tab p.o. q.d.', 4),
            (1, 'Exiglu-M SR [ 10/500 ] mg 1 tab p.o. q.d.', 5),
            (1, 'Exiglu-M SR [ 10/1000 ] mg 1 tab p.o. q.d.', 6),
            (1, '...', 7),
            (1, 'Actos [ 15 ] mg 1 tab p.o. q.d.', 8),
            (1, 'Atos [ 30 ] mg 1 tab p.o. q.d.', 9);

            INSERT INTO Medications (subcategory_id, med_text, sort_order) VALUES
            (2, 'Amaryl [ 1 ]  mg  0.5 tab p.o. q.d.', 1),
            (2, 'Amaryl [ 1 ]  mg  1 tab p.o. q.d.', 2),
            (2, 'Amaryl [ 1 ]  mg  1 tab p.o. b.i.d.', 3),
            (2, 'Amaryl [ 2 ]  mg  1 tab p.o. q.d.', 4),
            (2, 'Amaryl [ 2 ]  mg  1 tab p.o. b.i.d.', 5),
            (2, '...', 6),
            (2, 'Amaryl-M [ 1/500 ]  mg  1 tab p.o. q.d.', 7),
            (2, 'Amaryl-M [ 1/500 ]  mg  1 tab p.o. b.i.d.', 8),
            (2, 'Amaryl-M [ 2/500 ]  mg  1 tab p.o. q.d.', 9),
            (2, 'Amaryl-M [ 2/500 ]  mg  1 tab p.o. b.i.d.', 10);

            INSERT INTO Medications (subcategory_id, med_text, sort_order) VALUES
            (3, 'Januvia [ 50 ] mg 1 tab p.o. q.d.', 1),
            (3, 'Januvia [ 100 ] mg 1 tab p.o. q.d.', 2),
            (3, 'Janumet [ 50/500 ] mg 1 tab p.o. q.d.', 3),
            (3, 'Janumet [ 50/500 ] mg 1 tab p.o. b.i.d.', 4);

            INSERT INTO Medications (subcategory_id, med_text, sort_order) VALUES
            (4, 'Lantus Solosta  [     ] IU SC AM', 1),
            (4, 'Ryzodeg FlexTouch [     ] IU SC AM', 2),
            (4, 'Tresiba FlexTouch  [     ] IU SC AM', 3),
            (4, 'Levemir FlexPen [     ] IU SC AM', 4),
            (4, 'Tuojeo Solostar  [     ] IU SC AM', 5),
            (4, '---Rapid acting---', 6),
            (4, 'NovoRapid FlexPen 100u/mL [     ] IU SC', 7),
            (4, 'NOVOMIX 30 Flexpen 100U/mL  [     ] IU SC', 8),
            (4, 'Apidra Inj. SoloStar [     ] IU SC ', 9),
            (4, 'Fiasp Flex Touch  [     ] IU SC', 10),
            (4, 'Humalog Mix 25 Quick Pen  [     ] IU SC', 11),
            (4, 'Humalog Mix 50 Quick Pen  [     ] IU SC', 12),
            (4, '---Mixed---', 13),
            (4, 'Soliqua Pen (10-40) [     ] IU SC ', 14);

            INSERT INTO Medications (subcategory_id, med_text, sort_order) VALUES
            (5, 'Diabex [ 250 ] mg 1 tab p.o. q.d.', 1),
            (5, 'Diabex [ 500 ] mg 1 tab p.o. q.d.', 2),
            (5, 'Diabex [ 250 ] mg 1 tab p.o. b.i.d.', 3),
            (5, 'Diabex [ 500 ] mg 1 tab p.o. b.i.d.', 4),
            (5, '------', 5),
            (5, 'Diamicron [ 30 ] mg 1 tab p.o. q.d.', 6),
            (5, 'Diamicron [ 30 ] mg 1 tab p.o. b.i.d.', 7),
            (5, 'Diamicron [ 60 ] mg 1 tab p.o. q.d.', 8);

            INSERT INTO Medications (subcategory_id, med_text, sort_order) VALUES
            (6, 'Atacand [ 8 ] mg 1 tab p.o. q.d.', 1),
            (6, 'Atacand [ 16 ] mg 1 tab p.o. q.d.', 2),
            (6, 'Atacand-plus [ 16/12.5 ] mg 1 tab p.o. q.d.', 3),
            (6, '...', 4),
            (6, 'Noevasc [ 2.5 ] mg 1 tab p.o. q.d.', 5),
            (6, 'Norvasc [ 5 ]    mg 1 tab p.o. q.d.', 6),
            (6, 'Norvasc [ 10 ]  mg 1 tab p.o. q.d.', 7),
            (6, '...', 8);

            INSERT INTO Medications (subcategory_id, med_text, sort_order) VALUES
            (7, 'Sevikar [ 5/20 ] mg 1 tab p.o. q.d.', 1),
            (7, 'Sevikar [ 5/40 ] mg 1 tab p.o. q.d.', 2),
            (7, 'Sevikar [ 10/40 ] mg 1 tab p.o. q.d.', 3),
            (7, 'Sevikar HCT [ 5/20/12.5 ] mg 1 tab p.o. q.d.', 4),
            (7, 'Sevikar HCT [ 5/40/12.5 ] mg 1 tab p.o. q.d.', 5),
            (7, 'Sevikar HCT [ 10/40/12.5 ] mg 1 tab p.o. q.d.', 6),
            (7, '...', 7);

            INSERT INTO Medications (subcategory_id, med_text, sort_order) VALUES
            (8, 'Livalo [ 1 ] mg 1 tab p.o. q.d.', 1),
            (8, 'Livalo [ 2 ] mg 1 tab p.o. q.d.', 2),
            (8, 'Livalo [ 3 ] mg 1 tab p.o. q.d.', 3),
            (8, 'Livalo [ 4 ] mg 1 tab p.o. q.d.', 4);

            INSERT INTO Medications (subcategory_id, med_text, sort_order) VALUES
            (9, 'Vytorin [ 10/10 ]  mg  1 tab p.o. q.d.', 1),
            (9, 'Vytorin [ 10/10 ]  mg  1 tab p.o. q.o.d.', 2),
            (9, 'Vytorin [ 10/20 ]  mg  1 tab p.o. q.d.', 3),
            (9, 'Vytorin [ 10/40 ]  mg  1 tab p.o. q.d.', 4),
            (9, '...', 5);

            INSERT INTO Medications (subcategory_id, med_text, sort_order) VALUES
            (10, 'Crestor [ 5 ] mg 1 tab p.o. q.d.', 1),
            (10, 'Crestor [ 5 ] mg 1 tab p.o. q.o.d.', 2),
            (10, 'Crestor [ 10 ] mg 1 tab p.o. q.d.', 3),
            (10, 'Crestor [ 20 ] mg 1 tab p.o. q.d.', 4);

            INSERT INTO Medications (subcategory_id, med_text, sort_order) VALUES
            (11, 'Lipitor [ 10 ] mg 1 tab p.o. q.d.', 1),
            (11, 'Lipitor [ 10 ] mg 1 tab p.o. q.o.d.', 2),
            (11, 'Lipitor [ 20 ] mg 1 tab p.o. q.d.', 3),
            (11, 'Lipitor [ 40 ] mg 1 tab p.o. q.d.', 4),
            (11, 'Lipitor plus [ 10/10 ] mg 1 tab p.o. q.d.', 5);

            INSERT INTO Medications (subcategory_id, med_text, sort_order) VALUES
            (12, '...Plan to FBS, HbA1c \\n', 1),
            (12, '...Plan to FBS, HbA1c, +A/C \\n', 2),
            (12, '...Obtain CUS : [ Carotid artery Ultrasonography ]\\n', 3),
            (12, '[ → ] advised the patient to continue with current medication\\n', 4),
            (12, '[ ↘ ] decreased the dose of current medication\\n', 5),
            (12, '[ ↗ ] increased the dose of current medication\\n', 6),
            (12, '[ ⭯ ] changed the dose of current medication\\n', 7),
            (12, ' |→   Starting new medication\\n', 8),
            (12, '  →|  discontinue current medication\\n', 9);
            """;
    }
}
