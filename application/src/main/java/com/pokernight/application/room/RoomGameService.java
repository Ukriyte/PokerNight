package com.pokernight.application.room;

import com.pokernight.application.bot.BaselineBotDecisionStrategy;
import com.pokernight.application.bot.BotDecisionStrategy;
import com.pokernight.application.bot.ThreadLocalDecisionRandom;
import com.pokernight.engine.model.PlayerAction;
import com.pokernight.engine.state.PrivatePlayerState;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/** Gameplay use cases. Transport adapters call this service and then route its returned events. */
public final class RoomGameService {
    private final RoomManager rooms;
    private final Clock clock;
    private final BotTurnProcessor bots;

    public RoomGameService(RoomManager rooms) {
        this(rooms, Clock.systemUTC(), new BaselineBotDecisionStrategy(new ThreadLocalDecisionRandom()));
    }

    public RoomGameService(RoomManager rooms, Clock clock) {
        this(rooms, clock, new BaselineBotDecisionStrategy(new ThreadLocalDecisionRandom()));
    }

    public RoomGameService(RoomManager rooms, Clock clock, BotDecisionStrategy strategy) {
        this.rooms = rooms;
        this.clock = clock;
        this.bots = new BotTurnProcessor(strategy);
    }

    public GameCommandResult startHand(RoomCode roomCode, String hostId) {
        var room = rooms.requireRoom(roomCode);
        var now = clock.instant();
        var result = room.startHand(hostId, now);
        rooms.persistSafe(room);
        return result;
    }

    public GameCommandResult submitAction(
        RoomCode roomCode,
        String playerId,
        PlayerAction action,
        Long amount
    ) {
        var room = rooms.requireRoom(roomCode);
        var now = clock.instant();
        var result = room.submitAction(playerId, action, amount, now);
        rooms.persistSafe(room);
        return result;
    }

    /** Executes at most one pending bot action. Delay policy belongs to the transport adapter. */
    public Optional<GameCommandResult> playPendingBotTurn(RoomCode roomCode) {
        var room = rooms.requireRoom(roomCode);
        var result = bots.advanceOne(room, clock.instant());
        result.ifPresent(ignored -> rooms.persistSafe(room));
        return result;
    }

    public boolean isBotTurn(RoomCode roomCode) {
        var room = rooms.requireRoom(roomCode);
        String currentPlayerId = room.view().game().currentPlayerId();
        return currentPlayerId != null && room.isBot(currentPlayerId);
    }

    public GameCommandResult startNewGame(RoomCode roomCode, String hostId) {
        var room = rooms.requireRoom(roomCode);
        var result = room.startNewGame(hostId, clock.instant());
        rooms.persistSafe(room);
        return result;
    }

    public PrivatePlayerState getPrivateState(RoomCode roomCode, String playerId) {
        return rooms.requireRoom(roomCode).privateState(playerId);
    }

    /** Newest entries first, suitable for the host/player history panel. */
    public List<HandHistoryEntry> getHistory(RoomCode roomCode) {
        return rooms.requireRoom(roomCode).historyNewestFirst();
    }
}
