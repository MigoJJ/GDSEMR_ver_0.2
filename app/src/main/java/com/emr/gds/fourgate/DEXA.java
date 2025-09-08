package com.emr.gds.fourgate;

import javax.swing.*;

import com.emr.gds.input.IAIMain;

import java.awt.*;
import java.awt.event.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class DEXA extends JFrame implements ActionListener {

    private JTextField scoreField, ageField;
    private JComboBox<String> genderComboBox;
    private JTextArea outputTextArea;
    private JCheckBox fragilityFractureCheckBox, menopauseCheckBox, hrtCheckBox, tahCheckBox, stonesCheckBox;
    private ButtonGroup scoreTypeButtonGroup;
    private JRadioButton tScoreRadioButton, zScoreRadioButton;

    public DEXA() {
        setTitle("Osteoporosis Risk Assessment");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        initComponents();
        createLayout();
        addListeners();

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
        scoreField.requestFocusInWindow();
    }

    private void initComponents() {
        scoreField = new JTextField(10);
        ageField = new JTextField(10);
        genderComboBox = new JComboBox<>(new String[]{"Female", "Male"});
        outputTextArea = new JTextArea(10, 40);
        outputTextArea.setEditable(false);
        outputTextArea.setBackground(new Color(250, 250, 250)); // Light Sky Blue

        fragilityFractureCheckBox = new JCheckBox();
        menopauseCheckBox = new JCheckBox();
        hrtCheckBox = new JCheckBox();
        tahCheckBox = new JCheckBox();
        stonesCheckBox = new JCheckBox();

        scoreTypeButtonGroup = new ButtonGroup();
        tScoreRadioButton = new JRadioButton("T-Score", true);
        zScoreRadioButton = new JRadioButton("Z-Score");

        // Set font to bold and larger
        Font boldFont = new Font("SansSerif", Font.BOLD, 14); // Example size, adjust as needed
        scoreField.setFont(boldFont);
        ageField.setFont(boldFont);

        // Center the text within the JTextFields
        scoreField.setHorizontalAlignment(JTextField.CENTER);
        ageField.setHorizontalAlignment(JTextField.CENTER);
        
        scoreTypeButtonGroup.add(tScoreRadioButton);
        scoreTypeButtonGroup.add(zScoreRadioButton);
    }

    private void addListeners() {
        scoreField.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    ageField.requestFocusInWindow();
                }
            }
        });

        ageField.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    genderComboBox.requestFocusInWindow();
                }
            }
        });
    }

    private void createLayout() {
        add(new JScrollPane(outputTextArea), BorderLayout.NORTH); // Output Area
        add(createWestPanel(), BorderLayout.WEST);
        add(createInputPanel(), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        String[] buttonNames = {"Assess Risk", "Clear", "Save", "Quit"};
        for (String name : buttonNames) {
            JButton button = new JButton(name);
            button.addActionListener(this);
            buttonPanel.add(button);
        }
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private JPanel createWestPanel() {
        JTextArea zScoreInfo = new JTextArea("""
                Z-Score:
                - Children/adolescents
                - Premenopausal women (<50)
                - Men under 50

                Key Tests:
                * Calcium & Phosphorus
                * Vitamin D
                * Kidney Function (eGFR, Cr)
                * Bone Turnover Markers (CTX/NTX, BSAP)
                """);

        zScoreInfo.setEditable(false);
        zScoreInfo.setBackground(new Color(205, 206, 250));

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Z-Score Explanation"));
        panel.add(zScoreInfo, BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(300, 100));

        return panel;
    }

    private JPanel createInputPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Input Data"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        addField(panel, gbc, "Score:", scoreField, 0);
        addField(panel, gbc, "Age:", ageField, 1);
        addField(panel, gbc, "Gender:", genderComboBox, 2);
        addField(panel, gbc, "Menopause:", menopauseCheckBox, 3);
        addField(panel, gbc, "Fracture:", fragilityFractureCheckBox, 4);
        addField(panel, gbc, "HRT:", hrtCheckBox, 5);
        addField(panel, gbc, "TAH:", tahCheckBox, 6);
        addField(panel, gbc, "Kidney Stones:", stonesCheckBox, 7);
        addField(panel, gbc, "Score Type:", tScoreRadioButton, 8);

        gbc.gridx = 2;
        gbc.gridy = 8;
        panel.add(zScoreRadioButton, gbc);

        return panel;
    }

    private void addField(JPanel panel, GridBagConstraints gbc, String labelText, Component comp, int y) {
        JLabel label = new JLabel(labelText);
        gbc.gridx = 0;
        gbc.gridy = y;
        panel.add(label, gbc);

        gbc.gridx = 1;
        panel.add(comp, gbc);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String command = e.getActionCommand();

        switch (command) {
            case "Assess Risk" -> processInput();
            case "Clear" -> clearFields();
            case "Save" -> saveToEMR();
            case "Quit" -> dispose();
        }
    }

    private void saveToEMR() {
        final String out = outputTextArea.getText().trim();
        if (out.isEmpty()) return;

        if (!bridgeReady()) {
            showError("Cannot save data: EMR text areas not ready.");
            return;
        }

        // Target O> index = 5
        IAIMain.getTextAreaManager().focusArea(5);
        IAIMain.getTextAreaManager().insertLineIntoFocusedArea("\t" + out);
        clearFields();
    }

    private boolean bridgeReady() {
        // Placeholder for checking if the EMR bridge is ready
        // Replace with actual implementation
        return true;
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void processInput() {
        try {
            double score = Double.parseDouble(scoreField.getText());
            int age = Integer.parseInt(ageField.getText());
            String gender = (String) genderComboBox.getSelectedItem();
            boolean fracture = fragilityFractureCheckBox.isSelected();
            boolean menopause = menopauseCheckBox.isSelected();
            boolean hrt = hrtCheckBox.isSelected();
            boolean tah = tahCheckBox.isSelected();
            boolean stones = stonesCheckBox.isSelected();
            String scoreType = tScoreRadioButton.isSelected() ? "T-Score" : "Z-Score";

            outputTextArea.setText(formatReport(score, scoreType, age, gender, fracture, menopause, hrt, tah, stones));
        } catch (NumberFormatException ex) {
            outputTextArea.setText("Invalid input. Enter numeric values for score and age.");
        }
    }

    private String formatReport(double score, String scoreType, int age, String gender, boolean fracture, boolean menopause, boolean hrt, boolean tah, boolean stones) {
        String diagnosis;

        if (scoreType.equals("T-Score")) {
             diagnosis = (score <= -2.5 && fracture) ? "Severe Osteoporosis" :
                    (score <= -2.5 ? "Osteoporosis" :
                            (score < -1.0 ? "Osteopenia" : "Normal Bone Density"));
        } else { // Z-Score
            if (score > -2.0) {
                diagnosis = "Normal: Z-score greater than -2.0";
            } else if (score <= -2.0) {
                diagnosis = "Below the expected range for age: Z-score less than or equal to -2.0";
            } else { // This condition should ideally not happen but included for completeness.
                diagnosis = "Above the expected range for age: Z-score significantly above the mean";
            }
        }

        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));

        StringBuilder report = new StringBuilder();
        report.append("< DEXA >\n");
        report.append(String.format("\t%s %s [%.1f]\n", diagnosis, scoreType, score));
        report.append(String.format("\tAge: [%d]  Gender: [%s]\n", age, gender));

        if (gender.equals("Female")) {
            report.append(String.format("\tMenopause: [%s]  Fracture: [%s]\n", menopause ? "Postmenopausal" : "Premenopausal", fracture ? "+" : "-"));
            report.append(String.format("\tHRT: [%s]  TAH: [%s]  Stones: [%s]\n", hrt ? "+" : "-", tah ? "+" : "-", stones ? "+" : "-"));
        } else { // Male
            report.append(String.format("\tFracture: [%s]  Stones: [%s]\n", fracture ? "+" : "-", stones ? "+" : "-"));
        }

        report.append("Comment>\n");
        report.append(String.format("# %s %s [%.1f]   %s\n", diagnosis, scoreType, score, date));

        return report.toString();
    }

    private void clearFields() {
        scoreField.setText("");
        ageField.setText("");
        genderComboBox.setSelectedIndex(0);
        fragilityFractureCheckBox.setSelected(false);
        menopauseCheckBox.setSelected(false);
        hrtCheckBox.setSelected(false);
        tahCheckBox.setSelected(false);
        stonesCheckBox.setSelected(false);
        tScoreRadioButton.setSelected(true);
        outputTextArea.setText("");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(DEXA::new);
    }
}