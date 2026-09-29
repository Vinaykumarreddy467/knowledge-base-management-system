package com.kbms.index;

import java.util.Locale;
import java.util.regex.Pattern;

/** Text normalisation that keeps headings, lists and code intact. */
final class TextCleanup {

    private static final Pattern EXCESS_BLANK_LINES = Pattern.compile("\\n{3,}");
    private static final Pattern TRAILING_SPACES = Pattern.compile("[ \\t]+\\n");

    private TextCleanup() {}

    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        text = TRAILING_SPACES.matcher(text).replaceAll("\n");
        text = EXCESS_BLANK_LINES.matcher(text).replaceAll("\n\n");
        // Tika emits a form feed per PDF page; keep it as a page break marker.
        return text.replace('\f', '\n').strip();
    }

    /** Single-line form for embedding, where layout carries no signal. */
    static String flatten(String text) {
        return text.replace("\n", " ").replaceAll("\\s{2,}", " ").strip();
    }

    static String truncate(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxChars ? value : value.substring(0, maxChars - 1) + "\u2026";
    }

    static String guessTitle(String filename) {
        String name = filename.replaceFirst("(?i)\\.(pdf|docx|txt|md|markdown)$", "");
        name = name.replaceAll("[_\\-]+", " ").replaceAll("\\s+", " ").strip();
        return name.isEmpty() ? filename : name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }
}
