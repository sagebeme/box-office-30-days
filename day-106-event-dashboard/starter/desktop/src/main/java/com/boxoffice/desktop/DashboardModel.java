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
        // TODO: title.set(...) the event's name
        // TODO: when.set(...) "Sat 14 Nov, 18:00 to Sun 15 Nov, 04:00": NairobiTime.format the start
        //       and the end (event.startsAt().toInstant()), with " to " between them
        // TODO: takings.set(...) gate.takings(), formatted
        // TODO: types.setAll(...) one TypeSummary per ticket type, in the event's order.
        //       summarise(type) below makes one; a stream with .map(this::summarise).toList() makes them all.
        throw new UnsupportedOperationException("Not written yet: Day 106, step 4");
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
