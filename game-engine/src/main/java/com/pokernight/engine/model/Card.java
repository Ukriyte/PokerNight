package com.pokernight.engine.model;

import java.util.Objects;

public record Card(Rank rank, Suit suit) {
    public Card {
        Objects.requireNonNull(rank, "rank must not be null");
        Objects.requireNonNull(suit, "suit must not be null");
    }

    public String code() {
        return rank.code() + suit.name().charAt(0);
    }
}
