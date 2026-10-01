package com.pokernight.engine.pot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure side-pot calculation from final hand contributions and folded players. */
public final class PotCalculator {
    private PotCalculator() { }

    public static PotBreakdown calculate(Map<String, Long> contributions, Set<String> foldedPlayerIds) {
        validate(contributions);
        List<Long> levels = contributions.values().stream()
            .filter(value -> value > 0)
            .distinct()
            .sorted()
            .toList();

        List<Pot> pots = new ArrayList<>();
        Map<String, Long> refunds = new HashMap<>();
        long previousLevel = 0;

        for (long level : levels) {
            Set<String> contributors = new HashSet<>();
            contributions.forEach((playerId, amount) -> {
                if (amount >= level) contributors.add(playerId);
            });
            long layerAmount = Math.multiplyExact(level - previousLevel, contributors.size());

            if (contributors.size() == 1) {
                String playerId = contributors.iterator().next();
                refunds.merge(playerId, layerAmount, Math::addExact);
            } else if (layerAmount > 0) {
                Set<String> eligible = new HashSet<>(contributors);
                eligible.removeAll(foldedPlayerIds);
                if (eligible.isEmpty()) {
                    throw new IllegalStateException("a pot layer has no eligible player");
                }
                pots.add(new Pot(layerAmount, eligible));
            }
            previousLevel = level;
        }

        return new PotBreakdown(pots, refunds);
    }

    private static void validate(Map<String, Long> contributions) {
        contributions.forEach((playerId, amount) -> {
            if (playerId == null || playerId.isBlank()) throw new IllegalArgumentException("playerId must not be blank");
            if (amount == null || amount < 0) throw new IllegalArgumentException("contributions cannot be negative");
        });
    }
}
