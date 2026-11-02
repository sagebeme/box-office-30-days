package com.boxoffice.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The workflow only runs on GitHub, after a tag is pushed: a slow, public
 * place to find a typo. These checks catch the ones that matter here first.
 */
class ReleaseWorkflowTest {

    private static String workflow;

    @BeforeAll
    static void read() throws IOException {
        // Tests run in cli/, and the workflow is at the top of the project.
        workflow = Files.readString(Path.of("../.github/workflows/release.yml"));
    }

    @Test
    void runsWhenAVersionTagIsPushed() {
        assertThat(workflow).contains("""
                on:
                  push:
                    tags: ["v*.*.*"]""");
    }

    @Test
    void buildsEachInstallerOnItsOwnSystem() {
        assertThat(workflow)
                .contains("- os: ubuntu-latest\n            type: deb")
                .contains("- os: windows-latest\n            type: msi")
                .contains("- os: macos-latest\n            type: dmg")
                .contains("./mvnw -B package -Pinstaller -Dinstaller.type=${{ matrix.type }}");
    }

    @Test
    void usesTheCoursesJava() {
        assertThat(workflow).contains("distribution: temurin").contains("java-version: \"21\"");
    }

    @Test
    void refusesATagThatDoesntMatchThePom() {
        assertThat(workflow).contains("-Dexpression=project.version").contains("if [ \"v$version\" != \"$GITHUB_REF_NAME\" ]");
    }

    @Test
    void onlyThePublishingJobMayWrite() {
        assertThat(workflow).startsWith("# Day 129").contains("permissions:\n  contents: read\n");
        assertThat(count("contents: write")).isEqualTo(1);
    }

    @Test
    void publishesNotesAndChecksums() {
        assertThat(workflow)
                .contains("release-notes --last \"$last\" --version \"$GITHUB_REF_NAME\" > notes.md")
                .contains("sha256sum * > SHA256SUMS")
                .contains("gh release create \"$GITHUB_REF_NAME\" installers/*")
                .contains("fetch-depth: 0");
    }

    @Test
    void signsOnlyWhenTheSecretIsThere() {
        assertThat(workflow).contains("if: runner.os == 'Windows' && env.WINDOWS_CERTIFICATE != ''")
                .contains("WINDOWS_CERTIFICATE: ${{ secrets.WINDOWS_CERTIFICATE }}")
                .doesNotContain("MIIK");
    }

    @Test
    void everyActionNamesAVersion() {
        Matcher uses = Pattern.compile("uses: (\\S+)").matcher(workflow);
        int actions = 0;
        while (uses.find()) {
            actions++;
            assertThat(uses.group(1)).matches("[\\w-]+/[\\w-]+@v\\d+");
        }
        assertThat(actions).isGreaterThanOrEqualTo(6);
    }

    private static int count(String text) {
        return workflow.split(Pattern.quote(text), -1).length - 1;
    }
}
