package com.boxoffice.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads the organiser's guest list, a CSV file like this:
 *
 * <pre>
 * name,type
 * Wanjiku Kamau,VIP
 * Otieno Odhiambo,REG
 * </pre>
 *
 * Kept simple on purpose: one comma per line, so a name can't contain a comma.
 */
public final class GuestListCsv {

    static final String HEADER = "name,type";

    // Excel puts this invisible character at the very start of a CSV it saves.
    private static final String BYTE_ORDER_MARK = "﻿";

    private GuestListCsv() {
    }

    public static List<Guest> parse(Reader input) {
        List<Guest> guests = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(input)) {
            String line;
            int lineNumber = 0;
            boolean seenHeader = false;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1 && line.startsWith(BYTE_ORDER_MARK)) {
                    line = line.substring(1);
                }
                if (line.isBlank()) {
                    continue;
                }
                if (!seenHeader) {
                    if (!line.strip().equalsIgnoreCase(HEADER)) {
                        throw new GuestListException(lineNumber, "the first line should be " + HEADER);
                    }
                    seenHeader = true;
                    continue;
                }
                String[] columns = line.split(",", -1);
                if (columns.length != 2) {
                    throw new GuestListException(lineNumber,
                            "expected 2 columns (" + HEADER + "), found " + columns.length);
                }
                String name = columns[0].strip();
                String type = columns[1].strip().toUpperCase(Locale.ROOT);
                if (name.isEmpty()) {
                    throw new GuestListException(lineNumber, "a guest needs a name");
                }
                if (type.isEmpty()) {
                    throw new GuestListException(lineNumber, "a guest needs a ticket type");
                }
                guests.add(new Guest(name, type));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return guests;
    }
}
