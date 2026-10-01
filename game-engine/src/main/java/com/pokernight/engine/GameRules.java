package com.pokernight.engine;

import com.pokernight.engine.betting.BlindStructure;

public record GameRules(BlindStructure blinds) {
    public GameRules {
        if (blinds == null) throw new IllegalArgumentException("blinds must not be null");
    }

    public static GameRules standard() {
        return new GameRules(new BlindStructure(25, 50));
    }
}
