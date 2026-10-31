package com.boxoffice.desktop;

import com.boxoffice.core.Event;
import com.boxoffice.core.Gate;
import com.boxoffice.core.NairobiTime;
import com.boxoffice.core.TicketType;
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
    private final ObservableList<TypeSummary> types = FXCollections.observableArrayList();

    public DashboardModel(Gate gate) {
        this.gate = gate;
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
    }

    private TypeSummary summarise(TicketType type) {
        return new TypeSummary(type.name(), type.price().format(), gate.remaining(type.code()), type.capacity());
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

    public ObservableList<TypeSummary> types() {
        return FXCollections.unmodifiableObservableList(types);
    }
}
