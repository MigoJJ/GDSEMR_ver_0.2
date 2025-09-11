package com.emr.gds.fourgate;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.emr.gds.input.IAIMain;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;


public class EKG extends JFrame {
    // Main frame components
    private JCheckBox[] leadCheckboxes;
    private JTextArea summaryArea;
    
    // Input format panel components
    private List<JTextField> textFields = new ArrayList<>();
    private List<String> fieldLabels = new ArrayList<>();
    private List<JCheckBox> checkBoxes = new ArrayList<>();
    private Map<JTextField, String> textFieldToSection = new HashMap<>();
    private Map<JCheckBox, String> checkBoxToSection = new HashMap<>();
    private List<String> sectionTitles = new ArrayList<>();

    public EKG() {
        setTitle("EMR EKG Analysis");
        setSize(1300, 850);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Initialize components
        initializeComponents();
        setupLayout();
        setupEventListeners();
    }

    private void initializeComponents() {
        // South Panel with buttons
        JPanel southPanel = new JPanel();
        JButton clearButton = new JButton("Clear All");
        JButton saveButton = new JButton("Save");
        JButton quitButton = new JButton("Quit");
        JButton refButton = new JButton("EKG reference");
        southPanel.add(clearButton);
        southPanel.add(saveButton);
        southPanel.add(quitButton);
        southPanel.add(refButton);

        // East Panel with vertical checkboxes
        JPanel eastPanel = createEastPanel();

        // West Panel with Summary and Conclusion TextArea
        JPanel westPanel = createWestPanel();

        // Central Panel with input format
        JPanel centralPanel = createCentralPanel();

        // Container for West and Central panels with GridBagLayout
        JPanel mainContentPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 0, 0, 0);

        // West panel: 22% width
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.220;
        gbc.weighty = 1.0;
        mainContentPanel.add(westPanel, gbc);

        // Central panel: 60% width
        gbc.gridx = 1;
        gbc.weightx = 0.6;
        mainContentPanel.add(centralPanel, gbc);

        // Add components to frame
        add(southPanel, BorderLayout.SOUTH);
        add(eastPanel, BorderLayout.EAST);
        add(mainContentPanel, BorderLayout.CENTER);

