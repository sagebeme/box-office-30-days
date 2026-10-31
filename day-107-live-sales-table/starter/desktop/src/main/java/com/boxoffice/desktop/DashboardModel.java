package com.boxoffice.desktop;

import com.boxoffice.core.Event;
import com.boxoffice.core.Gate;
import com.boxoffice.core.NairobiTime;
import com.boxoffice.core.SaleOutcome;
import com.boxoffice.core.Ticket;
import com.boxoffice.core.TicketType;
import java.util.List;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * What the dashboard shows, kept apart from the window itself. The screen
 * binds to these properties and redraws when they change, so the model never
 * touches a Label, and tests can check it without opening a window.
 */
public final class DashboardModel {

    private final Gate gate;

    private final ReadOnlyStringWrapper title = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper when = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper takings = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper("");
    private final ObservableList<TypeSummary> types = FXCollections.observableArrayList();
    private final ObservableList<SaleRow> sales = FXCollections.observableArrayList();
    private final StringBinding soldCount;

    public DashboardModel(Gate gate) {
        this.gate = gate;
        // Worked out again whenever the sales list changes, so it can never be out of date.
        // TODO: make this say "0 sold", "1 sold", "2 sold"... and change by itself when sales changes:
        //       the first argument works out the text, the second is what to watch (sales).
        soldCount = Bindings.createStringBinding(() -> "", sales);
    }

    /** Reads the gate again and updates everything the screen shows. */
    public void refresh() {
        Event event = gate.event();
        title.set(event.name());
        when.set(NairobiTime.format(event.startsAt().toInstant())
                + " to " + NairobiTime.format(event.endsAt().toInstant()));
        takings.set(gate.takings().format());
        types.setAll(event.ticketTypes().stream()
                .map(this::summarise)
                .toList());
        // TODO: sales.setAll(...) one row(ticket) per ticket sold, newest first.
        //       gate.ticketsSold() is oldest first; .reversed() turns a List around.
    }

    /** Sells a ticket, says what happened, and updates the screen if it sold. */
    public SaleOutcome sell(String typeCode, String buyer) {
        // TODO: ask the gate to sell, put the outcome's describe() in message,
        //       refresh() if it sold (instanceof SaleOutcome.Sold), and return the outcome
        throw new UnsupportedOperationException("Not written yet: Day 107, step 4");
    }

    /** The kinds of ticket the event sells, for the type picker. */
    public List<TicketType> ticketTypes() {
        return gate.event().ticketTypes();
    }

    private TypeSummary summarise(TicketType type) {
        return new TypeSummary(type.name(), type.price().format(), gate.remaining(type.code()), type.capacity());
    }

    private SaleRow row(Ticket ticket) {
        String typeName = gate.event().ticketType(ticket.typeCode()).map(TicketType::name).orElse(ticket.typeCode());
        return new SaleRow(ticket.code(), typeName, ticket.buyer(), ticket.price().format(),
                NairobiTime.timeOfDay(ticket.soldAt()));
    }

    // Read-only from outside: the screen can watch these but only the model changes them.

    public ReadOnlyStringProperty titleProperty() {
        return title.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty whenProperty() {
        return when.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty takingsProperty() {
        return takings.getReadOnlyProperty();
    }

    /** What happened with the last sale: "Sold VIP-0001 to Wanjiku for KES 5,000". */
    public ReadOnlyStringProperty messageProperty() {
        return message.getReadOnlyProperty();
    }

    /** "3 sold". */
    public StringBinding soldCount() {
        return soldCount;
    }

    public ObservableList<TypeSummary> types() {
        return FXCollections.unmodifiableObservableList(types);
    }

    /** Every sale, newest at the top. */
    public ObservableList<SaleRow> sales() {
        return FXCollections.unmodifiableObservableList(sales);
    }
}
