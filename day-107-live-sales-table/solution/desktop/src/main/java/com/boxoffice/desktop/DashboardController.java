package com.boxoffice.desktop;

import com.boxoffice.core.TicketType;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

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
    private Label soldCount;
    @FXML
    private ListView<TypeSummary> types;
    @FXML
    private ChoiceBox<TicketType> typeChoice;
    @FXML
    private TextField buyerField;
    @FXML
    private Button sellButton;
    @FXML
    private Label message;
    @FXML
    private TableView<SaleRow> salesTable;
    @FXML
    private TableColumn<SaleRow, String> codeColumn;
    @FXML
    private TableColumn<SaleRow, String> typeColumn;
    @FXML
    private TableColumn<SaleRow, String> buyerColumn;
    @FXML
    private TableColumn<SaleRow, String> priceColumn;
    @FXML
    private TableColumn<SaleRow, String> timeColumn;

    DashboardController(DashboardModel model) {
        this.model = model;
    }

    /** FXMLLoader calls this once every @FXML field is filled in. */
    @FXML
    private void initialize() {
        title.textProperty().bind(model.titleProperty());
        when.textProperty().bind(model.whenProperty());
        takings.textProperty().bind(model.takingsProperty());
        soldCount.textProperty().bind(model.soldCount());
        message.textProperty().bind(model.messageProperty());
        types.setItems(model.types());

        typeChoice.getItems().setAll(model.ticketTypes());
        typeChoice.setConverter(new StringConverter<>() {
            @Override
            public String toString(TicketType type) {
                return type == null ? "" : type.name();
            }

            @Override
            public TicketType fromString(String text) {
                throw new UnsupportedOperationException("The type picker isn't typed into");
            }
        });
        typeChoice.getSelectionModel().selectFirst();
        sellButton.setOnAction(event -> sell());

        salesTable.setItems(model.sales());
        show(codeColumn, SaleRow::code);
        show(typeColumn, SaleRow::type);
        show(buyerColumn, SaleRow::buyer);
        show(priceColumn, SaleRow::price);
        show(timeColumn, SaleRow::time);
    }

    private void sell() {
        model.sell(typeChoice.getValue().code(), buyerField.getText());
        buyerField.clear();
        buyerField.requestFocus();
    }

    /**
     * Tells a column which part of the row to show. PropertyValueFactory("code")
     * looks for getCode() or codeProperty(), and a record only has code(), so
     * it would show empty cells: a lambda says it straight.
     */
    private static void show(TableColumn<SaleRow, String> column, Function<SaleRow, String> part) {
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(part.apply(cell.getValue())));
    }
}
