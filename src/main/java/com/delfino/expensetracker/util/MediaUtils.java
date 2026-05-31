package com.delfino.expensetracker.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utility class for media type detection and PDF processing.
 */
public final class MediaUtils {

    private static final Logger log = LoggerFactory.getLogger(MediaUtils.class);

    private MediaUtils() {}

    public static String detectMediaType(byte[] bytes) {
        if (bytes.length >= 4
                && bytes[0] == 0x25 && bytes[1] == 0x50
                && bytes[2] == 0x44 && bytes[3] == 0x46) {
            return "application/pdf";
        }
        if (bytes.length >= 3
                && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) {
            return "image/jpeg";
        }
        if (bytes.length >= 8
                && bytes[0] == (byte) 0x89 && bytes[1] == 0x50) {
            return "image/png";
        }
        throw new IllegalArgumentException("Cannot detect media type from file bytes");
    }

    public static List<byte[]> convertPdfToImages(byte[] pdfBytes) throws IOException {
        List<byte[]> pages = new ArrayList<>();
        try (PDDocument doc = PDDocument.load(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            int pageCount = doc.getNumberOfPages();
            for (int p = 0; p < pageCount; p++) {
                BufferedImage bim = renderer.renderImageWithDPI(p, 300, ImageType.RGB);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(bim, "jpg", baos);
                pages.add(baos.toByteArray());
            }
        }
        return pages;
    }

    public static String extractPdfText(byte[] pdfBytes) {
        try (PDDocument doc = PDDocument.load(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            return text != null ? text.trim() : "";
        } catch (Exception e) {
            log.warn("PDF text extraction failed: {}", e.getMessage());
            return "";
        }
    }

    public static boolean hasUsableText(String text) {
        if (text == null || text.isBlank()) return false;

        String stripped = text.replaceAll("\\s+", " ").trim();
        if (stripped.length() < 50) return false;

        long wordCount = Arrays.stream(stripped.split("\\s+"))
                .filter(w -> w.matches(".*[a-zA-Z0-9].*"))
                .count();
        if (wordCount < 5) return false;

        if (!stripped.matches(".*\\d+.*")) return false;

        long printable = stripped.chars().filter(c -> c >= 32 && c < 127).count();
        double ratio = (double) printable / stripped.length();
        return ratio >= 0.85;
    }
}

