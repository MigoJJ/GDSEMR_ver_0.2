package com.emr.gds.fourgate.vaccine;

/**
 * A final class to hold constants for the vaccine module, primarily for UI elements.
 * This class cannot be instantiated.
 */
public final class VaccineConstants {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private VaccineConstants() {}

    /**
     * An array of strings representing UI elements for the vaccine selection window.
     * Headers are prefixed with "###", and action items are included at the end.
     */
    public static final String[] UI_ELEMENTS = {
            "### Respiratory Vaccines",
            "Sanofi's Vaxigrip® Vaccine(H3N2)",
            "GC Flu Plus (H1N1, H3N2, Victoria lineage)® vaccine [NIP]",
            "Prevena 20 (pneumococcal vaccine (PCV20))",
            "Prevena 13 (pneumococcal vaccine (PCV13))",
            "COVID-19 vaccine (mRNA, viral vector, or protein subunit)",

            "### Travel / Endemic Disease Vaccines",
            "MMR (Measles, Mumps, Rubella)",
            "Varicella (Chickenpox) vaccine",
            "Japanese Encephalitis vaccine",
            "Yellow Fever vaccine",
            "Typhoid vaccine (oral or injectable)",
            "Meningococcal vaccine (MenACWY)",

            "### Occupational / High-Risk Vaccines",
            "HPV vaccine (Gardasil 9, adults up to age 45)",
            "Anthrax vaccine",
            "Smallpox/Mpox vaccine (JYNNEOS)",

            "### Booster / Additional Doses",
            "HAV vaccine #1/2",
            "HBV vaccine #1/3",
            "Shingles Vaccine (Shingrix) #1/2",
            "TdaP (Tetanus, Diphtheria, Pertussis)",
            "Td booster (Tetanus, Diphtheria)",

            "### Regional / Seasonal Vaccines",
            "Cholera vaccine",

            "### Actions",
            "Side Effect",
            "Quit"
    };
}
