package com.boxoffice.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PhoneNumbersTest {

    @Test
    void turnsTheWayPeopleSayItIntoTheWayMpesaWantsIt() {
        assertThat(PhoneNumbers.normalise("0712345678")).contains("254712345678");
    }

    @Test
    void ignoresSpacesAndDashes() {
        assertThat(PhoneNumbers.normalise("0712 345 678")).contains("254712345678");
        assertThat(PhoneNumbers.normalise("0712-345-678")).contains("254712345678");
    }

    @Test
    void acceptsTheCountryCodeWithOrWithoutAPlus() {
        assertThat(PhoneNumbers.normalise("+254 712 345 678")).contains("254712345678");
        assertThat(PhoneNumbers.normalise("254712345678")).contains("254712345678");
    }

    @Test
    void acceptsTheNewerNumbersThatStartWithOne() {
        assertThat(PhoneNumbers.normalise("0110 123 456")).contains("254110123456");
    }

    @Test
    void refusesANumberThatIsTooShortOrTooLong() {
        assertThat(PhoneNumbers.normalise("071234567")).isEmpty();
        assertThat(PhoneNumbers.normalise("07123456789")).isEmpty();
    }

    @Test
    void refusesALandlineOrAForeignNumber() {
        assertThat(PhoneNumbers.normalise("020 123 4567")).isEmpty();
        assertThat(PhoneNumbers.normalise("+44 7700 900123")).isEmpty();
    }

    @Test
    void refusesLettersAndNothing() {
        assertThat(PhoneNumbers.normalise("07I2345678")).isEmpty();
        assertThat(PhoneNumbers.normalise("")).isEmpty();
        assertThat(PhoneNumbers.normalise(null)).isEmpty();
    }
}
