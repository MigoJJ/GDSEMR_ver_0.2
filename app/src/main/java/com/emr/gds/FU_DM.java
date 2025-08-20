// src/main/java/com/emr/gds/FU_DM.java
package com.emr.gds;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.UnaryOperator;

public class FU_DM extends Stage {

    private final IttiaApp app;                     // to reuse Formatter etc.
    private final Map<String, String> abbrevMap;    // to reuse your abbreviation expansions
    private final List<TextArea> areas = new ArrayList<>(10);
    private TextArea lastFocusedArea = null;

    public FU_DM(IttiaApp app, Map<String, String> abbrevMap) {
        this.app = app;
        this.abbrevMap = abbrevMap;

        setTitle("DM Follow-up (FU_DM)");
        initModality(Modality.NONE);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // Top bar (title + actions)
        root.setTop(buildTopBar());

        // Center (10 text areas, 5x2)
        root.setCenter(buildCenterAreas());

        // Bottom (Clear, CE, Save, Quit)
        root.setBottom(buildBottomBar());

        Scene scene = new Scene(root, 1200, 760);
        setScene(scene);

        // focus first area when shown
        setOnShown(e -> Platform.runLater(() -> {
            if (!areas.isEmpty()) {
                areas.get(0).requestFocus();
                lastFocusedArea = areas.get(0);
            }
        }));

        // keyboard shortcuts (optional quality-of-life)
        installShortcuts(scene);
    }

