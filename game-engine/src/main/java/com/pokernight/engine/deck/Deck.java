package com.pokernight.engine.deck;

import com.pokernight.engine.model.Card;
import com.pokernight.engine.model.Rank;
import com.pokernight.engine.model.Suit;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

public final class Deck {
    private static final int CARD_COUNT = 52;

    private final CardShuffler shuffler;
    private final List<Card> cards = new ArrayList<>(CARD_COUNT);
    private int nextCardIndex;

    public Deck() {
        this(new SecureCardShuffler());
    }

    public Deck(CardShuffler shuffler) {
        this.shuffler = shuffler;
        reset();
    }

    public void reset() {
        cards.clear();
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                cards.add(new Card(rank, suit));
            }
        }
        nextCardIndex = 0;
    }

    public void shuffle() {
        shuffler.shuffle(cards);
        nextCardIndex = 0;
    }

    public Card deal() {
        if (nextCardIndex >= cards.size()) {
            throw new NoSuchElementException("deck is empty");
        }
        return cards.get(nextCardIndex++);
    }

    public int remaining() {
        return cards.size() - nextCardIndex;
    }
}
