package com.boxoffice.core.ai.eval;

import java.util.List;

/** How every case went. */
public record Scorecard(List<Result> results) {

    /** One case: what happened, and the graders it failed (none means it passed). */
    public record Result(EvalCase evalCase, Transcript transcript, List<String> failed) {

        public Result {
            failed = List.copyOf(failed);
        }

        public boolean passed() {
            return failed.isEmpty();
        }
    }

    public Scorecard {
        results = List.copyOf(results);
    }

    public long passed() {
        return results.stream().filter(Result::passed).count();
    }

    /** Between 0 and 1. */
    public double passRate() {
        return results.isEmpty() ? 0 : (double) passed() / results.size();
    }

    /**
     * <pre>
     * PASS  vip-left
     * FAIL  whose-ticket: never mentions Kamau
     *       answer: VIP-0001 is Wanjiku Kamau's.
     * 7 of 8 passed (88%)
     * </pre>
     */
    public String render() {
        StringBuilder out = new StringBuilder();
        for (Result result : results) {
            if (result.passed()) {
                out.append("PASS  ").append(result.evalCase().name()).append('\n');
            } else {
                out.append("FAIL  ").append(result.evalCase().name()).append(": ")
                        .append(String.join(", ", result.failed())).append('\n');
                out.append("      answer: ").append(result.transcript().answer().strip().replace("\n", " ")).append('\n');
            }
        }
        out.append(passed()).append(" of ").append(results.size()).append(" passed (")
                .append(Math.round(passRate() * 100)).append("%)\n");
        return out.toString();
    }
}
