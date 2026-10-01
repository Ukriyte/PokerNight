package com.pokernight.application.command;

import com.pokernight.application.room.GameCommandResult;
import com.pokernight.application.room.RoomCode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Executes an actor command at most once within a configurable retention window. */
public final class IdempotentCommandExecutor {
    private static final Duration DEFAULT_RETENTION = Duration.ofHours(24);
    private final int maximumEntries;
    private final Duration retention;
    private final Clock clock;
    private final CommandReceiptStore store;
    private final Map<CommandIdentity, StoredCommand> completed = new LinkedHashMap<>(16, 0.75f, true);

    public IdempotentCommandExecutor(int maximumEntries) {
        this(maximumEntries, DEFAULT_RETENTION, Clock.systemUTC(), new NoOpCommandReceiptStore());
    }

    public IdempotentCommandExecutor(int maximumEntries, Duration retention, Clock clock,
                                     CommandReceiptStore store) {
        if (maximumEntries < 1) throw new IllegalArgumentException("maximumEntries must be positive");
        if (retention == null || retention.isZero() || retention.isNegative()) {
            throw new IllegalArgumentException("retention must be positive");
        }
        this.maximumEntries = maximumEntries;
        this.retention = retention;
        this.clock = Objects.requireNonNull(clock);
        this.store = Objects.requireNonNull(store);
        restoreReceipts();
    }

    public synchronized IdempotentCommandResult execute(
        CommandIdentity identity,
        String fingerprint,
        Supplier<GameCommandResult> command
    ) {
        Objects.requireNonNull(identity);
        Objects.requireNonNull(fingerprint);
        Objects.requireNonNull(command);
        removeExpired(clock.instant());

        StoredCommand existing = completed.get(identity);
        if (existing != null) {
            if (!existing.fingerprint.equals(fingerprint)) {
                throw new IdempotencyConflictException("commandId was already used with a different command payload");
            }
            return new IdempotentCommandResult(existing.liveResult, existing.summary, true);
        }

        GameCommandResult result = command.get();
        var summary = new CommandResultSummary(result.state().phase().name(), result.state().handNumber());
        completed.put(identity, new StoredCommand(fingerprint, summary, clock.instant(), result));
        evictOldestEntries();
        persist();
        return new IdempotentCommandResult(result, summary, false);
    }

    private void restoreReceipts() {
        Instant now = clock.instant();
        for (PersistedCommand saved : store.loadAll()) {
            if (saved.completedAt().plus(retention).isAfter(now)) {
                var identity = new CommandIdentity(new RoomCode(saved.roomCode()), saved.actorId(), saved.commandId());
                completed.put(identity, new StoredCommand(
                    saved.fingerprint(), saved.result(), saved.completedAt(), null
                ));
            }
        }
        evictOldestEntries();
        persist();
    }

    private void removeExpired(Instant now) {
        boolean changed = completed.entrySet().removeIf(entry ->
            !entry.getValue().completedAt.plus(retention).isAfter(now)
        );
        if (changed) persist();
    }

    private void evictOldestEntries() {
        while (completed.size() > maximumEntries) completed.remove(completed.keySet().iterator().next());
    }

    private void persist() {
        List<PersistedCommand> snapshots = completed.entrySet().stream().map(entry -> new PersistedCommand(
            entry.getKey().roomCode().value(), entry.getKey().actorId(), entry.getKey().commandId(),
            entry.getValue().fingerprint, entry.getValue().summary, entry.getValue().completedAt
        )).toList();
        store.saveAll(snapshots);
    }

    private record StoredCommand(
        String fingerprint,
        CommandResultSummary summary,
        Instant completedAt,
        GameCommandResult liveResult
    ) { }
}
