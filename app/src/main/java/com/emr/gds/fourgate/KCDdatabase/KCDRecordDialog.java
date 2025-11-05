package com.emr.gds.fourgate.KCDdatabase;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;

public class KCDRecordDialog extends Dialog<KCDRecord> {

    private final TextField diseaseCodeField;

    public KCDRecordDialog(String title, KCDRecord initialData) {
        setTitle(title);
        initModality(Modality.APPLICATION_MODAL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField classificationField = new TextField();
        diseaseCodeField = new TextField();
        TextField checkField = new TextField();
        TextField koreanNameField = new TextField();
        TextField englishNameField = new TextField();
        TextArea noteTextArea = new TextArea();

        grid.add(new Label("Classification:"), 0, 0);
        grid.add(classificationField, 1, 0);
        grid.add(new Label("Disease Code:"), 0, 1);
        grid.add(diseaseCodeField, 1, 1);
        grid.add(new Label("Check Field:"), 0, 2);
        grid.add(checkField, 1, 2);
        grid.add(new Label("Korean Name:"), 0, 3);
        grid.add(koreanNameField, 1, 3);
        grid.add(new Label("English Name:"), 0, 4);
        grid.add(englishNameField, 1, 4);
        grid.add(new Label("Note:"), 0, 5);
        grid.add(noteTextArea, 1, 5);

        if (initialData != null) {
            classificationField.setText(initialData.getClassification());
            diseaseCodeField.setText(initialData.getDiseaseCode());
            checkField.setText(initialData.getCheckField());
            koreanNameField.setText(initialData.getKoreanName());
            englishNameField.setText(initialData.getEnglishName());
            noteTextArea.setText(initialData.getNote());
        }

        getDialogPane().setContent(grid);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return new KCDRecord(
                        classificationField.getText(),
                        diseaseCodeField.getText(),
                        checkField.getText(),
                        koreanNameField.getText(),
                        englishNameField.getText(),
                        noteTextArea.getText()
                );
            }
            return null;
        });
    }

    public void setDiseaseCodeEditable(boolean editable) {
        diseaseCodeField.setEditable(editable);
    }
}