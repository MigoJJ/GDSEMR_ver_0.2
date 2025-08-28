package com.emr.gds.input;

import javax.swing.*;
import javax.swing.border.TitledBorder;

import com.emr.gds.IttiaAppTextArea.IttiaAppMain;

import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;
import javafx.application.Platform;

/**
 * A combined JFrame for frequently used data input panels: BMI, HbA1c, and Vital Signs.
 */
public class FreqInputFrame extends JFrame {

    //<editor-fold desc="Fields for EMR_BMI_calculator">
    private static final String[] BMI_FIELDS = {"Height (cm): ", "Weight (kg): ", "Waist (cm or inch): "};
    private final JTextField[] bmiInputs = new JTextField[BMI_FIELDS.length];
    //</editor-fold>

    //<editor-fold desc="Fields for EMR_HbA1c">
    private static final String[] HBA1C_LABELS = {"FBS / PP2 time", "Glucose mg/dL", "HbA1c %"};
    private static final String[][] GLUCOSE_STATUS = {
        {"9.0", "Very poor"}, {"8.5", "Poor"}, {"7.5", "Fair"},
        {"6.5", "Good"}, {"0.0", "Excellent"}
    };
    private final JTextArea hba1cOutputArea = new JTextArea(4, 20);
    private final JTextField[] hba1cInputs = new JTextField[HBA1C_LABELS.length];
    //</editor-fold>

    //<editor-fold desc="Fields for Vitalsign">
    private JTextField vsInputField;
    private JTextArea vsOutputArea;
    private JTextArea vsDescriptionArea;
    private Set<String> vsValidInputs;
    private Integer sbp = null;
    private Integer dbp = null;
    private Integer pulseRate = null;
    private Double bodyTemperature = null;
    private Integer respirationRate = null;
    //</editor-fold>

    /**
     * Constructs the main frame and initializes all UI components.
     */
    public FreqInputFrame() {
        setupFrame();
        createUI();
        setVisible(true);
    }

