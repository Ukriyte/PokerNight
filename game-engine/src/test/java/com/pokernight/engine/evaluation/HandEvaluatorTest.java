package com.pokernight.engine.evaluation;

import com.pokernight.engine.model.Card;
import com.pokernight.engine.model.Rank;
import com.pokernight.engine.model.Suit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HandEvaluatorTest {
    private final HandEvaluator evaluator = new HandEvaluator();

    @Test
    void choosesASevenCardStraightFlushOverOtherPossibilities() {
        EvaluatedHand result = evaluator.evaluate(List.of(
            c(Rank.ACE, Suit.SPADES), c(Rank.KING, Suit.SPADES), c(Rank.QUEEN, Suit.SPADES),
            c(Rank.JACK, Suit.SPADES), c(Rank.TEN, Suit.SPADES), c(Rank.ACE, Suit.HEARTS),
            c(Rank.ACE, Suit.CLUBS)
        ));

        assertThat(result.value().category()).isEqualTo(HandCategory.STRAIGHT_FLUSH);
        assertThat(result.value().tieBreakers()).containsExactly(14);
    }

    @Test
    void recognisesTheWheelAsFiveHighStraight() {
        EvaluatedHand result = evaluator.evaluate(List.of(
            c(Rank.ACE, Suit.SPADES), c(Rank.TWO, Suit.HEARTS), c(Rank.THREE, Suit.CLUBS),
            c(Rank.FOUR, Suit.DIAMONDS), c(Rank.FIVE, Suit.SPADES)
        ));

        assertThat(result.value()).isEqualTo(new HandValue(HandCategory.STRAIGHT, List.of(5)));
    }

    @Test
    void comparesPairKickersInOrder() {
        HandValue aceKicker = evaluator.evaluate(List.of(
            c(Rank.KING, Suit.SPADES), c(Rank.KING, Suit.HEARTS), c(Rank.ACE, Suit.CLUBS),
            c(Rank.NINE, Suit.DIAMONDS), c(Rank.THREE, Suit.SPADES)
        )).value();
        HandValue queenKicker = evaluator.evaluate(List.of(
            c(Rank.KING, Suit.CLUBS), c(Rank.KING, Suit.DIAMONDS), c(Rank.QUEEN, Suit.CLUBS),
            c(Rank.NINE, Suit.HEARTS), c(Rank.THREE, Suit.CLUBS)
        )).value();

        assertThat(aceKicker).isGreaterThan(queenKicker);
    }

    private static Card c(Rank rank, Suit suit) {
        return new Card(rank, suit);
    }
}
