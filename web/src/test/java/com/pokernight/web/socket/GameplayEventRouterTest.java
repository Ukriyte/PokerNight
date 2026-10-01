package com.pokernight.web.socket;

import com.pokernight.application.room.GameCommandResult;
import com.pokernight.application.room.GameplayEvent;
import com.pokernight.application.room.RoomCode;
import com.pokernight.engine.event.GameEvent;
import com.pokernight.engine.hand.HoleCards;
import com.pokernight.engine.model.Card;
import com.pokernight.engine.model.GamePhase;
import com.pokernight.engine.model.PlayerAction;
import com.pokernight.engine.model.Rank;
import com.pokernight.engine.model.Suit;
import com.pokernight.engine.state.PublicGameState;
import com.pokernight.engine.table.TablePositions;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class GameplayEventRouterTest {
    @Test
    void routesCardsAndTurnsPrivatelyWhilePublishingOnlySafeEventsAndState() {
        SimpMessagingTemplate messages = mock(SimpMessagingTemplate.class);
        GameplayEventRouter router = new GameplayEventRouter(messages);
        GameEvent.CardsDealt cards = new GameEvent.CardsDealt(
            "p1", new HoleCards(card(Rank.ACE, Suit.SPADES), card(Rank.KING, Suit.SPADES))
        );
        GameEvent.ActionRequired turn = new GameEvent.ActionRequired(
            "p2", Set.of(PlayerAction.CALL, PlayerAction.FOLD), 50, 25, 50, 75, 975
        );
        GameEvent.HandStarted started = new GameEvent.HandStarted(
            1, new TablePositions("p1", "p2", "p3", "p1", "p2", List.of("p1", "p2", "p3"))
        );
        PublicGameState state = state();
        GameCommandResult result = new GameCommandResult(
            new RoomCode("ABC123"), state,
            List.of(new GameplayEvent.Engine(cards), new GameplayEvent.Engine(turn), new GameplayEvent.Engine(started))
        );

        router.route(result);

        verify(messages).convertAndSendToUser("p1", "/queue/cards",
            new SocketEnvelope("CARDS_DEALT", cards));
        verify(messages).convertAndSendToUser("p2", "/queue/turn",
            new SocketEnvelope("ACTION_REQUIRED", turn));
        verify(messages).convertAndSend("/topic/rooms/ABC123/events",
            new SocketEnvelope("HAND_STARTED", started));
        verify(messages).convertAndSend("/topic/rooms/ABC123/state", state);
        verify(messages, never()).convertAndSend("/topic/rooms/ABC123/events", cards);
        verify(messages, never()).convertAndSend("/topic/rooms/ABC123/events", turn);
    }

    private static PublicGameState state() {
        return new PublicGameState(
            GamePhase.PRE_FLOP, 1, true, List.of(), List.of(), java.util.Map.of(), 75, 50, 50, "p1"
        );
    }

    private static Card card(Rank rank, Suit suit) {
        return new Card(rank, suit);
    }
}
