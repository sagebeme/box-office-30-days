package com.boxoffice.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

class GuestListCsvTest {

    private static List<Guest> parse(String csv) {
        return GuestListCsv.parse(new StringReader(csv));
    }

    @Test
    void readsOneGuestPerLine() {
        assertThat(parse("""
                name,type
                Wanjiku Kamau,VIP
                Otieno Odhiambo,REG
                """)).containsExactly(
                new Guest("Wanjiku Kamau", "VIP"),
                new Guest("Otieno Odhiambo", "REG"));
    }

    @Test
    void tidiesSpacesAndCapitalisesTheType() {
        assertThat(parse("name,type\n  Achieng ,  vip \n"))
                .containsExactly(new Guest("Achieng", "VIP"));
    }

    @Test
    void skipsBlankLines() {
        assertThat(parse("name,type\n\nAchieng,VIP\n\n")).hasSize(1);
    }

    @Test
    void copesWithTheMarkExcelPutsAtTheStart() {
        assertThat(parse("﻿name,type\nAchieng,VIP\n"))
                .containsExactly(new Guest("Achieng", "VIP"));
    }

    @Test
    void acceptsAHeaderInCapitals() {
        assertThat(parse("Name,Type\nAchieng,VIP\n")).hasSize(1);
    }

    @Test
    void anEmptyListHasNoGuests() {
        assertThat(parse("name,type\n")).isEmpty();
    }

    @Test
    void refusesAFileWithoutTheHeader() {
        assertThatThrownBy(() -> parse("Achieng,VIP\n"))
                .isInstanceOf(GuestListException.class)
                .hasMessage("Line 1: the first line should be name,type");
    }

    @Test
    void namesTheLineWithTheWrongNumberOfColumns() {
        assertThatThrownBy(() -> parse("name,type\nAchieng,VIP\nKamau, John,REG\n"))
                .isInstanceOf(GuestListException.class)
                .hasMessage("Line 3: expected 2 columns (name,type), found 3");
    }

    @Test
    void refusesAGuestWithNoName() {
        assertThatThrownBy(() -> parse("name,type\n ,VIP\n"))
                .isInstanceOf(GuestListException.class)
                .hasMessage("Line 2: a guest needs a name");
    }

    @Test
    void refusesAGuestWithNoType() {
        assertThatThrownBy(() -> parse("name,type\nAchieng,\n"))
                .isInstanceOf(GuestListException.class)
                .hasMessage("Line 2: a guest needs a ticket type");
    }
}
