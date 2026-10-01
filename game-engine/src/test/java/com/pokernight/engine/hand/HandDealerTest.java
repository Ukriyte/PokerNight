package com.pokernight.engine.hand;

import com.pokernight.engine.deck.Deck;
import com.pokernight.engine.model.Card;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HandDealerTest {
    @Test
    void dealsClockwiseFromSmallBlindInTwoPasses() {
        HandDealer dealer = new HandDealer(new Deck(cards -> { }));

        DealtHand hand = dealer.deal(List.of("p1", "p2", "p3"), "p2");

        assertThat(codes(hand.holeCardsFor("p2").orElseThrow())).containsExactly("2C", "5C");
        assertThat(codes(hand.holeCardsFor("p3").orElseThrow())).containsExactly("3C", "6C");
        assertThat(codes(hand.holeCardsFor("p1").orElseThrow())).containsExactly("4C", "7C");
    }

    @Test
    void burnsCardsAndNeverDuplicatesAnyVisibleOrPrivateCard() {
        HandDealer dealer = new HandDealer(new Deck(cards -> { }));
        DealtHand hand = dealer.deal(List.of("p1", "p2", "p3"), "p2");

        hand.dealFlop();
        hand.dealTurn();
        hand.dealRiver();

        List<Card> allCards = new java.util.ArrayList<>();
        hand.allHoleCards().values().forEach(hole -> allCards.addAll(hole.asList()));
        allCards.addAll(hand.communityCards());
        allCards.addAll(hand.burnCards());
        assertThat(new HashSet<>(allCards)).hasSameSizeAs(allCards);
        assertThat(hand.communityCards()).extracting(Card::code)
            .containsExactly("9C", "TC", "JC", "KC", "2D");
        assertThat(hand.burnCards()).extracting(Card::code)
            .containsExactly("8C", "QC", "AC");
    }

    @Test
    void preventsStreetsFromBeingDealtOutOfOrder() {
        DealtHand hand = new HandDealer(new Deck(cards -> { }))
            .deal(List.of("p1", "p2"), "p1");

        assertThatThrownBy(hand::dealTurn)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cannot deal turn");
    }

    private static List<String> codes(HoleCards cards) {
        return cards.asList().stream().map(Card::code).toList();
    }
}
