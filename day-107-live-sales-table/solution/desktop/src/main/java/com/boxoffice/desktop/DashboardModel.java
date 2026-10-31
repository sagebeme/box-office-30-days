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
        soldCount = Bindings.createStringBinding(() -> sales.size() + " sold", sales);
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
        sales.setAll(gate.ticketsSold().reversed().stream()
                .map(this::row)
                .toList());
    }

    /** Sells a ticket, says what happened, and updates the screen if it sold. */
    public SaleOutcome sell(String typeCode, String buyer) {
        SaleOutcome outcome = gate.sell(typeCode, buyer);
        message.set(outcome.describe());
        if (outcome instanceof SaleOutcome.Sold) {
            refresh();
        }
        return outcome;
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
