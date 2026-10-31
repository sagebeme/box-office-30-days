package com.boxoffice.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.boxoffice.core.release.SemVer;
import org.junit.jupiter.api.Test;

class BoxOfficeVersionTest {

    @Test
    void knowsItsOwnVersion() {
        // The version in pom.xml, filled in by Maven: change the pom and this follows.
        assertThat(BoxOffice.version()).isEqualTo("1.0.0");
        assertThat(SemVer.parse(BoxOffice.version())).isEqualTo(new SemVer(1, 0, 0));
    }
}
