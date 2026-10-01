package com.pokernight.web.session;

import com.pokernight.application.room.RoomCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Room-scoped opaque sessions with expiry, revocation, and optional file persistence. */
@Service
public final class InMemorySessionService {
    private static final Duration DEFAULT_RETENTION = Duration.ofDays(7);
    private final SecureRandom random = new SecureRandom();
    private final Map<String, StoredSession> sessions = new ConcurrentHashMap<>();
    private final SessionSnapshotStore snapshots;
    private final Clock clock;
    private final Duration retention;

    public InMemorySessionService() {
        this(new NoOpSessionSnapshotStore(), Clock.systemUTC(), DEFAULT_RETENTION);
    }

    @Autowired
    public InMemorySessionService(
        SessionSnapshotStore snapshots,
        @Value("${pokernight.sessions.retention:PT168H}") String retention
    ) {
        this(snapshots, Clock.systemUTC(), Duration.parse(retention));
    }

    public InMemorySessionService(SessionSnapshotStore snapshots, Clock clock, Duration retention) {
        if (retention == null || retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("session retention must be positive");
        }
        this.snapshots = snapshots;
        this.clock = clock;
        this.retention = retention;
        Instant now = clock.instant();
        snapshots.loadAll().stream()
            .filter(saved -> saved.expiresAt().isAfter(now))
            .forEach(saved -> sessions.put(saved.token(), new StoredSession(saved.principal(), saved.expiresAt())));
        persist();
    }

    public String newSubjectId() { return UUID.randomUUID().toString(); }

    public String issue(String subjectId, RoomCode roomCode, SessionRole role) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        sessions.put(token, new StoredSession(
            new ApiPrincipal(subjectId, roomCode, role), clock.instant().plus(retention)
        ));
        persist();
        return token;
    }

    public void revokeSubject(RoomCode roomCode, String subjectId) {
        sessions.entrySet().removeIf(entry ->
            entry.getValue().principal.roomCode().equals(roomCode)
                && entry.getValue().principal.subjectId().equals(subjectId)
        );
        persist();
    }

    public void revokeRoom(RoomCode roomCode) {
        sessions.entrySet().removeIf(entry -> entry.getValue().principal.roomCode().equals(roomCode));
        persist();
    }

    public boolean isActive(ApiPrincipal principal) {
        removeExpired();
        return sessions.values().stream().anyMatch(session -> session.principal.equals(principal));
    }

    public ApiPrincipal requireHost(String authorization, RoomCode roomCode) {
        ApiPrincipal principal = require(authorization);
        requireRoom(principal, roomCode);
        if (principal.role() != SessionRole.HOST) {
            throw new ApiAuthenticationException(HttpStatus.FORBIDDEN, "NOT_HOST", "Host permission is required");
        }
        return principal;
    }

    public ApiPrincipal requirePlayer(String authorization, RoomCode roomCode) {
        ApiPrincipal principal = require(authorization);
        requireRoom(principal, roomCode);
        if (principal.role() != SessionRole.PLAYER) {
            throw new ApiAuthenticationException(HttpStatus.FORBIDDEN, "NOT_PLAYER", "Player permission is required");
        }
        return principal;
    }

    public ApiPrincipal requireRoomSession(String authorization, RoomCode roomCode) {
        ApiPrincipal principal = require(authorization);
        requireRoom(principal, roomCode);
        return principal;
    }

    public ApiPrincipal require(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ApiAuthenticationException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Bearer token is required");
        }
        String token = authorization.substring(7);
        StoredSession session = sessions.get(token);
        if (session == null) {
            throw new ApiAuthenticationException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Bearer token is invalid");
        }
        if (!session.expiresAt.isAfter(clock.instant())) {
            sessions.remove(token);
            persist();
            throw new ApiAuthenticationException(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "Bearer token has expired");
        }
        return session.principal;
    }

    private void removeExpired() {
        Instant now = clock.instant();
        if (sessions.entrySet().removeIf(entry -> !entry.getValue().expiresAt.isAfter(now))) persist();
    }

    private void persist() {
        snapshots.saveAll(sessions.entrySet().stream().map(entry -> new SessionSnapshot(
            entry.getKey(), entry.getValue().principal, entry.getValue().expiresAt
        )).toList());
    }

    private void requireRoom(ApiPrincipal principal, RoomCode roomCode) {
        if (!principal.roomCode().equals(roomCode)) {
            throw new ApiAuthenticationException(HttpStatus.FORBIDDEN, "WRONG_ROOM", "Token does not belong to this room");
        }
    }

    private record StoredSession(ApiPrincipal principal, Instant expiresAt) { }
}
