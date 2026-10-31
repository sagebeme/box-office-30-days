package com.boxoffice.desktop;

import com.boxoffice.core.Gate;
import com.boxoffice.core.SampleEvent;
import com.boxoffice.core.TicketStore;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * The gate window. JavaFX calls init(), then start() on the JavaFX thread,
 * then stop() when the last window closes.
 *
 * <pre>
 * java -jar desktop/target/boxoffice-desktop.jar             uses gate.db here
 * java -jar desktop/target/boxoffice-desktop.jar --db=x.db   uses x.db
 * java -jar desktop/target/boxoffice-desktop.jar --slow-disk every sale takes 2 seconds to save
 * </pre>
 */
public final class BoxOfficeApp extends Application {

    private TicketStore store;
    private ExecutorService worker;
    private DashboardModel model;

    @Override
    public void init() {
        String database = getParameters().getNamed().getOrDefault("db", "gate.db");
        store = TicketStore.open(Path.of(database));
        if (getParameters().getUnnamed().contains("--slow-disk")) {
            store.simulateSlowDisk(Duration.ofSeconds(2));
        }
        SampleEvent.ensureIn(store);
        // One worker thread, so sales are saved one at a time, in the order they were made.
        worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "gate-worker");
            // A daemon thread doesn't keep the app running after the window closes.
            thread.setDaemon(true);
            return thread;
        });
        model = new DashboardModel(new Gate(store, SampleEvent.ID, SampleEvent.practiceClock()), worker);
    }

    @Override
    public void start(Stage stage) throws IOException {
        stage.setTitle("Box Office");
        stage.setScene(new Scene(loadDashboard(model), 900, 600));
        stage.show();
    }

    @Override
    public void stop() throws InterruptedException {
        // Let a sale that's still saving finish before the database closes under it.
        worker.shutdown();
        worker.awaitTermination(10, TimeUnit.SECONDS);
        store.close();
    }

    /** Loads dashboard.fxml and hands its controller the model. */
    static Parent loadDashboard(DashboardModel model) throws IOException {
        FXMLLoader loader = new FXMLLoader(BoxOfficeApp.class.getResource("dashboard.fxml"));
        // FXML would make the controller itself with an empty constructor; this lets us pass the model in.
        loader.setControllerFactory(type -> new DashboardController(model));
        Parent root = loader.load();
        root.getStylesheets().add(BoxOfficeApp.class.getResource("box-office.css").toExternalForm());
        model.refresh();
        return root;
    }
}
