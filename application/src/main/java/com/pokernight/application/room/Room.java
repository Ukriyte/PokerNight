package com.pokernight.application.room;

import com.pokernight.application.bot.BotRosterFactory;
import com.pokernight.application.bot.BotSeat;
import com.pokernight.application.persistence.MemberSnapshot;
import com.pokernight.application.persistence.RoomSnapshot;
import com.pokernight.engine.GameEngine;
import com.pokernight.engine.event.GameEvent;
import com.pokernight.engine.model.GamePhase;
import com.pokernight.engine.model.PlayerAction;
import com.pokernight.engine.model.PlayerDecision;
import com.pokernight.engine.state.PrivatePlayerState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Thread-safe room aggregate. All mutation of one room is serialized here. */
final class Room {
    private final RoomCode code;
    private final String hostId;
    private final String hostName;
    private final RoomConfig config;
    private final GameEngineFactory gameFactory;
    private final List<BotSeat> bots;
    private GameEngine game;
    private final Instant createdAt;
    private final Map<String, MutableMember> members = new LinkedHashMap<>();
    private final List<HandHistoryEntry> history = new ArrayList<>();
    private Instant lastActivityAt;
    private long historySequence;

    Room(
        RoomCode code,
        String hostId,
        String hostName,
        RoomConfig config,
        Instant now,
        GameEngineFactory gameFactory,
        BotRosterFactory botRosterFactory
    ) {
        this.code = code;
        this.hostId = requireText(hostId, "hostId");
        this.hostName = requireText(hostName, "hostName");
        this.config = config;
        this.gameFactory = gameFactory;
        this.game = gameFactory.create(config.gameRules());
        this.bots = botRosterFactory.create(code.value(), config.botCount(), config.maximumPlayers());
        seatBots(this.game);
        this.createdAt = now;
        this.lastActivityAt = now;
    }

    Room(RoomSnapshot snapshot, GameEngineFactory gameFactory, BotRosterFactory botRosterFactory) {
        this.code = new RoomCode(snapshot.roomCode());
        this.hostId = requireText(snapshot.hostId(), "hostId");
        this.hostName = requireText(snapshot.hostName(), "hostName");
        this.config = snapshot.config();
        this.gameFactory = gameFactory;
        this.game = gameFactory.create(config.gameRules());
        this.bots = botRosterFactory.create(code.value(), config.botCount(), config.maximumPlayers());
        bots.forEach(bot -> game.seatPlayer(bot.playerId(), bot.displayName(), bot.seatNumber(),
            snapshot.playerChips().getOrDefault(bot.playerId(), config.startingChips())));
        for (MemberSnapshot saved : snapshot.members()) {
            MutableMember member = new MutableMember(saved.playerId(), saved.displayName());
            member.status = saved.status();
            member.seatNumber = saved.seatNumber();
            members.put(member.playerId, member);
            if (member.status == MemberStatus.APPROVED) {
                game.seatPlayer(member.playerId, member.displayName, member.seatNumber,
                    snapshot.playerChips().getOrDefault(member.playerId, config.startingChips()));
            }
        }
        game.restoreProgress(snapshot.completedHandNumber(), snapshot.previousDealerSeat());
        history.addAll(snapshot.history());
        historySequence = history.stream().mapToLong(HandHistoryEntry::sequence).max().orElse(0);
        this.createdAt = snapshot.createdAt();
        this.lastActivityAt = snapshot.lastActivityAt();
    }

    synchronized void requestJoin(String playerId, String displayName, Instant now) {
        requireText(playerId, "playerId");
        requireText(displayName, "displayName");
        if (members.containsKey(playerId)) {
            throw failure(RoomFailure.PLAYER_ALREADY_REGISTERED, "player is already registered in this room");
        }
        members.put(playerId, new MutableMember(playerId, displayName));
        touch(now);
    }

    synchronized void approve(String requesterId, String playerId, int seatNumber, Instant now) {
        requireHost(requesterId);
        MutableMember member = requireStatus(playerId, MemberStatus.PENDING, RoomFailure.PLAYER_NOT_PENDING);
        long approved = members.values().stream().filter(candidate -> candidate.status == MemberStatus.APPROVED).count();
        int humanCapacity = config.maximumPlayers() - config.botCount();
        if (approved >= humanCapacity) throw failure(RoomFailure.ROOM_FULL, "room has no available player slots");

        try {
            game.seatPlayer(member.playerId, member.displayName, seatNumber, config.startingChips());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw failure(RoomFailure.SEAT_UNAVAILABLE, exception.getMessage());
        }
        member.status = MemberStatus.APPROVED;
        member.seatNumber = seatNumber;
        touch(now);
    }

