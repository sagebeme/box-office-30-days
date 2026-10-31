package com.boxoffice.desktop;

/** One row of the sales table, already turned into the text the table shows. */
public record SaleRow(String code, String type, String buyer, String price, String time) {
}
