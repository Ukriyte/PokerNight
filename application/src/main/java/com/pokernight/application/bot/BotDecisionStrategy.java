package com.pokernight.application.bot;

/** Pure decision port. Implementations must not mutate rooms, engines, clocks, or sockets. */
@FunctionalInterface
public interface BotDecisionStrategy {
    BotDecision decide(BotDecisionContext context);
}
