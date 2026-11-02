package com.boxoffice.core.ai;

import com.boxoffice.core.Event;
import com.boxoffice.core.TicketStore;
import java.time.Clock;
import java.util.function.Consumer;

/**
 * Lets the gate staff ask about the night in their own words: "How many VIP
 * are left?", "Are we past half on Regular?". Each question gets a fresh look
 * at the database, so the answer is about now, not about when the app opened.
 */
public final class SalesQuestions {

    /** Longer than any real question, short enough that nobody pastes a book in by mistake. */
    public static final int LONGEST_QUESTION = 500;

    private final Claude claude;
    private final TicketStore store;
    private final Event event;
    private final Clock clock;

    public SalesQuestions(Claude claude, TicketStore store, Event event, Clock clock) {
        this.claude = claude;
        this.store = store;
        this.event = event;
        this.clock = clock;
    }

    /** The night as it stands this second. */
    public SalesSnapshot snapshot() {
        return SalesSnapshot.of(event, store.tickets(event.id()), store.countCheckedIn(event.id()), clock.instant());
    }

    /**
     * Asks Claude, streaming the answer to {@code onText}. A question that's
     * blank or too long is refused here, before anything is sent.
     */
    public Reply ask(String question, Consumer<String> onText) {
        // TODO Day 125, step 3: if problemWith(question) finds a problem, return a Reply.Refused with it
        //   and don't call Claude. Otherwise ask claude with systemPrompt(snapshot()) and the
        //   question with its spaces stripped, passing onText along.
        throw new UnsupportedOperationException("Not written yet: Day 125, step 3");
    }

    /** What's wrong with the question, or null if it can be sent. */
    public static String problemWith(String question) {
        // TODO Day 125, step 3: null or blank -> "Type a question first";
        //   longer than LONGEST_QUESTION once stripped -> "Keep the question under 500 characters"
        //   (build it from LONGEST_QUESTION); anything else -> null.
        return null;
    }

    /** The instructions Claude works from, with tonight's facts inside. */
    static String systemPrompt(SalesSnapshot snapshot) {
        return """
                You help the gate staff at %s, a weekend event in Nairobi. They ask about \
                tonight's ticket sales from a laptop at the gate, between customers, so they need \
                short answers they can read at a glance.

                Here are the night's figures, taken from the gate's database a moment ago:

                <facts>
                %s</facts>

                Answer from these facts only. If they don't hold the answer (a buyer's name, \
                tomorrow's weather, last year's numbers), say you can't see that from here rather \
                than guessing, because the staff will act on what you say. Do the arithmetic when \
                the question needs it, and give money in Kenyan shillings the way the facts write \
                it (KES 1,500). Reply in plain sentences without Markdown: the answer is shown in \
                a terminal, where asterisks and pound signs print as they are.
                """.formatted(snapshot.event().name(), snapshot.facts());
    }
}
