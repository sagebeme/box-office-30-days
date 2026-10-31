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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
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
 * The whole sell screen, driven only from the keyboard, the way gate staff use
 * it at 10 at night with a queue in front of them. If a change breaks the
 * keyboard flow, one of these fails before anyone at a gate finds out.
 */
@ExtendWith(ApplicationExtension.class)
class SellScreenTest {

    private static final Clock NINE_PM = Clock.fixed(Instant.parse("2026-11-14T18:00:00Z"), ZoneOffset.UTC);

    private Path folder;
    private TicketStore store;
    private Gate gate;
    // Holds every save until the test lets it go, so a test can type while a sale is "saving".
    private CountDownLatch diskIsDone;

    @Start
    void openTheWindow(Stage stage) throws IOException {
        folder = Files.createTempDirectory("sell-screen");
        store = TicketStore.open(folder.resolve("gate.db"));
        SampleEvent.ensureIn(store);
        gate = new Gate(store, SampleEvent.ID, NINE_PM);
        diskIsDone = new CountDownLatch(0);

        Executor worker = task -> new Thread(() -> {
            try {
                diskIsDone.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            task.run();
        }).start();
        DashboardModel model = new DashboardModel(gate, worker);
        stage.setScene(new Scene(BoxOfficeApp.loadDashboard(model), 1000, 650));
        stage.show();
    }

    @AfterEach
    void closeTheStoreAndTidyUp() throws IOException {
        store.close();
        try (var files = Files.walk(folder)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    /** Waits until the message says how the sale went, not "Saving…". */
    private static void waitForTheSale(FxRobot robot) {
        try {
            WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> {
                String message = robot.lookup("#message").queryAs(Label.class).getText();
                return !message.isEmpty() && !message.equals("Saving…");
            });
        } catch (TimeoutException e) {
            throw new AssertionError("The sale didn't finish in 5 seconds", e);
        }
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static String text(FxRobot robot, String id) {
        if (robot.lookup(id).query() instanceof TextField field) {
            return field.getText();
        }
        return robot.lookup(id).queryAs(Label.class).getText();
    }

    @SuppressWarnings("unchecked")
    private static int rows(FxRobot robot) {
        return robot.lookup("#salesTable").queryAs(TableView.class).getItems().size();
    }

    @Test
    void theCursorStartsInTheNameBox(FxRobot robot) {
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(robot.lookup("#buyerField").queryAs(TextField.class).isFocused()).isTrue();
    }

    @Test
    void typeANameAndPressEnterToSell(FxRobot robot) {
        robot.write("Otieno").type(KeyCode.ENTER);
        waitForTheSale(robot);

        assertThat(text(robot, "#message")).isEqualTo("Sold REG-0001 to Otieno for KES 1,500");
        assertThat(rows(robot)).isEqualTo(1);
        assertThat(text(robot, "#takings")).isEqualTo("KES 1,500");
    }

    @Test
    void theFormEmptiesForTheNextPerson(FxRobot robot) {
        robot.write("Otieno").type(KeyCode.ENTER);
        waitForTheSale(robot);

        assertThat(text(robot, "#buyerField")).isEmpty();
        assertThat(robot.lookup("#buyerField").queryAs(TextField.class).isFocused()).isTrue();
    }

    @Test
    void fTwoSellsVip(FxRobot robot) {
        robot.type(KeyCode.F2).write("Wanjiku").type(KeyCode.ENTER);
        waitForTheSale(robot);

        assertThat(text(robot, "#message")).isEqualTo("Sold VIP-0001 to Wanjiku for KES 5,000");
    }

    @Test
    void tabMovesToThePhoneAndABadNumberStopsTheSale(FxRobot robot) {
        robot.write("Otieno").type(KeyCode.TAB).write("0712 34");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(text(robot, "#problem")).isEqualTo("Phone should look like 0712 345 678");
        assertThat(robot.lookup("#sellButton").queryAs(Button.class).isDisabled()).isTrue();

        robot.type(KeyCode.ENTER);
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(rows(robot)).isZero();
    }

    @Test
    void escapeClearsTheForm(FxRobot robot) {
        robot.write("Otieno").type(KeyCode.ESCAPE);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(text(robot, "#buyerField")).isEmpty();
    }

    @Test
    void fFiveShowsSalesMadeByAnotherProgram(FxRobot robot) {
        // The CLI, on the same gate.db, sells while the window is open.
        new Gate(store, SampleEvent.ID, NINE_PM).sell("VIP", "Kamau");
        assertThat(rows(robot)).isZero();

        robot.type(KeyCode.F5);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(rows(robot)).isEqualTo(1);
    }

    @Test
    void theNextNameTypedWhileASaleSavesIsKept(FxRobot robot) {
        diskIsDone = new CountDownLatch(1);
        robot.write("Otieno").type(KeyCode.ENTER);
        WaitForAsyncUtils.waitForFxEvents();

        // The sale is still saving; the next person in the queue gives their name.
        robot.eraseText(6).write("Achieng");
        diskIsDone.countDown();
        waitForTheSale(robot);

        assertThat(rows(robot)).isEqualTo(1);
        assertThat(text(robot, "#buyerField")).isEqualTo("Achieng");
    }
}
