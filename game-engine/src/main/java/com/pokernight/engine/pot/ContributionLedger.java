package com.pokernight.engine.pot;

import java.util.LinkedHashMap;
import java.util.Map;

/** Records hand-wide committed chips independently from individual betting streets. */
public final class ContributionLedger {
    private final Map<String, Long> contributions = new LinkedHashMap<>();

    public void registerPlayer(String playerId) {
        if (playerId == null || playerId.isBlank()) throw new IllegalArgumentException("playerId must not be blank");
        if (contributions.putIfAbsent(playerId, 0L) != null) {
            throw new IllegalStateException("player is already registered: " + playerId);
        }
    }

    public void record(String playerId, long amount) {
        if (amount < 0) throw new IllegalArgumentException("contribution cannot be negative");
        contributions.compute(playerId, (id, current) -> {
            if (current == null) throw new IllegalArgumentException("unknown player: " + id);
            return Math.addExact(current, amount);
        });
    }

    public long total() {
        return contributions.values().stream().mapToLong(Long::longValue).sum();
    }

    public Map<String, Long> snapshot() {
        return Map.copyOf(contributions);
    }
}
