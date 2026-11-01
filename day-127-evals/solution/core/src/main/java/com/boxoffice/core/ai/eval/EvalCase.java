package com.boxoffice.core.ai.eval;

import com.boxoffice.core.Gate;
import com.boxoffice.core.NairobiTime;
import com.boxoffice.core.SampleEvent;
import com.boxoffice.core.TicketStore;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Consumer;

/**
 * One question to grade: a night set up just so, the question the staff
 * ask, and what a good answer does.
 *
 * @param night sells and checks in whatever the question needs, on an empty gate
 */
public record EvalCase(String name, Consumer<Night> night, String question, List<Grader> graders) {

    public EvalCase {
        graders = List.copyOf(graders);
    }

    /** The gate on the night of Kilele Nights, at whatever time a sale or scan should happen. */
    public static final class Night {

        private final TicketStore store;

        Night(TicketStore store) {
            this.store = store;
        }

        /** The gate with its clock at this time on Saturday 14 November, e.g. at("20:15").sell(...). */
        public Gate at(String time) {
            String[] parts = time.split(":");
            Clock clock = Clock.fixed(NairobiTime.at(2026, 11, 14,
                    Integer.parseInt(parts[0]), Integer.parseInt(parts[1])).toInstant(), ZoneOffset.UTC);
            return new Gate(store, SampleEvent.ID, clock);
        }
    }
}
