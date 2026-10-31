package com.boxoffice.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BoxOfficeTest {

    @Test
    void welcomesTheEventByName() {
        assertThat(BoxOffice.welcome("Kilele Nights"))
                .isEqualTo("Box Office is open for Kilele Nights");
    }

    @Test
    void ignoresSpacesAroundTheName() {
        assertThat(BoxOffice.welcome("  Kilele Nights  "))
                .isEqualTo("Box Office is open for Kilele Nights");
    }

    @Test
    void refusesAnEventWithNoName() {
        assertThatThrownBy(() -> BoxOffice.welcome("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("An event needs a name");
    }
}
