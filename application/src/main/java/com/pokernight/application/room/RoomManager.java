package com.pokernight.application.room;

import com.pokernight.application.bot.BotRosterFactory;
import com.pokernight.application.bot.DefaultBotRosterFactory;
import com.pokernight.application.persistence.NoOpRoomSnapshotStore;
import com.pokernight.application.persistence.RoomSnapshotStore;
import com.pokernight.engine.GameEngine;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Application use cases for room lifecycle and lobby membership. */
public final class RoomManager {
    private static final int MAX_CODE_ATTEMPTS = 100;

    private final RoomRepository rooms;
    private final RoomCodeGenerator codeGenerator;
    private final Clock clock;
    private final GameEngineFactory gameFactory;
    private final BotRosterFactory botRosterFactory;
    private final RoomSnapshotStore snapshots;

    public RoomManager() {
        this(new InMemoryRoomRepository(), new SecureRoomCodeGenerator(), Clock.systemUTC(), GameEngine::new,
            new DefaultBotRosterFactory(), new NoOpRoomSnapshotStore());
    }

    public RoomManager(RoomSnapshotStore snapshots) {
        this(new InMemoryRoomRepository(), new SecureRoomCodeGenerator(), Clock.systemUTC(), GameEngine::new,
            new DefaultBotRosterFactory(), snapshots);
    }

    public RoomManager(RoomCodeGenerator codeGenerator, Clock clock) {
        this(new InMemoryRoomRepository(), codeGenerator, clock, GameEngine::new, new DefaultBotRosterFactory(),
            new NoOpRoomSnapshotStore());
    }

    RoomManager(RoomRepository rooms, RoomCodeGenerator codeGenerator, Clock clock) {
        this(rooms, codeGenerator, clock, GameEngine::new, new DefaultBotRosterFactory(), new NoOpRoomSnapshotStore());
    }

    RoomManager(RoomRepository rooms, RoomCodeGenerator codeGenerator, Clock clock, GameEngineFactory gameFactory) {
        this(rooms, codeGenerator, clock, gameFactory, new DefaultBotRosterFactory(), new NoOpRoomSnapshotStore());
    }

    RoomManager(RoomRepository rooms, RoomCodeGenerator codeGenerator, Clock clock,
                GameEngineFactory gameFactory, BotRosterFactory botRosterFactory) {
        this(rooms, codeGenerator, clock, gameFactory, botRosterFactory, new NoOpRoomSnapshotStore());
    }

    RoomManager(RoomRepository rooms, RoomCodeGenerator codeGenerator, Clock clock,
                GameEngineFactory gameFactory, BotRosterFactory botRosterFactory, RoomSnapshotStore snapshots) {
        this.rooms = rooms;
        this.codeGenerator = codeGenerator;
        this.clock = clock;
        this.gameFactory = gameFactory;
        this.botRosterFactory = botRosterFactory;
        this.snapshots = snapshots;
        snapshots.loadAll().forEach(snapshot -> {
            if (snapshot.schemaVersion() != com.pokernight.application.persistence.RoomSnapshot.CURRENT_SCHEMA) {
                throw new IllegalStateException("unsupported room snapshot schema: " + snapshot.schemaVersion());
            }
            rooms.create(new Room(snapshot, gameFactory, botRosterFactory));
        });
    }

    public RoomView createRoom(String hostId, String hostName, RoomConfig config) {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            RoomCode code = codeGenerator.nextCode();
            Instant now = clock.instant();
            Room room = new Room(code, hostId, hostName, config, now, gameFactory, botRosterFactory);
            if (rooms.create(room)) {
                persistSafe(room);
                return room.view();
            }
        }
        throw failure(RoomFailure.CODE_GENERATION_EXHAUSTED, "could not allocate a unique room code");
    }

    public RoomView getRoom(RoomCode roomCode) {
        return requireRoom(roomCode).view();
    }

    public RoomView requestToJoin(RoomCode roomCode, String playerId, String displayName) {
        Room room = requireRoom(roomCode);
        room.requestJoin(playerId, displayName, clock.instant());
        persistSafe(room);
        return room.view();
    }

    public RoomView approvePlayer(RoomCode roomCode, String hostId, String playerId, int seatNumber) {
        Room room = requireRoom(roomCode);
        room.approve(hostId, playerId, seatNumber, clock.instant());
        persistSafe(room);
        return room.view();
    }

    public RoomView rejectPlayer(RoomCode roomCode, String hostId, String playerId) {
        Room room = requireRoom(roomCode);
        room.reject(hostId, playerId, clock.instant());
        persistSafe(room);
        return room.view();
    }

    public RoomView kickPlayer(RoomCode roomCode, String hostId, String playerId) {
        Room room = requireRoom(roomCode);
        room.kick(hostId, playerId, clock.instant());
        persistSafe(room);
        return room.view();
    }

    public void destroyRoom(RoomCode roomCode, String hostId) {
        Room room = requireRoom(roomCode);
        room.authorizeHost(hostId);
        rooms.delete(roomCode);
        snapshots.delete(roomCode);
    }

    /** Removes rooms whose last mutation is older than maxIdleTime. */
    public int reapStaleRooms(Duration maxIdleTime) {
        if (maxIdleTime == null || maxIdleTime.isNegative() || maxIdleTime.isZero()) {
            throw new IllegalArgumentException("maxIdleTime must be positive");
        }
        Instant cutoff = clock.instant().minus(maxIdleTime);
        int removed = 0;
        for (Room room : rooms.findAll()) {
            if (room.lastActivityAt().isBefore(cutoff) && rooms.delete(room.code())) {
                snapshots.delete(room.code());
                removed++;
            }
        }
        return removed;
    }

    public int activeRoomCount() {
        return rooms.findAll().size();
    }

    Room requireRoom(RoomCode roomCode) {
        return rooms.find(roomCode)
            .orElseThrow(() -> failure(RoomFailure.ROOM_NOT_FOUND, "room not found: " + roomCode));
    }

    void persistSafe(Room room) {
        if (!room.handInProgress()) snapshots.save(room.safeSnapshot());
    }

    private static RoomOperationException failure(RoomFailure failure, String message) {
        return new RoomOperationException(failure, message);
    }
}