    synchronized void reject(String requesterId, String playerId, Instant now) {
        requireHost(requesterId);
        MutableMember member = requireStatus(playerId, MemberStatus.PENDING, RoomFailure.PLAYER_NOT_PENDING);
        member.status = MemberStatus.REJECTED;
        touch(now);
    }

    synchronized void kick(String requesterId, String playerId, Instant now) {
        requireHost(requesterId);
        MutableMember member = requireStatus(playerId, MemberStatus.APPROVED, RoomFailure.PLAYER_NOT_APPROVED);
        if (game.publicState().handInProgress()) {
            throw failure(RoomFailure.GAME_IN_PROGRESS, "players can be removed only between hands");
        }
        game.removePlayer(playerId);
        member.status = MemberStatus.KICKED;
        member.seatNumber = null;
        touch(now);
    }

    synchronized void authorizeHost(String requesterId) {
        requireHost(requesterId);
    }

    synchronized GameCommandResult startHand(String requesterId, Instant now) {
        requireGameplayHost(requesterId);
        try {
            game.startHand();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw gameplayFailure(GameplayFailure.GAME_NOT_READY, exception.getMessage());
        }
        touch(now);
        return consumeEngineEvents(now);
    }

    synchronized GameCommandResult submitAction(
        String playerId,
        PlayerAction action,
        Long amount,
        Instant now
    ) {
        requireParticipant(playerId);
        try {
            game.submit(new PlayerDecision(playerId, action, amount));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw gameplayFailure(GameplayFailure.ILLEGAL_ACTION, exception.getMessage());
        }
        touch(now);
        return consumeEngineEvents(now);
    }

    synchronized GameCommandResult startNewGame(String requesterId, Instant now) {
        requireGameplayHost(requesterId);
        if (game.publicState().phase() != GamePhase.GAME_COMPLETE) {
            throw gameplayFailure(GameplayFailure.GAME_NOT_READY, "a new game can start only after game completion");
        }

        game = gameFactory.create(config.gameRules());
        seatBots(game);
        members.values().stream()
            .filter(member -> member.status == MemberStatus.APPROVED)
            .forEach(member -> game.seatPlayer(
                member.playerId, member.displayName, member.seatNumber, config.startingChips()
            ));
        history.clear();
        historySequence = 0;
        touch(now);
        return new GameCommandResult(
            code, game.publicState(), List.of(new GameplayEvent.NewGameStarted(game.publicState()))
        );
    }

    synchronized PrivatePlayerState privateState(String playerId) {
        requireParticipant(playerId);
        return game.privateState(playerId);
    }

    synchronized boolean isBot(String playerId) {
        return bots.stream().anyMatch(bot -> bot.playerId().equals(playerId));
    }

    synchronized List<HandHistoryEntry> historyNewestFirst() {
        return history.stream()
            .sorted(Comparator.comparingLong(HandHistoryEntry::sequence).reversed())
            .toList();
    }

    synchronized RoomView view() {
        return new RoomView(
            code, hostId, hostName, config,
            members.values().stream().map(MutableMember::view).toList(),
            bots.stream().map(BotSeat::playerId).collect(java.util.stream.Collectors.toUnmodifiableSet()),
            game.publicState(), createdAt, lastActivityAt
        );
    }

    synchronized Instant lastActivityAt() {
        return lastActivityAt;
    }

    synchronized boolean handInProgress() {
        return game.publicState().handInProgress();
    }

    synchronized RoomSnapshot safeSnapshot() {
        var state = game.publicState();
        if (state.handInProgress()) throw new IllegalStateException("active hands are not persistence boundaries");
        Map<String, Long> stacks = state.players().stream().collect(Collectors.toUnmodifiableMap(
            player -> player.playerId(), player -> player.chips()
        ));
        int dealerSeat = state.players().stream().filter(player -> player.dealer())
            .mapToInt(player -> player.seatNumber()).findFirst().orElse(-1);
        return new RoomSnapshot(
            RoomSnapshot.CURRENT_SCHEMA, code.value(), hostId, hostName, config,
            members.values().stream().map(member -> new MemberSnapshot(
                member.playerId, member.displayName, member.status, member.seatNumber
            )).toList(),
            stacks, state.handNumber(), dealerSeat, List.copyOf(history), createdAt, lastActivityAt
        );
    }

