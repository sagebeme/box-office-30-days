package com.boxoffice.desktop;

import com.boxoffice.core.Event;
import com.boxoffice.core.Receipt;
import com.boxoffice.core.Ticket;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Window;

/**
 * Prints a receipt on whatever printer the laptop has: the till printer at
 * the gate, or any office printer. JavaFX prints a Node the same way it draws
 * one on screen, so the receipt is just a small layout of labels and the QR.
 */
final class ReceiptPrinter {

    private ReceiptPrinter() {
    }

    /** The receipt as something JavaFX can draw (or print). */
    static Node layout(Event event, Ticket ticket) {
        VBox receipt = new VBox(2);
        receipt.setStyle("-fx-background-color: white; -fx-padding: 8;");
        for (String line : Receipt.forTicket(event, ticket)) {
            Label label = new Label(line);
            label.setFont(Font.font("Monospaced", 10));
            receipt.getChildren().add(label);
        }
        receipt.getChildren().add(new ImageView(QrImages.of(ticket.code(), 3)));
        return receipt;
    }

    /**
     * Shows the system's print dialog and prints. Returns what to tell the
     * staff: printing can't be tested automatically, so the message says what happened.
     */
    static String print(Event event, Ticket ticket, Window owner) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            return "No printer found. Save a PDF instead (Ctrl+S).";
        }
        if (!job.showPrintDialog(owner)) {
            return "Printing cancelled";
        }
        if (job.printPage(layout(event, ticket))) {
            job.endJob();
            return "Printed " + ticket.code();
        }
        return "The printer didn't take the receipt";
    }
}
