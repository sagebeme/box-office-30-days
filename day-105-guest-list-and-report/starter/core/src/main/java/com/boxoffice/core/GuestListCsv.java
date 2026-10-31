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
                // TODO: on line 1, drop the BYTE_ORDER_MARK if the line starts with it
                // TODO: skip blank lines (continue)
                // TODO: the first line that isn't blank must be the HEADER (ignore case and spaces);
                //       if it isn't -> throw new GuestListException(lineNumber, "the first line should be " + HEADER)
                // TODO: every other line: split on "," (use line.split(",", -1) so an empty last column still counts)
                //   - not exactly 2 columns -> "expected 2 columns (" + HEADER + "), found " + columns.length
                //   - strip both; upper-case the type with toUpperCase(Locale.ROOT)
                //   - empty name -> "a guest needs a name"; empty type -> "a guest needs a ticket type"
                //   - otherwise add new Guest(name, type)
                throw new UnsupportedOperationException("Not written yet: Day 105, step 3");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return guests;
    }
}
