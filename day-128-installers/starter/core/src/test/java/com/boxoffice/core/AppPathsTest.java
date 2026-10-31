package com.boxoffice.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AppPathsTest {

    @Test
    void windowsUsesLocalAppData() {
        assertThat(AppPaths.dataFolder("Windows 11", Map.of("LOCALAPPDATA", "C:\\Users\\wanjiku\\AppData\\Local"),
                "C:\\Users\\wanjiku"))
                .isEqualTo("C:\\Users\\wanjiku\\AppData\\Local\\BoxOffice");
    }

    @Test
    void windowsWithoutLocalAppDataFallsBackToTheHomeFolder() {
        assertThat(AppPaths.dataFolder("Windows 10", Map.of(), "C:\\Users\\otieno"))
                .isEqualTo("C:\\Users\\otieno\\AppData\\Local\\BoxOffice");
    }

    @Test
    void macUsesApplicationSupport() {
        assertThat(AppPaths.dataFolder("Mac OS X", Map.of(), "/Users/wanjiku"))
                .isEqualTo("/Users/wanjiku/Library/Application Support/BoxOffice");
    }

    @Test
    void linuxUsesTheXdgDataFolder() {
        assertThat(AppPaths.dataFolder("Linux", Map.of(), "/home/wanjiku"))
                .isEqualTo("/home/wanjiku/.local/share/boxoffice");
        assertThat(AppPaths.dataFolder("Linux", Map.of("XDG_DATA_HOME", "/data/wanjiku"), "/home/wanjiku"))
                .isEqualTo("/data/wanjiku/boxoffice");
    }

    @Test
    void aRelativeXdgDataHomeIsIgnored() {
        // The XDG rule says to treat a relative path as if it weren't set.
        assertThat(AppPaths.dataFolder("Linux", Map.of("XDG_DATA_HOME", "data"), "/home/wanjiku"))
                .isEqualTo("/home/wanjiku/.local/share/boxoffice");
    }

    @Test
    void runFromAJarTheDatabaseStaysInTheCurrentFolder() {
        assertThat(AppPaths.defaultDatabase("Linux", Map.of(), "/home/wanjiku", false)).isEqualTo("gate.db");
        assertThat(AppPaths.defaultDatabase("Windows 11", Map.of(), "C:\\Users\\wanjiku", false)).isEqualTo("gate.db");
    }

    @Test
    void installedTheDatabaseGoesInTheDataFolder() {
        assertThat(AppPaths.defaultDatabase("Linux", Map.of(), "/home/wanjiku", true))
                .isEqualTo("/home/wanjiku/.local/share/boxoffice/gate.db");
        assertThat(AppPaths.defaultDatabase("Windows 11", Map.of("LOCALAPPDATA", "C:\\Users\\w\\AppData\\Local"),
                "C:\\Users\\w", true))
                .isEqualTo("C:\\Users\\w\\AppData\\Local\\BoxOffice\\gate.db");
        assertThat(AppPaths.defaultDatabase("Mac OS X", Map.of(), "/Users/w", true))
                .isEqualTo("/Users/w/Library/Application Support/BoxOffice/gate.db");
    }

    @Test
    void theseTestsAreNotInstalled() {
        assertThat(AppPaths.installed()).isFalse();
    }
}
