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

    private final IttiaApp app;
    private final Map<String, String> abbrevMap;
    private final List<TextArea> areas = new ArrayList<>(10);
    private TextArea lastFocusedArea = null;

    public FU_DM(IttiaApp app, Map<String, String> abbrevMap) {
        this.app = app;
        this.abbrevMap = abbrevMap;

        setTitle("DM Follow-up (FU_DM)");
        initModality(Modality.NONE);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        root.setTop(buildTopBar());
        root.setCenter(buildCenterAreas());
        root.setBottom(buildBottomBar());

        Scene scene = new Scene(root, 1200, 760);
        setScene(scene);

        setOnShown(e -> Platform.runLater(() -> {
            if (!areas.isEmpty()) {
                areas.get(0).requestFocus();
                lastFocusedArea = areas.get(0);
            }
        }));

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

            String title = (i < IttiaApp.TEXT_AREA_TITLES.length)
                    ? IttiaApp.TEXT_AREA_TITLES[i]
                    : "Area " + (i + 1);
            ta.setPromptText(title);

            ta.focusedProperty().addListener((obs, o, n) -> {
                if (n) lastFocusedArea = ta;
            });

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

            ta.setTextFormatter(new TextFormatter<>(filterControlChars()));

            grid.add(ta, i % cols, i / cols);
            areas.add(ta);
        }
        return grid;
    }

    private HBox buildBottomBar() {
        Button btnClearAll = new Button("Clear");
        btnClearAll.setOnAction(e -> clearAll());

        Button btnCE = new Button("CE");
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
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN),
                this::saveToFile
        );
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.E, KeyCombination.CONTROL_DOWN),
                this::clearEntry
        );
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN),
                this::clearAll
        );
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN),
                this::close
        );

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
    
    // 이 메서드를 보완
    private void appendToIttiaApp() {
        // app.getTextAreas() 메서드가 존재한다고 가정하고 IttiaApp의 TextArea 목록을 가져옵니다.
        List<TextArea> ittiaAppAreas = app.getTextAreas();
        
        if (ittiaAppAreas.size() < areas.size()) {
            showError("The main application does not have enough text areas to append to.");
            return;
        }
        
        boolean appended = false;
        for (int i = 0; i < areas.size(); i++) {
            // FU_DM의 i번째 TextArea에서 고유한 라인들을 가져옵니다.
            String textToAppend = uniqueLines(areas.get(i).getText());
            
            // 텍스트가 비어있지 않고, IttiaApp에 해당하는 TextArea가 있다면
            if (!textToAppend.isBlank() && i < ittiaAppAreas.size()) {
                TextArea targetArea = ittiaAppAreas.get(i);
                
                // 새로운 줄바꿈으로 기존 텍스트에 추가합니다.
                String existingText = targetArea.getText();
                String newText = existingText.isEmpty() ? textToAppend : existingText + "\n\n" + textToAppend;
                
                targetArea.setText(newText);
                appended = true;
            }
        }
        
        if (appended) {
            toast("Appended each FU_DM note into its corresponding area in the main IttiaApp.");
        } else {
            toast("Nothing to append.");
        }
    }
}