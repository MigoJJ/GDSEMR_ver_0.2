package com.emr.gds.fourgate.KCDdatabase;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;

public class KCDRecordDialog extends Dialog<KCDRecord> {

    public KCDRecordDialog(String title, KCDRecord initialData) {
        setTitle(title);
        initModality(Modality.APPLICATION_MODAL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        String[] labels = {"Classification:", "Disease Code:", "Check Field:", "Korean Name:", "English Name:", "Note:"};
        TextField[] fields = new TextField[labels.length - 1];
        TextArea noteTextArea = new TextArea();

        for (int i = 0; i < labels.length; i++) {
            grid.add(new Label(labels[i]), 0, i);
            if (i < fields.length) {
                fields[i] = new TextField();
                grid.add(fields[i], 1, i);
            } else {
                grid.add(noteTextArea, 1, i);
            }
        }

        if (initialData != null) {
            fields[0].setText(initialData.getClassification());
            fields[1].setText(initialData.getDiseaseCode());
            fields[2].setText(initialData.getCheckField());
            fields[3].setText(initialData.getKoreanName());
            fields[4].setText(initialData.getEnglishName());
            noteTextArea.setText(initialData.getNote());
        }

        getDialogPane().setContent(grid);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return new KCDRecord(
                        fields[0].getText(),
                        fields[1].getText(),
                        fields[2].getText(),
                        fields[3].getText(),
                        fields[4].getText(),
                        noteTextArea.getText()
                );
            }
            return null;
        });
    }
}