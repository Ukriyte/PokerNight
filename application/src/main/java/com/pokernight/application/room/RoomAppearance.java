package com.pokernight.application.room;

import java.util.Set;

/** Visual room configuration. It is application metadata and never enters the game engine. */
public record RoomAppearance(String themeId, String cardPackId) {
    private static final Set<String> THEMES = Set.of("midnight", "broadcast");
    private static final Set<String> CARD_PACKS = Set.of("classic", "four-color");

    public RoomAppearance {
        themeId = themeId == null ? "midnight" : themeId;
        cardPackId = cardPackId == null ? "classic" : cardPackId;
        if (!THEMES.contains(themeId)) throw new IllegalArgumentException("unsupported room theme");
        if (!CARD_PACKS.contains(cardPackId)) throw new IllegalArgumentException("unsupported card pack");
    }

    public static RoomAppearance standard() {
        return new RoomAppearance("midnight", "classic");
    }
}
