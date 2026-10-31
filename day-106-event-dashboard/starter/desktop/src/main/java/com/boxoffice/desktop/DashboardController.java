package com.boxoffice.desktop;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

/**
 * Connects dashboard.fxml to the model. The @FXML fields are filled in by
 * FXMLLoader from the elements with the same fx:id in the FXML file.
 */
public final class DashboardController {

    private final DashboardModel model;

    @FXML
    private Label title;
    @FXML
    private Label when;
    @FXML
    private Label takings;
    @FXML
    private ListView<TypeSummary> types;

    DashboardController(DashboardModel model) {
        this.model = model;
    }

    /** FXMLLoader calls this once every @FXML field is filled in. */
    @FXML
    private void initialize() {
        title.textProperty().bind(model.titleProperty());
        // TODO: bind when's and takings' text to the model, the same way as title
        // TODO: show the model's types in the list: types.setItems(model.types())
        // (No test checks this file. Run the app and look: Day 106, step 6.)
    }
}
