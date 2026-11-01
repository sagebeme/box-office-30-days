package com.boxoffice.core.update;

import static org.assertj.core.api.Assertions.assertThat;

import com.boxoffice.core.Event;
import com.boxoffice.core.NairobiTime;
import com.boxoffice.core.SampleEvent;
import com.boxoffice.core.release.SemVer;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UpdateCheckerTest {

    private static final SemVer ONE_O = SemVer.parse("1.0.0");

    private static Asset asset(String name) {
        return new Asset(name, URI.create("https://example.test/" + name), 1000);
    }

    private static Release release(String version, String... files) {
        return new Release(SemVer.parse(version), "Notes", List.of(files).stream().map(UpdateCheckerTest::asset).toList());
    }

    private static UpdateCheck check(Optional<Release> latest, String os) {
        return new UpdateChecker(() -> latest, ONE_O, os).check();
    }

    private static final Release ONE_ONE = release("v1.1.0", "boxoffice_1.1.0_amd64.deb", "BoxOffice-1.1.0.msi",
            "BoxOffice-1.1.0.dmg", "SHA256SUMS");

    @Test
    void aNewerReleaseWithAnInstallerForThisSystem() {
        assertThat(check(Optional.of(ONE_ONE), "Linux"))
                .isEqualTo(new UpdateCheck.Available(ONE_O, ONE_ONE, asset("boxoffice_1.1.0_amd64.deb")));
        assertThat(((UpdateCheck.Available) check(Optional.of(ONE_ONE), "Windows 11")).installer().name())
                .isEqualTo("BoxOffice-1.1.0.msi");
        assertThat(((UpdateCheck.Available) check(Optional.of(ONE_ONE), "Mac OS X")).installer().name())
                .isEqualTo("BoxOffice-1.1.0.dmg");
    }

    @Test
    void theSameOrAnOlderVersionIsUpToDate() {
        assertThat(check(Optional.of(release("v1.0.0", "a.deb")), "Linux")).isEqualTo(new UpdateCheck.UpToDate(ONE_O));
        assertThat(check(Optional.of(release("v0.9.0", "a.deb")), "Linux")).isEqualTo(new UpdateCheck.UpToDate(ONE_O));
    }

    @Test
    void versionsCompareAsNumbers() {
        UpdateCheck check = new UpdateChecker(() -> Optional.of(release("v1.10.0", "a.deb")), SemVer.parse("1.9.0"),
                "Linux").check();

        assertThat(check).isInstanceOf(UpdateCheck.Available.class);
    }

    @Test
    void noReleasesYet() {
        assertThat(check(Optional.empty(), "Linux")).isEqualTo(new UpdateCheck.NoReleases(ONE_O));
    }

    @Test
    void aReleaseWithNothingForThisSystem() {
        Release windowsOnly = release("v1.1.0", "BoxOffice-1.1.0.msi");

        assertThat(check(Optional.of(windowsOnly), "Linux")).isEqualTo(new UpdateCheck.NothingForThisSystem(ONE_O, windowsOnly));
    }

    @Test
    void noUpdatesFromTwoHoursBeforeTheDoorsUntilTheEnd() {
        Event night = SampleEvent.kileleNights();

        assertThat(UpdateChecker.duringTheEvent(night, NairobiTime.at(2026, 11, 14, 15, 59).toInstant())).isFalse();
        assertThat(UpdateChecker.duringTheEvent(night, NairobiTime.at(2026, 11, 14, 16, 0).toInstant())).isTrue();
        assertThat(UpdateChecker.duringTheEvent(night, NairobiTime.at(2026, 11, 14, 21, 0).toInstant())).isTrue();
        assertThat(UpdateChecker.duringTheEvent(night, NairobiTime.at(2026, 11, 15, 3, 59).toInstant())).isTrue();
        assertThat(UpdateChecker.duringTheEvent(night, NairobiTime.at(2026, 11, 15, 4, 0).toInstant())).isFalse();
    }
}
