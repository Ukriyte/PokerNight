package com.pokernight.engine.deck;

import com.pokernight.engine.model.Card;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Collections;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeckTest {
    @Test
    void containsExactlyFiftyTwoUniqueCards() {
        Deck deck = new Deck(cards -> { });

        var dealt = new HashSet<Card>();
        for (int i = 0; i < 52; i++) {
            dealt.add(deck.deal());
        }

        assertThat(dealt).hasSize(52);
        assertThat(deck.remaining()).isZero();
        assertThatThrownBy(deck::deal).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void resetRestoresAConsumedDeck() {
        Deck deck = new Deck(Collections::reverse);
        deck.shuffle();
        deck.deal();

        deck.reset();

        assertThat(deck.remaining()).isEqualTo(52);
    }
}
