package com.pokernight.application.bot;

/** Injected random source keeps strategy tests deterministic. */
@FunctionalInterface
public interface DecisionRandom {
    int nextInt(int bound);
}