    RoomCode code() {
        return code;
    }

    private GameCommandResult consumeEngineEvents(Instant now) {
        List<GameplayEvent> events = game.drainEvents().stream()
            .peek(event -> projectHistory(event, now))
            .map(GameplayEvent.Engine::new)
            .map(GameplayEvent.class::cast)
            .toList();
        return new GameCommandResult(code, game.publicState(), events);
    }

    private void projectHistory(GameEvent event, Instant now) {
        if (event instanceof GameEvent.HandStarted started) {
            appendHistory(started.handNumber(), "HAND_STARTED", "Hand #" + started.handNumber() + " started", now);
        } else if (event instanceof GameEvent.PlayerActed acted) {
            String name = displayName(acted.playerId());
            String amount = acted.committedChips() > 0 ? " " + acted.committedChips() : "";
            appendHistory(game.publicState().handNumber(), "PLAYER_ACTED", name + " " + acted.action() + amount, now);
        } else if (event instanceof GameEvent.StreetDealt street) {
            appendHistory(game.publicState().handNumber(), "STREET_DEALT", street.street().name(), now);
        } else if (event instanceof GameEvent.HandCompleted completed) {
            String message = completed.payouts().entrySet().stream()
                .map(entry -> displayName(entry.getKey()) + " won " + entry.getValue())
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse("Hand completed");
            appendHistory(game.publicState().handNumber(), "HAND_COMPLETED", message, now);
        } else if (event instanceof GameEvent.GameCompleted completed) {
            appendHistory(game.publicState().handNumber(), "GAME_COMPLETED",
                displayName(completed.winnerId()) + " won the game", now);
        }
    }

    private void appendHistory(long handNumber, String type, String message, Instant now) {
        history.add(new HandHistoryEntry(++historySequence, handNumber, type, message, now));
    }

    private String displayName(String playerId) {
        for (BotSeat bot : bots) if (bot.playerId().equals(playerId)) return bot.displayName();
        MutableMember member = members.get(playerId);
        return member == null ? playerId : member.displayName;
    }

    private void requireParticipant(String playerId) {
        if (isBot(playerId)) return;
        MutableMember member = members.get(playerId);
        if (member == null || member.status != MemberStatus.APPROVED) {
            throw gameplayFailure(GameplayFailure.PLAYER_NOT_APPROVED, "player is not approved in this room");
        }
    }

    private void seatBots(GameEngine target) {
        bots.forEach(bot -> target.seatPlayer(
            bot.playerId(), bot.displayName(), bot.seatNumber(), config.startingChips()
        ));
    }

    private void requireGameplayHost(String requesterId) {
        if (!hostId.equals(requesterId)) {
            throw gameplayFailure(GameplayFailure.NOT_HOST, "only the room host may control the game");
        }
    }

    private MutableMember requireStatus(
        String playerId,
        MemberStatus expected,
        RoomFailure failure
    ) {
        MutableMember member = members.get(playerId);
        if (member == null || member.status != expected) {
            throw failure(failure, "player " + playerId + " is not " + expected.name().toLowerCase());
        }
        return member;
    }

    private void requireHost(String requesterId) {
        if (!hostId.equals(requesterId)) {
            throw failure(RoomFailure.NOT_HOST, "only the room host may perform this operation");
        }
    }

    private void touch(Instant now) {
        lastActivityAt = now;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }

    private static RoomOperationException failure(RoomFailure failure, String message) {
        return new RoomOperationException(failure, message);
    }

    private static GameplayOperationException gameplayFailure(GameplayFailure failure, String message) {
        return new GameplayOperationException(failure, message);
    }

    private static final class MutableMember {
        private final String playerId;
        private final String displayName;
        private MemberStatus status = MemberStatus.PENDING;
        private Integer seatNumber;

        private MutableMember(String playerId, String displayName) {
            this.playerId = playerId;
            this.displayName = displayName;
        }

        private RoomMemberView view() {
            return new RoomMemberView(playerId, displayName, status, seatNumber);
        }
    }
}
