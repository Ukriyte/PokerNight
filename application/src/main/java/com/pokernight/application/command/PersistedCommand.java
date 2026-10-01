package com.pokernight.application.command;

import java.time.Instant;

/** Minimal non-private receipt stored across process restarts. */
public record PersistedCommand(
    String roomCode,
    String actorId,
    String commandId,
    String fingerprint,
    CommandResultSummary result,
    Instant completedAt
) { }
