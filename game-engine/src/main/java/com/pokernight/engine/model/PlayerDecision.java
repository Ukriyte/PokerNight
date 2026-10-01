package com.pokernight.engine.model;

import java.util.Objects;

public record PlayerDecision(String playerId, PlayerAction action, Long amount) {
    public PlayerDecision {
        if (playerId == null || playerId.isBlank()) {
            throw new IllegalArgumentException("playerId must not be blank");
        }
        Objects.requireNonNull(action, "action must not be null");
        if (amount != null && amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (action == PlayerAction.RAISE && amount == null) {
            throw new IllegalArgumentException("a raise requires an amount");
        }
    }
}
