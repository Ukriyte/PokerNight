package com.pokernight.engine.hand;

import com.pokernight.engine.deck.Deck;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Creates a fresh shuffled hand and deals one card clockwise per pass from the small blind. */
public final class HandDealer {
    private final Deck deck;

    public HandDealer() {
        this(new Deck());
    }

    public HandDealer(Deck deck) {
        this.deck = deck;
    }

    public DealtHand deal(List<String> clockwisePlayerIds, String smallBlindId) {
        if (clockwisePlayerIds.size() < 2) {
            throw new IllegalArgumentException("at least two players are required");
        }
        int start = clockwisePlayerIds.indexOf(smallBlindId);
        if (start < 0) {
            throw new IllegalArgumentException("small blind must be in the hand");
        }

        deck.reset();
        deck.shuffle();

        Map<String, List<com.pokernight.engine.model.Card>> cards = new LinkedHashMap<>();
        clockwisePlayerIds.forEach(id -> cards.put(id, new ArrayList<>(2)));
        for (int pass = 0; pass < 2; pass++) {
            for (int offset = 0; offset < clockwisePlayerIds.size(); offset++) {
                String playerId = clockwisePlayerIds.get((start + offset) % clockwisePlayerIds.size());
                cards.get(playerId).add(deck.deal());
            }
        }

        Map<String, HoleCards> result = new LinkedHashMap<>();
        cards.forEach((id, pair) -> result.put(id, new HoleCards(pair.get(0), pair.get(1))));
        return new DealtHand(deck, result);
    }
}
