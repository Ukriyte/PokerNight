package com.pokernight.application.bot;

import com.pokernight.engine.hand.HoleCards;
import com.pokernight.engine.model.Card;
import com.pokernight.engine.model.GamePhase;
import com.pokernight.engine.model.PlayerAction;

import java.util.List;
import java.util.Set;

/** Complete immutable information available to one bot for one turn. */
public record BotDecisionContext(
    String playerId,
    HoleCards holeCards,
    List<Card> communityCards,
    GamePhase phase,
    Set<PlayerAction> validActions,
    long currentBet,
    long playerContribution,
    long minimumRaise,
    long pot,
    long playerChips,
    int activeOpponentCount
) {
    public BotDecisionContext {
        communityCards = List.copyOf(communityCards);
        validActions = Set.copyOf(validActions);
        if (validActions.isEmpty()) throw new IllegalArgumentException("validActions must not be empty");
    }
}
