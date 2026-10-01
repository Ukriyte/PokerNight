package com.pokernight.web.api;

import com.pokernight.application.room.RoomMemberView;
import com.pokernight.application.room.RoomView;
import com.pokernight.web.api.RoomApiModels.LobbyResponse;
import com.pokernight.web.api.RoomApiModels.MembershipStatusNotification;
import com.pokernight.web.api.RoomApiModels.RoomClosedNotification;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import com.pokernight.web.socket.SocketEnvelope;

/** Routes sensitive lobby state only to the host and affected player. */
@Component
public final class LobbyNotificationRouter {
    private final SimpMessagingTemplate messages;

    public LobbyNotificationRouter(SimpMessagingTemplate messages) {
        this.messages = messages;
    }

    public void membershipChanged(RoomView room, String playerId, String message) {
        messages.convertAndSendToUser(room.hostId(), "/queue/lobby", LobbyResponse.from(room));
        room.members().stream()
            .filter(member -> member.playerId().equals(playerId))
            .findFirst()
            .ifPresent(member -> sendStatus(room, member, message));
    }

    public void roomClosed(RoomView room) {
        var notification = new SocketEnvelope("ROOM_CLOSED",
            new RoomClosedNotification(room.code().value(), "The host closed the room"));
        messages.convertAndSendToUser(room.hostId(), "/queue/status", notification);
        room.members().stream().map(RoomMemberView::playerId).forEach(playerId ->
            messages.convertAndSendToUser(playerId, "/queue/status", notification)
        );
    }

    private void sendStatus(RoomView room, RoomMemberView member, String message) {
        messages.convertAndSendToUser(member.playerId(), "/queue/status",
            new SocketEnvelope("MEMBERSHIP_STATUS", new MembershipStatusNotification(
                room.code().value(), member.playerId(), member.status(), member.seatNumber(), message
            )));
    }
}
