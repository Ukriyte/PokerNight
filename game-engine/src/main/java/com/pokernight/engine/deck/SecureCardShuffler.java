package com.pokernight.engine.deck;

import com.pokernight.engine.model.Card;

import java.security.SecureRandom;
import java.util.List;

public final class SecureCardShuffler implements CardShuffler {
    private final SecureRandom random;

    public SecureCardShuffler() {
        this(new SecureRandom());
    }

    SecureCardShuffler(SecureRandom random) {
        this.random = random;
    }

    @Override
    public void shuffle(List<Card> cards) {
        for (int i = cards.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Card card = cards.get(i);
            cards.set(i, cards.get(j));
            cards.set(j, card);
        }
    }
}
