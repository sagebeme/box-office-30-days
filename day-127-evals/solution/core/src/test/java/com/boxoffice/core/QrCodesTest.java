package com.boxoffice.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.api.Test;

class QrCodesTest {

    /** Reads a grid back the way a scanner would: as a picture, 8 pixels per square. */
    private static String scan(boolean[][] grid) throws Exception {
        int scale = 8;
        int size = grid.length * scale;
        int[] pixels = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                pixels[y * size + x] = grid[y / scale][x / scale] ? 0xFF000000 : 0xFFFFFFFF;
            }
        }
        BinaryBitmap picture = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(size, size, pixels)));
        return new QRCodeReader().decode(picture).getText();
    }

    @Test
    void aScannerReadsBackTheTicketCode() throws Exception {
        assertThat(scan(QrCodes.encode("VIP-0001"))).isEqualTo("VIP-0001");
    }

    @Test
    void isASquareGrid() {
        boolean[][] grid = QrCodes.encode("REG-0042");

        assertThat(grid.length).isGreaterThanOrEqualTo(21);
        assertThat(grid[0]).hasSize(grid.length);
    }

    @Test
    void hasAWhiteBorderSoTheScannerCanFindTheEdges() {
        boolean[][] grid = QrCodes.encode("REG-0042");

        for (boolean square : grid[0]) {
            assertThat(square).isFalse();
        }
    }

    @Test
    void refusesBlankText() {
        assertThatThrownBy(() -> QrCodes.encode(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
