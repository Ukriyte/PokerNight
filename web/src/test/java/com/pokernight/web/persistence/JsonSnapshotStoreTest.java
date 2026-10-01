package com.pokernight.web.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokernight.application.persistence.MemberSnapshot;
import com.pokernight.application.persistence.RoomSnapshot;
import com.pokernight.application.room.HandHistoryEntry;
import com.pokernight.application.room.MemberStatus;
import com.pokernight.application.room.RoomCode;
import com.pokernight.application.room.RoomConfig;
import com.pokernight.web.session.ApiPrincipal;
import com.pokernight.web.session.SessionRole;
import com.pokernight.web.session.SessionSnapshot;
import com.pokernight.application.command.CommandResultSummary;
import com.pokernight.application.command.PersistedCommand;
import com.pokernight.application.command.CommandIdentity;
import com.pokernight.application.command.IdempotentCommandExecutor;
import com.pokernight.application.room.RoomGameService;
import com.pokernight.application.room.RoomManager;
import com.pokernight.engine.model.PlayerAction;
import com.pokernight.web.session.InMemorySessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSnapshotStoreTest {
    @TempDir Path directory;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void roomSnapshotsRoundTripAndReplaceAtomically() throws Exception {
        var store = new JsonRoomSnapshotStore(directory.resolve("rooms"), json);
        RoomSnapshot first = snapshot(1_000);
        store.save(first);
        store.save(snapshot(1_250));

        assertThat(store.loadAll()).singleElement().satisfies(saved ->
            assertThat(saved.playerChips().get("p1")).isEqualTo(1_250));
        assertThat(Files.exists(directory.resolve("rooms/ABC123.json.tmp"))).isFalse();

        store.delete(new RoomCode("ABC123"));
        assertThat(store.loadAll()).isEmpty();
    }

    @Test
    void opaqueSessionsSurviveAdapterRecreation() {
        Path file = directory.resolve("sessions.json");
        var first = new JsonSessionSnapshotStore(file, json);
        var saved = new SessionSnapshot("token", new ApiPrincipal(
            "p1", new RoomCode("ABC123"), SessionRole.PLAYER
        ), Instant.parse("2026-08-20T00:00:00Z"));
        first.saveAll(List.of(saved));

        assertThat(new JsonSessionSnapshotStore(file, json).loadAll()).containsExactly(saved);
    }

    @Test
    void commandReceiptsSurviveAdapterRecreation() {
        Path file = directory.resolve("commands.json");
        var saved = new PersistedCommand(
            "ABC123", "p1", "cmd-1", "ACTION:CALL:null",
            new CommandResultSummary("FLOP", 4), Instant.parse("2026-08-13T00:00:00Z")
        );
        new JsonCommandReceiptStore(file, json).saveAll(List.of(saved));

        assertThat(new JsonCommandReceiptStore(file, json).loadAll()).containsExactly(saved);
    }

    @Test
    void completeRoomSessionsHistoryAndCommandReceiptsRecoverTogetherAfterRestart() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC);
        Path roomsPath = directory.resolve("rooms");
        Path sessionsPath = directory.resolve("sessions.json");
        Path commandsPath = directory.resolve("commands.json");

        var firstRooms = new RoomManager(new JsonRoomSnapshotStore(roomsPath, json));
        var created = firstRooms.createRoom("host", "Host", RoomConfig.standard());
        RoomCode code = created.code();
        firstRooms.requestToJoin(code, "p1", "Alice");
        firstRooms.requestToJoin(code, "p2", "Bob");
        firstRooms.approvePlayer(code, "host", "p1", 0);
        firstRooms.approvePlayer(code, "host", "p2", 1);

        var firstSessions = new InMemorySessionService(
            new JsonSessionSnapshotStore(sessionsPath, json), clock, Duration.ofDays(7));
        String hostToken = firstSessions.issue("host", code, SessionRole.HOST);
        String playerToken = firstSessions.issue("p1", code, SessionRole.PLAYER);

        var firstGames = new RoomGameService(firstRooms, clock);
        var started = firstGames.startHand(code, "host");
        String actor = started.state().currentPlayerId();
        var completed = firstGames.submitAction(code, actor, PlayerAction.FOLD, null);
        assertThat(completed.state().handInProgress()).isFalse();

        var identity = new CommandIdentity(code, actor, "fold-command-1");
        var firstCommands = new IdempotentCommandExecutor(
            100, Duration.ofHours(24), clock, new JsonCommandReceiptStore(commandsPath, json));
        firstCommands.execute(identity, "ACTION:FOLD:null", () -> completed);

        var restartedRooms = new RoomManager(new JsonRoomSnapshotStore(roomsPath, json));
        var restartedSessions = new InMemorySessionService(
            new JsonSessionSnapshotStore(sessionsPath, json), clock, Duration.ofDays(7));
        var restartedCommands = new IdempotentCommandExecutor(
            100, Duration.ofHours(24), clock, new JsonCommandReceiptStore(commandsPath, json));

        assertThat(restartedRooms.getRoom(code).members()).hasSize(2);
        assertThat(new RoomGameService(restartedRooms, clock).getHistory(code))
            .isNotEmpty().first().extracting(HandHistoryEntry::eventType).isEqualTo("HAND_COMPLETED");
        assertThat(restartedSessions.require("Bearer " + hostToken).subjectId()).isEqualTo("host");
        assertThat(restartedSessions.require("Bearer " + playerToken).subjectId()).isEqualTo("p1");
        var replay = restartedCommands.execute(identity, "ACTION:FOLD:null", () -> {
            throw new AssertionError("persisted command must not execute twice");
        });
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.summary().handNumber()).isEqualTo(1);
    }

    private static RoomSnapshot snapshot(long chips) {
        Instant now = Instant.parse("2026-08-13T00:00:00Z");
        return new RoomSnapshot(
            1, "ABC123", "host", "Host", RoomConfig.standard(),
            List.of(new MemberSnapshot("p1", "Alice", MemberStatus.APPROVED, 0)),
            Map.of("p1", chips), 3, 0,
            List.of(new HandHistoryEntry(1, 3, "HAND_COMPLETED", "Alice won", now)), now, now
        );
    }
}
