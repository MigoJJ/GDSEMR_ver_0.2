package com.emr.gds.main;
import javafx.scene.control.TextFormatter;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.UnaryOperator;
/**
 * Utility class for text formatting and manipulation operations.
 * 
 * <p>This class provides methods for normalization, duplicate line removal,
 * bullet/whitespace cleanup, and EMR-safe final formatting. It cannot be instantiated.</p>
 */
public final class IAMTextFormatUtil {
    /** Prevent instantiation. */
    private IAMTextFormatUtil() {}
    // -------------------------------------------------------------------------
    // Basic String Normalization
    // -------------------------------------------------------------------------
    /**
     * Normalizes a single line by trimming it and collapsing multiple spaces into one.
     *
     * @param s The input string.
     * @return The normalized string, or an empty string if null.
     */
    public static String normalizeLine(String s) {
        return (s == null) ? "" : s.trim().replaceAll("\\s+", " ");
    }
    // -------------------------------------------------------------------------
    // Line-Level Processing
    // -------------------------------------------------------------------------
    /**
     * Removes duplicate lines while preserving the order of first occurrences.
     *
     * @param text The input text, possibly with duplicates/multiple lines.
     * @return A string with unique, trimmed lines separated by newlines.
     */
    public static String getUniqueLines(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        Set<String> uniqueLines = new LinkedHashSet<>();
        text.lines()
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .forEach(uniqueLines::add);
        return String.join("\n", uniqueLines);
    }
    // -------------------------------------------------------------------------
    // Input Filtering (for JavaFX TextFormatter)
    // -------------------------------------------------------------------------
    /**
     * Filters out unwanted ASCII control characters, allowing only TAB (U+0009) and LF (U+000A).
     *
     * @return A filter suitable for use in a {@link TextFormatter}.
     */
    public static UnaryOperator<TextFormatter.Change> filterControlChars() {
        return change -> {
            String text = change.getText();
            if (text == null || text.isEmpty()) return change;
            // Remove unwanted ASCII control chars [\u0000 - \u001F] excluding \t and \n
            String filtered = text.replaceAll("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]", "");
            change.setText(filtered);
            return change;
        };
    }
    // -------------------------------------------------------------------------
    // Formatting Utilities
    // -------------------------------------------------------------------------
    /**
     * Cleans and normalizes raw text:
     * <ul>
     *   <li>Normalizes bullet points to "- "</li>
     *   <li>Collapses multiple blank lines</li>
     *   <li>Trims trailing spaces</li>
     * </ul>
     *
     * @param raw The unprocessed input text.
     * @return A cleaned and consistently formatted version of the text.
     */
    public static String autoFormat(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String[] lines = raw.replace("\r", "").split("\n", -1);
        StringBuilder out = new StringBuilder();
        boolean lastBlank = false;
        
        for (String line : lines) {
            String t = line.strip();
            
            // 빈 줄 처리
            if (t.isEmpty()) {
                if (!lastBlank) {
                    out.append("\n");
                    lastBlank = true;
                }
                continue; // 빈 줄은 여기서 처리 완료
            }
            
            // 불릿 포인트 정규화: 다양한 기호들을 "- "로 통일
            t = t.replaceAll("^[•·→▶▷‣⦿∘*]+\\s*", "- ");
            
            // 대시 정규화: 이미 올바른 형태("- ")가 아닌 경우에만 처리
            if (t.matches("^[-]{1,2}\\s*.*") && !t.startsWith("- ")) {
                t = t.replaceAll("^[-]{1,2}\\s*", "- ");
            }
            
            // 후행 공백 제거
            t = t.replaceAll("\\s+$", "");
            
            // 내용이 있는 줄 추가
            out.append(t).append("\n");
            lastBlank = false;
        }
        
        return out.toString().strip();
    }
    /**
     * Finalizes text for EMR export:
     * <ul>
     *   <li>Ensures headers follow Markdown-style ("# Header")</li>
     *   <li>Guarantees a single blank line between sections</li>
     *   <li>Trims leading/trailing spaces</li>
     * </ul>
     *
     * @param raw The processed or raw text.
     * @return Clean, export-ready text.
     */
    public static String finalizeForEMR(String raw) {
        String s = autoFormat(raw);
        // Ensure headers have a space after "#"
        s = s.replaceAll("^(#+)([^#\\n])", "$1 $2");
        // Collapse excessive blank lines to just 2
        s = s.replaceAll("\\n{3,}", "\n\n");
        return s.trim();
    }
}