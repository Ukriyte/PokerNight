package com.pokernight.engine.betting;

import java.util.List;

public record BettingRoundSnapshot(
    List<PlayerBetSnapshot> players,
    String currentPlayerId,
    long currentBet,
    long minimumRaise,
    boolean complete,
    String lastStandingPlayerId
) {
    public BettingRoundSnapshot {
        players = List.copyOf(players);
    }
}
