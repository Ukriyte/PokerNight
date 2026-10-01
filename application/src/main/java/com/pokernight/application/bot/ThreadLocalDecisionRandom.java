package com.pokernight.application.bot;

import java.util.concurrent.ThreadLocalRandom;

public final class ThreadLocalDecisionRandom implements DecisionRandom {
    @Override
    public int nextInt(int bound) {
        return ThreadLocalRandom.current().nextInt(bound);
    }
}
