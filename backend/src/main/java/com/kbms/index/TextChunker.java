package com.kbms.index;

import com.kbms.config.AppProperties;
import java.util.ArrayList;
import java.util.List;

/** Paragraph-aware fixed-size chunker with overlap. Splits only on code-point boundaries. */
public class TextChunker {

    private final int targetChars;
    private final int overlapChars;

    public TextChunker(AppProperties properties) {
        this(properties.chunking().targetChars(), properties.chunking().overlapChars());
    }

    public TextChunker(int targetChars, int overlapChars) {
        this.targetChars = Math.max(200, targetChars);
        this.overlapChars = Math.max(0, Math.min(overlapChars, this.targetChars - 50));
    }

    public record Chunk(int index, String content, int charStart, int charEnd) {}

    public List<Chunk> split(String text) {
        List<Chunk> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        int position = 0;
        int length = text.length();
        while (position < length) {
            int end = Math.min(position + targetChars, length);
            if (end < length) {
                end = lastSensibleBreak(text, position, end);
            }
            String body = text.substring(position, end).strip();
            if (!body.isEmpty()) {
                chunks.add(new Chunk(chunks.size(), body, position, end));
            }
            if (end >= length) {
                break;
            }
            // Never split a surrogate pair: step back one code point when the boundary lands mid-pair.
            int next = Math.max(position + 1, end - overlapChars);
            if (next > 0 && Character.isHighSurrogate(text.charAt(next - 1))) {
                next--;
            }
            position = next;
        }
        return chunks;
    }

    /** Prefers a paragraph, then a sentence, then a whitespace break near the target end. */
    private int lastSensibleBreak(String text, int start, int end) {
        for (int i = end; i > start + targetChars / 2; i--) {
            char c = text.charAt(i);
            if (c == '\n' && i > start && text.charAt(i - 1) == '\n') {
                return i + 1;
            }
        }
        int sentence = text.lastIndexOf(". ", end);
        if (sentence > start + targetChars / 2) {
            return sentence + 2;
        }
        int space = text.lastIndexOf(' ', end);
        return space > start + targetChars / 2 ? space + 1 : end;
    }
}
