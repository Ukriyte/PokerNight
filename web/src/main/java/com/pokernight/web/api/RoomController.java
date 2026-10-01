package com.pokernight.web.api;

import com.pokernight.application.room.RoomCode;
import com.pokernight.application.room.RoomConfig;
import com.pokernight.application.room.RoomManager;
import com.pokernight.application.room.RoomAppearance;
import com.pokernight.application.room.RoomMemberView;
import com.pokernight.application.room.RoomOperationException;
import com.pokernight.application.room.RoomFailure;
import com.pokernight.application.room.RoomView;
import com.pokernight.application.room.RoomGameService;
import com.pokernight.engine.betting.BlindStructure;
import com.pokernight.web.api.RoomApiModels.ApprovePlayerRequest;
import com.pokernight.web.api.RoomApiModels.CreateRoomRequest;
import com.pokernight.web.api.RoomApiModels.CreateRoomResponse;
import com.pokernight.web.api.RoomApiModels.JoinRoomRequest;
import com.pokernight.web.api.RoomApiModels.JoinRoomResponse;
import com.pokernight.web.api.RoomApiModels.LobbyResponse;
import com.pokernight.web.api.RoomApiModels.MeResponse;
import com.pokernight.web.api.RoomApiModels.PublicRoomResponse;
import com.pokernight.web.api.RoomApiModels.HistoryEntryResponse;
import com.pokernight.web.api.RoomApiModels.HistoryPageResponse;
import com.pokernight.web.api.RoomApiModels.HostBootstrapResponse;
import com.pokernight.web.session.ApiPrincipal;
import com.pokernight.web.session.InMemorySessionService;
import com.pokernight.web.session.SessionRole;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {
    private final RoomManager rooms;
    private final InMemorySessionService sessions;
    private final RoomGameService games;
    private final LobbyNotificationRouter notifications;

    public RoomController(RoomManager rooms, InMemorySessionService sessions, RoomGameService games,
                          LobbyNotificationRouter notifications) {
        this.rooms = rooms;
        this.sessions = sessions;
        this.games = games;
        this.notifications = notifications;
    }

    @PostMapping
    ResponseEntity<CreateRoomResponse> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        String hostId = sessions.newSubjectId();
        RoomConfig config = new RoomConfig(
            request.startingChips(), new BlindStructure(request.smallBlind(), request.bigBlind()),
            request.maximumPlayers(), request.botCount(),
            new RoomAppearance(request.themeId(), request.cardPackId())
        );
        RoomView room = rooms.createRoom(hostId, request.hostName(), config);
        String token = sessions.issue(hostId, room.code(), SessionRole.HOST);
        String code = room.code().value();
        CreateRoomResponse response = new CreateRoomResponse(
            code, token, "/room/" + code, "/room/" + code + "/play", "/room/" + code + "/tv"
        );
        return ResponseEntity.created(URI.create("/api/v1/rooms/" + code)).body(response);
    }

    @GetMapping("/{code}")
    PublicRoomResponse getPublicRoom(@PathVariable("code") String code) {
        return PublicRoomResponse.from(rooms.getRoom(roomCode(code)));
    }

    @GetMapping("/{code}/state")
    PublicRoomResponse getState(@PathVariable("code") String code) {
        return PublicRoomResponse.from(rooms.getRoom(roomCode(code)));
    }

    @GetMapping("/{code}/history")
    HistoryPageResponse getHistory(
        @PathVariable("code") String code,
        @RequestParam(value = "cursor", required = false) Long cursor,
        @RequestParam(value = "limit", defaultValue = "50") int limit,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        RoomCode roomCode = roomCode(code);
        sessions.requireRoomSession(authorization, roomCode);
        return historyPage(roomCode, cursor, limit);
    }

    @GetMapping("/{code}/host-bootstrap")
    HostBootstrapResponse getHostBootstrap(
        @PathVariable("code") String code,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        RoomCode roomCode = roomCode(code);
        sessions.requireHost(authorization, roomCode);
        RoomView room = rooms.getRoom(roomCode);
        return new HostBootstrapResponse(
            PublicRoomResponse.from(room), LobbyResponse.from(room), historyPage(roomCode, null, 50)
        );
    }

    @PostMapping("/{code}/join-requests")
    ResponseEntity<JoinRoomResponse> requestToJoin(
        @PathVariable("code") String code,
        @Valid @RequestBody JoinRoomRequest request
    ) {
        RoomCode roomCode = roomCode(code);
        String playerId = sessions.newSubjectId();
        RoomView room = rooms.requestToJoin(roomCode, playerId, request.displayName());
        String token = sessions.issue(playerId, roomCode, SessionRole.PLAYER);
        notifications.membershipChanged(room, playerId, "Waiting for host approval");
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(new JoinRoomResponse(playerId, token, "PENDING"));
    }

    @GetMapping("/{code}/lobby")
    LobbyResponse getLobby(
        @PathVariable("code") String code,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        RoomCode roomCode = roomCode(code);
        sessions.requireHost(authorization, roomCode);
        return LobbyResponse.from(rooms.getRoom(roomCode));
    }

    @GetMapping("/{code}/me")
    MeResponse getMe(
        @PathVariable("code") String code,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        RoomCode roomCode = roomCode(code);
        ApiPrincipal principal = sessions.requirePlayer(authorization, roomCode);
        RoomView room = rooms.getRoom(roomCode);
        RoomMemberView member = room.members().stream()
            .filter(candidate -> candidate.playerId().equals(principal.subjectId()))
            .findFirst()
            .orElseThrow(() -> new RoomOperationException(RoomFailure.PLAYER_NOT_APPROVED, "player is not registered"));
        return new MeResponse(
            member.playerId(), member.displayName(), member.status(), member.seatNumber(), room.config(), room.game(),
            member.status() == com.pokernight.application.room.MemberStatus.APPROVED
                ? games.getPrivateState(roomCode, member.playerId())
                : null
        );
    }

    @PostMapping("/{code}/members/{playerId}/approval")
    LobbyResponse approvePlayer(
        @PathVariable("code") String code,
        @PathVariable("playerId") String playerId,
        @Valid @RequestBody ApprovePlayerRequest request,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        RoomCode roomCode = roomCode(code);
        ApiPrincipal host = sessions.requireHost(authorization, roomCode);
        RoomView room = rooms.approvePlayer(roomCode, host.subjectId(), playerId, request.seatNumber());
        notifications.membershipChanged(room, playerId, "You were approved to join the table");
        return LobbyResponse.from(room);
    }

    @PostMapping("/{code}/members/{playerId}/rejection")
    LobbyResponse rejectPlayer(
        @PathVariable("code") String code,
        @PathVariable("playerId") String playerId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        RoomCode roomCode = roomCode(code);
        ApiPrincipal host = sessions.requireHost(authorization, roomCode);
        RoomView room = rooms.rejectPlayer(roomCode, host.subjectId(), playerId);
        notifications.membershipChanged(room, playerId, "The host rejected your join request");
        sessions.revokeSubject(roomCode, playerId);
        return LobbyResponse.from(room);
    }

    @DeleteMapping("/{code}/members/{playerId}")
    LobbyResponse kickPlayer(
        @PathVariable("code") String code,
        @PathVariable("playerId") String playerId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        RoomCode roomCode = roomCode(code);
        ApiPrincipal host = sessions.requireHost(authorization, roomCode);
        RoomView room = rooms.kickPlayer(roomCode, host.subjectId(), playerId);
        notifications.membershipChanged(room, playerId, "The host removed you from the table");
        sessions.revokeSubject(roomCode, playerId);
        return LobbyResponse.from(room);
    }

    @DeleteMapping("/{code}")
    ResponseEntity<Void> destroyRoom(
        @PathVariable("code") String code,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        RoomCode roomCode = roomCode(code);
        ApiPrincipal host = sessions.requireHost(authorization, roomCode);
        RoomView room = rooms.getRoom(roomCode);
        rooms.destroyRoom(roomCode, host.subjectId());
        notifications.roomClosed(room);
        sessions.revokeRoom(roomCode);
        return ResponseEntity.noContent().build();
    }

    private RoomCode roomCode(String code) {
        return new RoomCode(code);
    }

    private HistoryPageResponse historyPage(RoomCode roomCode, Long cursor, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        if (cursor != null && cursor < 1) throw new IllegalArgumentException("cursor must be positive");
        var eligible = games.getHistory(roomCode).stream()
            .filter(entry -> cursor == null || entry.sequence() < cursor)
            .toList();
        var page = eligible.stream().limit(limit).toList();
        Long nextCursor = eligible.size() > page.size() && !page.isEmpty()
            ? page.getLast().sequence()
            : null;
        return new HistoryPageResponse(page.stream().map(entry -> new HistoryEntryResponse(
            entry.sequence(), entry.handNumber(), entry.eventType(), entry.message(), entry.occurredAt()
        )).toList(), nextCursor);
    }
}
