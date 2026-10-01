package com.pokernight.application.bot;

import com.pokernight.engine.model.PlayerAction;

import java.util.Objects;

/** Conservative baseline bot. It always produces one legal engine decision. */
public final class BaselineBotDecisionStrategy implements BotDecisionStrategy {
    private final DecisionRandom random;

    public BaselineBotDecisionStrategy(DecisionRandom random) {
        this.random = Objects.requireNonNull(random);
    }

    @Override
    public BotDecision decide(BotDecisionContext context) {
        var legal = context.validActions();
        int roll = random.nextInt(100);

        if (legal.contains(PlayerAction.RAISE) && roll < 12) {
            long target = Math.max(context.currentBet() + context.minimumRaise(), context.minimumRaise());
            long maximum = context.playerContribution() + context.playerChips();
            if (target < maximum) return new BotDecision(PlayerAction.RAISE, target);
        }
        if (legal.contains(PlayerAction.CHECK)) return new BotDecision(PlayerAction.CHECK, null);
        if (legal.contains(PlayerAction.CALL) && roll < 88) return new BotDecision(PlayerAction.CALL, null);
        if (legal.contains(PlayerAction.FOLD)) return new BotDecision(PlayerAction.FOLD, null);
        if (legal.contains(PlayerAction.ALL_IN)) return new BotDecision(PlayerAction.ALL_IN, null);
        PlayerAction fallback = legal.iterator().next();
        return new BotDecision(fallback, fallback == PlayerAction.RAISE
            ? Math.min(context.playerContribution() + context.playerChips(), context.currentBet() + context.minimumRaise())
            : null);
    }
}
