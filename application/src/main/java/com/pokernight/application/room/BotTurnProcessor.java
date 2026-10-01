package com.pokernight.application.room;

import com.pokernight.application.bot.BotDecisionContext;
import com.pokernight.application.bot.BotDecisionStrategy;
import com.pokernight.engine.betting.BettingStatus;
import java.time.Instant;
import java.util.Optional;

/** Decides and executes one bot turn through the same command path used by a human. */
final class BotTurnProcessor {
    private final BotDecisionStrategy strategy;

    BotTurnProcessor(BotDecisionStrategy strategy) {
        this.strategy = strategy;
    }

    Optional<GameCommandResult> advanceOne(Room room, Instant now) {
        var state = room.view().game();
        String playerId = state.currentPlayerId();
        if (playerId == null || !room.isBot(playerId)) return Optional.empty();

        var privateState = room.privateState(playerId);
        var player = state.players().stream()
            .filter(candidate -> candidate.playerId().equals(playerId))
            .findFirst().orElseThrow();
        var context = new BotDecisionContext(
            playerId,
            privateState.holeCards().orElseThrow(() -> new IllegalStateException("bot has no hole cards")),
            state.communityCards(), state.phase(), privateState.validActions(),
            state.currentBet(), player.streetContribution(), state.minimumRaise(),
            state.pot(), player.chips(),
            (int) state.players().stream()
                .filter(candidate -> !candidate.playerId().equals(playerId))
                .filter(candidate -> candidate.chips() > 0 || candidate.bettingStatus() == BettingStatus.ALL_IN)
                .filter(candidate -> candidate.bettingStatus() != BettingStatus.FOLDED)
                .count()
        );
        var decision = strategy.decide(context);
        if (!privateState.validActions().contains(decision.action())) {
            throw new IllegalStateException("bot strategy returned illegal action " + decision.action());
        }
        return Optional.of(room.submitAction(playerId, decision.action(), decision.amount(), now));
    }
}
