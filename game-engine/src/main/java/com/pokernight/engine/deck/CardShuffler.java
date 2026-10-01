package com.pokernight.engine.deck;

import com.pokernight.engine.model.Card;

import java.util.List;

@FunctionalInterface
public interface CardShuffler {
    void shuffle(List<Card> cards);
}