    private HBox buildTopBar() {
        Label title = new Label("DM Follow-up – Diabetes Clinic Note");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");

        HBox box = new HBox(10, title);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(0, 0, 10, 0));
        return box;
    }

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

            // reuse titles from main app
            String title = (i < IttiaApp.TEXT_AREA_TITLES.length)
                    ? IttiaApp.TEXT_AREA_TITLES[i]
                    : "Area " + (i + 1);
            ta.setPromptText(title);

            // track focus
            ta.focusedProperty().addListener((obs, o, n) -> {
                if (n) lastFocusedArea = ta;
            });

            // live abbreviation expansion on SPACE:  ":cd" -> ISO date, ":xx" -> from abbrevMap
            ta.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.SPACE) {
                    int caret = ta.getCaretPosition();
                    String textUpToCaret = ta.getText(0, caret);
                    int start = Math.max(textUpToCaret.lastIndexOf(' '), textUpToCaret.lastIndexOf('\n')) + 1;
                    String word = textUpToCaret.substring(start);

                    if (word.startsWith(":")) {
                        String key = word.substring(1);
                        String replacement = "cd".equals(key)
                                ? LocalDate.now().format(DateTimeFormatter.ISO_DATE)
                                : abbrevMap.get(key);
                        if (replacement != null) {
                            ta.deleteText(start, caret);
                            ta.insertText(start, replacement + " ");
                            event.consume();
                        }
                    }
                }
            });

            // filter control chars like in IttiaApp
            ta.setTextFormatter(new TextFormatter<>(filterControlChars()));

            grid.add(ta, i % cols, i / cols);
            areas.add(ta);
        }

        return grid;
    }

    private HBox buildBottomBar() {
        Button btnClearAll = new Button("Clear");
        btnClearAll.setOnAction(e -> clearAll());

        Button btnCE = new Button("CE"); // Clear Entry
        btnCE.setOnAction(e -> clearEntry());

        Button btnSave = new Button("Save");
        btnSave.setOnAction(e -> saveToFile());

        Button btnAppend = new Button("Append to IttiaApp");
        btnAppend.setOnAction(e -> appendToIttiaApp());

        Button btnQuit = new Button("Quit");
        btnQuit.setOnAction(e -> close());

        HBox box = new HBox(10, btnClearAll, btnCE, btnSave, btnAppend, btnQuit);
        box.setAlignment(Pos.CENTER_RIGHT);
        box.setPadding(new Insets(10, 0, 0, 0));
        return box;
    }


    private void installShortcuts(Scene scene) {
        // Ctrl+S -> Save
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN),
                this::saveToFile
        );
        // Ctrl+E -> CE (clear entry)
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.E, KeyCombination.CONTROL_DOWN),
                this::clearEntry
        );
        // Ctrl+L -> Clear all
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN),
                this::clearAll
        );
        // Ctrl+W -> Quit
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN),
                this::close
        );

        // Ctrl+1..9, Ctrl+0 to jump areas
        for (int i = 1; i <= 9; i++) {
            final int idx = i - 1;
            scene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.getKeyCode(String.valueOf(i)), KeyCombination.CONTROL_DOWN),
                    () -> focusArea(idx)
            );
        }
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.CONTROL_DOWN),
                () -> focusArea(9)
        );
    }

    private void focusArea(int idx) {
        if (idx >= 0 && idx < areas.size()) {
            areas.get(idx).requestFocus();
            lastFocusedArea = areas.get(idx);
        }
    }

    private void clearAll() {
        areas.forEach(TextArea::clear);
        toast("All cleared.");
    }

    private void clearEntry() {
        TextArea ta = getFocusedArea();
        if (ta != null) {
            ta.clear();
            toast("Entry cleared.");
        } else {
            toast("No focused area.");
        }
    }

    private void saveToFile() {
        // Build a single EMR-formatted block from the 10 areas
        StringJoiner sj = new StringJoiner("\n\n");
        for (int i = 0; i < areas.size(); i++) {
            String text = uniqueLines(areas.get(i).getText());
            if (!text.isBlank()) {
                String title = (i < IttiaApp.TEXT_AREA_TITLES.length)
                        ? IttiaApp.TEXT_AREA_TITLES[i].replaceAll(">$", "")
                        : "Area " + (i + 1);
                sj.add("# " + title + "\n" + text);
            }
        }

        String content = IttiaApp.Formatter.finalizeForEMR(sj.toString());
        if (content.isBlank()) {
            toast("Nothing to save.");
            return;
        }

        FileChooser fc = new FileChooser();
        fc.setTitle("Save DM Follow-up Note");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files (*.txt)", "*.txt"));
        fc.setInitialFileName("DM_FU_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".txt");
        File file = fc.showSaveDialog(this);
        if (file == null) return;

        try (FileWriter fw = new FileWriter(file)) {
            fw.write(content);
            toast("Saved: " + file.getName());
        } catch (IOException ex) {
            showError("Failed to save file:\n" + ex.getMessage());
        }
    }

    private TextArea getFocusedArea() {
        for (TextArea ta : areas) {
            if (ta.isFocused()) return ta;
        }
        return lastFocusedArea;
    }

    private static UnaryOperator<TextFormatter.Change> filterControlChars() {
        return change -> {
            if (change.getText() == null || change.getText().isEmpty()) return change;
            String filtered = change.getText().replaceAll("[\\u0000-\\u001F\\u007F]", "\n");
            change.setText(filtered);
            return change;
        };
    }

    private static String uniqueLines(String txt) {
        if (txt == null || txt.isBlank()) return "";
        Set<String> uniq = new LinkedHashSet<>();
        txt.lines().map(String::trim).filter(s -> !s.isEmpty()).forEach(uniq::add);
        return String.join("\n", uniq);
    }

    private void toast(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.setTitle("Info");
        a.showAndWait();
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setHeaderText("Error");
        a.setTitle("Error");
        a.showAndWait();
    }
    
    
    private void appendToIttiaApp() {
        StringJoiner sj = new StringJoiner("\n\n");
        for (int i = 0; i < areas.size(); i++) {
            String text = uniqueLines(areas.get(i).getText());
            if (!text.isBlank()) {
                String title = (i < IttiaApp.TEXT_AREA_TITLES.length)
                        ? IttiaApp.TEXT_AREA_TITLES[i].replaceAll(">$", "")
                        : "Area " + (i + 1);
                sj.add("# " + title + "\n" + text);
            }
        }
        String block = IttiaApp.Formatter.finalizeForEMR(sj.toString());
        if (!block.isBlank()) {
            app.insertBlockIntoFocusedArea(block + "\n\n");
            toast("Appended FU_DM note into main IttiaApp.");
        } else {
            toast("Nothing to append.");
        }
    }

}
