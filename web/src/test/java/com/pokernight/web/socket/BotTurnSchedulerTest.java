package com.pokernight.web.socket;

import com.pokernight.application.room.GameCommandResult;
import com.pokernight.application.room.RoomCode;
import com.pokernight.application.room.RoomGameService;
import com.pokernight.engine.model.GamePhase;
import com.pokernight.engine.state.PublicGameState;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotTurnSchedulerTest {
    @Test
    void schedulesAnObservableBotTurnTwoSecondsLater() {
        RoomGameService games = mock(RoomGameService.class);
        GameplayEventRouter router = mock(GameplayEventRouter.class);
        TaskScheduler tasks = mock(TaskScheduler.class);
        RoomCode code = new RoomCode("ABC123");
        when(games.isBotTurn(code)).thenReturn(true);
        when(tasks.schedule(any(Runnable.class), any(Instant.class)))
            .thenReturn(mock(java.util.concurrent.ScheduledFuture.class));
        var scheduler = new BotTurnScheduler(games, router, tasks, Duration.ofSeconds(2));
        var result = new GameCommandResult(code, new PublicGameState(
            GamePhase.PRE_FLOP, 1, true, List.of(), List.of(), Map.of(), 75, 50, 50, "bot-1"
        ), List.of());
        Instant before = Instant.now();

        scheduler.scheduleAfter(result);

        var deadline = ArgumentCaptor.forClass(Instant.class);
        verify(tasks).schedule(any(Runnable.class), deadline.capture());
        assertThat(deadline.getValue()).isBetween(
            before.plusMillis(1_900), Instant.now().plusMillis(2_100)
        );
    }
}
