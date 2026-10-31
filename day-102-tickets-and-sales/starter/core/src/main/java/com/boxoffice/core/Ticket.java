package com.boxoffice.core;

/** A ticket the gate has issued. The code is what gets printed and scanned. */
public record Ticket(String code, String typeCode, String buyer) {
}
