package com.pokernight.engine.pot;

import java.util.Set;

public record Pot(long amount, Set<String> eligiblePlayerIds) {
    public Pot {
        if (amount <= 0) throw new IllegalArgumentException("pot amount must be positive");
        eligiblePlayerIds = Set.copyOf(eligiblePlayerIds);
        if (eligiblePlayerIds.isEmpty()) throw new IllegalArgumentException("pot requires an eligible player");
    }
}
