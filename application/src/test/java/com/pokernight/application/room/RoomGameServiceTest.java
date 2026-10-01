package com.pokernight.application.room;

import com.pokernight.engine.GameEngine;
import com.pokernight.engine.deck.Deck;
import com.pokernight.engine.evaluation.HandEvaluator;
import com.pokernight.engine.event.GameEvent;
import com.pokernight.engine.hand.HandDealer;
import com.pokernight.engine.model.Card;
import com.pokernight.engine.model.GamePhase;
import com.pokernight.engine.model.PlayerAction;
import com.pokernight.engine.model.Rank;
import com.pokernight.engine.model.Suit;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoomGameServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void hostStartsHandAndPlayersReceiveOnlyTheirPrivateState() {
        Fixture fixture = fixture(RoomConfig.standard(), GameEngine::new);
        approve(fixture, "p1", "Alice", 0);
        approve(fixture, "p2", "Bob", 1);

        GameCommandResult result = fixture.games.startHand(fixture.code, "host");

        assertThat(result.state().phase()).isEqualTo(GamePhase.PRE_FLOP);
        assertThat(result.events()).filteredOn(event ->
            event instanceof GameplayEvent.Engine wrapped
                && wrapped.event() instanceof GameEvent.CardsDealt
        ).hasSize(2);
        assertThat(fixture.games.getPrivateState(fixture.code, "p1").holeCards()).isPresent();
        assertThat(fixture.games.getPrivateState(fixture.code, "p2").holeCards()).isPresent();
        assertThat(fixture.games.getPrivateState(fixture.code, "p1").holeCards())
            .isNotEqualTo(fixture.games.getPrivateState(fixture.code, "p2").holeCards());
    }

    @Test
    void completesAHandThroughCommandsAndBuildsNewestFirstHistory() {
        Fixture fixture = fixture(RoomConfig.standard(), GameEngine::new);
        approve(fixture, "p1", "Alice", 0);
        approve(fixture, "p2", "Bob", 1);
        approve(fixture, "p3", "Cara", 2);
        GameCommandResult result = fixture.games.startHand(fixture.code, "host");

        int safety = 100;
        while (result.state().handInProgress() && safety-- > 0) {
            String current = result.state().currentPlayerId();
            var actions = fixture.games.getPrivateState(fixture.code, current).validActions();
            PlayerAction action = actions.contains(PlayerAction.CHECK) ? PlayerAction.CHECK : PlayerAction.CALL;
            result = fixture.games.submitAction(fixture.code, current, action, null);
        }

        assertThat(safety).isPositive();
        assertThat(result.state().phase()).isEqualTo(GamePhase.HAND_COMPLETE);
        List<HandHistoryEntry> history = fixture.games.getHistory(fixture.code);
        assertThat(history).isNotEmpty();
        assertThat(history.getFirst().eventType()).isEqualTo("HAND_COMPLETED");
        assertThat(history.getLast().eventType()).isEqualTo("HAND_STARTED");
        assertThat(history).extracting(HandHistoryEntry::sequence).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test
    void rejectsUnauthorizedHostPendingPlayerWrongTurnAndCrossRoomAction() {
        Fixture fixture = fixture(RoomConfig.standard(), GameEngine::new);
        approve(fixture, "p1", "Alice", 0);
        approve(fixture, "p2", "Bob", 1);
        fixture.rooms.requestToJoin(fixture.code, "pending", "Pending");

        assertGameplayFailure(GameplayFailure.NOT_HOST,
            () -> fixture.games.startHand(fixture.code, "p1"));
        fixture.games.startHand(fixture.code, "host");
        assertGameplayFailure(GameplayFailure.PLAYER_NOT_APPROVED,
            () -> fixture.games.submitAction(fixture.code, "pending", PlayerAction.FOLD, null));

        String current = fixture.rooms.getRoom(fixture.code).game().currentPlayerId();
        String wrong = current.equals("p1") ? "p2" : "p1";
        assertGameplayFailure(GameplayFailure.ILLEGAL_ACTION,
            () -> fixture.games.submitAction(fixture.code, wrong, PlayerAction.FOLD, null));

        RoomView other = fixture.rooms.createRoom("other-host", "Other", RoomConfig.standard());
        fixture.rooms.requestToJoin(other.code(), "outsider", "Outsider");
        fixture.rooms.approvePlayer(other.code(), "other-host", "outsider", 0);
        assertGameplayFailure(GameplayFailure.PLAYER_NOT_APPROVED,
            () -> fixture.games.submitAction(fixture.code, "outsider", PlayerAction.FOLD, null));
    }

    @Test
    void startsFreshGameAfterGameCompletionAndRestoresConfiguredStacks() {
        RoomConfig config = new RoomConfig(100, RoomConfig.standard().blinds(), 2, 0);
        Fixture fixture = fixture(config, deterministicHeadsUpFactory());
        approve(fixture, "p1", "Alice", 0);
        approve(fixture, "p2", "Bob", 1);
        fixture.games.startHand(fixture.code, "host");

        fixture.games.submitAction(fixture.code, "p1", PlayerAction.ALL_IN, null);
        GameCommandResult completed = fixture.games.submitAction(fixture.code, "p2", PlayerAction.CALL, null);
        assertThat(completed.state().phase()).isEqualTo(GamePhase.GAME_COMPLETE);
        assertThat(fixture.games.getHistory(fixture.code)).isNotEmpty();

        GameCommandResult reset = fixture.games.startNewGame(fixture.code, "host");

        assertThat(reset.state().phase()).isEqualTo(GamePhase.WAITING_FOR_PLAYERS);
        assertThat(reset.state().players()).allSatisfy(player -> assertThat(player.chips()).isEqualTo(100));
        assertThat(reset.events()).containsExactly(new GameplayEvent.NewGameStarted(reset.state()));
        assertThat(fixture.games.getHistory(fixture.code)).isEmpty();
    }

    @Test
    void newGameIsRejectedBeforeCurrentGameEnds() {
        Fixture fixture = fixture(RoomConfig.standard(), GameEngine::new);
        approve(fixture, "p1", "Alice", 0);
        approve(fixture, "p2", "Bob", 1);

        assertGameplayFailure(GameplayFailure.GAME_NOT_READY,
            () -> fixture.games.startNewGame(fixture.code, "host"));
    }

    private static Fixture fixture(RoomConfig config, GameEngineFactory factory) {
        InMemoryRoomRepository repository = new InMemoryRoomRepository();
        AtomicInteger codeSequence = new AtomicInteger(0xABC123);
        RoomManager rooms = new RoomManager(
            repository,
            () -> new RoomCode("%06X".formatted(codeSequence.getAndIncrement())),
            CLOCK,
            factory
        );
        RoomView room = rooms.createRoom("host", "Host", config);
        return new Fixture(rooms, new RoomGameService(rooms, CLOCK), room.code());
    }

    private static void approve(Fixture fixture, String id, String name, int seat) {
        fixture.rooms.requestToJoin(fixture.code, id, name);
        fixture.rooms.approvePlayer(fixture.code, "host", id, seat);
    }

    private static GameEngineFactory deterministicHeadsUpFactory() {
        return rules -> {
            List<Card> topCards = List.of(
                card(Rank.ACE, Suit.SPADES), card(Rank.KING, Suit.SPADES),
                card(Rank.ACE, Suit.HEARTS), card(Rank.KING, Suit.HEARTS),
                card(Rank.SIX, Suit.CLUBS),
                card(Rank.TWO, Suit.CLUBS), card(Rank.THREE, Suit.DIAMONDS), card(Rank.SEVEN, Suit.SPADES),
                card(Rank.EIGHT, Suit.CLUBS), card(Rank.NINE, Suit.DIAMONDS),
                card(Rank.TEN, Suit.CLUBS), card(Rank.QUEEN, Suit.DIAMONDS)
            );
            Deck deck = new Deck(cards -> {
                List<Card> ordered = new ArrayList<>(new LinkedHashSet<>(topCards));
                cards.stream().filter(card -> !ordered.contains(card)).forEach(ordered::add);
                cards.clear();
                cards.addAll(ordered);
            });
            return new GameEngine(rules, new HandDealer(deck), new HandEvaluator());
        };
    }

    private static Card card(Rank rank, Suit suit) {
        return new Card(rank, suit);
    }

    private static void assertGameplayFailure(GameplayFailure expected, Runnable operation) {
        assertThatThrownBy(operation::run)
            .isInstanceOfSatisfying(GameplayOperationException.class,
                exception -> assertThat(exception.failure()).isEqualTo(expected));
    }

    private record Fixture(RoomManager rooms, RoomGameService games, RoomCode code) { }
}
