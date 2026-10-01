package com.pokernight.web.api;

import com.pokernight.application.room.MemberStatus;
import com.pokernight.application.room.RoomCode;
import com.pokernight.application.room.RoomConfig;
import com.pokernight.application.room.RoomMemberView;
import com.pokernight.application.room.RoomView;
import com.pokernight.engine.model.GamePhase;
import com.pokernight.engine.state.PublicGameState;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import com.pokernight.web.socket.SocketEnvelope;

class LobbyNotificationRouterTest {
    @Test
    void sendsLobbyOnlyToHostAndStatusOnlyToAffectedPlayer() {
        SimpMessagingTemplate messages = mock(SimpMessagingTemplate.class);
        var router = new LobbyNotificationRouter(messages);
        RoomView room = room(MemberStatus.APPROVED);

        router.membershipChanged(room, "p1", "approved");

        verify(messages).convertAndSendToUser("host", "/queue/lobby",
            RoomApiModels.LobbyResponse.from(room));
        verify(messages).convertAndSendToUser("p1", "/queue/status",
            new SocketEnvelope("MEMBERSHIP_STATUS",
                new RoomApiModels.MembershipStatusNotification(
                    "ABC123", "p1", MemberStatus.APPROVED, 0, "approved")));
    }

    @Test
    void roomClosureIsSentPrivatelyToHostAndEveryRegisteredPlayer() {
        SimpMessagingTemplate messages = mock(SimpMessagingTemplate.class);
        var router = new LobbyNotificationRouter(messages);
        var notification = new SocketEnvelope("ROOM_CLOSED",
            new RoomApiModels.RoomClosedNotification("ABC123", "The host closed the room"));

        router.roomClosed(room(MemberStatus.PENDING));

        verify(messages).convertAndSendToUser("host", "/queue/status", notification);
        verify(messages).convertAndSendToUser("p1", "/queue/status", notification);
    }

    private static RoomView room(MemberStatus status) {
        Instant now = Instant.parse("2026-08-13T00:00:00Z");
        return new RoomView(
            new RoomCode("ABC123"), "host", "Host", RoomConfig.standard(),
            List.of(new RoomMemberView("p1", "Alice", status, status == MemberStatus.APPROVED ? 0 : null)),
            Set.of(), new PublicGameState(GamePhase.WAITING_FOR_PLAYERS, 0, false,
                List.of(), List.of(), java.util.Map.of(), 0, 0, 50, null), now, now
        );
    }
}
