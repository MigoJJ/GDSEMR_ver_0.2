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
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * EMR Medication Helper – fully working Add / Edit / Delete / Save / Find
 */
public class MedsMain extends Application {

    private TextArea outputTextArea;
    private DatabaseManager dbManager;

    private Button btnEdit, btnDelete, btnSave;

        /* ------------------------------------------------------------------ */
    
    
    @Override
    public void init() {
        dbManager = new DatabaseManager();
        dbManager.createTables();
        dbManager.ensureSeedData();
    }

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();
        root.setLeft(createWestPanel());
        root.setCenter(createCenterPane());

        Scene scene = new Scene(root, 1200, 720);
        primaryStage.setTitle("EMR Medication Helper");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    /* -------------------------- LEFT PANEL --------------------------- */
    public VBox createWestPanel() {
        VBox west = new VBox(10);
        west.setPadding(new Insets(10));
        west.setPrefWidth(340);

        outputTextArea = new TextArea();
        outputTextArea.setPromptText("Clicked medications appear here…");
        outputTextArea.setWrapText(true);
        VBox.setVgrow(outputTextArea, Priority.ALWAYS);

        Button copyAll = new Button("Copy All");
        copyAll.setMaxWidth(Double.MAX_VALUE);
        copyAll.setOnAction(e -> {
            String txt = outputTextArea.getText();
            if (!txt.isBlank()) {
                ClipboardContent cc = new ClipboardContent();
                cc.putString(txt);
                Clipboard.getSystemClipboard().setContent(cc);
            }
        });

        Button clear = new Button("Clear");
        clear.setMaxWidth(Double.MAX_VALUE);
        clear.setOnAction(e -> outputTextArea.clear());

        west.getChildren().addAll(
                new Label("Selected Items:"), outputTextArea, copyAll, clear);
        return west;
    }

    /* -------------------------- CENTER ----------------------------- */
    public VBox createCenterPane() {
        VBox center = new VBox();
        center.getChildren().addAll(createToolbar(), createTabPane());
        VBox.setVgrow(center.getChildren().get(1), Priority.ALWAYS);
        return center;
    }

    private ToolBar createToolbar() {
        Button btnFind = new Button("Find");
        btnEdit   = new Button("Edit");
        Button btnAdd   = new Button("Add");
        btnDelete = new Button("Delete");
        btnSave   = new Button("Save");
        Button btnQuit  = new Button("Quit");

        btnEdit.setDisable(true);
        btnDelete.setDisable(true);
        btnSave.setDisable(true);

        btnFind.setOnAction(e -> doFind());
        btnEdit.setOnAction(e -> doEdit());
        btnAdd.setOnAction(e -> doAdd());
        btnDelete.setOnAction(e -> doDelete());
        btnSave.setOnAction(e -> {
            dbManager.commitPending();
            btnSave.setDisable(true);
            showInfo("Saved.");
        });
        btnQuit.setOnAction(e -> Platform.exit());

        return new ToolBar(
                btnFind, new Separator(),
                btnEdit, btnAdd, btnDelete,
                new Separator(), btnSave,
                new Separator(), btnQuit);
    }

