package com.pokernight.web.session;

import com.pokernight.application.room.RoomCode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemorySessionServiceTest {
    @Test
    void expiresAndRevokesSessions() {
        var clock = new MutableClock(Instant.parse("2026-08-13T00:00:00Z"));
        var sessions = new InMemorySessionService(new NoOpSessionSnapshotStore(), clock, Duration.ofHours(1));
        RoomCode room = new RoomCode("ABC123");
        String first = sessions.issue("p1", room, SessionRole.PLAYER);
        assertThat(sessions.require("Bearer " + first).subjectId()).isEqualTo("p1");

        sessions.revokeSubject(room, "p1");
        assertThatThrownBy(() -> sessions.require("Bearer " + first))
            .isInstanceOf(ApiAuthenticationException.class);

        String second = sessions.issue("p1", room, SessionRole.PLAYER);
        clock.advance(Duration.ofHours(2));
        assertThatThrownBy(() -> sessions.require("Bearer " + second))
            .isInstanceOfSatisfying(ApiAuthenticationException.class,
                error -> assertThat(error.code()).isEqualTo("TOKEN_EXPIRED"));
    }

    private static final class MutableClock extends Clock {
        private Instant now;
        private MutableClock(Instant now) { this.now = now; }
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
