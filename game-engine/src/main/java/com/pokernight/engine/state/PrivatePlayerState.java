package com.pokernight.engine.state;

import com.pokernight.engine.hand.HoleCards;
import com.pokernight.engine.model.PlayerAction;

import java.util.Optional;
import java.util.Set;

public record PrivatePlayerState(
    PublicGameState game,
    String playerId,
    Optional<HoleCards> holeCards,
    Set<PlayerAction> validActions
) {
    public PrivatePlayerState {
        holeCards = holeCards == null ? Optional.empty() : holeCards;
        validActions = Set.copyOf(validActions);
    }
}
