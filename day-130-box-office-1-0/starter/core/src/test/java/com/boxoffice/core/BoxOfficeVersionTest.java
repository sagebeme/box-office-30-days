package com.boxoffice.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.boxoffice.core.release.SemVer;
import org.junit.jupiter.api.Test;

class BoxOfficeVersionTest {

    @Test
    void knowsItsOwnVersion() {
        // The version in pom.xml, filled in by Maven: change the pom and this follows.
        assertThat(BoxOffice.version()).isEqualTo(System.getProperty("boxoffice.version"));
        assertThat(SemVer.parse(BoxOffice.version())).isNotNull();
    }
}
