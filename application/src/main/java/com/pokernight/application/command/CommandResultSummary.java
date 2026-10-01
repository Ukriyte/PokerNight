package com.pokernight.application.command;

public record CommandResultSummary(String phase, long handNumber) {
    public CommandResultSummary {
        if (phase == null || phase.isBlank()) throw new IllegalArgumentException("phase must not be blank");
        if (handNumber < 0) throw new IllegalArgumentException("handNumber must not be negative");
    }
}
