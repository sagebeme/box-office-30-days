package com.boxoffice.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.boxoffice.core.ai.eval.EvalRunner;
import com.boxoffice.core.ai.eval.GateEvals;
import com.boxoffice.core.ai.eval.Scorecard;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/**
 * The evals against the real Claude. It costs money and needs the internet,
 * so it's skipped unless you ask for it:
 *
 * <pre>
 * BOXOFFICE_LIVE_EVALS=yes ANTHROPIC_API_KEY=... ../../mvnw test -Dtest=LiveEvalTest -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 *
 * The bar is below 100% on purpose: a real model words things its own way,
 * and one odd answer in eight shouldn't fail a build. A drop well below the
 * bar after a change is the signal to look at.
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "BOXOFFICE_LIVE_EVALS", matches = "yes")
class LiveEvalTest {

    @TempDir
    Path folder;

    @Test
    void claudePassesTheGateEvals() {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isBlank(), "Set ANTHROPIC_API_KEY too");

        try (AnthropicClaude claude = AnthropicClaude.connect(apiKey, null)) {
            Scorecard card = new EvalRunner(folder).run(GateEvals.cases(), claude);
            System.out.print(card.render());
            assertThat(card.passRate()).as(card.render()).isGreaterThanOrEqualTo(0.75);
        }
    }
}
