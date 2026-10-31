package com.boxoffice.server;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CallerIpTest {

    @Test
    void withNoProxyTheHeaderIsIgnored() {
        // Anyone can send X-Forwarded-For. With no proxy of ours, it's just a lie to ignore.
        assertThat(CallerIp.of("41.90.2.2", "196.201.214.200", 0)).isEqualTo("41.90.2.2");
    }

    @Test
    void behindOneProxyTheCallerIsTheLastEntryTheProxyAdded() {
        assertThat(CallerIp.of("10.0.0.5", "41.90.2.2", 1)).isEqualTo("41.90.2.2");
    }

    @Test
    void whatTheCallerMadeUpAtTheStartIsSkipped() {
        // The caller sent "X-Forwarded-For: 196.201.214.200"; our proxy added their real address after it.
        assertThat(CallerIp.of("10.0.0.5", "196.201.214.200, 41.90.2.2", 1)).isEqualTo("41.90.2.2");
    }

    @Test
    void behindTwoProxiesSkipTwo() {
        assertThat(CallerIp.of("10.0.0.7", "41.90.2.2, 10.0.0.5", 2)).isEqualTo("41.90.2.2");
    }

    @Test
    void noHeaderMeansWhoeverConnected() {
        assertThat(CallerIp.of("41.90.2.2", null, 1)).isEqualTo("41.90.2.2");
    }

    @Test
    void aShortChainGivesTheFirstEntry() {
        assertThat(CallerIp.of("10.0.0.7", "41.90.2.2", 5)).isEqualTo("41.90.2.2");
    }
}
