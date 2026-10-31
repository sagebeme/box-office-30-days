package com.boxoffice.core.ai;

import com.boxoffice.core.Event;
import com.boxoffice.core.TicketStore;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Lets the gate staff ask about the night in their own words: "How many VIP
 * are left?", "Are we past half on Regular?". Each question gets a fresh look
 * at the database, so the answer is about now, not about when the app opened.
 */
public final class SalesQuestions {

    /** Longer than any real question, short enough that nobody pastes a book in by mistake. */
    public static final int LONGEST_QUESTION = 500;

    /** Day 126: rounds of lookups one question may take. A real question needs one or two. */
    public static final int MOST_LOOKUP_ROUNDS = 5;

    private final Claude claude;
    private final SalesTools tools;
    private final TicketStore store;
    private final Event event;
    private final Clock clock;

    public SalesQuestions(Claude claude, TicketStore store, Event event, Clock clock) {
        this.claude = claude;
        this.tools = new SalesTools(store, event);
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
        return ask(question, onText, call -> { });
    }

    /**
     * Day 126: Claude may ask for lookups before it answers. Each one runs
     * here, on this laptop, and its result goes back; {@code onLookup} hears
     * about each, so the screen can say what's happening.
     */
    public Reply ask(String question, Consumer<String> onText, Consumer<ToolCall> onLookup) {
        String problem = problemWith(question);
        if (problem != null) {
            return new Reply.Refused(problem);
        }
        // TODO Day 126, step 5: the lookup loop.
        //   Start a chat with systemPrompt(snapshot()) and tools.specs(), and ask the stripped question.
        //   While the turn is a Turn.UsesTools: run every call in order (tell onLookup first), collect
        //   the results, and hand them all back with chat.answer(...), which gives the next turn.
        //   After MOST_LOOKUP_ROUNDS rounds, stop with a Reply.Refused:
        //       "Claude still wanted to look things up after 5 goes. Ask something narrower"
        //   When the turn is a Turn.Finished, return its reply.
        throw new UnsupportedOperationException("Not written yet: Day 126, step 5");
    }

    /** What's wrong with the question, or null if it can be sent. */
    public static String problemWith(String question) {
        if (question == null || question.isBlank()) {
            return "Type a question first";
        }
        if (question.strip().length() > LONGEST_QUESTION) {
            return "Keep the question under " + LONGEST_QUESTION + " characters";
        }
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

                You also have lookups for what the facts leave out: one ticket by its code, the \
                sales between two times, and who has come in. Use them when a question needs them; \
                for the totals above, the facts are enough. They only read: nothing you do can \
                sell, refund or change a ticket, so if the staff ask for that, tell them to do it \
                on the gate screen.

                Answer from the facts and what your lookups find. If neither holds the answer \
                (tomorrow's weather, last year's numbers, a buyer's full name or phone number), say \
                you can't see that from here rather than guessing, because the staff will act on \
                what you say.

                Do the arithmetic when \
                the question needs it, and give money in Kenyan shillings the way the facts write \
                it (KES 1,500). Reply in plain sentences without Markdown: the answer is shown in \
                a terminal, where asterisks and pound signs print as they are.
                """.formatted(snapshot.event().name(), snapshot.facts());
    }
}
