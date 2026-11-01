package com.boxoffice.core.ai.eval;

import com.boxoffice.core.Event;
import com.boxoffice.core.NairobiTime;
import com.boxoffice.core.SampleEvent;
import com.boxoffice.core.TicketStore;
import com.boxoffice.core.ai.Claude;
import com.boxoffice.core.ai.Reply;
import com.boxoffice.core.ai.SalesQuestions;
import com.boxoffice.core.ai.ToolCall;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Asks every case's question on its own fresh gate and grades the answer.
 * The same runner grades the fake Claude in the tests and the real one from
 * the CLI: only the Claude passed in changes.
 */
public final class EvalRunner {

    /** Every answer must do these, whatever the question. */
    public static final List<Grader> ALWAYS = List.of(Graders.answered(), Graders.plainText(), Graders.atMostWords(80));

    /** The questions are asked at 21:00 on the night. */
    public static final Clock NINE_PM = Clock.fixed(NairobiTime.at(2026, 11, 14, 21, 0).toInstant(), ZoneOffset.UTC);

    private final Path folder;

    /** @param folder where each case's database goes: a temporary folder */
    public EvalRunner(Path folder) {
        this.folder = folder;
    }

    public Scorecard run(List<EvalCase> cases, Claude claude) {
        List<Scorecard.Result> results = new ArrayList<>();
        for (EvalCase evalCase : cases) {
            results.add(run(evalCase, claude));
        }
        return new Scorecard(results);
    }

    Scorecard.Result run(EvalCase evalCase, Claude claude) {
        Transcript transcript;
        try (TicketStore store = TicketStore.open(folder.resolve(evalCase.name() + ".db"))) {
            Event event = SampleEvent.ensureIn(store);
            evalCase.night().accept(new EvalCase.Night(store));
            List<ToolCall> lookups = new ArrayList<>();
            Reply reply = new SalesQuestions(claude, store, event, NINE_PM)
                    .ask(evalCase.question(), piece -> { }, lookups::add);
            transcript = new Transcript(evalCase.question(), reply, lookups);
        }
        // TODO Day 127, step 4: grade the transcript. Run every grader in ALWAYS, then every grader
        //   in evalCase.graders(), in that order; collect the name of each one whose passes() test
        //   is false. Return a Scorecard.Result with the case, the transcript and those names.
        throw new UnsupportedOperationException("Not written yet: Day 127, step 4");
    }
}