    /**
     * Sets up the main JFrame properties, size, and location.
     */
    private void setupFrame() {
        setTitle("Frequent Data Input");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setUndecorated(true);

        // Position frame at the top-right of the screen
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int frameWidth = 320;
        setLocation(screenSize.width - frameWidth, 0);
        
        setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));
    }

    /**
     * Creates and adds the individual panels for each function to the main frame.
     */
    private void createUI() {
        initializeVitalsignValidInputs();
        
        add(createBmiPanel());
        add(createHba1cPanel());
        add(createVitalsignPanel());
        
        // Add a master Quit button at the bottom
        JPanel quitPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton quitButton = new JButton("Quit All");
        quitButton.addActionListener(e -> dispose());
        quitPanel.add(quitButton);
        add(quitPanel);

        pack(); // Adjust frame size to fit all components
    }

    //<editor-fold desc="BMI Calculator Panel and Logic">
    private JPanel createBmiPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("BMI Calculator"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 5, 2, 5);

        for (int i = 0; i < BMI_FIELDS.length; i++) {
            addBmiInputRow(panel, gbc, i);
        }

        JButton saveButton = new JButton("Save BMI");
        saveButton.addActionListener(e -> calculateBMI());
        gbc.gridx = 1;
        gbc.gridy = BMI_FIELDS.length;
        gbc.gridwidth = 2;
        panel.add(saveButton, gbc);

        return panel;
    }

    private void addBmiInputRow(JPanel panel, GridBagConstraints gbc, int row) {
        gbc.gridx = 0;
        gbc.gridy = row;
        panel.add(new JLabel(BMI_FIELDS[row]), gbc);

        JTextField field = new JTextField(10);
        field.setPreferredSize(new Dimension(15, 30));
        field.setHorizontalAlignment(SwingConstants.CENTER);
        
        bmiInputs[row] = field;
        gbc.gridx = 1;
        panel.add(field, gbc);

        field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (row < bmiInputs.length - 1) {
                        bmiInputs[row + 1].requestFocusInWindow();
                    } else {
                        calculateBMI();
                    }
                }
            }
        });
    }

    private void calculateBMI() {
        try {
            double height = Double.parseDouble(bmiInputs[0].getText());
            double weight = Double.parseDouble(bmiInputs[1].getText());
            double bmi = weight / Math.pow(height / 100, 2);

            String bmiCategory = getBMICategory(bmi);
            String waistMeasurement = processWaistMeasurement();

            updateEMRFrameWithBMI(bmi, height, weight, bmiCategory, waistMeasurement);
            
            // Clear fields after calculation
            for(JTextField field : bmiInputs) {
                field.setText("");
            }
            bmiInputs[0].requestFocusInWindow();

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Please enter valid numbers for Height and Weight");
        }
    }
    
    private String processWaistMeasurement() {
        String waist = bmiInputs[2].getText().trim();
        if (waist.isEmpty()) return "";
        if (waist.toLowerCase().contains("i")) {
            double inches = Double.parseDouble(waist.replaceAll("[^\\d.]", ""));
            return String.format("%.1f", inches * 2.54);
        }
        return waist;
    }

    private String getBMICategory(double bmi) {
        if (bmi < 18.5) return "Underweight";
        if (bmi < 25) return "Healthy weight";
        if (bmi < 30) return "Overweight";
        return "Obesity";
    }

    private void updateEMRFrameWithBMI(double bmi, double height, double weight, 
            String category, String waist) {
    		String bmiText = String.format("%s : BMI: [ %.2f ]kg/m^2", category, bmi);
    		String details = String.format("\n< BMI >\n%s\nHeight : %.1f cm   Weight : %.1f kg%s",bmiText, height, weight,
    					waist.isEmpty() ? "" : "   Waist: " + waist + " cm"
    				);

    		IttiaAppMain.getTextAreaManager().insertBlockIntoFocusedArea(details);
    }

    //</editor-fold>
    
    //<editor-fold desc="HbA1c Panel and Logic">
    private JPanel createHba1cPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("HbA1c EMR"));

        panel.add(hba1cOutputArea, BorderLayout.NORTH);
        panel.add(createHba1cInputPanel(), BorderLayout.CENTER);
        panel.add(createHba1cButtonPanel(), BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createHba1cInputPanel() {
        JPanel panel = new JPanel(new GridLayout(3, 2, 5, 5));
        for (int i = 0; i < HBA1C_LABELS.length; i++) {
            panel.add(new JLabel(HBA1C_LABELS[i], SwingConstants.CENTER));
            hba1cInputs[i] = createStyledTextField(i);
            panel.add(hba1cInputs[i]);
        }
        return panel;
    }

    private JTextField createStyledTextField(int index) {
        JTextField field = new JTextField();
        field.setPreferredSize(new Dimension(field.getPreferredSize().width, 30));
        field.setHorizontalAlignment(JTextField.CENTER);
        field.addActionListener(e -> processHba1cInput(index));
        return field;
    }

    private JPanel createHba1cButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout());
        for (String name : new String[]{"Clear", "Save"}) {
            JButton button = new JButton(name);
            button.addActionListener(e -> handleHba1cButton(name));
            panel.add(button);
        }
        return panel;
    }

    private void processHba1cInput(int index) {
        String value = hba1cInputs[index].getText();
        if (value.trim().isEmpty()) return;

        if (index == 0) {
            hba1cOutputArea.append("\n   " + (value.equals("0") ? "FBS" : "PP" + value));
        } else if (index == 1) {
            hba1cOutputArea.append("   [    " + value + "   ] mg/dL");
        } else if (index == 2) {
            processHbA1cValue(value);
        }
        hba1cInputs[index].setText("");
        if (index < hba1cInputs.length - 1) {
            hba1cInputs[index + 1].requestFocus();
        } else {
            hba1cInputs[0].requestFocus();
        }
    }

    private void processHbA1cValue(String value) {
        try {
            double hba1c = Double.parseDouble(value);
            hba1cOutputArea.append("   HbA1c       [    " + value + "   ] %\n");
            calculateAndDisplayHba1c(hba1c);
            saveHba1cData();
            clearHba1cFields();
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid HbA1c value.");
        }
    }
    
    private void calculateAndDisplayHba1c(double hba1c) {
        double ifcc = (hba1c - 2.15) * 10.929;
        double eagMgDl = (28.7 * hba1c) - 46.7;
        double eagMmolL = eagMgDl / 18.01559;

        hba1cOutputArea.append(String.format(
            "\n\tIFCC HbA1c: [ %.0f ] mmol/mol" +
            "\n\teAG: [ %.0f ] mg/dL" +
            "\n\teAG: [ %.2f ] mmol/l\n",
            ifcc, eagMgDl, eagMmolL));

        determineGlucoseStatus(hba1c);
    }
    
    private void determineGlucoseStatus(double hba1c) {
        for (String[] status : GLUCOSE_STATUS) {
            if (hba1c > Double.parseDouble(status[0])) {
                final String line = "\n...now [ " + status[1] + " ] controlled glucose status";
                Platform.runLater(() -> {
                    // A> is index 7 in TEXT_AREA_TITLES
                    IttiaAppMain.getTextAreaManager().focusArea(7);
                    IttiaAppMain.getTextAreaManager().insertLineIntoFocusedArea(line);
                });
                break;
            }
        }
    }

    private void handleHba1cButton(String name) {
        switch (name) {
            case "Clear" -> clearHba1cFields();
            case "Save" -> saveHba1cData();
        }
    }

    private void clearHba1cFields() {
        hba1cOutputArea.setText("");
        for (JTextField field : hba1cInputs) field.setText("");
        hba1cInputs[0].requestFocusInWindow();
    }

    private void saveHba1cData() {
        final String text = hba1cOutputArea.getText().trim();
        if (!text.isEmpty()) {
            Platform.runLater(() -> {
                // O> is index 5 in TEXT_AREA_TITLES
                IttiaAppMain.getTextAreaManager().focusArea(5);
                // If it’s multi-line, keep it as a block; otherwise a single line is fine
                if (text.contains("\n")) {
                    IttiaAppMain.getTextAreaManager().insertBlockIntoFocusedArea(text + "\n");
                } else {
                    IttiaAppMain.getTextAreaManager().insertLineIntoFocusedArea(text);
                }
            });
        }
    }

    //</editor-fold>

    //<editor-fold desc="Vitalsign Panel and Logic">
    private JPanel createVitalsignPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Vital Sign Tracker"));

        vsInputField = new JTextField(20);
        vsInputField.setHorizontalAlignment(JTextField.CENTER);
        Dimension preferredSize = vsInputField.getPreferredSize();
        preferredSize.height = 30;
        vsInputField.setPreferredSize(preferredSize);
        vsInputField.setMaximumSize(vsInputField.getPreferredSize());
        panel.add(vsInputField);

        vsDescriptionArea = new JTextArea(1, 20);
        vsDescriptionArea.setText(" at GDS : Regular pulse, Right Seated Position");
        vsDescriptionArea.setBorder(BorderFactory.createTitledBorder("Description"));
        vsDescriptionArea.setLineWrap(true);
        vsDescriptionArea.setWrapStyleWord(true);
        vsDescriptionArea.setEditable(false);
        panel.add(new JScrollPane(vsDescriptionArea));

        vsOutputArea = new JTextArea(5, 20);
        vsOutputArea.setBorder(BorderFactory.createTitledBorder("Output"));
        vsOutputArea.setEditable(false);
        vsOutputArea.setLineWrap(true);
        vsOutputArea.setWrapStyleWord(true);
        panel.add(new JScrollPane(vsOutputArea));

        vsInputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    handleVitalsignInput();
                    vsInputField.setText("");
                }
            }
        });
        
        JPanel buttonPanel = new JPanel();
        JButton clearButton = new JButton("Clear");
        JButton saveButton = new JButton("Save");
        buttonPanel.add(clearButton);
        buttonPanel.add(saveButton);
        panel.add(buttonPanel);

        clearButton.addActionListener(e -> resetVitalsignFields());
        saveButton.addActionListener(e -> saveVitalsignData());

        return panel;
    }

    private void initializeVitalsignValidInputs() {
        vsValidInputs = new HashSet<>();
        vsValidInputs.add("h"); // Home
        vsValidInputs.add("o"); // Other clinic
        vsValidInputs.add("g"); // GDS
        vsValidInputs.add("l"); // Left position
        vsValidInputs.add("r"); // Right position
        vsValidInputs.add("i"); // Irregular pulse
        vsValidInputs.add("t"); // Temperature input prefix
    }
    
    private void handleVitalsignInput() {
        String input = vsInputField.getText().trim().toLowerCase();
        if (vsValidInputs.contains(input) || input.startsWith("t")) {
            if (input.startsWith("t")) {
                handleVitalsignTemperatureInput(input);
            } else {
                updateVitalsignDescriptionArea(input);
            }
        } else {
            handleVitalsignNumericInput(input);
        }
    }
    
    private void updateVitalsignDescriptionArea(String input) {
        String currentText = vsDescriptionArea.getText();
        switch (input) {
            case "h" -> vsDescriptionArea.setText("   at home by self");
            case "o" -> vsDescriptionArea.setText("   at Other clinic");
            case "g" -> vsDescriptionArea.setText(" at GDS : Regular pulse, Right Seated Position");
            case "l" -> vsDescriptionArea.setText(currentText.replace("Right", "Left"));
            case "r" -> vsDescriptionArea.setText(currentText.replace("Left", "Right"));
            case "i" -> vsDescriptionArea.setText(currentText.replace("Regular", "Irregular"));
        }
    }
    
    private void handleVitalsignTemperatureInput(String input) {
        try {
            double tempValue = Double.parseDouble(input.substring(1));
            vsDescriptionArea.setText(" at GDS : Forehead (Temporal Artery) Thermometer:");
            vsOutputArea.setText("Body Temperature [ " + tempValue + " ] ℃");
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            vsOutputArea.setText("Invalid temperature input. Use 't' followed by a number.");
        }
    }

    private void handleVitalsignNumericInput(String input) {
        try {
            double value = Double.parseDouble(input);
            processVitalsignMeasurement(value);
        } catch (NumberFormatException e) {
            vsOutputArea.setText("Invalid input. Please enter a number.");
        }
    }
    
    private void processVitalsignMeasurement(double value) {
        if (sbp == null) {
            sbp = (int) value;
            vsOutputArea.setText("\tSBP [" + sbp + "] mmHg   ");
        } else if (dbp == null) {
            dbp = (int) value;
            vsOutputArea.setText("BP [" + sbp + " / " + dbp + "] mmHg   ");
        } else if (pulseRate == null) {
            pulseRate = (int) value;
            vsOutputArea.append("PR [" + pulseRate + "]/minute   ");
        } else if (bodyTemperature == null) {
            bodyTemperature = value;
            vsOutputArea.append("\n\tBody Temperature [" + bodyTemperature + "]℃");
        } else if (respirationRate == null) {
            respirationRate = (int) value;
            vsOutputArea.append("\n\tRespiration Rate [" + respirationRate + "]/minute");
            resetVitalsignMeasurements();
        }
    }
    
    private void resetVitalsignMeasurements() {
        sbp = null;
        dbp = null;
        pulseRate = null;
        bodyTemperature = null;
        respirationRate = null;
    }

    private void resetVitalsignFields() {
        vsInputField.setText("");
        vsOutputArea.setText("");
        vsDescriptionArea.setText(" at GDS : Regular pulse, Right Seated Position");
        resetVitalsignMeasurements();
    }
    
    private void saveVitalsignData() {
        if (!vsDescriptionArea.getText().isBlank()) {
            IttiaAppMain.getTextAreaManager().insertLineIntoFocusedArea(vsDescriptionArea.getText());
        }
        if (!vsOutputArea.getText().isBlank()) {
            IttiaAppMain.getTextAreaManager().insertLineIntoFocusedArea("\t" + vsOutputArea.getText());
        }
        resetVitalsignFields();
    }

    //</editor-fold>
    



    /**
     * Main method to run the application.
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(FreqInputFrame::new);
    }
}