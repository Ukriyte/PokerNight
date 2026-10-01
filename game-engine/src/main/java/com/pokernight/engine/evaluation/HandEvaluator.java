package com.pokernight.engine.evaluation;

import com.pokernight.engine.hand.HoleCards;
import com.pokernight.engine.model.Card;
import com.pokernight.engine.model.Rank;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Pure evaluator that chooses the strongest five-card combination from five to seven cards. */
public final class HandEvaluator {
    public EvaluatedHand evaluate(HoleCards holeCards, List<Card> communityCards) {
        List<Card> cards = new ArrayList<>(holeCards.asList());
        cards.addAll(communityCards);
        return evaluate(cards);
    }

    public EvaluatedHand evaluate(List<Card> cards) {
        if (cards.size() < 5 || cards.size() > 7) {
            throw new IllegalArgumentException("hand evaluation requires between five and seven cards");
        }
        if (new LinkedHashSet<>(cards).size() != cards.size()) {
            throw new IllegalArgumentException("cards must be unique");
        }

        EvaluatedHand best = null;
        for (List<Card> five : combinationsOfFive(cards)) {
            EvaluatedHand candidate = evaluateFive(five);
            if (best == null || candidate.value().compareTo(best.value()) > 0) best = candidate;
        }
        return best;
    }

    private EvaluatedHand evaluateFive(List<Card> cards) {
        List<Integer> ranks = cards.stream().map(this::rankValue).sorted(Comparator.reverseOrder()).toList();
        Map<Integer, Integer> counts = new HashMap<>();
        ranks.forEach(rank -> counts.merge(rank, 1, Integer::sum));

        boolean flush = cards.stream().map(Card::suit).distinct().count() == 1;
        Integer straightHigh = straightHigh(ranks);
        HandValue value;

        if (flush && straightHigh != null) {
            value = new HandValue(HandCategory.STRAIGHT_FLUSH, List.of(straightHigh));
        } else {
            List<Integer> four = ranksWithCount(counts, 4);
            List<Integer> three = ranksWithCount(counts, 3);
            List<Integer> pairs = ranksWithCount(counts, 2);
            if (!four.isEmpty()) {
                value = new HandValue(HandCategory.FOUR_OF_A_KIND,
                    List.of(four.getFirst(), highestExcept(ranks, four.getFirst())));
            } else if (!three.isEmpty() && !pairs.isEmpty()) {
                value = new HandValue(HandCategory.FULL_HOUSE, List.of(three.getFirst(), pairs.getFirst()));
            } else if (flush) {
                value = new HandValue(HandCategory.FLUSH, ranks);
            } else if (straightHigh != null) {
                value = new HandValue(HandCategory.STRAIGHT, List.of(straightHigh));
            } else if (!three.isEmpty()) {
                value = new HandValue(HandCategory.THREE_OF_A_KIND,
                    append(three.getFirst(), kickersExcluding(ranks, List.of(three.getFirst()), 2)));
            } else if (pairs.size() >= 2) {
                int highPair = pairs.get(0);
                int lowPair = pairs.get(1);
                value = new HandValue(HandCategory.TWO_PAIR,
                    List.of(highPair, lowPair, highestExcept(ranks, highPair, lowPair)));
            } else if (pairs.size() == 1) {
                int pair = pairs.getFirst();
                value = new HandValue(HandCategory.ONE_PAIR,
                    append(pair, kickersExcluding(ranks, List.of(pair), 3)));
            } else {
                value = new HandValue(HandCategory.HIGH_CARD, ranks);
            }
        }
        return new EvaluatedHand(value, cards);
    }

    private List<List<Card>> combinationsOfFive(List<Card> cards) {
        List<List<Card>> result = new ArrayList<>();
        choose(cards, 0, new ArrayList<>(5), result);
        return result;
    }

    private void choose(List<Card> cards, int start, List<Card> selected, List<List<Card>> result) {
        if (selected.size() == 5) {
            result.add(List.copyOf(selected));
            return;
        }
        int needed = 5 - selected.size();
        for (int i = start; i <= cards.size() - needed; i++) {
            selected.add(cards.get(i));
            choose(cards, i + 1, selected, result);
            selected.removeLast();
        }
    }

    private List<Integer> ranksWithCount(Map<Integer, Integer> counts, int count) {
        return counts.entrySet().stream()
            .filter(entry -> entry.getValue() == count)
            .map(Map.Entry::getKey)
            .sorted(Comparator.reverseOrder())
            .toList();
    }

    private Integer straightHigh(List<Integer> descendingRanks) {
        List<Integer> unique = new ArrayList<>(new LinkedHashSet<>(descendingRanks));
        if (unique.contains(14)) unique.add(1);
        int run = 1;
        for (int i = 1; i < unique.size(); i++) {
            if (unique.get(i - 1) - 1 == unique.get(i)) {
                run++;
                if (run == 5) return unique.get(i - 4);
            } else {
                run = 1;
            }
        }
        return null;
    }

    private int rankValue(Card card) {
        return switch (card.rank()) {
            case TWO -> 2;
            case THREE -> 3;
            case FOUR -> 4;
            case FIVE -> 5;
            case SIX -> 6;
            case SEVEN -> 7;
            case EIGHT -> 8;
            case NINE -> 9;
            case TEN -> 10;
            case JACK -> 11;
            case QUEEN -> 12;
            case KING -> 13;
            case ACE -> 14;
        };
    }

    private int highestExcept(List<Integer> ranks, int... excluded) {
        for (int rank : ranks) {
            boolean skip = false;
            for (int value : excluded) if (rank == value) skip = true;
            if (!skip) return rank;
        }
        throw new IllegalStateException("no kicker found");
    }

    private List<Integer> kickersExcluding(List<Integer> ranks, List<Integer> excluded, int limit) {
        return ranks.stream().filter(rank -> !excluded.contains(rank)).distinct().limit(limit).toList();
    }

    private List<Integer> append(int first, List<Integer> remainder) {
        List<Integer> result = new ArrayList<>(1 + remainder.size());
        result.add(first);
        result.addAll(remainder);
        return List.copyOf(result);
    }
}
