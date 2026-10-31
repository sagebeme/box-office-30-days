package com.boxoffice.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Writes receipts and reports as PDF files: something the organiser can
 * email, print anywhere, or keep, when the gate has no printer at all.
 *
 * <p>PDF measures in points: 72 to an inch. The page's (0, 0) is the
 * bottom-left corner, so text goes down the page as y gets smaller.
 */
public final class PdfExport {

    // An 80 mm wide till roll, cut to 140 mm.
    static final PDRectangle RECEIPT_PAGE = new PDRectangle(mm(80), mm(140));

    private static final float MARGIN = mm(6);
    private static final float LEADING = 12;

    private PdfExport() {
    }

    /** One receipt per page, with the ticket's QR code under the words. */
    public static void writeReceipt(Event event, Ticket ticket, Path file) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(RECEIPT_PAGE);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float top = RECEIPT_PAGE.getHeight() - MARGIN;
                float bottomOfText = writeLines(content, Receipt.forTicket(event, ticket), 8.5f, top);
                drawQr(content, QrCodes.encode(ticket.code()), MARGIN, bottomOfText - LEADING, mm(30));
            }
            document.save(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't write " + file, e);
        }
    }

    /** The night's sales report on an A4 page, in a mono font so the columns line up. */
    public static void writeReport(SalesReport report, Path file) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float top = PDRectangle.A4.getHeight() - mm(20);
                writeLines(content, report.render().lines().toList(), 11, top);
            }
            document.save(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't write " + file, e);
        }
    }

    private static final PDType1Font COURIER = new PDType1Font(Standard14Fonts.FontName.COURIER);

    /**
     * The built-in PDF fonts only know Western European letters, so a name
     * like "Wanjikũ" would crash the export. This turns ũ into u, and any other
     * letter Courier can't draw into '?'.
     */
    static String printable(String text) {
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        StringBuilder out = new StringBuilder();
        plain.codePoints().forEach(c -> {
            String letter = Character.toString(c);
            try {
                COURIER.encode(letter);
                out.append(letter);
            } catch (IOException | IllegalArgumentException e) {
                out.append('?');
            }
        });
        return out.toString();
    }

    /** Writes the lines top to bottom from y = top, and returns the y just under the last one. */
    static float writeLines(PDPageContentStream content, List<String> lines, float fontSize, float top)
            throws IOException {
        // TODO: writing text on a PDF page goes like this:
        //   content.beginText();
        //   content.setFont(COURIER, fontSize);
        //   content.setLeading(LEADING);              (the gap from one line to the next)
        //   content.newLineAtOffset(MARGIN, top);     (where the first line starts)
        //   then for each line: content.showText(printable(line)); content.newLine();
        //   (printable, above, swaps letters Courier can't draw, so a name like "Wanjikũ" can't crash it)
        //   content.endText();
        // TODO: return how far down the text reached: top - LEADING * lines.size()
        throw new UnsupportedOperationException("Not written yet: Day 112, step 4");
    }

    /** Draws the QR grid as filled squares, its top-left corner at (left, top). */
    private static void drawQr(PDPageContentStream content, boolean[][] grid, float left, float top, float size)
            throws IOException {
        float square = size / grid.length;
        for (int y = 0; y < grid.length; y++) {
            for (int x = 0; x < grid.length; x++) {
                if (grid[y][x]) {
                    content.addRect(left + x * square, top - (y + 1) * square, square, square);
                }
            }
        }
        content.fill();
    }

    private static float mm(float millimetres) {
        return millimetres / 25.4f * 72;
    }
}
