package com.boxoffice.desktop;

import com.boxoffice.core.Gate;
import com.boxoffice.core.SampleEvent;
import com.boxoffice.core.TicketStore;
import java.io.IOException;
import java.nio.file.Path;
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
 * </pre>
 */
public final class BoxOfficeApp extends Application {

    private TicketStore store;
    private DashboardModel model;

    @Override
    public void init() {
        String database = getParameters().getNamed().getOrDefault("db", "gate.db");
        store = TicketStore.open(Path.of(database));
        SampleEvent.ensureIn(store);
        model = new DashboardModel(new Gate(store, SampleEvent.ID, SampleEvent.practiceClock()));
    }

    @Override
    public void start(Stage stage) throws IOException {
        stage.setTitle("Box Office");
        stage.setScene(new Scene(loadDashboard(model), 900, 600));
        stage.show();
    }

    @Override
    public void stop() {
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
