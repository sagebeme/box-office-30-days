package com.boxoffice.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * The installer build runs jlink and jpackage, which take minutes and other
 * systems' tools, so the tests don't run it. They read desktop/pom.xml
 * instead and check the settings a mistake in would ship a broken app.
 */
class InstallerProfileTest {

    private static Document pom;
    private static XPath xpath;

    @BeforeAll
    static void readThePom() throws Exception {
        // Tests run in the module's own folder, so this is desktop/pom.xml.
        pom = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(Path.of("pom.xml").toFile());
        xpath = XPathFactory.newInstance().newXPath();
    }

    private static String text(String path) throws Exception {
        return ((String) xpath.evaluate(path, pom, XPathConstants.STRING)).strip();
    }

    private static List<String> texts(String path) throws Exception {
        NodeList nodes = (NodeList) xpath.evaluate(path, pom, XPathConstants.NODESET);
        String[] values = new String[nodes.getLength()];
        for (int i = 0; i < values.length; i++) {
            values[i] = nodes.item(i).getTextContent().strip();
        }
        return List.of(values);
    }

    private static final String INSTALLER = "/project/profiles/profile[id='installer']";
    private static final String EXEC = INSTALLER + "/build/plugins/plugin[artifactId='exec-maven-plugin']";

    private static List<String> jlinkArguments() throws Exception {
        return texts(EXEC + "/executions/execution[id='jlink']/configuration/arguments/argument");
    }

    private static String jpackageArguments() throws Exception {
        return text(EXEC + "/executions/execution[id='jpackage']/configuration/commandlineArgs");
    }

    private static List<String> runtimeModules() throws Exception {
        List<String> arguments = jlinkArguments();
        return Arrays.asList(arguments.get(arguments.indexOf("--add-modules") + 1).split(","));
    }

    @Test
    void theRuntimeHasWhatTheGateUses() throws Exception {
        assertThat(runtimeModules()).contains(
                "java.base",
                "java.sql",          // the database
                "java.net.http",     // syncing with the server (Day 115)
                "jdk.httpserver",    // the LAN feed between gates (Day 117)
                "java.desktop",      // printing receipts (Day 112)
                "jdk.crypto.ec");    // HTTPS: no class names it, so jdeps can't see it
    }

    @Test
    void theRuntimeIsStrippedAndSqueezed() throws Exception {
        assertThat(jlinkArguments()).contains("--strip-debug", "--no-header-files", "--no-man-pages", "--compress=zip-6");
        assertThat(runtimeModules()).doesNotContain("jdk.compiler", "jdk.jshell", "java.se");
    }

    @Test
    void jpackageStartsTheMainThatDodgesTheJavaFxCheck() throws Exception {
        assertThat(jpackageArguments())
                .contains("--type ${installer.type}")
                .contains("--name BoxOffice")
                .contains("--main-jar boxoffice-desktop.jar")
                .contains("--main-class com.boxoffice.desktop.Main")
                .contains("--runtime-image ${project.build.directory}/runtime")
                // Only the jar's folder: jpackage copies all of its input into the app.
                .contains("--input ${project.build.directory}/installer-input");
    }

    @Test
    void withNoTypeItMakesAFolderThatNeedsNoOtherTools() throws Exception {
        assertThat(text(INSTALLER + "/properties/installer.type")).isEqualTo("app-image");
    }

    @Test
    void windowsKeepsOneUpgradeIdForEveryVersion() throws Exception {
        assertThat(text("/project/profiles/profile[id='msi']/properties/installer.options"))
                .contains("--win-upgrade-uuid 7f3c2a64-5b0e-4f2d-9a51-6c1d8e0b4a77");
    }

    @Test
    void theSqliteLibraryKeptForEachSystemReallyExists() throws Exception {
        Map<String, String> kept = new LinkedHashMap<>();
        NodeList profiles = (NodeList) xpath.evaluate("/project/profiles/profile[starts-with(id, 'sqlite-')]", pom,
                XPathConstants.NODESET);
        for (int i = 0; i < profiles.getLength(); i++) {
            String id = xpath.evaluate("id", profiles.item(i)).strip();
            kept.put(id, xpath.evaluate("properties/sqlite.native", profiles.item(i)).strip());
        }

        assertThat(kept).containsKeys("sqlite-linux-x64", "sqlite-mac-arm64", "sqlite-windows-x64");
        // A typo here would build an app with no database driver for its own system.
        kept.forEach((id, folder) -> {
            String library = folder.startsWith("Windows") ? "sqlitejdbc.dll"
                    : folder.startsWith("Mac") ? "libsqlitejdbc.dylib" : "libsqlitejdbc.so";
            String resource = "org/sqlite/native/" + folder + "/" + library;
            assertThat(getClass().getClassLoader().getResource(resource)).as(id + ": " + resource).isNotNull();
        });
    }

    @Test
    void theShadedJarKeepsOnlyThatLibrary() throws Exception {
        String filter = INSTALLER + "/build/plugins/plugin[artifactId='maven-shade-plugin']"
                + "//filter[artifact='org.xerial:sqlite-jdbc']/includes/include";

        assertThat(texts(filter)).contains("org/sqlite/native/${sqlite.native}/**")
                .noneMatch(include -> include.equals("org/sqlite/native/**"));
    }
}
