package com.pokernight.application.room;

import java.time.Instant;

public record HandHistoryEntry(
    long sequence,
    long handNumber,
    String eventType,
    String message,
    Instant occurredAt
) { }
