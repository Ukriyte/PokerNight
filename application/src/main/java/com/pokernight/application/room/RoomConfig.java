package com.pokernight.application.room;

import com.pokernight.engine.GameRules;
import com.pokernight.engine.betting.BlindStructure;

public record RoomConfig(
    long startingChips,
    BlindStructure blinds,
    int maximumPlayers,
    int botCount,
    RoomAppearance appearance
) {
    public RoomConfig {
        if (startingChips <= 0) throw new IllegalArgumentException("starting chips must be positive");
        if (blinds == null) throw new IllegalArgumentException("blinds must not be null");
        if (startingChips < blinds.bigBlind()) {
            throw new IllegalArgumentException("starting chips must cover at least the big blind");
        }
        if (maximumPlayers < 2 || maximumPlayers > 6) {
            throw new IllegalArgumentException("maximum players must be between 2 and 6");
        }
        if (botCount < 0 || botCount >= maximumPlayers) {
            throw new IllegalArgumentException("bot count must be between 0 and maximumPlayers - 1");
        }
        appearance = appearance == null ? RoomAppearance.standard() : appearance;
    }

    public RoomConfig(long startingChips, BlindStructure blinds, int maximumPlayers, int botCount) {
        this(startingChips, blinds, maximumPlayers, botCount, RoomAppearance.standard());
    }

    public static RoomConfig standard() {
        return new RoomConfig(1_000, new BlindStructure(25, 50), 6, 0, RoomAppearance.standard());
    }

    GameRules gameRules() {
        return new GameRules(blinds);
    }
}
