package com.boxoffice.desktop;

import com.boxoffice.core.PhoneNumbers;
import com.boxoffice.core.TicketType;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * What's typed into the sell form, and whether it's ready to sell. The screen
 * binds its fields to these properties both ways, so typing changes the form
 * and clear() empties the screen.
 */
public final class SaleForm {

    private final List<TicketType> types;

    private final ObjectProperty<TicketType> type = new SimpleObjectProperty<>();
    private final StringProperty buyer = new SimpleStringProperty("");
    private final StringProperty phone = new SimpleStringProperty("");
    private final StringBinding problem;
    private final BooleanBinding ready;

    public SaleForm(List<TicketType> types) {
        this.types = List.copyOf(types);
        // Worked out again whenever the type, buyer or phone changes.
        problem = Bindings.createStringBinding(this::findProblem, type, buyer, phone);
        ready = problem.isEmpty();
    }

    /** The first thing stopping a sale, in words for the gate staff, or "" when it's ready. */
    private String findProblem() {
        // TODO: check these in order and return the first one that's a problem:
        //   no type picked (type.get() == null) -> "Pick a ticket type: " + keyHints()
        //   a blank buyer                       -> "Type the buyer's name"
        //   a phone that's typed (not blank) but PhoneNumbers.normalise says empty
        //                                       -> "Phone should look like 0712 345 678"
        //   nothing wrong                       -> ""
        throw new UnsupportedOperationException("Not written yet: Day 108, step 5");
    }

    /** "F1 Regular, F2 VIP". */
    public String keyHints() {
        return IntStream.range(0, types.size())
                .mapToObj(i -> "F" + (i + 1) + " " + types.get(i).name())
                .collect(Collectors.joining(", "));
    }

    /** F1 picks the first type, F2 the second... A key with no type does nothing. */
    public void pick(int functionKey) {
        // TODO: if functionKey is between 1 and types.size(), set type to that one.
        //       Careful: F1 is the first type, and lists count from 0.
    }

    /** Ready for the next person in the queue. Keeps the type: queues often want the same one. */
    public void clear() {
        buyer.set("");
        phone.set("");
    }

    public List<TicketType> types() {
        return types;
    }

    public ObjectProperty<TicketType> typeProperty() {
        return type;
    }

    public StringProperty buyerProperty() {
        return buyer;
    }

    public StringProperty phoneProperty() {
        return phone;
    }

    public StringBinding problem() {
        return problem;
    }

    public BooleanBinding ready() {
        return ready;
    }
}
