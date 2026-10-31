package com.boxoffice.core;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.util.Map;

/**
 * Turns a ticket code into a QR code: a grid of dark and light squares that
 * the gate's USB scanner reads back as the same text. Kept as plain booleans,
 * so core doesn't need JavaFX; the desktop app paints them.
 */
public final class QrCodes {

    private QrCodes() {
    }

    /** grid[y][x] is true for a dark square. Includes a one-square white border. */
    public static boolean[][] encode(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("A QR code needs some text");
        }
        try {
            // Width and height 0 mean "the smallest grid that fits": one array cell per square.
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.MARGIN, 1));
            boolean[][] grid = new boolean[matrix.getHeight()][matrix.getWidth()];
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    grid[y][x] = matrix.get(x, y);
                }
            }
            return grid;
        } catch (WriterException e) {
            throw new IllegalArgumentException("Can't make a QR code from " + text, e);
        }
    }
}
