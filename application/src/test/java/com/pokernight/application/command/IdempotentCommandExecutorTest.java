package com.pokernight.application.command;

import com.pokernight.application.room.GameCommandResult;
import com.pokernight.application.room.RoomCode;
import com.pokernight.engine.model.GamePhase;
import com.pokernight.engine.state.PublicGameState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotentCommandExecutorTest {
    @Test
    void identicalRetryReturnsOriginalResultWithoutExecutingAgain() {
        var executor = new IdempotentCommandExecutor(100);
        var identity = new CommandIdentity(new RoomCode("ABC123"), "player", "cmd-1");
        var calls = new AtomicInteger();

        var first = executor.execute(identity, "ACTION:CALL:null", () -> result(calls.incrementAndGet()));
        var retry = executor.execute(identity, "ACTION:CALL:null", () -> result(calls.incrementAndGet()));

        assertThat(first.replayed()).isFalse();
        assertThat(retry.replayed()).isTrue();
        assertThat(retry.result()).isSameAs(first.result());
        assertThat(calls).hasValue(1);
    }

    @Test
    void reusedIdWithDifferentPayloadIsRejected() {
        var executor = new IdempotentCommandExecutor(100);
        var identity = new CommandIdentity(new RoomCode("ABC123"), "player", "cmd-1");
        executor.execute(identity, "ACTION:CALL:null", () -> result(1));

        assertThatThrownBy(() -> executor.execute(identity, "ACTION:FOLD:null", () -> result(2)))
            .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void failedCommandIsNotCachedAndCanBeRetried() {
        var executor = new IdempotentCommandExecutor(100);
        var identity = new CommandIdentity(new RoomCode("ABC123"), "player", "cmd-1");
        assertThatThrownBy(() -> executor.execute(identity, "ACTION:CALL:null", () -> {
            throw new IllegalStateException("temporary failure");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(executor.execute(identity, "ACTION:CALL:null", () -> result(1)).replayed()).isFalse();
    }

    @Test
    void expiredCommandIdMayBeUsedAgain() {
        var clock = new MutableClock(Instant.parse("2026-08-13T00:00:00Z"));
        var executor = new IdempotentCommandExecutor(100, Duration.ofMinutes(10), clock,
            new MemoryReceiptStore());
        var identity = new CommandIdentity(new RoomCode("ABC123"), "player", "cmd-1");
        executor.execute(identity, "ACTION:CALL:null", () -> result(1));

        clock.advance(Duration.ofMinutes(11));
        var reused = executor.execute(identity, "ACTION:FOLD:null", () -> result(2));

        assertThat(reused.replayed()).isFalse();
        assertThat(reused.summary().handNumber()).isEqualTo(2);
    }

    @Test
    void concurrentRetriesExecuteSupplierExactlyOnce() throws Exception {
        var executor = new IdempotentCommandExecutor(100);
        var identity = new CommandIdentity(new RoomCode("ABC123"), "player", "cmd-1");
        var calls = new AtomicInteger();
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(8);
        var futures = new ArrayList<java.util.concurrent.Future<IdempotentCommandResult>>();
        for (int index = 0; index < 20; index++) {
            futures.add(pool.submit(() -> {
                start.await();
                return executor.execute(identity, "ACTION:CALL:null", () -> result(calls.incrementAndGet()));
            }));
        }
        start.countDown();
        var outcomes = futures.stream().map(future -> {
            try { return future.get(); }
            catch (Exception exception) { throw new RuntimeException(exception); }
        }).toList();
        pool.shutdownNow();

        assertThat(calls).hasValue(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.replayed()).hasSize(1);
        assertThat(outcomes).filteredOn(IdempotentCommandResult::replayed).hasSize(19);
    }

    @Test
    void persistedReceiptSuppressesExecutionAfterProcessRestart() {
        var store = new MemoryReceiptStore();
        var clock = new MutableClock(Instant.parse("2026-08-13T00:00:00Z"));
        var identity = new CommandIdentity(new RoomCode("ABC123"), "player", "cmd-1");
        new IdempotentCommandExecutor(100, Duration.ofHours(24), clock, store)
            .execute(identity, "ACTION:CALL:null", () -> result(7));
        var calls = new AtomicInteger();

        var replay = new IdempotentCommandExecutor(100, Duration.ofHours(24), clock, store)
            .execute(identity, "ACTION:CALL:null", () -> result(calls.incrementAndGet()));

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.result()).isNull();
        assertThat(replay.summary().handNumber()).isEqualTo(7);
        assertThat(calls).hasValue(0);
    }

    private static GameCommandResult result(long handNumber) {
        return new GameCommandResult(new RoomCode("ABC123"), new PublicGameState(
            GamePhase.WAITING_FOR_PLAYERS, handNumber, false, List.of(), List.of(), java.util.Map.of(), 0, 0, 50, null
        ), List.of());
    }

    private static final class MemoryReceiptStore implements CommandReceiptStore {
        private List<PersistedCommand> values = List.of();
        @Override public List<PersistedCommand> loadAll() { return values; }
        @Override public void saveAll(List<PersistedCommand> commands) { values = List.copyOf(commands); }
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
