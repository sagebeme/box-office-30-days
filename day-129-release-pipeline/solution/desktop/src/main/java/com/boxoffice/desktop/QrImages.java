package com.boxoffice.desktop;

import com.boxoffice.core.QrCodes;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

/** Paints a QR code from core as a JavaFX image, big enough to scan off a phone or a screen. */
final class QrImages {

    private QrImages() {
    }

    /** Each square of the code becomes scale × scale pixels. */
    static WritableImage of(String text, int scale) {
        boolean[][] grid = QrCodes.encode(text);
        int size = grid.length * scale;
        WritableImage image = new WritableImage(size, size);
        PixelWriter pixels = image.getPixelWriter();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                pixels.setColor(x, y, grid[y / scale][x / scale] ? Color.BLACK : Color.WHITE);
            }
        }
        return image;
    }
}
