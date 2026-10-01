package com.pokernight.application.room;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoomManagerTest {
    private MutableClock clock;
    private QueueCodeGenerator codes;
    private RoomManager manager;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-08-12T10:00:00Z"));
        codes = new QueueCodeGenerator("AAAAAA", "BBBBBB", "CCCCCC", "DDDDDD");
        manager = new RoomManager(new InMemoryRoomRepository(), codes, clock);
    }

    @Test
    void createsIndependentRoomsAndRetriesCodeCollision() {
        RoomView first = manager.createRoom("h1", "Host One", RoomConfig.standard());
        codes.addFirst("AAAAAA");
        RoomView second = manager.createRoom("h2", "Host Two", RoomConfig.standard());

        assertThat(first.code()).isEqualTo(new RoomCode("AAAAAA"));
        assertThat(second.code()).isEqualTo(new RoomCode("BBBBBB"));

        manager.requestToJoin(first.code(), "p1", "Alice");
        assertThat(manager.getRoom(first.code()).members()).hasSize(1);
        assertThat(manager.getRoom(second.code()).members()).isEmpty();
    }

    @Test
    void hostApprovesPendingPlayerIntoSelectedSeat() {
        RoomView room = manager.createRoom("host", "Host", RoomConfig.standard());
        manager.requestToJoin(room.code(), "p1", "Alice");

        RoomView approved = manager.approvePlayer(room.code(), "host", "p1", 3);

        assertThat(approved.members()).containsExactly(
            new RoomMemberView("p1", "Alice", MemberStatus.APPROVED, 3)
        );
        assertThat(approved.game().players()).singleElement().satisfies(player -> {
            assertThat(player.playerId()).isEqualTo("p1");
            assertThat(player.seatNumber()).isEqualTo(3);
            assertThat(player.chips()).isEqualTo(1_000);
        });
    }

    @Test
    void nonHostCannotManageLobbyOrDestroyRoom() {
        RoomView room = manager.createRoom("host", "Host", RoomConfig.standard());
        manager.requestToJoin(room.code(), "p1", "Alice");

        assertFailure(RoomFailure.NOT_HOST,
            () -> manager.approvePlayer(room.code(), "intruder", "p1", 0));
        assertFailure(RoomFailure.NOT_HOST,
            () -> manager.rejectPlayer(room.code(), "intruder", "p1"));
        assertFailure(RoomFailure.NOT_HOST,
            () -> manager.destroyRoom(room.code(), "intruder"));
    }

    @Test
    void occupiedSeatDoesNotPartiallyApprovePlayer() {
        RoomView room = manager.createRoom("host", "Host", RoomConfig.standard());
        manager.requestToJoin(room.code(), "p1", "Alice");
        manager.requestToJoin(room.code(), "p2", "Bob");
        manager.approvePlayer(room.code(), "host", "p1", 0);

        assertFailure(RoomFailure.SEAT_UNAVAILABLE,
            () -> manager.approvePlayer(room.code(), "host", "p2", 0));
        assertThat(manager.getRoom(room.code()).members())
            .filteredOn(member -> member.playerId().equals("p2"))
            .singleElement().extracting(RoomMemberView::status).isEqualTo(MemberStatus.PENDING);
    }

    @Test
    void rejectsAndKicksPlayersWithoutLeakingEngineSeats() {
        RoomView room = manager.createRoom("host", "Host", RoomConfig.standard());
        manager.requestToJoin(room.code(), "p1", "Alice");
        manager.requestToJoin(room.code(), "p2", "Bob");
        manager.rejectPlayer(room.code(), "host", "p1");
        manager.approvePlayer(room.code(), "host", "p2", 2);

        RoomView afterKick = manager.kickPlayer(room.code(), "host", "p2");

        assertThat(afterKick.members()).extracting(RoomMemberView::status)
            .containsExactly(MemberStatus.REJECTED, MemberStatus.KICKED);
        assertThat(afterKick.game().players()).isEmpty();
    }

    @Test
    void refusesToRemoveAPlayerDuringAnActiveHandWithoutChangingTheirSeat() {
        RoomView room = manager.createRoom("host", "Host", RoomConfig.standard());
        manager.requestToJoin(room.code(), "p1", "Alice");
        manager.requestToJoin(room.code(), "p2", "Bob");
        manager.approvePlayer(room.code(), "host", "p1", 0);
        manager.approvePlayer(room.code(), "host", "p2", 1);
        new RoomGameService(manager, clock).startHand(room.code(), "host");

        assertFailure(RoomFailure.GAME_IN_PROGRESS,
            () -> manager.kickPlayer(room.code(), "host", "p1"));
        assertThat(manager.getRoom(room.code()).members())
            .filteredOn(member -> member.playerId().equals("p1"))
            .singleElement().extracting(RoomMemberView::status).isEqualTo(MemberStatus.APPROVED);
        assertThat(manager.getRoom(room.code()).game().players())
            .extracting(player -> player.playerId()).contains("p1");
    }

    @Test
    void reservesConfiguredCapacityForBots() {
        RoomConfig config = new RoomConfig(1_000, RoomConfig.standard().blinds(), 3, 1);
        RoomView room = manager.createRoom("host", "Host", config);
        manager.requestToJoin(room.code(), "p1", "Alice");
        manager.requestToJoin(room.code(), "p2", "Bob");
        manager.requestToJoin(room.code(), "p3", "Cara");
        manager.approvePlayer(room.code(), "host", "p1", 0);
        manager.approvePlayer(room.code(), "host", "p2", 1);

        assertFailure(RoomFailure.ROOM_FULL,
            () -> manager.approvePlayer(room.code(), "host", "p3", 2));
    }

    @Test
    void reapsOnlyRoomsIdleLongerThanThreshold() {
        RoomView oldRoom = manager.createRoom("h1", "Old Host", RoomConfig.standard());
        clock.advance(Duration.ofMinutes(30));
        RoomView activeRoom = manager.createRoom("h2", "Active Host", RoomConfig.standard());
        clock.advance(Duration.ofMinutes(31));
        manager.requestToJoin(activeRoom.code(), "p1", "Alice");

        int removed = manager.reapStaleRooms(Duration.ofHours(1));

        assertThat(removed).isEqualTo(1);
        assertFailure(RoomFailure.ROOM_NOT_FOUND, () -> manager.getRoom(oldRoom.code()));
        assertThat(manager.getRoom(activeRoom.code()).hostId()).isEqualTo("h2");
    }

    @Test
    void hostCanDestroyRoom() {
        RoomView room = manager.createRoom("host", "Host", RoomConfig.standard());

        manager.destroyRoom(room.code(), "host");

        assertFailure(RoomFailure.ROOM_NOT_FOUND, () -> manager.getRoom(room.code()));
    }

    private static void assertFailure(RoomFailure expected, Runnable operation) {
        assertThatThrownBy(operation::run)
            .isInstanceOfSatisfying(RoomOperationException.class,
                exception -> assertThat(exception.failure()).isEqualTo(expected));
    }

    private static final class QueueCodeGenerator implements RoomCodeGenerator {
        private final ArrayDeque<String> values = new ArrayDeque<>();

        private QueueCodeGenerator(String... values) {
            this.values.addAll(java.util.List.of(values));
        }

        private void addFirst(String value) {
            values.addFirst(value);
        }

        @Override
        public RoomCode nextCode() {
            return new RoomCode(values.remove());
        }
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
