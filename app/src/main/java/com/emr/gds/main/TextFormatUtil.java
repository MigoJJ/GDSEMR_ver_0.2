// TextFormatUtil.java
package com.emr.gds.main;

import javafx.scene.control.TextFormatter;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * A utility class containing static methods for text formatting and manipulation.
 * This class cannot be instantiated.
 */
public final class TextFormatUtil {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private TextFormatUtil() {}

    /**
     * Normalizes a line of text by trimming it and collapsing multiple whitespace characters into a single space.
     * @param s The string to normalize.
     * @return The normalized string, or an empty string if the input is null.
     */
    public static String normalizeLine(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    /**
     * Processes a block of text, removing duplicate lines while preserving the order of the first occurrence.
     * @param text The input text, potentially with multiple lines.
     * @return A string containing only the unique lines from the input, joined by newlines.
     */
    public static String getUniqueLines(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        // Use a LinkedHashSet to maintain insertion order while ensuring uniqueness.
        Set<String> uniqueLines = new LinkedHashSet<>();
        text.lines()
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .forEach(uniqueLines::add);
        return String.join("\n", uniqueLines);
    }

    /**
     * Provides a UnaryOperator to filter out unwanted ASCII control characters from TextFormatter changes.
     * Allows Tab (U+0009) and Line Feed (U+000A).
     * @return A UnaryOperator for use in a TextFormatter.
     */
    public static UnaryOperator<TextFormatter.Change> filterControlChars() {
        return change -> {
            String text = change.getText();
            if (text == null || text.isEmpty()) return change;
            // Block ASCII control chars except TAB (U+0009) and LF (U+000A).
            String filtered = text.replaceAll("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]", "");
            change.setText(filtered);
            return change;
        };
    }

    /**
     * Formats raw text by normalizing bullets, collapsing blank lines, and trimming spaces.
     * @param raw The raw input string.
     * @return The auto-formatted string.
     */
    public static String autoFormat(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String[] lines = raw.replace("\r", "").split("\n", -1);
        StringBuilder out = new StringBuilder();
        boolean lastBlank = false;

        for (String line : lines) {
            String t = line.strip();
            // Normalize bullets to "- "
            t = t.replaceAll("^[•·→▶▷‣⦿∘*]+\\s*", "- ");
            t = t.replaceAll("^[-]{1,2}\\s*", "- ");
            // trim trailing spaces
            t = t.replaceAll("\\s+$", "");

            if (t.isEmpty()) {
                if (!lastBlank) {
                    out.append("\n");
                    lastBlank = true;
                }
            } else {
                out.append(t).append("\n");
                lastBlank = false;
            }
        }
        return out.toString().strip();
    }

    /**
     * Performs a final formatting pass for EMR export.
     * Ensures headers start with '# ' and normalizes newlines between sections.
     * @param raw The string to finalize.
     * @return The finalized, clean string for export.
     */
    public static String finalizeForEMR(String raw) {
        String s = autoFormat(raw);
        // Ensure markdown-like headers start with '# '
        s = s.replaceAll("^(#+)([^#\\n])", "$1 $2");
        // Guarantee single blank line between sections
        s = s.replaceAll("\\n{3,}", "\n\n");
        return s.trim();
    }
}