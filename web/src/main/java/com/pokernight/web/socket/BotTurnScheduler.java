package com.pokernight.web.socket;

import com.pokernight.application.room.GameCommandResult;
import com.pokernight.application.room.RoomCode;
import com.pokernight.application.room.RoomGameService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/** Presentation pacing for bots. The game engine and bot strategy remain timer-free. */
@Component
public final class BotTurnScheduler {
    private final RoomGameService games;
    private final GameplayEventRouter router;
    private final TaskScheduler scheduler;
    private final Duration delay;
    private final Map<RoomCode, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    public BotTurnScheduler(
        RoomGameService games,
        GameplayEventRouter router,
        @Qualifier("botTaskScheduler") TaskScheduler scheduler,
        @Value("${pokernight.bot-turn-delay:PT2S}") Duration delay
    ) {
        this.games = games;
        this.router = router;
        this.scheduler = scheduler;
        this.delay = delay;
    }

    public void scheduleAfter(GameCommandResult result) {
        RoomCode roomCode = result.roomCode();
        if (!games.isBotTurn(roomCode)) {
            cancel(roomCode);
            return;
        }
        pending.compute(roomCode, (ignored, existing) -> {
            if (existing != null && !existing.isDone()) return existing;
            return scheduler.schedule(() -> play(roomCode), Instant.now().plus(delay));
        });
    }

    private void play(RoomCode roomCode) {
        pending.remove(roomCode);
        games.playPendingBotTurn(roomCode).ifPresent(result -> {
            router.route(result);
            scheduleAfter(result);
        });
    }

    private void cancel(RoomCode roomCode) {
        ScheduledFuture<?> task = pending.remove(roomCode);
        if (task != null) task.cancel(false);
    }
}
