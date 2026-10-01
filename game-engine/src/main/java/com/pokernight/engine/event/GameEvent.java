package com.pokernight.engine.event;

import com.pokernight.engine.evaluation.EvaluatedHand;
import com.pokernight.engine.hand.HoleCards;
import com.pokernight.engine.model.Card;
import com.pokernight.engine.model.GamePhase;
import com.pokernight.engine.model.PlayerAction;
import com.pokernight.engine.table.TablePositions;

import java.util.List;
import java.util.Map;
import java.util.Set;

public sealed interface GameEvent {
    record HandStarted(long handNumber, TablePositions positions) implements GameEvent { }

    /** Private event: the application layer must route it only to playerId. */
    record CardsDealt(String playerId, HoleCards cards) implements GameEvent { }

    /** Private event: the application layer must route it only to playerId. */
    record ActionRequired(
        String playerId,
        Set<PlayerAction> validActions,
        long currentBet,
        long playerContribution,
        long minimumRaise,
        long pot,
        long playerChips
    ) implements GameEvent {
        public ActionRequired {
            validActions = Set.copyOf(validActions);
        }
    }

    record PlayerActed(String playerId, PlayerAction action, Long amount, long committedChips) implements GameEvent { }

    record StreetDealt(GamePhase street, List<Card> communityCards) implements GameEvent {
        public StreetDealt {
            communityCards = List.copyOf(communityCards);
        }
    }

    record HandCompleted(
        Map<String, Long> payouts,
        Map<String, EvaluatedHand> shownHands,
        long totalPot,
        List<Card> communityCards
    ) implements GameEvent {
        public HandCompleted {
            payouts = Map.copyOf(payouts);
            shownHands = Map.copyOf(shownHands);
            communityCards = List.copyOf(communityCards);
        }
    }

    record GameCompleted(String winnerId) implements GameEvent { }
}
