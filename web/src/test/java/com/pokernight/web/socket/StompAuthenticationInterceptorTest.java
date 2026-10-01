package com.pokernight.web.socket;

import com.pokernight.application.room.RoomCode;
import com.pokernight.application.room.RoomManager;
import com.pokernight.application.room.RoomConfig;
import com.pokernight.web.session.ApiAuthenticationException;
import com.pokernight.web.session.InMemorySessionService;
import com.pokernight.web.session.SessionRole;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class StompAuthenticationInterceptorTest {
    @Test
    void authenticatesConnectFrameFromBearerHeader() {
        InMemorySessionService sessions = new InMemorySessionService();
        RoomCode roomCode = new RoomCode("ABC123");
        String token = sessions.issue("p1", roomCode, SessionRole.PLAYER);
        StompAuthenticationInterceptor interceptor = new StompAuthenticationInterceptor(sessions, mock(RoomManager.class));
        Message<byte[]> connect = connect("Bearer " + token);

        Message<?> result = interceptor.preSend(connect, mock(org.springframework.messaging.MessageChannel.class));

        assertThat(StompHeaderAccessor.wrap(result).getUser())
            .isEqualTo(new StompPrincipal(sessions.require("Bearer " + token)));
    }

    @Test
    void rejectsConnectWithoutToken() {
        StompAuthenticationInterceptor interceptor = new StompAuthenticationInterceptor(
            new InMemorySessionService(), mock(RoomManager.class));

        assertThatThrownBy(() -> interceptor.preSend(
            connect(null), mock(org.springframework.messaging.MessageChannel.class)
        )).isInstanceOf(ApiAuthenticationException.class);
    }

    @Test
    void sameBearerTokenCanAuthenticateAReplacementSocketAfterDisconnect() {
        InMemorySessionService sessions = new InMemorySessionService();
        RoomCode roomCode = new RoomCode("ABC123");
        String token = sessions.issue("p1", roomCode, SessionRole.PLAYER);
        StompAuthenticationInterceptor interceptor = new StompAuthenticationInterceptor(sessions, mock(RoomManager.class));

        Message<?> first = interceptor.preSend(connect("Bearer " + token),
            mock(org.springframework.messaging.MessageChannel.class));
        Message<?> replacement = interceptor.preSend(connect("Bearer " + token),
            mock(org.springframework.messaging.MessageChannel.class));

        assertThat(StompHeaderAccessor.wrap(replacement).getUser())
            .isEqualTo(StompHeaderAccessor.wrap(first).getUser());
    }

    @Test
    void approvedPlayerMaySubscribeToOwnRoomButNotAnotherRoom() {
        RoomManager rooms = new RoomManager(() -> new RoomCode("ABC123"), java.time.Clock.systemUTC());
        rooms.createRoom("host", "Host", RoomConfig.standard());
        rooms.requestToJoin(new RoomCode("ABC123"), "p1", "Alice");
        rooms.approvePlayer(new RoomCode("ABC123"), "host", "p1", 0);
        InMemorySessionService sessions = new InMemorySessionService();
        String token = sessions.issue("p1", new RoomCode("ABC123"), SessionRole.PLAYER);
        var principal = new StompPrincipal(sessions.require("Bearer " + token));
        var interceptor = new StompAuthenticationInterceptor(sessions, rooms);

        assertThat(interceptor.preSend(frame(StompCommand.SUBSCRIBE,
            "/topic/rooms/ABC123/state", principal), mock(org.springframework.messaging.MessageChannel.class)))
            .isNotNull();
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE,
            "/topic/rooms/DEF456/state", principal), mock(org.springframework.messaging.MessageChannel.class)))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void pendingOrRevokedPlayerCannotAccessGameplayDestinations() {
        RoomManager rooms = new RoomManager(() -> new RoomCode("ABC123"), java.time.Clock.systemUTC());
        rooms.createRoom("host", "Host", RoomConfig.standard());
        rooms.requestToJoin(new RoomCode("ABC123"), "p1", "Alice");
        InMemorySessionService sessions = new InMemorySessionService();
        String token = sessions.issue("p1", new RoomCode("ABC123"), SessionRole.PLAYER);
        var principal = new StompPrincipal(sessions.require("Bearer " + token));
        var interceptor = new StompAuthenticationInterceptor(sessions, rooms);

        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE,
            "/topic/rooms/ABC123/events", principal), mock(org.springframework.messaging.MessageChannel.class)))
            .isInstanceOf(MessagingException.class);
        assertThat(interceptor.preSend(frame(StompCommand.SUBSCRIBE,
            "/user/queue/status", principal), mock(org.springframework.messaging.MessageChannel.class))).isNotNull();

        sessions.revokeSubject(new RoomCode("ABC123"), "p1");
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE,
            "/user/queue/status", principal), mock(org.springframework.messaging.MessageChannel.class)))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void clientsCannotSubscribeDirectlyToBrokerQueues() {
        InMemorySessionService sessions = new InMemorySessionService();
        String token = sessions.issue("host", new RoomCode("ABC123"), SessionRole.HOST);
        var principal = new StompPrincipal(sessions.require("Bearer " + token));
        var interceptor = new StompAuthenticationInterceptor(sessions, mock(RoomManager.class));

        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE,
            "/queue/cards", principal), mock(org.springframework.messaging.MessageChannel.class)))
            .isInstanceOf(MessagingException.class);
    }

    private static Message<byte[]> connect(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null) accessor.setNativeHeader(HttpHeaders.AUTHORIZATION, authorization);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Message<byte[]> frame(StompCommand command, String destination, StompPrincipal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setUser(principal);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
