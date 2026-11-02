package com.boxoffice.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import com.boxoffice.core.Gate;
import com.boxoffice.core.SampleEvent;
import com.boxoffice.core.TicketStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Drives the real window the way the gate does: a USB scanner is a keyboard
 * that types the code fast and presses Enter, so the robot types and presses Enter.
 * It runs on Monocle's pretend screen, so nothing pops up while the tests run.
 */
@ExtendWith(ApplicationExtension.class)
class CheckInScreenTest {

    private static final Clock NINE_PM = Clock.fixed(Instant.parse("2026-11-14T18:00:00Z"), ZoneOffset.UTC);

    private Path folder;
    private TicketStore store;

    @Start
    void openTheWindow(Stage stage) throws IOException {
        folder = Files.createTempDirectory("check-in");
        store = TicketStore.open(folder.resolve("gate.db"));
        SampleEvent.ensureIn(store);
        Gate gate = new Gate(store, SampleEvent.ID, NINE_PM);
        gate.sell("VIP", "Wanjiku");

        DashboardModel model = new DashboardModel(gate, Runnable::run);
        stage.setScene(new Scene(BoxOfficeApp.loadDashboard(model), 900, 600));
        stage.show();
    }

    @AfterEach
    void closeTheStoreAndTidyUp() throws IOException {
        store.close();
        // @TempDir doesn't work with @Start, so this test makes and deletes its own folder.
        try (var files = Files.walk(folder)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private void scan(FxRobot robot, String code) {
        robot.clickOn("#scanField").write(code).type(KeyCode.ENTER);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private String result(FxRobot robot) {
        return robot.lookup("#checkInResult").queryAs(Label.class).getText();
    }

    @Test
    void ctrlTwoOpensCheckInWithTheCursorInTheScanBox(FxRobot robot) {
        robot.clickOn("#buyerField");
        robot.press(KeyCode.CONTROL).type(KeyCode.DIGIT2).release(KeyCode.CONTROL);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(robot.lookup("#scanField").queryAs(TextField.class).isFocused()).isTrue();
    }

    @Test
    void scanningAGoodTicketLetsThemIn(FxRobot robot) {
        robot.clickOn("Check in  (Ctrl+2)");

        scan(robot, "VIP-0001");

        assertThat(result(robot)).isEqualTo("Admitted: VIP-0001 · Wanjiku (VIP)");
        assertThat(robot.lookup("#checkInResult").queryAs(Label.class).getStyleClass())
                .contains("check-in-admitted");
    }

    @Test
    void theScanBoxEmptiesItselfForTheNextTicket(FxRobot robot) {
        robot.clickOn("Check in  (Ctrl+2)");

        scan(robot, "VIP-0001");

        assertThat(robot.lookup("#scanField").queryAs(TextField.class).getText()).isEmpty();
    }

    @Test
    void scanningTheSameTicketAgainIsCaught(FxRobot robot) {
        robot.clickOn("Check in  (Ctrl+2)");

        scan(robot, "VIP-0001");
        scan(robot, "vip-0001");

        assertThat(result(robot)).isEqualTo("Already in: VIP-0001 came in at 21:00");
        assertThat(robot.lookup("#checkInResult").queryAs(Label.class).getStyleClass())
                .contains("check-in-already-in")
                .doesNotContain("check-in-admitted");
    }

    @Test
    void aFakeTicketIsRefused(FxRobot robot) {
        robot.clickOn("Check in  (Ctrl+2)");

        scan(robot, "VIP-9999");

        assertThat(result(robot)).isEqualTo("No such ticket: VIP-9999");
    }
}
