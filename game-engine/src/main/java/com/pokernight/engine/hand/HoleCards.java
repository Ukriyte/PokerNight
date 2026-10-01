package com.pokernight.engine.hand;

import com.pokernight.engine.model.Card;

import java.util.List;
import java.util.Objects;

public record HoleCards(Card first, Card second) {
    public HoleCards {
        Objects.requireNonNull(first, "first card must not be null");
        Objects.requireNonNull(second, "second card must not be null");
        if (first.equals(second)) {
            throw new IllegalArgumentException("hole cards must be different");
        }
    }

    public List<Card> asList() {
        return List.of(first, second);
    }
}
