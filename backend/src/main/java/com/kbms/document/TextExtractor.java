package com.kbms.document;

import com.kbms.common.ApiException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;

/**
 * Text extraction for PDF, DOCX, TXT and Markdown via Apache Tika.
 *
 * <p>No OCR: a scanned PDF yields no text and is reported as a failure rather than indexed as empty.
 */
@Component
public class TextExtractor {

    private static final int MAX_EXTRACTED_CHARS = 2_000_000;

    private final Parser parser = new AutoDetectParser();

    public String extract(byte[] content, FileType declaredType) {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "upload." + declaredType.extension());
        metadata.set(Metadata.CONTENT_TYPE, declaredType.tikaContentType());
        try {
            BodyContentHandler handler = new BodyContentHandler(MAX_EXTRACTED_CHARS);
            parser.parse(new ByteArrayInputStream(content), handler, metadata, new ParseContext());
            return handler.toString();
        } catch (org.apache.tika.exception.EncryptedDocumentException ex) {
            throw ApiException.badRequest("The document is password protected and cannot be read");
        } catch (IOException | org.xml.sax.SAXException | org.apache.tika.exception.TikaException ex) {
            throw ApiException.badRequest("The document could not be read: " + ex.getMessage());
        }
    }
}
