package com.boxoffice.core.update;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChecksumsTest {

    @TempDir
    Path folder;

    @Test
    void fingerprintsAFile() throws IOException {
        // The SHA-256 of "abc" is in the standard that defines SHA-256, so it's a fair test.
        Path file = Files.writeString(folder.resolve("abc.txt"), "abc");

        assertThat(Checksums.sha256(file)).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void oneByteChangesEverything() throws IOException {
        String abc = Checksums.sha256(Files.writeString(folder.resolve("a"), "abc"));
        String abd = Checksums.sha256(Files.writeString(folder.resolve("b"), "abd"));

        assertThat(abd).isNotEqualTo(abc).hasSize(64);
    }

    @Test
    void readsWhatSha256sumWrites() {
        String sums = """
                ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad  boxoffice_1.1.0_amd64.deb
                BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD *BoxOffice-1.1.0.msi
                not a checksum line
                """;

        assertThat(Checksums.parse(sums))
                .containsEntry("boxoffice_1.1.0_amd64.deb", "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
                .containsEntry("BoxOffice-1.1.0.msi", "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
                .hasSize(2);
    }
}
