package com.pokernight.engine.state;

import com.pokernight.engine.model.Card;
import com.pokernight.engine.model.GamePhase;
import com.pokernight.engine.hand.HoleCards;

import java.util.List;
import java.util.Map;

public record PublicGameState(
    GamePhase phase,
    long handNumber,
    boolean handInProgress,
    List<PublicPlayerState> players,
    List<Card> communityCards,
    Map<String, HoleCards> revealedHoleCards,
    long pot,
    long currentBet,
    long minimumRaise,
    String currentPlayerId
) {
    public PublicGameState {
        players = List.copyOf(players);
        communityCards = List.copyOf(communityCards);
        revealedHoleCards = Map.copyOf(revealedHoleCards);
    }
}