    private TabPane createTabPane() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        for (String cat : dbManager.getOrderedCategories()) {
            Accordion acc = new Accordion();
            for (MedicationGroup g : dbManager.getMedicationData().getOrDefault(cat, List.of())) {
                ListView<MedicationItem> lv = new ListView<>();
                lv.getItems().addAll(g.medications());

                lv.setOnMouseClicked(ev -> {
                    MedicationItem sel = lv.getSelectionModel().getSelectedItem();
                    if (sel != null && !isSeparator(sel.getText())) {
                        ClipboardContent cc = new ClipboardContent();
                        cc.putString(sel.getText());
                        Clipboard.getSystemClipboard().setContent(cc);
                        outputTextArea.appendText(sel.getText() + "\n");
                    }
                });

                lv.focusedProperty().addListener((o, ov, nv) -> updateButtons(lv));
                lv.getSelectionModel().selectedItemProperty()
                        .addListener((o, ov, nv) -> updateButtons(lv));

                acc.getPanes().add(new TitledPane(g.title(), lv));
            }
            tabPane.getTabs().add(new Tab(cat, acc));
        }
        return tabPane;
    }

    private void updateButtons(ListView<MedicationItem> lv) {
        MedicationItem sel = lv.getSelectionModel().getSelectedItem();
        boolean ok = sel != null && !isSeparator(sel.getText());
        boolean focused = lv.isFocused();

        btnEdit.setDisable(!focused || !ok);
        btnDelete.setDisable(!focused || !ok);
        btnSave.setDisable(!dbManager.hasPendingChanges());
    }

    private boolean isSeparator(String txt) {
        return txt.trim().matches("^-{3,}$|^---.*---$");
    }

    /* -------------------------- CURRENT LISTVIEW ------------------- */
    private ListView<MedicationItem> currentListView() {
        Scene s = outputTextArea.getScene();
        if (s == null) return null;

        TabPane tp = (TabPane) s.lookup(".tab-pane");
        if (tp == null) return null;

        Tab tab = tp.getSelectionModel().getSelectedItem();
        if (tab == null) return null;

        Accordion acc = (Accordion) tab.getContent();
        TitledPane pane = acc.getExpandedPane();
        if (pane == null) return null;

        return findListView(pane.getContent());
    }

    private ListView<MedicationItem> findListView(javafx.scene.Node n) {
        if (n instanceof ListView<?> lv && !lv.getItems().isEmpty()
                && lv.getItems().get(0) instanceof MedicationItem) {
            return (ListView<MedicationItem>) lv;
        }
        if (n instanceof javafx.scene.Parent p) {
            for (javafx.scene.Node child : p.getChildrenUnmodifiable()) {
                ListView<MedicationItem> found = findListView(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    /* -------------------------- ACTIONS ---------------------------- */
    private void doFind() {
        ListView<MedicationItem> lv = currentListView();
        if (lv == null) return;

        TextInputDialog d = new TextInputDialog();
        d.setTitle("Find");
        d.setHeaderText("Search (case-insensitive):");
        d.showAndWait().ifPresent(q -> {
            String regex = "(?i).*" + Pattern.quote(q) + ".*";
            lv.getItems().stream()
                    .filter(i -> i.getText().matches(regex))
                    .findFirst()
                    .ifPresent(m -> {
                        lv.getSelectionModel().select(m);
                        lv.scrollTo(m);
                    });
        });
    }

    private void doEdit() {
        ListView<MedicationItem> lv = currentListView();
        if (lv == null) return;

        MedicationItem it = lv.getSelectionModel().getSelectedItem();
        if (it == null || isSeparator(it.getText())) return;

        TextInputDialog d = new TextInputDialog(it.getText());
        d.setTitle("Edit");
        d.setHeaderText("New text:");
        d.showAndWait().ifPresent(nt -> {
            if (!nt.equals(it.getText())) {
                it.setText(nt);
                lv.refresh();
                dbManager.updateMedication(it.getId(), nt);
                btnSave.setDisable(false);
            }
        });
    }

    private void doAdd() {
        ListView<MedicationItem> lv = currentListView();
        if (lv == null) return;

        int subId = getCurrentSubcategoryId(lv);
        if (subId == -1) {
            showError("Cannot determine subcategory.");
            return;
        }

        TextInputDialog d = new TextInputDialog();
        d.setTitle("Add");
        d.setHeaderText("Medication text:");
        d.showAndWait().ifPresent(txt -> {
            if (!txt.isBlank()) {
                int newId = dbManager.insertMedication(subId, txt);
                if (newId > 0) {
                    lv.getItems().add(new MedicationItem(newId, txt));
                    btnSave.setDisable(false);
                } else {
                    showError("Insert failed.");
                }
            }
        });
    }

    private void doDelete() {
        ListView<MedicationItem> lv = currentListView();
        if (lv == null) return;

        MedicationItem it = lv.getSelectionModel().getSelectedItem();
        if (it == null || isSeparator(it.getText())) return;

        Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete \"" + it.getText() + "\" ?", ButtonType.YES, ButtonType.NO);
        a.showAndWait()
                .filter(r -> r == ButtonType.YES)
                .ifPresent(r -> {
                    lv.getItems().remove(it);
                    dbManager.deleteMedication(it.getId());
                    btnSave.setDisable(false);
                });
    }

    /* -------------------------- SUBCATEGORY ID -------------------- */
    private int getCurrentSubcategoryId(ListView<MedicationItem> lv) {
        // Walk up to TitledPane
        javafx.scene.Node n = lv;
        TitledPane tp = null;
        while (n != null) {
            if (n instanceof TitledPane t) { tp = t; break; }
            n = n.getParent();
        }
        if (tp == null) return -1;
        String subName = tp.getText();

        // Tab title = category name
        TabPane tabPane = (TabPane) lv.getScene().lookup(".tab-pane");
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab == null) return -1;
        String catName = tab.getText();

        return dbManager.getSubcategoryId(catName, subName);
    }

    private void showInfo(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait();
    }

    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }

    /* -------------------------- DATA CLASSES ----------------------- */
    public static class MedicationItem {
        private final int id;
        private String text;

        public MedicationItem(int id, String text) { this.id = id; this.text = text; }
        public int getId() { return id; }
        public String getText() { return text; }
        public void setText(String t) { this.text = t; }
        @Override public String toString() { return text; }
    }

    public static record MedicationGroup(String title, List<MedicationItem> medications) {}

    /* -------------------------- DATABASE -------------------------- */
    public static class DatabaseManager {
        private static final String DB_DIR = System.getProperty("user.home") + "/.emr";
        private static final String DB_PATH = DB_DIR + "/meds.db";
        private static final String JDBC_URL = "jdbc:sqlite:" + DB_PATH;

        private final Set<Integer> pendingDeletes = new HashSet<>();
        private final Map<Integer, String> pendingUpdates = new HashMap<>();
        private final List<InsertHolder> pendingInserts = new ArrayList<>();

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
                ('Diabetes Mellitus', 1),('Hypertension', 2),('Lipids (Statins)', 3),('Follow-up', 4);

                INSERT INTO SubCategories (category_id, name, sort_order) VALUES
                (1,'SGLT2-i / TZD',1),(1,'Sulfonylurea',2),(1,'DPP4-i',3),(1,'Insulin',4),(1,'Metformin / Gliclazide',5),
                (2,'ARB / CCB',1),(2,'ARB/CCB Combo',2),
                (3,'Pitavastatin',1),(3,'Statin/Ezetimibe Combo',2),(3,'Rosuvastatin',3),(3,'Atorvastatin',4),
                (4,'Plans & Actions',1);

                INSERT INTO Medications (subcategory_id, med_text, sort_order) VALUES
                (1,'Jadian [ 10 ] mg 1 tab p.o. q.d.',1),
                (1,'Jadian [ 25 ] mg 1 tab p.o. q.d.',2),
                (1,'...',3),
                (1,'Exiglu [ 10 ] mg 1 tab p.o. q.d.',4),
                (1,'Exiglu-M SR [ 10/500 ] mg 1 tab p.o. q.d.',5),
                (1,'Exiglu-M SR [ 10/1000 ] mg 1 tab p.o. q.d.',6),
                (1,'...',7),
                (1,'Actos [ 15 ] mg 1 tab p.o. q.d.',8),
                (1,'Atos [ 30 ] mg 1 tab p.o. q.d.',9),

                (2,'Amaryl [ 1 ] mg 0.5 tab p.o. q.d.',1),
                (2,'Amaryl [ 1 ] mg 1 tab p.o. q.d.',2),
                (2,'Amaryl [ 1 ] mg 1 tab p.o. b.i.d.',3),
                (2,'Amaryl [ 2 ] mg 1 tab p.o. q.d.',4),
                (2,'Amaryl [ 2 ] mg 1 tab p.o. b.i.d.',5),
                (2,'...',6),
                (2,'Amaryl-M [ 1/500 ] mg 1 tab p.o. q.d.',7),
                (2,'Amaryl-M [ 1/500 ] mg 1 tab p.o. b.i.d.',8),
                (2,'Amaryl-M [ 2/500 ] mg 1 tab p.o. q.d.',9),
                (2,'Amaryl-M [ 2/500 ] mg 1 tab p.o. b.i.d.',10),

                (3,'Januvia [ 50 ] mg 1 tab p.o. q.d.',1),
                (3,'Januvia [ 100 ] mg 1 tab p.o. q.d.',2),
                (3,'Janumet [ 50/500 ] mg 1 tab p.o. q.d.',3),
                (3,'Janumet [ 50/500 ] mg 1 tab p.o. b.i.d.',4),

                (4,'Lantus Solosta [ ] IU SC AM',1),
                (4,'Ryzodeg FlexTouch [ ] IU SC AM',2),
                (4,'Tresiba FlexTouch [ ] IU SC AM',3),
                (4,'Levemir FlexPen [ ] IU SC AM',4),
                (4,'Tuojeo Solostar [ ] IU SC AM',5),
                (4,'---Rapid acting---',6),
                (4,'NovoRapid FlexPen 100u/mL [ ] IU SC',7),
                (4,'NOVOMIX 30 Flexpen 100U/mL [ ] IU SC',8),
                (4,'Apidra Inj. SoloStar [ ] IU SC ',9),
                (4,'Fiasp Flex Touch [ ] IU SC',10),
                (4,'Humalog Mix 25 Quick Pen [ ] IU SC',11),
                (4,'Humalog Mix 50 Quick Pen [ ] IU SC',12),
                (4,'---Mixed---',13),
                (4,'Soliqua Pen (10-40) [ ] IU SC ',14),

                (5,'Diabex [ 250 ] mg 1 tab p.o. q.d.',1),
                (5,'Diabex [ 500 ] mg 1 tab p.o. q.d.',2),
                (5,'Diabex [ 250 ] mg 1 tab p.o. b.i.d.',3),
                (5,'Diabex [ 500 ] mg 1 tab p.o. b.i.d.',4),
                (5,'------',5),
                (5,'Diamicron [ 30 ] mg 1 tab p.o. q.d.',6),
                (5,'Diamicron [ 30 ] mg 1 tab p.o. b.i.d.',7),
                (5,'Diamicron [ 60 ] mg 1 tab p.o. q.d.',8),

                (6,'Atacand [ 8 ] mg 1 tab p.o. q.d.',1),
                (6,'Atacand [ 16 ] mg 1 tab p.o. q.d.',2),
                (6,'Atacand-plus [ 16/12.5 ] mg 1 tab p.o. q.d.',3),
                (6,'...',4),
                (6,'Noevasc [ 2.5 ] mg 1 tab p.o. q.d.',5),
                (6,'Norvasc [ 5 ] mg 1 tab p.o. q.d.',6),
                (6,'Norvasc [ 10 ] mg 1 tab p.o. q.d.',7),
                (6,'...',8),

                (7,'Sevikar [ 5/20 ] mg 1 tab p.o. q.d.',1),
                (7,'Sevikar [ 5/40 ] mg 1 tab p.o. q.d.',2),
                (7,'Sevikar [ 10/40 ] mg 1 tab p.o. q.d.',3),
                (7,'Sevikar HCT [ 5/20/12.5 ] mg 1 tab p.o. q.d.',4),
                (7,'Sevikar HCT [ 5/40/12.5 ] mg 1 tab p.o. q.d.',5),
                (7,'Sevikar HCT [ 10/40/12.5 ] mg 1 tab p.o. q.d.',6),
                (7,'...',7),

                (8,'Livalo [ 1 ] mg 1 tab p.o. q.d.',1),
                (8,'Livalo [ 2 ] mg 1 tab p.o. q.d.',2),
                (8,'Livalo [ 3 ] mg 1 tab p.o. q.d.',3),
                (8,'Livalo [ 4 ] mg 1 tab p.o. q.d.',4),

                (9,'Vytorin [ 10/10 ] mg 1 tab p.o. q.d.',1),
                (9,'Vytorin [ 10/10 ] mg 1 tab p.o. q.o.d.',2),
                (9,'Vytorin [ 10/20 ] mg 1 tab p.o. q.d.',3),
                (9,'Vytorin [ 10/40 ] mg 1 tab p.o. q.d.',4),
                (9,'...',5),

                (10,'Crestor [ 5 ] mg 1 tab p.o. q.d.',1),
                (10,'Crestor [ 5 ] mg 1 tab p.o. q.o.d.',2),
                (10,'Crestor [ 10 ] mg 1 tab p.o. q.d.',3),
                (10,'Crestor [ 20 ] mg 1 tab p.o. q.d.',4),

                (11,'Lipitor [ 10 ] mg 1 tab p.o. q.d.',1),
                (11,'Lipitor [ 10 ] mg 1 tab p.o. q.o.d.',2),
                (11,'Lipitor [ 20 ] mg 1 tab p.o. q.d.',3),
                (11,'Lipitor [ 40 ] mg 1 tab p.o. q.d.',4),
                (11,'Lipitor plus [ 10/10 ] mg 1 tab p.o. q.d.',5),

                (12,'...Plan to FBS, HbA1c \\n',1),
                (12,'...Plan to FBS, HbA1c, +A/C \\n',2),
                (12,'...Obtain CUS : [ Carotid artery Ultrasonography ]\\n',3),
                (12,'[ → ] advised the patient to continue with current medication\\n',4),
                (12,'[ ↓ ] decreased the dose of current medication\\n',5),
                (12,'[ ↑ ] increased the dose of current medication\\n',6),
                (12,'[ ↔ ] changed the dose of current medication\\n',7),
                (12,' |→ Starting new medication\\n',8),
                (12,' →| discontinue current medication\\n',9);
                """;

        public DatabaseManager() {
            try { Files.createDirectories(Path.of(DB_DIR)); }
            catch (IOException ignored) {}
        }

        private Connection conn() throws SQLException { return DriverManager.getConnection(JDBC_URL); }

        public void createTables() {
            try (Connection c = conn(); Statement st = c.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON;");
                // run only the CREATE part (everything before the first INSERT)
                String schema = SEED_SQL.split("INSERT INTO")[0];
                for (String stmt : schema.split(";")) {
                    String s = stmt.trim();
                    if (!s.isEmpty()) st.execute(s);
                }
            } catch (SQLException e) { throw new RuntimeException(e); }
        }

        public boolean hasAnyData() {
            try (Connection c = conn();
                 PreparedStatement ps = c.prepareStatement("SELECT 1 FROM Medications LIMIT 1");
                 ResultSet rs = ps.executeQuery()) {
                return rs.next();
            } catch (SQLException e) { return false; }
        }

        public void ensureSeedData() {
            if (hasAnyData()) return;
            executeScript(SEED_SQL);
        }

        private void executeScript(String sql) {
            try (Connection c = conn(); Statement st = c.createStatement();
                 BufferedReader r = new BufferedReader(new StringReader(sql))) {
                c.setAutoCommit(false);
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) {
                    String t = line.trim();
                    if (t.isEmpty() || t.startsWith("--")) continue;
                    sb.append(line).append("\n");
                    if (t.endsWith(";")) {
                        st.execute(sb.substring(0, sb.length() - 1).trim());
                        sb.setLength(0);
                    }
                }
                c.commit();
            } catch (Exception e) { throw new RuntimeException(e); }
        }

        public List<String> getOrderedCategories() {
            List<String> list = new ArrayList<>();
            try (Connection c = conn();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT name FROM Categories ORDER BY sort_order")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(rs.getString(1));
                }
            } catch (SQLException e) { throw new RuntimeException(e); }
            return list;
        }

        public Map<String, List<MedicationGroup>> getMedicationData() {
            Map<String, List<MedicationGroup>> map = new LinkedHashMap<>();
            String sql = """
                SELECT c.name cat, sc.id sub_id, sc.name sub_name
                FROM SubCategories sc JOIN Categories c ON c.id = sc.category_id
                ORDER BY c.sort_order, sc.sort_order
                """;
            try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.computeIfAbsent(rs.getString("cat"), k -> new ArrayList<>())
                       .add(new MedicationGroup(rs.getString("sub_name"),
                               loadMeds(rs.getInt("sub_id"))));
                }
            } catch (SQLException e) { throw new RuntimeException(e); }
            return map;
        }

        private List<MedicationItem> loadMeds(int subId) {
            List<MedicationItem> list = new ArrayList<>();
            try (Connection c = conn();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT id, med_text FROM Medications WHERE subcategory_id = ? ORDER BY sort_order")) {
                ps.setInt(1, subId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new MedicationItem(rs.getInt(1), rs.getString(2)));
                    }
                }
            } catch (SQLException e) { throw new RuntimeException(e); }
            return list;
        }

        public int getSubcategoryId(String cat, String sub) {
            try (Connection c = conn();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT sc.id FROM SubCategories sc JOIN Categories c ON c.id = sc.category_id WHERE c.name=? AND sc.name=?")) {
                ps.setString(1, cat); ps.setString(2, sub);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : -1;
                }
            } catch (SQLException e) { return -1; }
        }

        public void updateMedication(int id, String txt) { pendingUpdates.put(id, txt); }
        public void deleteMedication(int id) { pendingDeletes.add(id); }

        public int insertMedication(int subId, String txt) {
            try (Connection c = conn()) {
                int sort = 1 + maxSort(subId, c);
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO Medications (subcategory_id, med_text, sort_order) VALUES (?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, subId); ps.setString(2, txt); ps.setInt(3, sort);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            int id = rs.getInt(1);
                            pendingInserts.add(new InsertHolder(subId, txt, id));
                            return id;
                        }
                    }
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return -1;
        }

        private int maxSort(int subId, Connection c) throws SQLException {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COALESCE(MAX(sort_order),0) FROM Medications WHERE subcategory_id=?")) {
                ps.setInt(1, subId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        }

        public boolean hasPendingChanges() {
            return !(pendingDeletes.isEmpty() && pendingUpdates.isEmpty() && pendingInserts.isEmpty());
        }

        public void commitPending() {
            if (!hasPendingChanges()) return;
            try (Connection c = conn()) {
                c.setAutoCommit(false);
                try {
                    // deletes
                    try (PreparedStatement ps = c.prepareStatement("DELETE FROM Medications WHERE id=?")) {
                        for (int id : pendingDeletes) { ps.setInt(1, id); ps.addBatch(); }
                        ps.executeBatch();
                    }
                    // updates
                    try (PreparedStatement ps = c.prepareStatement("UPDATE Medications SET med_text=? WHERE id=?")) {
                        for (Map.Entry<Integer, String> e : pendingUpdates.entrySet()) {
                            ps.setString(1, e.getValue()); ps.setInt(2, e.getKey()); ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                    c.commit();
                } catch (SQLException ex) { c.rollback(); throw ex; }
                finally {
                    pendingDeletes.clear(); pendingUpdates.clear(); pendingInserts.clear();
                    c.setAutoCommit(true);
                }
            } catch (SQLException e) { throw new RuntimeException(e); }
        }

        private record InsertHolder(int subId, String text, int id) {}
    }

    /* ------------------------------------------------------------------ */
    public static void main(String[] args) { launch(args); }
}