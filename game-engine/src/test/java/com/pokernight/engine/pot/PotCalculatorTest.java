package com.pokernight.engine.pot;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PotCalculatorTest {
    @Test
    void createsMainAndSidePotsAndRefundsUncalledExcess() {
        Map<String, Long> contributions = Map.of("p1", 100L, "p2", 300L, "p3", 500L);

        PotBreakdown result = PotCalculator.calculate(contributions, Set.of("p2"));

        assertThat(result.pots()).containsExactly(
            new Pot(300, Set.of("p1", "p3")),
            new Pot(400, Set.of("p3"))
        );
        assertThat(result.refunds()).containsEntry("p3", 200L);
        assertThat(result.totalAccountedFor()).isEqualTo(900L);
    }

    @Test
    void ledgerTracksContributionsAcrossMultipleBettingRounds() {
        ContributionLedger ledger = new ContributionLedger();
        ledger.registerPlayer("p1");
        ledger.registerPlayer("p2");

        ledger.record("p1", 50);
        ledger.record("p1", 100);
        ledger.record("p2", 150);

        assertThat(ledger.snapshot()).containsEntry("p1", 150L).containsEntry("p2", 150L);
        assertThat(ledger.total()).isEqualTo(300L);
    }
}
