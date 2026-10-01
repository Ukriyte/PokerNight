package com.pokernight.application.room;

import com.pokernight.application.bot.BotDecision;
import com.pokernight.engine.model.PlayerAction;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BotLifecycleTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void configuredBotsAreCreatedSeatedAndExposedSeparatelyFromHumans() {
        var rooms = rooms();
        var config = new RoomConfig(2_000, RoomConfig.standard().blinds(), 6, 3);
        RoomView room = rooms.createRoom("host", "Host", config);

        assertThat(room.botPlayerIds()).hasSize(3);
        assertThat(room.members()).isEmpty();
        assertThat(room.game().players())
            .filteredOn(player -> room.botPlayerIds().contains(player.playerId()))
            .hasSize(3)
            .allSatisfy(player -> assertThat(player.chips()).isEqualTo(2_000));
        assertThat(room.game().players()).extracting(player -> player.seatNumber())
            .containsExactlyInAnyOrder(3, 4, 5);
    }

    @Test
    void botSeatCannotBeAssignedToAHuman() {
        var rooms = rooms();
        RoomView room = rooms.createRoom("host", "Host",
            new RoomConfig(1_000, RoomConfig.standard().blinds(), 6, 1));
        rooms.requestToJoin(room.code(), "human", "Alice");

        assertThatThrownBy(() -> rooms.approvePlayer(room.code(), "host", "human", 5))
            .isInstanceOfSatisfying(RoomOperationException.class,
                error -> assertThat(error.failure()).isEqualTo(RoomFailure.SEAT_UNAVAILABLE));
    }

    @Test
    void consecutiveBotTurnsUseNormalCommandsOneAtATimeWithoutEngineTimers() {
        var rooms = rooms();
        RoomView room = rooms.createRoom("host", "Host",
            new RoomConfig(1_000, RoomConfig.standard().blinds(), 3, 2));
        var games = new RoomGameService(rooms, CLOCK, context -> {
            if (context.validActions().contains(PlayerAction.CHECK)) return new BotDecision(PlayerAction.CHECK, null);
            if (context.validActions().contains(PlayerAction.CALL)) return new BotDecision(PlayerAction.CALL, null);
            return new BotDecision(PlayerAction.FOLD, null);
        });

        GameCommandResult result = games.startHand(room.code(), "host");

        int safety = 100;
        while (result.state().handInProgress() && safety-- > 0) {
            result = games.playPendingBotTurn(room.code()).orElseThrow();
        }

        assertThat(result.state().handInProgress()).isFalse();
        assertThat(safety).isPositive();
        assertThat(games.getHistory(room.code())).isNotEmpty();
        long totalChips = result.state().players().stream().mapToLong(player -> player.chips()).sum();
        assertThat(totalChips).isEqualTo(2_000);
    }

    private static RoomManager rooms() {
        return new RoomManager(new InMemoryRoomRepository(), () -> new RoomCode("ABC123"), CLOCK);
    }
}
