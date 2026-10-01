package com.pokernight.application.command;

import com.pokernight.application.room.GameCommandResult;

/** result is absent only when replaying a receipt loaded after process restart. */
public record IdempotentCommandResult(
    GameCommandResult result,
    CommandResultSummary summary,
    boolean replayed
) { }
