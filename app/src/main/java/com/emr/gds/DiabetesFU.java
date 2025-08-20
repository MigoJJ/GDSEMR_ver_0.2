// DiabetesFU.java
package com.emr.gds;

import javafx.scene.control.TextArea;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Utility class to insert a sample diabetes follow-up note into the
 * application's text areas.
 */
public class DiabetesFU {

    private static final String SAMPLE = """
            Patient Information
            - Name: [Redacted]
            - Age/Sex: 58 / Male
            - Date of Visit: 2025-08-19
            - MRN: XXXXXXX
            Chief Complaint>
            - Follow-up for Diabetes Mellitus
            History of Present Illness>
            - Patient with known Type 2 Diabetes Mellitus since 2002 (23 years).
            - Reports suboptimal control over the past year, with recent HbA1c 8.0% (previous 6.5–7.2%).
            - Denies polyuria, polydipsia, weight loss.
            - Has not attended eye examination despite referral.
            Past Medical History>
            - Type 2 Diabetes Mellitus (E11), 2002–present
            - Hypertension (I10), diagnosed 2013, controlled
            - Hypercholesterolemia (E78.0), diagnosed 2016
            - No history of cardiovascular events
            Medications
            - Metformin 1000 mg bid
            - DPP-4 inhibitor qd
            - ACE inhibitor qd
            - Statin qd
            Allergies
            - No known drug or food allergies as of 2025.08.19
            Family History
            - Father: Type 2 Diabetes, deceased (MI)
            - Mother: Hypertension, alive
            Social History
            - Non-smoker, occasional alcohol
            - Retired office worker
            Examination
            - BP: 128/76 mmHg
            - HR: 76 bpm, regular
            - Weight: 72 kg, BMI 26.4 kg/m²
            - Foot exam: intact sensation, no ulcers
            Investigations
            - HbA1c: 8.0% (07/2025)
            - FBS: 152 mg/dL
            - LDL: 118 mg/dL
            - Creatinine: 1.02 mg/dL, eGFR normal
            Assessment>
            - Type 2 Diabetes Mellitus, suboptimally controlled
            - Hypertension, well controlled
            - Hyperlipidemia, on treatment
            Plan>
            1. Intensify diabetes management: add SGLT2 inhibitor.
            2. Reinforce diet and exercise education.
            3. Annual eye exam referral (pending).
            4. Continue antihypertensive and statin therapy.
            5. Follow-up in 3 months with repeat HbA1c.
            Comments>
            Physician
            Dr. [Endocrinologist Name]
            Endocrinology Department
            """;

    /**
     * Parse the sample note and append it to the application's text areas.
     *
     * @param app the main application instance
     */
    public static void insertSample(IttiaApp app) {
        // This call will now work because getTextAreas() is fixed
        List<TextArea> areas = app.getTextAreas();
        if (areas == null || areas.isEmpty()) {
            System.err.println("Cannot insert sample, TextAreas are not available.");
            return;
        }

        String[] sections = parseSample();

        for (int i = 0; i < areas.size() && i < sections.length; i++) {
            String content = sections[i];
            if (content == null || content.isEmpty()) continue;

            TextArea ta = areas.get(i);
            // Append with a newline separator if the area already has text
            if (!ta.getText().isEmpty()) {
                ta.appendText("\n\n");
            }
            ta.appendText(content);
        }
    }

    /**
     * Parses the multi-line SAMPLE string into an array of strings,
     * where each element corresponds to a TextArea.
     *
     * @return An array of strings with the content for each section.
     */
    private static String[] parseSample() {
        // CORRECTED: Reference the constant from the IttiaApp class
        String[] result = new String[IttiaApp.TEXT_AREA_TITLES.length];
        StringBuilder[] builders = new StringBuilder[result.length];
        for (int i = 0; i < builders.length; i++) {
            builders[i] = new StringBuilder();
        }

        // Maps section titles to their corresponding index in the `areas` list
        Map<String, Integer> titleToIndexMap = new HashMap<>();
        // Note: The index corresponds to the order in IttiaApp.TEXT_AREA_TITLES
        titleToIndexMap.put("Chief Complaint>", 0);
        titleToIndexMap.put("History of Present Illness>", 1);
        // ROS> (index 2) is not in the sample
        titleToIndexMap.put("Past Medical History>", 3);
        titleToIndexMap.put("Medications", 3); // Merged into PMH
        titleToIndexMap.put("Allergies", 3);   // Merged into PMH
        titleToIndexMap.put("Family History", 3); // Merged into PMH
        titleToIndexMap.put("Social History>", 4);
        titleToIndexMap.put("Investigations", 5);  // Corresponds to O>
        titleToIndexMap.put("Examination", 6);     // Corresponds to Physical Exam>
        titleToIndexMap.put("Assessment>", 7);
        titleToIndexMap.put("Plan>", 8);
        titleToIndexMap.put("Comments>", 9);

        int currentIndex = -1; // No section selected initially
        String[] lines = SAMPLE.split("\r?\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (titleToIndexMap.containsKey(trimmed)) {
                // This line is a header. Switch the current index.
                currentIndex = titleToIndexMap.get(trimmed);
                // Optionally add the header to the text area if it's not the main title
                if (builders[currentIndex].length() > 0) {
                     builders[currentIndex].append("\n\n");
                }
                 // Add sub-headers like "Medications", "Allergies", etc.
                if (!trimmed.endsWith(">")) {
                   builders[currentIndex].append(trimmed).append("\n");
                }
            } else if (currentIndex != -1) {
                // This is a content line. Append it to the current builder.
                if(builders[currentIndex].length() > 0 && !builders[currentIndex].toString().endsWith("\n")) {
                    builders[currentIndex].append("\n");
                }
                builders[currentIndex].append(line);
            }
        }

        // Convert StringBuilders to the final String array
        for (int i = 0; i < builders.length; i++) {
            result[i] = builders[i].toString().trim();
        }
        return result;
    }
}