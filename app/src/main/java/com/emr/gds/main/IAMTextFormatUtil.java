package com.emr.gds.main;

import javafx.scene.control.TextFormatter;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Utility class for text formatting and manipulation operations.
 * <p>
 * This class provides methods for normalization, duplicate line removal,
 * bullet point standardization, whitespace cleanup, and EMR-safe final formatting.
 * It is a final class and cannot be instantiated.
 */
public final class IAMTextFormatUtil {

    /**
     * Private constructor to prevent instantiation.
     */
    private IAMTextFormatUtil() {}

    // ================================ 
    // Basic String Normalization
    // ================================

    /**
     * Normalizes a single line by trimming leading/trailing whitespace and collapsing multiple internal spaces into one.
     *
     * @param s The input string.
     * @return The normalized string, or an empty string if the input is null.
     */
    public static String normalizeLine(String s) {
        return (s == null) ? "" : s.trim().replaceAll("\\s+", " ");
    }

    // ================================ 
    // Line-Level Processing
    // ================================

    /**
     * Removes duplicate lines from a block of text while preserving the order of the first occurrence of each line.
     *
     * @param text The input text, which may contain duplicate lines.
     * @return A string with unique, trimmed lines separated by newlines.
     */
    public static String getUniqueLines(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.lines()
                   .map(String::trim)
                   .filter(line -> !line.isEmpty())
                   .collect(Collectors.toCollection(LinkedHashSet::new))
                   .stream()
                   .collect(Collectors.joining("\n"));
    }

    // ================================ 
    // Input Filtering for JavaFX
    // ================================

    /**
     * Creates a UnaryOperator for a JavaFX TextFormatter that filters out unwanted ASCII control characters.
     * It allows only Tab (U+0009) and Line Feed (U+000A) to pass through.
     *
     * @return A filter suitable for use in a {@link TextFormatter}.
     */
    public static UnaryOperator<TextFormatter.Change> filterControlChars() {
        return change -> {
            if (change.isAdded()) {
                String text = change.getText();
                if (text != null && !text.isEmpty()) {
                    // Remove ASCII control characters except for tab and newline
                    String filtered = text.replaceAll("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]", "");
                    change.setText(filtered);
                }
            }
            return change;
        };
    }

    // ================================ 
    // Advanced Formatting Utilities
    // ================================

    /**
     * Cleans and normalizes a block of raw text by applying several formatting rules:
     * <ul>
     *   <li>Normalizes various bullet point symbols (•, ·, *, etc.) to a standard "- ".</li>
     *   <li>Collapses multiple consecutive blank lines into a single blank line.</li>
     *   <li>Trims trailing whitespace from each line.</li>
     * </ul>
     *
     * @param raw The unprocessed input text.
     * @return A cleaned and consistently formatted version of the text.
     */
    public static String autoFormat(String raw) {
        if (raw == null || raw.isBlank()) return "";

        StringBuilder out = new StringBuilder();
        boolean lastLineWasBlank = false;

        for (String line : raw.replace("\r", "").split("\n")) {
            String trimmedLine = line.strip();

            if (trimmedLine.isEmpty()) {
                if (!lastLineWasBlank) {
                    out.append("\n");
                    lastLineWasBlank = true;
                }
            } else {
                // Standardize bullet points
                String formattedLine = trimmedLine.replaceAll("^[•·→▶▷‣⦿∘*]+\\s*", "- ");
                // Standardize hyphens used as bullets
                if (formattedLine.matches("^[-]{1,2}\\s*.*") && !formattedLine.startsWith("- ")) {
                    formattedLine = formattedLine.replaceAll("^[-]{1,2}\\s*", "- ");
                }

                out.append(formattedLine).append("\n");
                lastLineWasBlank = false;
            }
        }
        return out.toString().strip();
    }

    /**
     * Finalizes a block of text for EMR export by ensuring it meets specific formatting standards.
     * <ul>
     *   <li>Ensures headers follow a Markdown-like style (e.g., "# Header").</li>
     *   <li>Guarantees a single blank line between sections.</li>
     *   <li>Trims any leading or trailing whitespace from the final block.</li>
     * </ul>
     *
     * @param raw The processed or raw text to be finalized.
     * @return A clean, export-ready string.
     */
    public static String finalizeForEMR(String raw) {
        String formatted = autoFormat(raw);
        // Ensure headers have a space after the # symbol (e.g., #Header -> # Header)
        formatted = formatted.replaceAll("^(#+)([^#\\s\\n])", "$1 $2");
        // Collapse more than two newlines into exactly two, ensuring a single blank line between paragraphs
        formatted = formatted.replaceAll("\\n{3,}", "\\n\\n");
        return formatted.trim();
    }
}
