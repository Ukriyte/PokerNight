package com.pokernight.engine.pot;

import java.util.List;
import java.util.Map;

public record PotBreakdown(List<Pot> pots, Map<String, Long> refunds) {
    public PotBreakdown {
        pots = List.copyOf(pots);
        refunds = Map.copyOf(refunds);
    }

    public long totalAccountedFor() {
        long potTotal = pots.stream().mapToLong(Pot::amount).sum();
        long refundTotal = refunds.values().stream().mapToLong(Long::longValue).sum();
        return potTotal + refundTotal;
    }
}
