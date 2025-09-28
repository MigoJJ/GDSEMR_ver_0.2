package com.emr.gds.fourgate.vaccine;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.emr.gds.input.IAIMain;

import javafx.application.Platform;

/**
 * A simple Swing tool-window for selecting a vaccine and logging into EMR.
 * - Bottom-right overlay style
 * - Launches a JavaFX side-effect window (VaccineSideEffect.open)
 */
public class VaccineMain {

    // ================================
    // UI Constants
    // ================================
    private static final int FRAME_WIDTH = 500;
    private static final int FRAME_HEIGHT = 850;
    private static final String FRAME_TITLE = "Vaccinations";

    // Fonts & Colors
    private static final Font LABEL_FONT = new Font("Malgun Gothic", Font.BOLD, 14);
    private static final Font BUTTON_FONT = new Font("Malgun Gothic", Font.PLAIN, 12);

    private static final Color HEADER_BG_COLOR = new Color(220, 230, 240);
    private static final Color VACCINE_BG_COLOR = Color.WHITE;
    private static final Color SIDEEFFECT_BG_COLOR = new Color(255, 250, 225);
    private static final Color QUIT_BG_COLOR = new Color(255, 224, 224);

    // Formatters
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    // JavaFX toolkit guard
    private static final Object FX_INIT_LOCK = new Object();
    private static volatile boolean fxInitialized = false;

    // UI elements (### = section header)
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

    // ================================
    // Entry
    // ================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(VaccineMain::new);
    }

    // ================================
    // Lifecycle
    // ================================
    public VaccineMain() {
        createAndShowGui();
    }

    private void createAndShowGui() {
        // Frame
        JFrame frame = new JFrame(FRAME_TITLE);
        // 툴창 성격이므로 종료 시 앱 전체 종료 대신 창만 닫기
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(FRAME_WIDTH, FRAME_HEIGHT);
        frame.setLayout(new GridLayout(UI_ELEMENTS.length, 1, 0, 3));
        frame.getContentPane().setBackground(Color.DARK_GRAY);

        // Shared listener
        ActionListener buttonClickListener = e -> {
            String clicked = ((JButton) e.getSource()).getText();
            handleButtonClick(frame, clicked);
        };

        // Build UI
        for (String text : UI_ELEMENTS) {
            if (text.startsWith("###")) {
                JLabel header = new JLabel(text.replace("###", "").trim());
                header.setFont(LABEL_FONT);
                header.setHorizontalAlignment(SwingConstants.CENTER);
                header.setOpaque(true);
                header.setBackground(HEADER_BG_COLOR);
                header.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
                frame.add(header);
            } else {
                JButton btn = new JButton(text);
                btn.setFont(BUTTON_FONT);
                btn.addActionListener(buttonClickListener);
                btn.setFocusPainted(false);

                if ("Side Effect".equals(text)) {
                    btn.setBackground(SIDEEFFECT_BG_COLOR);
                } else if ("Quit".equals(text)) {
                    btn.setBackground(QUIT_BG_COLOR);
                } else {
                    btn.setBackground(VACCINE_BG_COLOR);
                }
                frame.add(btn);
            }
        }

        // Show
        positionFrameToBottomRight(frame);
        frame.setVisible(true);
    }

    // ================================
    // Helpers
    // ================================
    private static void ensureJavaFxToolkit() {
        if (!fxInitialized) {
            synchronized (FX_INIT_LOCK) {
                if (!fxInitialized) {
                    // Initialize JavaFX toolkit (no window)
                    new JPanel();
                    fxInitialized = true;
                }
            }
        }
    }

    private void positionFrameToBottomRight(JFrame frame) {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int x = screen.width - frame.getWidth();
        int y = screen.height - frame.getHeight();
        frame.setLocation(x, y);
    }

    // ================================
    // Actions
    // ================================
    private void handleButtonClick(JFrame frame, String buttonText) {
        if ("Quit".equals(buttonText)) {
        	frame.dispose();
            return;
        }

        if (buttonText.contains("Side Effect")) {
            ensureJavaFxToolkit();
            Platform.runLater(VaccineSideEffect::open);
            return;
        }

        // Insert vaccination notes into EMR
        String currentDate = DATE_FORMAT.format(new Date());

        String subjectiveNote =
            "The patient visits for Vaccination\n" +
            " [ ✔ ]  no allergy to eggs, chicken\n" +
            "        , or any other component of the vaccine.\n" +
            " [ ✔ ]  no s/p Guillain-Barré syndrome.\n" +
            " [ ✔ ]  no adverse reactions to previous vaccines.\n" +
            " [ ✔ ]  no immunosuppression.\n";

        String assessmentNote = "\n #  " + buttonText + "  [" + currentDate + "]";
        String planNote = "...Vaccination as scheduled";

        IAIMain.getTextAreaManager().focusArea(0);
        IAIMain.getTextAreaManager().insertBlockIntoFocusedArea(subjectiveNote);

        IAIMain.getTextAreaManager().focusArea(0);
        IAIMain.getTextAreaManager().insertLineIntoFocusedArea(assessmentNote);

        IAIMain.getTextAreaManager().focusArea(8);
        IAIMain.getTextAreaManager().insertLineIntoFocusedArea(planNote);
    }
}
