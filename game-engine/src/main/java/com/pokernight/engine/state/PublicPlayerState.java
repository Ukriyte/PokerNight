package com.pokernight.engine.state;

import com.pokernight.engine.betting.BettingStatus;

public record PublicPlayerState(
    String playerId,
    String displayName,
    int seatNumber,
    long chips,
    long streetContribution,
    BettingStatus bettingStatus,
    boolean dealer,
    boolean smallBlind,
    boolean bigBlind
) { }
