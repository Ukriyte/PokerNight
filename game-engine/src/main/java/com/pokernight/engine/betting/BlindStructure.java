package com.pokernight.engine.betting;

public record BlindStructure(long smallBlind, long bigBlind) {
    public BlindStructure {
        if (smallBlind <= 0 || bigBlind <= 0) {
            throw new IllegalArgumentException("blinds must be positive");
        }
        if (smallBlind >= bigBlind) {
            throw new IllegalArgumentException("small blind must be less than big blind");
        }
    }
}
