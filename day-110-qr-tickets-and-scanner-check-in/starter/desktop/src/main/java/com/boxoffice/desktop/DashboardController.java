package com.boxoffice.desktop;

import com.boxoffice.core.SaleOutcome;
import com.boxoffice.core.TicketType;
import java.util.Locale;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.event.ActionEvent;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.util.StringConverter;

/**
 * Connects dashboard.fxml to the model. The @FXML fields are filled in by
 * FXMLLoader from the elements with the same fx:id in the FXML file.
 *
 * <p>The keyboard, so nobody needs the mouse at the gate:
 * F1, F2... pick the ticket type; Enter sells; Esc clears the form;
 * Ctrl+1 and Ctrl+2 switch between selling and checking in.
 *
 * <p>A USB scanner is a keyboard too: it "types" the ticket code very fast
 * and presses Enter. So check-in is just a text field with an Enter handler.
 */
public final class DashboardController {

    private final DashboardModel model;
    private final SaleForm form;

    @FXML
    private BorderPane root;
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
    private TextField phoneField;
    @FXML
    private Button sellButton;
    @FXML
    private Label problem;
    @FXML
    private Label message;
    @FXML
    private TabPane tabs;
    @FXML
    private Tab sellTab;
    @FXML
    private Tab checkInTab;
    @FXML
    private ImageView qrImage;
    @FXML
    private Label qrCaption;
    @FXML
    private TextField scanField;
    @FXML
    private Label checkInResult;
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
        this.form = new SaleForm(model.ticketTypes());
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

        typeChoice.getItems().setAll(form.types());
        typeChoice.setConverter(new StringConverter<>() {
            @Override
            public String toString(TicketType type) {
                if (type == null) {
                    return "";
                }
                return "F" + (form.types().indexOf(type) + 1) + "  " + type.name() + " · " + type.price().format();
            }

            @Override
            public TicketType fromString(String text) {
                throw new UnsupportedOperationException("The type picker isn't typed into");
            }
        });

        // Both ways: typing changes the form, and form.clear() empties the fields.
        typeChoice.valueProperty().bindBidirectional(form.typeProperty());
        buyerField.textProperty().bindBidirectional(form.buyerProperty());
        phoneField.textProperty().bindBidirectional(form.phoneProperty());
        problem.textProperty().bind(form.problem());
        // No selling while the form isn't ready, or while the last sale is still saving.
        sellButton.disableProperty().bind(form.ready().not().or(model.busyProperty()));
        form.pick(1);
        // Start with the cursor in the name box, ready for the first person in the queue.
        Platform.runLater(buyerField::requestFocus);

        // Enter in either field sells; the Sell button is the "default button" too.
        buyerField.setOnAction(event -> sell());
        phoneField.setOnAction(event -> sell());
        sellButton.setOnAction(event -> sell());
        // A filter on the root sees every key first, whichever field has the focus.
        root.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);

        salesTable.setItems(model.sales());
        show(codeColumn, SaleRow::code);
        show(typeColumn, SaleRow::type);
        show(buyerColumn, SaleRow::buyer);
        show(priceColumn, SaleRow::price);
        show(timeColumn, SaleRow::time);
        salesTable.setFocusTraversable(false);

        // The QR code of the last ticket sold, ready to scan or print.
        model.lastSoldCodeProperty().addListener((property, before, code) -> {
            qrImage.setImage(code.isEmpty() ? null : QrImages.of(code, 6));
            qrCaption.setText(code);
        });

        scanField.setOnAction(this::onScan);
        checkInResult.textProperty().bind(model.checkInMessageProperty());
        model.checkInStateProperty().addListener((property, before, state) -> {
            checkInResult.getStyleClass().removeAll(styleFor(before));
            checkInResult.getStyleClass().add(styleFor(state));
        });
        // Whichever tab opens, the cursor goes where the next key press belongs. runLater waits
        // until the tab is on screen: a field that isn't showing yet can't take the focus.
        tabs.getSelectionModel().selectedItemProperty().addListener((property, before, tab) ->
                Platform.runLater(() -> (tab == checkInTab ? scanField : buyerField).requestFocus()));
    }

    /** The scanner pressed Enter: check the code in, then empty the box for the next ticket. */
    private void onScan(ActionEvent event) {
        // TODO: ask the model to check in what's in scanField, then clear scanField
        //       so it's empty for the next ticket.
        // TODO: event.consume(), so this Enter can't also press the Sell button on the other tab.
    }

    private static String styleFor(CheckInState state) {
        return "check-in-" + state.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private void onKey(KeyEvent event) {
        KeyCode key = event.getCode();
        if (key.isFunctionKey()) {
            // F1 is KeyCode.F1, F2 is KeyCode.F2... their names end in the number.
            form.pick(Integer.parseInt(key.getName().substring(1)));
            event.consume();
        } else if (event.isShortcutDown() && key == KeyCode.DIGIT1) {
            tabs.getSelectionModel().select(sellTab);
            event.consume();
        } else if (event.isShortcutDown() && key == KeyCode.DIGIT2) {
            tabs.getSelectionModel().select(checkInTab);
            event.consume();
        } else if (key == KeyCode.ESCAPE) {
            form.clear();
            buyerField.requestFocus();
            event.consume();
        }
    }

    private void sell() {
        if (!form.ready().get()) {
            return;
        }
        model.sell(form.typeProperty().get().code(), form.buyerProperty().get(), form.phoneProperty().get(),
                outcome -> {
                    if (outcome instanceof SaleOutcome.Sold) {
                        form.clear();
                    }
                });
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
