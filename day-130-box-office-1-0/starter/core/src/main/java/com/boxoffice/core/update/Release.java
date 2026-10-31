package com.boxoffice.core.update;

import com.boxoffice.core.release.SemVer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** A published version of Box Office, with its notes and files. */
public record Release(SemVer version, String notes, List<Asset> assets) {

    /** The file listing every other file's SHA-256, written by the Day 129 workflow. */
    public static final String CHECKSUMS = "SHA256SUMS";

    public Release {
        assets = List.copyOf(assets);
    }

    public Optional<Asset> asset(String name) {
        return assets.stream().filter(asset -> asset.name().equals(name)).findFirst();
    }

    /** The installer for this system: .msi on Windows, .dmg on a Mac, .deb on Linux. */
    public Optional<Asset> installerFor(String osName) {
        String os = osName.toLowerCase(Locale.ROOT);
        String ending = os.startsWith("windows") ? ".msi" : os.startsWith("mac") ? ".dmg" : ".deb";
        return assets.stream().filter(asset -> asset.name().endsWith(ending)).findFirst();
    }
}
