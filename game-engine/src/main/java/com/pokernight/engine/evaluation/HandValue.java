package com.pokernight.engine.evaluation;

import java.util.List;

/** Comparable poker value. Category is compared first, then ordered tie breakers. */
public record HandValue(HandCategory category, List<Integer> tieBreakers) implements Comparable<HandValue> {
    public HandValue {
        if (category == null) throw new IllegalArgumentException("category must not be null");
        tieBreakers = List.copyOf(tieBreakers);
    }

    @Override
    public int compareTo(HandValue other) {
        int categoryComparison = Integer.compare(category.ordinal(), other.category.ordinal());
        if (categoryComparison != 0) return categoryComparison;
        int size = Math.min(tieBreakers.size(), other.tieBreakers.size());
        for (int i = 0; i < size; i++) {
            int comparison = Integer.compare(tieBreakers.get(i), other.tieBreakers.get(i));
            if (comparison != 0) return comparison;
        }
        return Integer.compare(tieBreakers.size(), other.tieBreakers.size());
    }
}
