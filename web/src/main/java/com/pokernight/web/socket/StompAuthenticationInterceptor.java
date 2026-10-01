package com.pokernight.web.socket;

import com.pokernight.application.room.MemberStatus;
import com.pokernight.application.room.RoomCode;
import com.pokernight.application.room.RoomManager;
import com.pokernight.web.session.ApiPrincipal;
import com.pokernight.web.session.InMemorySessionService;
import com.pokernight.web.session.SessionRole;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Authenticates CONNECT and authorizes every client SEND/SUBSCRIBE destination. */
@Component
public final class StompAuthenticationInterceptor implements ChannelInterceptor {
    private static final Pattern ROOM_TOPIC = Pattern.compile("^/topic/rooms/([A-Z0-9]{6})/(state|events)$");
    private static final Pattern ROOM_COMMAND = Pattern.compile(
        "^/app/rooms/([A-Z0-9]{6})/(start-hand|new-game|actions)$"
    );
    private static final Set<String> SHARED_PRIVATE_QUEUES = Set.of(
        "/user/queue/status", "/user/queue/commands", "/user/queue/errors"
    );
    private final InMemorySessionService sessions;
    private final RoomManager rooms;
    private final Map<String, ApiPrincipal> socketPrincipals = new ConcurrentHashMap<>();

    public StompAuthenticationInterceptor(InMemorySessionService sessions, RoomManager rooms) {
        this.sessions = sessions;
        this.rooms = rooms;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) accessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            ApiPrincipal principal = sessions.require(accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION));
            accessor.setUser(new StompPrincipal(principal));
            if (accessor.getSessionId() != null) {
                socketPrincipals.put(accessor.getSessionId(), principal);
            }
            return message;
        }
        if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            if (accessor.getSessionId() != null) socketPrincipals.remove(accessor.getSessionId());
            return message;
        }
        if (accessor.getCommand() == null) return message;

        ApiPrincipal actor = principal(accessor);
        if (!sessions.isActive(actor)) deny("session is expired or revoked");
        if (!(accessor.getUser() instanceof StompPrincipal)) {
            accessor.setUser(new StompPrincipal(actor));
        }
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) authorizeSubscribe(actor, accessor.getDestination());
        if (StompCommand.SEND.equals(accessor.getCommand())) authorizeSend(actor, accessor.getDestination());
        return message;
    }

    private void authorizeSubscribe(ApiPrincipal actor, String destination) {
        if (destination == null) deny("subscription destination is required");
        if (SHARED_PRIVATE_QUEUES.contains(destination)) return;
        if (destination.equals("/user/queue/lobby")) {
            if (actor.role() != SessionRole.HOST) deny("only hosts may subscribe to lobby updates");
            return;
        }
        if (destination.equals("/user/queue/cards") || destination.equals("/user/queue/turn")) {
            if (actor.role() != SessionRole.PLAYER || !isApproved(actor)) {
                deny("only approved players may subscribe to private gameplay updates");
            }
            return;
        }
        var topic = ROOM_TOPIC.matcher(destination);
        if (topic.matches()) {
            requireSameRoom(actor, topic.group(1));
            if (actor.role() == SessionRole.PLAYER && !isApproved(actor)) {
                deny("player must be approved before subscribing to room gameplay");
            }
            return;
        }
        deny("subscription destination is not allowed");
    }

    private void authorizeSend(ApiPrincipal actor, String destination) {
        if (destination == null) deny("command destination is required");
        var command = ROOM_COMMAND.matcher(destination);
        if (!command.matches()) deny("command destination is not allowed");
        requireSameRoom(actor, command.group(1));
        String operation = command.group(2);
        if ((operation.equals("start-hand") || operation.equals("new-game"))
            && actor.role() != SessionRole.HOST) deny("host permission is required");
        if (operation.equals("actions") && (actor.role() != SessionRole.PLAYER || !isApproved(actor))) {
            deny("approved player permission is required");
        }
    }

    private boolean isApproved(ApiPrincipal actor) {
        return rooms.getRoom(actor.roomCode()).members().stream().anyMatch(member ->
            member.playerId().equals(actor.subjectId()) && member.status() == MemberStatus.APPROVED
        );
    }

    private void requireSameRoom(ApiPrincipal actor, String code) {
        if (!actor.roomCode().equals(new RoomCode(code))) deny("destination belongs to another room");
    }

    private ApiPrincipal principal(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof StompPrincipal stomp) return stomp.apiPrincipal();
        ApiPrincipal remembered = socketPrincipals.get(accessor.getSessionId());
        if (remembered != null) return remembered;
        throw new MessagingException("authenticated STOMP principal is required");
    }

    private static void deny(String message) { throw new MessagingException(message); }
}
