package com.pokernight.engine.betting;

public record BettingActionResult(long committedChips, BettingRoundSnapshot state) { }
