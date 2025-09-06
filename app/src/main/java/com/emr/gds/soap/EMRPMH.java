package com.emr.gds.soap;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.util.*;
import javax.swing.*;
import com.emr.gds.input.TextAreaManager;

/**
 * Compact EMR Past Medical History (PMH) dialog.
 */
public class EMRPMH extends JFrame {
    private final TextAreaManager textAreaManager;

    private final JTextArea selectedArea = textArea(6, 10, true);
    private final JTextArea displayArea  = textArea(12, 50, false);
    private final JPanel    checkBoxPanel = new JPanel(new GridLayout(0, 3, 6, 6));
    private final JButton   btnClear = new JButton("Clear");
    private final JButton   btnCopy  = new JButton("Copy");
    private final JButton   btnSave  = new JButton("Save");
    private final JButton   btnQuit  = new JButton("Quit");

    private static final String[][] ROWS = {
            {"Dyslipidemia", "Hypertension", "Diabetes Mellitus"},
            {"Cancer", "Operation", "Thyroid Disease"},
            {"Asthma", "Pneumonia", "Tuberculosis"},
            {"GERD", "Hepatitis A / B", "Gout"},
            {"Arthritis", "Hearing Loss", "Parkinson's Disease"},
            {"CVA", "Depression", "Cognitive Disorder"},
            {"AMI", "Angina Pectoris", "Arrhythmia"},
            {"Allergy", "All denied allergies..."},
            {"Food", "Injection", "Medication"}
    };

    private final Map<String, JCheckBox> boxes = new LinkedHashMap<>();
    private final Map<String, Boolean>   selectionMap = new LinkedHashMap<>();

    public EMRPMH(TextAreaManager manager) {
        this.textAreaManager = manager;
        initFrame();
        buildCheckBoxes();
        layoutUI();
        wireActions();
        refreshAll();
    }

    private void initFrame() {
        setTitle("EMR Past Medical History");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(820, 820);
        setLocationRelativeTo(null);
        displayArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        selectedArea.setFont(new Font("Consolas", Font.PLAIN, 12));
    }

    private static JTextArea textArea(int rows, int cols, boolean editable) {
        JTextArea ta = new JTextArea(rows, cols);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setEditable(editable);
        return ta;
    }

    private void buildCheckBoxes() {
        for (String[] row : ROWS) {
            for (String item : row) {
                if (item == null || item.isBlank()) continue;
                JCheckBox cb = new JCheckBox(item);
                cb.addItemListener(this::onToggle);
                boxes.put(item, cb);
                selectionMap.put(item, false);
            }
        }
        boxes.values().forEach(checkBoxPanel::add);
    }

    private void layoutUI() {
        JPanel topText = new JPanel(new GridLayout(2, 1, 6, 6));
        topText.add(new JScrollPane(selectedArea));
        topText.add(new JScrollPane(displayArea));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(btnClear);
        buttons.add(btnCopy);
        buttons.add(btnSave);
        buttons.add(btnQuit);

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.add(topText, BorderLayout.NORTH);
        center.add(new JScrollPane(checkBoxPanel), BorderLayout.CENTER);

        setLayout(new BorderLayout(8, 8));
        add(center, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
    }

    private void wireActions() {
        btnClear.addActionListener(this::onClear);
        btnCopy.addActionListener(this::onCopy);
        btnSave.addActionListener(this::onSave);
        btnQuit.addActionListener(e -> dispose());
    }

    private void onToggle(ItemEvent e) {
        JCheckBox src = (JCheckBox) e.getItemSelectable();
        selectionMap.put(src.getText(), e.getStateChange() == ItemEvent.SELECTED);
        refreshAll();
    }

    private void onClear(ActionEvent e) {
        selectionMap.keySet().forEach(k -> selectionMap.put(k, false));
        boxes.values().forEach(b -> b.setSelected(false));
        refreshAll();
    }

    private void onCopy(ActionEvent e) {
        String s = selectedSummary();
        if (!s.isBlank()) {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(s), null);
            JOptionPane.showMessageDialog(this, "Copied selected conditions to clipboard.");
        }
    }

    private void onSave(ActionEvent e) {
        if (textAreaManager == null) {
            error("TextAreaManager not available. Cannot save to main application.");
            return;
        }
        if (selectionMap.values().stream().noneMatch(Boolean::booleanValue) && selectedArea.getText().isBlank()) {
            error("No conditions or text entered. Nothing to save.");
            return;
        }

        // Combine both the formatted grid and free text from selectedArea
        StringBuilder block = new StringBuilder();
        if (!selectedArea.getText().isBlank()) {
            block.append(selectedArea.getText().trim()).append("\n\n");
        }
        block.append(renderGrid(true));

        try {
            textAreaManager.insertBlockIntoArea(TextAreaManager.AREA_PMH, block.toString(), true);
            JOptionPane.showMessageDialog(this, "Past Medical History text and selections appended and saved.");
            dispose();
        } catch (Exception ex) {
            error("Failed to save: " + ex.getMessage());
        }
    }

    private void refreshAll() {
        selectedArea.setText(selectedSummary());
        displayArea.setText(renderGrid(true));
    }

    private String selectedSummary() {
        StringBuilder sb = new StringBuilder();
        selectionMap.forEach((k, v) -> { if (v) sb.append("   ▣ ").append(k).append('\n'); });
        return sb.toString();
    }

    private String renderGrid(boolean withMarks) {
        StringBuilder sb = new StringBuilder();
        for (String[] row : ROWS) {
            int col = 0;
            for (String item : row) {
                if (item == null || item.isBlank()) continue;
                String mark = withMarks ? (isSelected(item) ? "▣" : "□") : "□";
                sb.append("   ").append(mark).append(' ').append(item);
                col++;
                if (col < row.length) sb.append("\t");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private boolean isSelected(String item) { return Boolean.TRUE.equals(selectionMap.get(item)); }

    private void error(String msg) { JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE); }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            System.out.println("Running EMRPMH (compact) in standalone test mode.");
            EMRPMH frame = new EMRPMH(null);
            frame.setVisible(true);
        });
    }
}
