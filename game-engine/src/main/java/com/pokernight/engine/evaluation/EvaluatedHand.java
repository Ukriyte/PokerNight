package com.pokernight.engine.evaluation;

import com.pokernight.engine.model.Card;

import java.util.List;

public record EvaluatedHand(HandValue value, List<Card> bestFiveCards) {
    public EvaluatedHand {
        bestFiveCards = List.copyOf(bestFiveCards);
        if (bestFiveCards.size() != 5) throw new IllegalArgumentException("best hand must contain five cards");
    }

    public String displayName() {
        return switch (value.category()) {
            case HIGH_CARD -> "High Card";
            case ONE_PAIR -> "One Pair";
            case TWO_PAIR -> "Two Pair";
            case THREE_OF_A_KIND -> "Three of a Kind";
            case STRAIGHT -> "Straight";
            case FLUSH -> "Flush";
            case FULL_HOUSE -> "Full House";
            case FOUR_OF_A_KIND -> "Four of a Kind";
            case STRAIGHT_FLUSH -> "Straight Flush";
        };
    }
}
