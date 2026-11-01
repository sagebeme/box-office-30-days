package com.boxoffice.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Where Box Office keeps its database. Run with java -jar, that's gate.db in
 * the folder you're in, as it has been since Day 104. Installed (Day 128), the
 * app's own folder may be read-only and is replaced on every update, so the
 * database goes where each system keeps an app's data instead:
 *
 * <pre>
 * Windows   C:\Users\wanjiku\AppData\Local\BoxOffice\gate.db
 * macOS     /Users/wanjiku/Library/Application Support/BoxOffice/gate.db
 * Linux     /home/wanjiku/.local/share/boxoffice/gate.db
 * </pre>
 *
 * The methods take the system name, environment and home folder as
 * arguments, so a test on Linux can check the Windows answer too.
 */
public final class AppPaths {

    public static final String DATABASE = "gate.db";

    private AppPaths() {
    }

    /** The folder an installed Box Office keeps its files in, written for that system. */
    public static String dataFolder(String osName, Map<String, String> environment, String userHome) {
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) {
            String local = environment.get("LOCALAPPDATA");
            String base = local == null || local.isBlank() ? userHome + "\\AppData\\Local" : local;
            return base + "\\BoxOffice";
        }
        if (os.startsWith("mac")) {
            return userHome + "/Library/Application Support/BoxOffice";
        }
        // Linux and the rest follow the XDG rule: $XDG_DATA_HOME if it's set to a full path, else ~/.local/share.
        String xdg = environment.get("XDG_DATA_HOME");
        String base = xdg == null || !xdg.startsWith("/") ? userHome + "/.local/share" : xdg;
        return base + "/boxoffice";
    }

    /**
     * The database to open when none was asked for.
     *
     * @param installed true when started by an installer's launcher, see {@link #installed()}
     */
    public static String defaultDatabase(String osName, Map<String, String> environment, String userHome,
                                         boolean installed) {
        if (!installed) {
            return DATABASE;
        }
        String separator = osName.toLowerCase(Locale.ROOT).startsWith("windows") ? "\\" : "/";
        return dataFolder(osName, environment, userHome) + separator + DATABASE;
    }

    /** The default database on this computer, its folder made if it isn't there yet. */
    public static Path defaultDatabase() {
        Path database = Path.of(defaultDatabase(System.getProperty("os.name"), System.getenv(),
                System.getProperty("user.home"), installed()));
        Path folder = database.toAbsolutePath().getParent();
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't make " + folder, e);
        }
        return database;
    }

    /** True when an installer's launcher started us: jpackage's launcher sets this property, java -jar doesn't. */
    public static boolean installed() {
        return System.getProperty("jpackage.app-path") != null;
    }
}
