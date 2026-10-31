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
        // TODO Day 128, step 2: the folder for each system, as in the comment at the top.
        //   osName lower-cased (Locale.ROOT) starts with "windows": LOCALAPPDATA from the environment,
        //       or userHome + "\\AppData\\Local" when it's missing or blank, then "\\BoxOffice".
        //   starts with "mac": userHome + "/Library/Application Support/BoxOffice".
        //   anything else: XDG_DATA_HOME if it's set and starts with "/", else userHome + "/.local/share",
        //       then "/boxoffice" (lower case: that's the Linux way).
        //   Build strings, not Paths: a Path on Linux can't hold a Windows path.
        throw new UnsupportedOperationException("Not written yet: Day 128, step 2");
    }

    /**
     * The database to open when none was asked for.
     *
     * @param installed true when started by an installer's launcher, see {@link #installed()}
     */
    public static String defaultDatabase(String osName, Map<String, String> environment, String userHome,
                                         boolean installed) {
        // TODO Day 128, step 3: not installed -> DATABASE, the folder you run from, as since Day 104.
        //   Installed -> dataFolder(...), the system's separator ("\\" on Windows, "/" elsewhere), DATABASE.
        throw new UnsupportedOperationException("Not written yet: Day 128, step 3");
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
