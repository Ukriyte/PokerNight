package com.pokernight.engine.betting;

public record PlayerBetSnapshot(
    String playerId,
    long remainingChips,
    long streetContribution,
    BettingStatus status
) { }
