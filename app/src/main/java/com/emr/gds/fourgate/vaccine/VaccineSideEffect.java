// VaccineSideEffect.java (JavaFX 전용 유틸)
package com.emr.gds.fourgate.vaccine;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.emr.gds.input.IAIMain; // 최신 브리지 사용 시 IttiaAppMain으로 교체

public final class VaccineSideEffect {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static Stage stage; // 싱글톤 윈도우

    private VaccineSideEffect() {}

    /** 이미 열려 있으면 앞으로 가져오고, 아니면 새 Stage를 생성합니다. */
    public static void open() {
        if (stage != null) {
            stage.toFront();
            stage.requestFocus();
            return;
        }

        stage = new Stage();
        stage.setTitle("Vaccine – Side Effects");

        // --- UI 구성 ---
        DatePicker datePicker = new DatePicker(LocalDate.now());
        datePicker.setPrefWidth(180);

        TextField vaccineField = new TextField();
        vaccineField.setPromptText("Vaccine name (e.g., Shingrix #2/2)");

        ComboBox<String> severity = new ComboBox<>();
        severity.getItems().addAll("Mild", "Moderate", "Severe");
        severity.setValue("Mild");
        severity.setPrefWidth(180);

        CheckBox fever       = new CheckBox("Fever/chills");
        CheckBox myalgia     = new CheckBox("Myalgia/arthralgia");
        CheckBox headache    = new CheckBox("Headache");
        CheckBox fatigue     = new CheckBox("Fatigue");
        CheckBox localPain   = new CheckBox("Local pain/redness/swelling");
        CheckBox rash        = new CheckBox("Rash/urticaria");
        CheckBox syncope     = new CheckBox("Syncope");
        CheckBox anaphylaxis = new CheckBox("Anaphylaxis (emergency)");

        TextArea note = new TextArea();
        note.setPromptText("Additional notes...");
        note.setPrefRowCount(4);

        Button insertBtn = new Button("Insert to EMR");
        Button closeBtn  = new Button("Close");

        // 폼
        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.add(new Label("Date"),     0, 0); form.add(datePicker, 1, 0);
        form.add(new Label("Vaccine"),  0, 1); form.add(vaccineField, 1, 1);
        form.add(new Label("Severity"), 0, 2); form.add(severity,    1, 2);

        // 체크박스 영역
        VBox effects = new VBox(8, fever, myalgia, headache, fatigue, localPain, rash, syncope, anaphylaxis);
        effects.setPadding(new Insets(10));
        effects.setStyle("-fx-background-color: rgba(255,250,225,0.6); -fx-background-radius: 8;");

        // ✅ 콤보박스 선택 → vaccineField 에 append
        VaccineSelector selector = new VaccineSelector().bindAppend(vaccineField);

        // 버튼 영역
        HBox buttons = new HBox(10, insertBtn, closeBtn);

        VBox root = new VBox(12,
            titled("Select Vaccine", selector),   // ← 콤보 UI
            titled("Details", form),
            titled("Side effects", effects),
            titled("Notes", note),
            buttons
        );
        root.setPadding(new Insets(12));

        // --- 이벤트 ---
        insertBtn.setDefaultButton(true);
        closeBtn.setCancelButton(true);

        insertBtn.setOnAction(e -> {
            LocalDate ld = (datePicker.getValue() != null) ? datePicker.getValue() : LocalDate.now();
            String d = ld.format(DATE_FMT);
            String v = vaccineField.getText().isBlank() ? "<unspecified vaccine>" : vaccineField.getText().trim();

            StringBuilder sb = new StringBuilder(256);
            sb.append("\n# Post-vaccination side effects [").append(d).append("]\n");
            sb.append("- Vaccine: ").append(v).append("\n");
            sb.append("- Severity: ").append(severity.getValue()).append("\n");
            sb.append("- Findings: ");

            boolean any = false;
            for (CheckBox cb : new CheckBox[]{fever, myalgia, headache, fatigue, localPain, rash, syncope, anaphylaxis}) {
                if (cb.isSelected()) {
                    if (any) sb.append(", ");
                    sb.append(cb.getText());
                    any = true;
                }
            }
            if (!any) sb.append("no significant adverse events reported");

            if (!note.getText().isBlank()) {
                sb.append("\n- Note: ").append(note.getText().trim());
            }

            // EMR 반영 (예: S> 섹션)
            try {
                IAIMain.getTextAreaManager().focusArea(4); // S>
                IAIMain.getTextAreaManager().insertBlockIntoFocusedArea(sb.toString());
                // 필요 시 A>/P> 추가 삽입:
                // IAIMain.getTextAreaManager().focusArea(8);
                // IAIMain.getTextAreaManager().insertLineIntoFocusedArea("Plan: observe & PRN analgesics");
                stage.close();
                stage = null;
            } catch (Exception ex) {
                showAlert("Failed to insert to EMR:\n" + ex.getMessage());
            }
        });

        closeBtn.setOnAction(e -> {
            stage.close();
            stage = null;
        });

        stage.setOnCloseRequest(e -> stage = null);
        stage.setScene(new Scene(root, 560, 520));
        stage.show();
    }

    private static TitledPane titled(String title, javafx.scene.Node content) {
        TitledPane tp = new TitledPane(title, content);
        tp.setExpanded(true);
        return tp;
    }

    private static void showAlert(String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
            a.initOwner(stage);
            a.setHeaderText(null);
            a.setTitle("Info");
            a.showAndWait();
        });
    }
}
