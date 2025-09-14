// VaccineSelector.java
package com.emr.gds.fourgate.vaccine;

import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

public class VaccineSelector extends VBox {

    private static final String[] UI_ELEMENTS = {
        "### Respiratory Vaccines",
        "Pneumovax 23 (PPSV23, pneumococcal polysaccharide vaccine)",
        "COVID-19 vaccine (mRNA, viral vector, or protein subunit)",
        "RSV vaccine (Arexvy, Abrysvo)",
        "Prevena 13 (pneumococcal vaccine (PCV13))",

        "### Travel / Endemic Disease Vaccines",
        "MMR (Measles, Mumps, Rubella)",
        "Varicella (Chickenpox) vaccine",
        "Japanese Encephalitis vaccine",
        "Yellow Fever vaccine",
        "Typhoid vaccine (oral or injectable)",
        "Meningococcal vaccine (MenACWY)",
        "Meningococcal vaccine (MenB)",
        "Rabies vaccine (pre-exposure prophylaxis)",

        "### Occupational / High-Risk Vaccines",
        "HPV vaccine (Gardasil 9, adults up to age 45)",
        "Anthrax vaccine",
        "Smallpox/Mpox vaccine (JYNNEOS)",

        "### Booster / Additional Doses",
        "HAV vaccine #2/2",
        "HBV vaccine #2/3",
        "HBV vaccine #3/3",
        "Shingles Vaccine (Shingrix) #2/2",
        "TdaP (Tetanus, Diphtheria, Pertussis)",
        "Td booster (Tetanus, Diphtheria)",

        "### Regional / Seasonal Vaccines",
        "Tick-borne Encephalitis vaccine",
        "Cholera vaccine",
        "Polio vaccine (IPV, for travelers to endemic areas)",

        "### Actions",
        "Side Effect",
        "Quit"
    };

    private final ComboBox<String> comboBox;
    private Consumer<String> onSelected; // 선택 콜백

    public VaccineSelector() {
        comboBox = new ComboBox<>(FXCollections.observableArrayList(UI_ELEMENTS));

        // 헤더/액션을 비활성 헤더처럼 렌더링
        comboBox.setCellFactory(cb -> new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setDisable(false); setStyle(""); return; }
                if (isHeaderOrAction(item)) {
                    setText(stripHeader(item));
                    setStyle("-fx-font-weight: bold; -fx-background-color: #e0e0e0;");
                    setDisable(true); // 선택 불가
                } else {
                    setText(item);
                    setStyle("");
                    setDisable(false);
                }
            }
        });

        // 버튼셀
        comboBox.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || isHeaderOrAction(item)) setText(null);
                else setText(item);
            }
        });

        comboBox.setPromptText("Select a vaccine...");

        // 선택 이벤트 → 콜백 호출
        comboBox.setOnAction(e -> {
            String sel = comboBox.getValue();
            if (sel == null || isHeaderOrAction(sel)) return;
            if (onSelected != null) onSelected.accept(sel);
            // (선택 후 초기화하고 싶으면) comboBox.getSelectionModel().clearSelection();
        });

        getChildren().addAll(new Label("Vaccine Selection:"), comboBox);
        setSpacing(10);
    }

    // ===== 공개 API =====

    /** 선택 시 호출될 콜백 등록 */
    public VaccineSelector onSelected(Consumer<String> handler) {
        this.onSelected = handler;
        return this;
    }

    /** 선택값을 주어진 TextField의 텍스트로 교체 */
    public VaccineSelector bindTo(TextField field) {
        return onSelected(sel -> field.setText(sel));
    }

    /** 선택값을 주어진 TextField 뒤에 ", "로 구분하여 추가(append) */
    public VaccineSelector bindAppend(TextField field) {
        return onSelected(sel -> {
            String prev = field.getText();
            if (prev == null || prev.isBlank()) field.setText(sel);
            else field.setText(prev + ", " + sel);
        });
    }

    public String getSelectedVaccine() { return comboBox.getValue(); }

    // ===== 내부 유틸 =====
    private static boolean isHeaderOrAction(String s) {
        if (s.startsWith("###")) return true;
        return "Side Effect".equals(s) || "Quit".equals(s);
    }
    private static String stripHeader(String s) {
        return s.startsWith("###") ? s.replace("###", "").trim() : s;
    }
}
