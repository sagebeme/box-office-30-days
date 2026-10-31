package com.boxoffice.desktop;

import com.boxoffice.core.Event;
import com.boxoffice.core.Gate;
import com.boxoffice.core.Money;
import com.boxoffice.core.NairobiTime;
import com.boxoffice.core.SaleOutcome;
import com.boxoffice.core.Ticket;
import com.boxoffice.core.TicketType;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;

/**
 * What the dashboard shows, kept apart from the window itself. The screen
 * binds to these properties and redraws when they change, so the model never
 * touches a Label, and tests can check it without opening a window.
 *
 * <p>Two threads, two jobs. The JavaFX thread draws the screen and runs every
 * click and key press; if it waits for the disk, the whole window freezes. So
 * slow work (saving a sale) goes to the worker, and only the finished result
 * comes back to the JavaFX thread to be shown.
 */
public final class DashboardModel {

    private final Gate gate;
    private final Executor worker;

    private final ReadOnlyStringWrapper title = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper when = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper takings = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper("");
    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper(false);
    private final ObservableList<TypeSummary> types = FXCollections.observableArrayList();
    private final ObservableList<SaleRow> sales = FXCollections.observableArrayList();
    private final StringBinding soldCount;

    /** Everything the screen shows, read from the gate in one go. Safe to make on any thread. */
    private record Snapshot(Money takings, List<TypeSummary> types, List<SaleRow> sales) {
    }

    /** What a sale's background work hands back to the JavaFX thread. */
    private record SaleResult(SaleOutcome outcome, Snapshot snapshot) {
    }

    /** @param worker where slow work runs: a background thread in the app, the calling thread in some tests */
    public DashboardModel(Gate gate, Executor worker) {
        this.gate = gate;
        this.worker = worker;
        // Worked out again whenever the sales list changes, so it can never be out of date.
        soldCount = Bindings.createStringBinding(() -> sales.size() + " sold", sales);
    }

    /** Reads the gate again and updates everything the screen shows. Call it on the JavaFX thread. */
    public void refresh() {
        Event event = gate.event();
        title.set(event.name());
        when.set(NairobiTime.format(event.startsAt().toInstant())
                + " to " + NairobiTime.format(event.endsAt().toInstant()));
        show(snapshot());
    }

    /** Sells a ticket without making the screen wait. */
    public void sell(String typeCode, String buyer) {
        sell(typeCode, buyer, "", outcome -> { });
    }

    /** The same, keeping the buyer's phone number. */
    public void sell(String typeCode, String buyer, String phone) {
        sell(typeCode, buyer, phone, outcome -> { });
    }

    /**
     * Starts a sale on the worker and returns straight away. When it's saved,
     * the screen updates and afterwards gets the outcome, both on the JavaFX thread.
     * While a sale is saving, busy is true and another sale is ignored.
     */
    public void sell(String typeCode, String buyer, String phone, Consumer<SaleOutcome> afterwards) {
        if (busy.get()) {
            return;
        }
        busy.set(true);
        message.set("Saving…");

        Task<SaleResult> save = new Task<>() {
            @Override
            protected SaleResult call() {
                // The worker thread: slow work is fine here, but no touching the screen.
                SaleOutcome outcome = gate.sell(typeCode, buyer, phone);
                return new SaleResult(outcome, snapshot());
            }
        };
        // Task runs these two back on the JavaFX thread, where changing the screen is allowed.
        save.setOnSucceeded(done -> {
            SaleResult result = save.getValue();
            show(result.snapshot());
            message.set(result.outcome().describe());
            busy.set(false);
            afterwards.accept(result.outcome());
        });
        save.setOnFailed(failed -> {
            message.set("Couldn't save the sale: " + save.getException().getMessage());
            busy.set(false);
        });
        worker.execute(save);
    }

    /** The kinds of ticket the event sells, for the type picker. */
    public List<TicketType> ticketTypes() {
        return gate.event().ticketTypes();
    }

    private Snapshot snapshot() {
        Event event = gate.event();
        return new Snapshot(
                gate.takings(),
                event.ticketTypes().stream().map(this::summarise).toList(),
                gate.ticketsSold().reversed().stream().map(this::row).toList());
    }

    private void show(Snapshot snapshot) {
        takings.set(snapshot.takings().format());
        types.setAll(snapshot.types());
        sales.setAll(snapshot.sales());
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

    /** True while a sale is being saved. */
    public ReadOnlyBooleanProperty busyProperty() {
        return busy.getReadOnlyProperty();
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
