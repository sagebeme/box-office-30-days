package com.boxoffice.core.ai.eval;

import java.util.function.Predicate;

/**
 * One thing a good answer does. The name says what, in words that read well
 * in a failure: "mentions 47", "never mentions Kamau".
 */
public record Grader(String name, Predicate<Transcript> passes) {
}
