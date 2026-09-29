package com.kbms.document;

import java.util.Locale;

public enum FileType {
    PDF,
    DOCX,
    TXT,
    MARKDOWN;

    /** Resolves from the extension only; the client-supplied MIME type is never trusted. */
    public static FileType fromFilename(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return PDF;
        }
        if (lower.endsWith(".docx")) {
            return DOCX;
        }
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return MARKDOWN;
        }
        if (lower.endsWith(".txt")) {
            return TXT;
        }
        return null;
    }

    public String extension() {
        return this == MARKDOWN ? "md" : name().toLowerCase(Locale.ROOT);
    }

    /** Tika content type used for detection, not a trust boundary. */
    public String tikaContentType() {
        return switch (this) {
            case PDF -> "application/pdf";
            case DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case TXT -> "text/plain";
            case MARKDOWN -> "text/markdown";
        };
    }
}
