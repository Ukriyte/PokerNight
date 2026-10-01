package com.pokernight.engine;

import com.pokernight.engine.deck.Deck;
import com.pokernight.engine.evaluation.HandEvaluator;
import com.pokernight.engine.event.GameEvent;
import com.pokernight.engine.hand.HandDealer;
import com.pokernight.engine.model.Card;
import com.pokernight.engine.model.GamePhase;
import com.pokernight.engine.model.PlayerAction;
import com.pokernight.engine.model.PlayerDecision;
import com.pokernight.engine.model.Rank;
import com.pokernight.engine.model.Suit;
import com.pokernight.engine.state.PublicPlayerState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GameEngineTest {
    @Test
    void playsACompletePassiveHandAndKeepsPrivateCardsOutOfPublicState() {
        GameEngine game = standardGame();
        seatThree(game, 1_000, 1_000, 1_000);

        game.startHand();
        List<GameEvent> startEvents = game.drainEvents();

        assertThat(startEvents).filteredOn(GameEvent.CardsDealt.class::isInstance).hasSize(3);
        assertThat(game.publicState().communityCards()).isEmpty();
        assertThat(game.publicState().revealedHoleCards()).isEmpty();
        assertThat(game.privateState("p1").holeCards()).isPresent();
        playPassively(game);

        assertThat(game.publicState().phase()).isEqualTo(GamePhase.HAND_COMPLETE);
        assertThat(game.publicState().communityCards()).hasSize(5);
        assertThat(game.publicState().revealedHoleCards()).containsOnlyKeys("p1", "p2", "p3");
        assertThat(totalChips(game)).isEqualTo(3_000L);
        assertThat(game.drainEvents()).anyMatch(GameEvent.HandCompleted.class::isInstance);
    }

    @Test
    void awardsThePotImmediatelyWhenEveryoneElseFolds() {
        GameEngine game = standardGame();
        seatThree(game, 1_000, 1_000, 1_000);
        game.startHand();
        game.drainEvents();

        game.submit(new PlayerDecision("p1", PlayerAction.FOLD, null));
        game.submit(new PlayerDecision("p2", PlayerAction.FOLD, null));

        assertThat(game.publicState().phase()).isEqualTo(GamePhase.HAND_COMPLETE);
        assertThat(chips(game)).containsEntry("p3", 1_025L);
        assertThat(totalChips(game)).isEqualTo(3_000L);
    }

    @Test
    void settlesMainPotSidePotAndUncalledExcessForThreeAllIns() {
        GameEngine game = gameWithTopCards(List.of(
            card(Rank.KING, Suit.HEARTS), card(Rank.QUEEN, Suit.HEARTS), card(Rank.ACE, Suit.SPADES),
            card(Rank.KING, Suit.DIAMONDS), card(Rank.QUEEN, Suit.DIAMONDS), card(Rank.ACE, Suit.HEARTS),
            card(Rank.FIVE, Suit.CLUBS),
            card(Rank.TWO, Suit.CLUBS), card(Rank.THREE, Suit.DIAMONDS), card(Rank.SEVEN, Suit.SPADES),
            card(Rank.SIX, Suit.CLUBS), card(Rank.EIGHT, Suit.CLUBS),
            card(Rank.TEN, Suit.CLUBS), card(Rank.NINE, Suit.DIAMONDS)
        ));
        seatThree(game, 100, 300, 500);
        game.startHand();
        game.drainEvents();

        game.submit(new PlayerDecision("p1", PlayerAction.ALL_IN, null));
        game.submit(new PlayerDecision("p2", PlayerAction.ALL_IN, null));
        game.submit(new PlayerDecision("p3", PlayerAction.ALL_IN, null));

        assertThat(game.publicState().phase()).isEqualTo(GamePhase.HAND_COMPLETE);
        assertThat(chips(game)).containsExactlyInAnyOrderEntriesOf(Map.of("p1", 300L, "p2", 400L, "p3", 200L));
        assertThat(totalChips(game)).isEqualTo(900L);
        GameEvent.HandCompleted completed = game.drainEvents().stream()
            .filter(GameEvent.HandCompleted.class::isInstance)
            .map(GameEvent.HandCompleted.class::cast)
            .findFirst().orElseThrow();
        assertThat(completed.totalPot()).isEqualTo(900L);
        assertThat(completed.payouts()).containsExactlyInAnyOrderEntriesOf(
            Map.of("p1", 300L, "p2", 400L, "p3", 200L)
        );
    }

    @Test
    void emitsGameCompletedWhenOnePlayerWinsEveryChip() {
        GameEngine game = gameWithTopCards(List.of(
            card(Rank.ACE, Suit.SPADES), card(Rank.KING, Suit.SPADES),
            card(Rank.ACE, Suit.HEARTS), card(Rank.KING, Suit.HEARTS),
            card(Rank.SIX, Suit.CLUBS),
            card(Rank.TWO, Suit.CLUBS), card(Rank.THREE, Suit.DIAMONDS), card(Rank.SEVEN, Suit.SPADES),
            card(Rank.EIGHT, Suit.CLUBS), card(Rank.NINE, Suit.DIAMONDS),
            card(Rank.TEN, Suit.CLUBS), card(Rank.QUEEN, Suit.DIAMONDS)
        ));
        game.seatPlayer("p1", "One", 0, 100);
        game.seatPlayer("p2", "Two", 1, 100);
        game.startHand();
        game.drainEvents();

        game.submit(new PlayerDecision("p1", PlayerAction.ALL_IN, null));
        game.submit(new PlayerDecision("p2", PlayerAction.CALL, null));

        assertThat(game.publicState().phase()).isEqualTo(GamePhase.GAME_COMPLETE);
        assertThat(chips(game)).containsEntry("p1", 200L).containsEntry("p2", 0L);
        assertThat(game.drainEvents()).anyMatch(event ->
            event instanceof GameEvent.GameCompleted completed && completed.winnerId().equals("p1"));
    }

    @Test
    void rotatesDealerAcrossHandsAndDrainsEventsOnlyOnce() {
        GameEngine game = standardGame();
        seatThree(game, 1_000, 1_000, 1_000);

        game.startHand();
        GameEvent.HandStarted first = startedEvent(game.drainEvents());
        assertThat(game.drainEvents()).isEmpty();
        playPassively(game);
        game.drainEvents();

        game.startHand();
        GameEvent.HandStarted second = startedEvent(game.drainEvents());

        assertThat(first.positions().dealerId()).isEqualTo("p1");
        assertThat(second.positions().dealerId()).isEqualTo("p2");
        assertThat(game.publicState().revealedHoleCards()).isEmpty();
    }

    @Test
    void postsMandatoryBlindsBeforeOfferingAnyPlayerAction() {
        GameEngine game = standardGame();
        seatThree(game, 1_000, 1_000, 1_000);

        game.startHand();

        assertThat(game.publicState().pot()).isEqualTo(75);
        assertThat(game.publicState().currentBet()).isEqualTo(50);
        assertThat(game.publicState().currentPlayerId()).isEqualTo("p1");
        PublicPlayerState smallBlind = game.publicState().players().stream()
            .filter(player -> player.playerId().equals("p2")).findFirst().orElseThrow();
        PublicPlayerState bigBlind = game.publicState().players().stream()
            .filter(player -> player.playerId().equals("p3")).findFirst().orElseThrow();
        assertThat(smallBlind.smallBlind()).isTrue();
        assertThat(smallBlind.chips()).isEqualTo(975);
        assertThat(smallBlind.streetContribution()).isEqualTo(25);
        assertThat(bigBlind.bigBlind()).isTrue();
        assertThat(bigBlind.chips()).isEqualTo(950);
        assertThat(bigBlind.streetContribution()).isEqualTo(50);
        assertThat(game.privateState("p1").validActions())
            .contains(PlayerAction.CALL, PlayerAction.RAISE, PlayerAction.FOLD)
            .doesNotContain(PlayerAction.CHECK);

        game.submit(new PlayerDecision("p1", PlayerAction.CALL, null));
        assertThat(game.privateState("p2").validActions())
            .contains(PlayerAction.CALL, PlayerAction.RAISE, PlayerAction.FOLD)
            .doesNotContain(PlayerAction.CHECK);
        game.submit(new PlayerDecision("p2", PlayerAction.CALL, null));
        assertThat(game.privateState("p3").validActions())
            .contains(PlayerAction.CHECK, PlayerAction.RAISE, PlayerAction.FOLD)
            .doesNotContain(PlayerAction.CALL);
    }

    private static void playPassively(GameEngine game) {
        int safety = 100;
        while (game.publicState().handInProgress() && safety-- > 0) {
            String playerId = game.publicState().currentPlayerId();
            var actions = game.privateState(playerId).validActions();
            PlayerAction action = actions.contains(PlayerAction.CHECK) ? PlayerAction.CHECK : PlayerAction.CALL;
            game.submit(new PlayerDecision(playerId, action, null));
        }
        assertThat(safety).isPositive();
    }

    private static GameEvent.HandStarted startedEvent(List<GameEvent> events) {
        return events.stream().filter(GameEvent.HandStarted.class::isInstance)
            .map(GameEvent.HandStarted.class::cast).findFirst().orElseThrow();
    }

    private static GameEngine standardGame() {
        return new GameEngine(GameRules.standard());
    }

    private static GameEngine gameWithTopCards(List<Card> topCards) {
        Deck deck = new Deck(cards -> {
            List<Card> ordered = new ArrayList<>(new LinkedHashSet<>(topCards));
            cards.stream().filter(card -> !ordered.contains(card)).forEach(ordered::add);
            cards.clear();
            cards.addAll(ordered);
        });
        return new GameEngine(GameRules.standard(), new HandDealer(deck), new HandEvaluator());
    }

    private static void seatThree(GameEngine game, long first, long second, long third) {
        game.seatPlayer("p1", "One", 0, first);
        game.seatPlayer("p2", "Two", 1, second);
        game.seatPlayer("p3", "Three", 2, third);
    }

    private static Map<String, Long> chips(GameEngine game) {
        return game.publicState().players().stream().collect(java.util.stream.Collectors.toMap(
            PublicPlayerState::playerId, PublicPlayerState::chips
        ));
    }

    private static long totalChips(GameEngine game) {
        return game.publicState().players().stream().mapToLong(PublicPlayerState::chips).sum();
    }

    private static Card card(Rank rank, Suit suit) {
        return new Card(rank, suit);
    }
}