        // Button actions
        clearButton.addActionListener(e -> clearFields());
        quitButton.addActionListener(e -> dispose());
        saveButton.addActionListener(e -> saveData());
        refButton.addActionListener(e -> refFile());
    }

    private JPanel createEastPanel() {
        JPanel eastPanel = new JPanel(new BorderLayout());
        String[] leads = {
            "Normal ECG","Sinus Bradycardia","Sinus Tachycardia","Atrial Fibrillation",
            "Premature Ventricular Contraction (PVC)",
            "Premature Atrial Contraction (PAC)",
            "Left Ventricular Hypertrophy (LVH) ECG pattern",
            "Non-specific ST-T changes",
            "Right Bundle Branch Block (RBBB)","Left Bundle Branch Block (LBBB)",
            "Prolonged QT","ST Elevation","ST Depression","Ventricular Tachycardia (VT)",
            "Wolff-Parkinson-White (WPW) syndrome","Supraventricular Tachycardia (SVT: PSVT, etc.)",
            "Anterior Wall Ischemia / STEMI",
            "Atypical T wave changes",
            "LAE","RAE","LVH","RVH","PTFV1",
            "Junctional rhythm",
            "Supraventricular tachycardia (SVT)",
            "Poor R Wave Progression ",
            "Atrial Flutter"
        };
        
        JPanel checkboxPanel = new JPanel();
        checkboxPanel.setLayout(new GridLayout(0, 1));
        leadCheckboxes = new JCheckBox[leads.length];
        
        for(int i = 0; i < leads.length; i++) {
            leadCheckboxes[i] = new JCheckBox(leads[i]);
            leadCheckboxes[i].setHorizontalAlignment(SwingConstants.LEFT);
            leadCheckboxes[i].addItemListener(e -> updateSummary());
            checkboxPanel.add(leadCheckboxes[i]);
        }
        
        JScrollPane scrollPane = new JScrollPane(checkboxPanel);
        scrollPane.setPreferredSize(new Dimension(330, 0));
        eastPanel.add(scrollPane, BorderLayout.CENTER);
        
        return eastPanel;
    }

    private JPanel createWestPanel() {
        JPanel westPanel = new JPanel(new BorderLayout());
        westPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JLabel summaryLabel = new JLabel("Summary and Conclusion:");
        summaryArea = new JTextArea(10, 20);
        summaryArea.setLineWrap(true);
        summaryArea.setWrapStyleWord(true);
        JScrollPane summaryScroll = new JScrollPane(summaryArea);
        westPanel.add(summaryLabel, BorderLayout.NORTH);
        westPanel.add(summaryScroll, BorderLayout.CENTER);
        return westPanel;
    }

    private JPanel createCentralPanel() {
        JPanel centralPanel = new JPanel(new BorderLayout());
        centralPanel.setBorder(BorderFactory.createTitledBorder("EKG Interpretation Input"));
        
        JPanel inputFormatPanel = createInputFormatPanel();
        centralPanel.add(inputFormatPanel, BorderLayout.CENTER);
        
        return centralPanel;
    }

    private JPanel createInputFormatPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        int row = 0;

        // Section: Patient Information
        addSection(formPanel, gbc, "🩺 Patient Information", row++);
        addField(formPanel, gbc, "Name:", row++, "🩺 Patient Information");
        addField(formPanel, gbc, "Date/Time:", row++, "🩺 Patient Information");
        addField(formPanel, gbc, "Age/Sex:", row++, "🩺 Patient Information");
        addField(formPanel, gbc, "Clinical Context:", row++, "🩺 Patient Information");

        // Section: Rhythm
        addSection(formPanel, gbc, "1. Rhythm", row++);
        addCheckGroup(formPanel, gbc, new String[]{"Regular", "Irregular"}, row++, "1. Rhythm");
        addCheckGroup(formPanel, gbc, new String[]{"Atrial fibrillation", "Atrial Flutter","Ectopy"}, row++, "1. Rhythm");
        addField(formPanel, gbc, "R-R intervals : ", row++, "1. Rhythm");

        // Section: Heart Rate
        addSection(formPanel, gbc, "2. Heart Rate (HR)", row++);
        addCheckGroup(formPanel, gbc, new String[]{"1500 Method", "6-second Rule", "Count R-R with ruler"}, row++, "2. Heart Rate (HR)");
        addField(formPanel, gbc, "Rate (bpm) : ", row++, "2. Heart Rate (HR)");

        // Section: P Waves
        addSection(formPanel, gbc, "3. P Waves", row++);
        addCheckGroup(formPanel, gbc, new String[]{"Present before each QRS", "Morphology consistent", "Absent or abnormal"}, row++, "3. P Waves");

        // Section: PR Interval
        addSection(formPanel, gbc, "4. PR Interval", row++);
        addField(formPanel, gbc, "Measured PR Interval (0.12-0.20 sec) : ", row++, "4. PR Interval");
        addCheckGroup(formPanel, gbc, new String[]{"Normal (0.12–0.20 sec)"}, row++, "4. PR Interval");
        addCheckGroup(formPanel, gbc, new String[]{"Prolonged → Suspect AV Block"}, row++, "4. PR Interval");
        addCheckGroup(formPanel, gbc, new String[]{"Shortened → Suspect pre-excitation"}, row++, "4. PR Interval");

        // Section: QRS Duration
        addSection(formPanel, gbc, "5. QRS Duration", row++);
        addField(formPanel, gbc, "Measured QRS (< 0.12 sec) : ", row++, "5. QRS Duration");
        addCheckGroup(formPanel, gbc, new String[]{"Normal (< 0.12 sec)", "Prolonged → Consider BBB or ventricular rhythm"}, row++, "5. QRS Duration");

        // Section: Ectopic or Early Beats
        addSection(formPanel, gbc, "6. Ectopic or Early Beats", row++);
        addCheckGroup(formPanel, gbc, new String[]{"None","PACs", "PJCs", "PVCs"}, row++, "6. Ectopic or Early Beats");
        addCheckGroup(formPanel, gbc, new String[]{"Bigeminy", "Trigeminy", "Couplets"}, row++, "6. Ectopic or Early Beats");

        // Section: R Wave Progression
        addSection(formPanel, gbc, "7. R Wave Progression (V1–V6)", row++);
        addCheckGroup(formPanel, gbc, new String[]{"Normal", "Poor R wave progression"}, row++, "7. R Wave Progression (V1–V6)");
        addField(formPanel, gbc, "Transition zone (e.g., V3):", row++, "7. R Wave Progression (V1–V6)");

        // Section: ST Segment
        addSection(formPanel, gbc, "8. ST Segment", row++);
        addCheckGroup(formPanel, gbc, new String[]{"Normal"}, row++, "8. ST Segment");
        addField(formPanel, gbc, "Elevated (leads)  : ", row++, "8. ST Segment");
        addField(formPanel, gbc, "Depressed (leads)  : ", row++, "8. ST Segment");
        addCheckGroup(formPanel, gbc, new String[]{"Concave", "Convex", "Horizontal"}, row++, "8. ST Segment");

        // Section: Q Waves
        addSection(formPanel, gbc, "9. Q Waves", row++);
        addCheckGroup(formPanel, gbc, new String[]{"Normal", "Pathological"}, row++, "9. Q Waves");
        addField(formPanel, gbc, "Leads Affected:", row++, "9. Q Waves");

        // Section: T Waves
        addSection(formPanel, gbc, "10. T Waves", row++);
        addCheckGroup(formPanel, gbc, new String[]{"Upright in most leads", "Inverted", "Peaked", "Biphasic"}, row++, "10. T Waves");
        addField(formPanel, gbc, "Leads Affected : ", row++, "10. T Waves");

        // Section: U Waves
        addSection(formPanel, gbc, "11. U Waves", row++);
        addCheckGroup(formPanel, gbc, new String[]{"Not visible", "Present", "Prominent in V2–V3"}, row++, "11. U Waves");
        addCheckGroup(formPanel, gbc, new String[]{"Consider: Hypokalemia", "Bradycardia"}, row++, "11. U Waves");

        // Section: Signs of Ischemia or Infarction
        addSection(formPanel, gbc, "12. Signs of Ischemia or Infarction", row++);
        addCheckGroup(formPanel, gbc, new String[]{"Not visible"}, row++, "12. Signs of Ischemia or Infarction");
        addCheckGroup(formPanel, gbc, new String[]{"ST Depression (Ischemia)", "ST Elevation (Infarction)", "Q Waves (Old infarct)"}, row++, "12. Signs of Ischemia or Infarction");
        addCheckGroup(formPanel, gbc, new String[]{"Anterior", "Inferior", "Lateral", "Posterior"}, row++, "12. Signs of Ischemia or Infarction");

        // Section: Final Interpretation / Summary
        addSection(formPanel, gbc, "13. Final Interpretation / Summary", row++);
        addField(formPanel, gbc, "Rhythm:", row++, "13. Final Interpretation / Summary");
        addField(formPanel, gbc, "Rate (bpm):", row++, "13. Final Interpretation / Summary");
        addField(formPanel, gbc, "Axis:", row++, "13. Final Interpretation / Summary");
        addField(formPanel, gbc, "PR:", row++, "13. Final Interpretation / Summary");
        addField(formPanel, gbc, "QRS:", row++, "13. Final Interpretation / Summary");
        addField(formPanel, gbc, "QT/QTc:", row++, "13. Final Interpretation / Summary");
        addField(formPanel, gbc, "Abnormal Findings:", row++, "13. Final Interpretation / Summary");
        addField(formPanel, gbc, "Possible Diagnosis:", row++, "13. Final Interpretation / Summary");
        addField(formPanel, gbc, "Recommendations / Action:", row++, "13. Final Interpretation / Summary");

        // Make the form scrollable
        JScrollPane scrollPane = new JScrollPane(formPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private void addSection(JPanel panel, GridBagConstraints gbc, String section, int row) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        JLabel label = new JLabel(section);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 15f));
        panel.add(label, gbc);
        gbc.gridwidth = 1;
        sectionTitles.add(section);
    }

    private void addField(JPanel panel, GridBagConstraints gbc, String label, int row, String section) {
        gbc.gridx = 0;
        gbc.gridy = row;
        JLabel jLabel = new JLabel(label);
        panel.add(jLabel, gbc);
        gbc.gridx = 1;
        JTextField field = new JTextField(25);
        field.setHorizontalAlignment(JTextField.LEFT);
        panel.add(field, gbc);
        textFields.add(field);
        fieldLabels.add(label);
        textFieldToSection.put(field, section);
    }

    private void addCheckGroup(JPanel panel, GridBagConstraints gbc, String[] labels, int row, String section) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        JPanel groupPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        for (String label : labels) {
            JCheckBox cb = new JCheckBox(label);
            groupPanel.add(cb);
            checkBoxes.add(cb);
            checkBoxToSection.put(cb, section);
        }
        panel.add(groupPanel, gbc);
        gbc.gridwidth = 1;
    }

    private void setupLayout() {
        // Layout is already set up in initializeComponents()
    }

    private void setupEventListeners() {
        // Add ItemListener to all checkboxes
        for (JCheckBox cb : checkBoxes) {
            cb.addItemListener(e -> {
                System.out.println("Checkbox " + cb.getText() + " state changed: " + cb.isSelected());
                updateSummary();
            });
        }

        // Add DocumentListener to all text fields
        for (JTextField tf : textFields) {
            tf.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    updateSummary();
                }
                @Override
                public void removeUpdate(DocumentEvent e) {
                    updateSummary();
                }
                @Override
                public void changedUpdate(DocumentEvent e) {
                    updateSummary();
                }
            });
        }
    }

    public void updateSummary() {
        StringBuilder sb = new StringBuilder();
        
        // Append selected lead checkboxes from East panel
        for (JCheckBox cb : leadCheckboxes) {
            if (cb.isSelected()) {
                sb.append("# ").append(cb.getText()).append("\n");
            }
        }

        // Append section titles and their inputs from inputFormatPanel
        Map<String, List<String>> sectionSummary = getSummary();
        for (String section : sectionSummary.keySet()) {
            sb.append("# ").append(section).append("\n");
            for (String item : sectionSummary.get(section)) {
                sb.append("     ").append(item).append("\n");
            }
        }

        // Debug: Log the summary content
        System.out.println("Updating summary: \n" + sb.toString());
        summaryArea.setText(sb.toString());
    }

    private Map<String, List<String>> getSummary() {
        Map<String, List<String>> sectionSummary = new LinkedHashMap<>();

        // Initialize lists for each section
        for (String section : sectionTitles) {
            sectionSummary.put(section, new ArrayList<>());
        }

        // Collect checkbox inputs
        for (JCheckBox cb : checkBoxes) {
            if (cb.isSelected()) {
                String section = checkBoxToSection.get(cb);
                sectionSummary.get(section).add(cb.getText());
            }
        }

        // Collect text field inputs
        for (int i = 0; i < textFields.size(); i++) {
            String text = textFields.get(i).getText().trim();
            if (!text.isEmpty()) {
                String section = textFieldToSection.get(textFields.get(i));
                sectionSummary.get(section).add(fieldLabels.get(i) + " " + text);
            }
        }

        // Remove sections with no inputs
        sectionSummary.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        // Debug: Log summary content
        System.out.println("getSummary() returned: " + sectionSummary);
        return sectionSummary;
    }

    private void clearFields() {
        summaryArea.setText("");
        for (JCheckBox cb : leadCheckboxes) {
            cb.setSelected(false);
        }
        for (JTextField tf : textFields) {
            tf.setText("");
        }
        for (JCheckBox cb : checkBoxes) {
            cb.setSelected(false);
        }
        // Debug: Log clear action
        System.out.println("Cleared all fields and summary");
    }

    private void saveData() {
        final String out = summaryArea.getText().trim();
        if (out.isEmpty()) {
            showError("Cannot save data: No summary content to save.");
            return;
        }
        if (!bridgeReady()) {
            showError("Cannot save data: EMR text areas not ready.");
            return;
        }
        
        // Target O> index = 5
        try {
            IAIMain.getTextAreaManager().focusArea(5);
            IAIMain.getTextAreaManager().insertLineIntoFocusedArea(
                String.format("\n< EKG > %s\n%s", LocalDate.now().format(DateTimeFormatter.ISO_DATE), out)
            );
            clearFields();
            System.out.println("Successfully saved EKG data to EMR area 5");
        } catch (Exception e) {
            showError("Error saving to EMR: " + e.getMessage());
            System.err.println("Error saving EKG data: " + e.getMessage());
        }
    }
    
    private boolean bridgeReady() {
        // Check if IAIMain and TextAreaManager are available and ready
        try {
            return IAIMain.getTextAreaManager() != null;
        } catch (Exception e) {
            System.err.println("Bridge not ready: " + e.getMessage());
            return false;
        }
    }
    
    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }
    
    private void refFile() {
        try {
            if (!Desktop.isDesktopSupported()) {
                throw new IOException("Desktop API unsupported");
            }
            
            String userDir = System.getProperty("user.dir");
            File file = new File(userDir + "/src/main/java/com/emr/gds/fourgate/EKG_reference.odt");
            
            System.out.println("Attempting to open: " + file.getAbsolutePath());
            if (!file.exists()) {
                throw new IOException("File not found: " + file.getAbsolutePath());
            }
            Desktop.getDesktop().open(file);
            System.out.println("Opened: " + file.getAbsolutePath());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Cannot open reference: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            System.err.println("Error: " + e.getMessage());
        }
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            EKG ekg = new EKG();
            ekg.setVisible(true);
        });
    }
}