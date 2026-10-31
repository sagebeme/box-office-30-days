package com.boxoffice.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfExportTest {

    private static final Instant NINE_PM = Instant.parse("2026-11-14T18:00:00Z");

    @TempDir
    Path folder;

    /** Opens the PDF the way a reader app would, and pulls its text back out. */
    private static String textOf(Path pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    void writesAReceiptAnyPdfReaderCanOpen() throws IOException {
        Path pdf = folder.resolve("VIP-0001.pdf");
        Ticket ticket = new Ticket("VIP-0001", "VIP", "Wanjiku Kamau", Money.ofKes(5_000), NINE_PM);

        PdfExport.writeReceipt(SampleEvent.kileleNights(), ticket, pdf);

        assertThat(Files.readString(pdf, StandardCharsets.ISO_8859_1))
                .startsWith("%PDF-");
        assertThat(textOf(pdf)).contains("KILELE NIGHTS", "Ticket  VIP-0001", "Buyer   Wanjiku Kamau", "Price   KES 5,000");
    }

    @Test
    void aReceiptIsOneTillRollPage() throws IOException {
        Path pdf = folder.resolve("receipt.pdf");

        PdfExport.writeReceipt(SampleEvent.kileleNights(),
                new Ticket("REG-0001", "REG", "Otieno", Money.ofKes(1_500), NINE_PM), pdf);

        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(document.getPage(0).getMediaBox().getWidth()).isEqualTo(PdfExport.RECEIPT_PAGE.getWidth());
        }
    }

    @Test
    void aNameWithKikuyuLettersStillPrints() throws IOException {
        Path pdf = folder.resolve("receipt.pdf");

        PdfExport.writeReceipt(SampleEvent.kileleNights(),
                new Ticket("REG-0001", "REG", "Wanjikũ Mũgo", Money.ofKes(1_500), NINE_PM), pdf);

        assertThat(textOf(pdf)).contains("Buyer   Wanjiku Mugo");
    }

    @Test
    void writesTheSalesReportWithItsColumns() throws IOException {
        Path pdf = folder.resolve("report.pdf");
        SalesReport report = SalesReport.of(SampleEvent.kileleNights(), List.of(
                new Ticket("VIP-0001", "VIP", "Wanjiku", Money.ofKes(5_000), NINE_PM)));

        PdfExport.writeReport(report, pdf);

        assertThat(textOf(pdf)).contains("Kilele Nights", "Takings", "KES 5,000");
    }
}
