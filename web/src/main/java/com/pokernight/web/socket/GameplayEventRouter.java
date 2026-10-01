package com.pokernight.web.socket;

import com.pokernight.application.room.GameCommandResult;
import com.pokernight.application.room.GameplayEvent;
import com.pokernight.engine.event.GameEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Converts transport-neutral gameplay events into public room or private user destinations. */
@Component
public final class GameplayEventRouter {
    private final SimpMessagingTemplate messages;

    public GameplayEventRouter(SimpMessagingTemplate messages) {
        this.messages = messages;
    }

    public void route(GameCommandResult result) {
        String code = result.roomCode().value();
        for (GameplayEvent gameplayEvent : result.events()) {
            if (gameplayEvent instanceof GameplayEvent.NewGameStarted reset) {
                messages.convertAndSend("/topic/rooms/" + code + "/events",
                    new SocketEnvelope("NEW_GAME_STARTED", reset));
                continue;
            }

            GameEvent event = ((GameplayEvent.Engine) gameplayEvent).event();
            if (event instanceof GameEvent.CardsDealt cards) {
                messages.convertAndSendToUser(cards.playerId(), "/queue/cards",
                    new SocketEnvelope("CARDS_DEALT", cards));
            } else if (event instanceof GameEvent.ActionRequired turn) {
                messages.convertAndSendToUser(turn.playerId(), "/queue/turn",
                    new SocketEnvelope("ACTION_REQUIRED", turn));
            } else {
                messages.convertAndSend("/topic/rooms/" + code + "/events",
                    new SocketEnvelope(eventType(event), event));
            }
        }
        messages.convertAndSend("/topic/rooms/" + code + "/state", result.state());
    }

    private static String eventType(GameEvent event) {
        if (event instanceof GameEvent.HandStarted) return "HAND_STARTED";
        if (event instanceof GameEvent.PlayerActed) return "PLAYER_ACTED";
        if (event instanceof GameEvent.StreetDealt) return "STREET_DEALT";
        if (event instanceof GameEvent.HandCompleted) return "HAND_COMPLETED";
        if (event instanceof GameEvent.GameCompleted) return "GAME_COMPLETED";
        throw new IllegalArgumentException("unsupported public game event: " + event.getClass().getName());
    }
}
