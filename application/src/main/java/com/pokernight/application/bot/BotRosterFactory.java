package com.pokernight.application.bot;

import java.util.List;

/** Creates bot identities and seats. It has no gameplay responsibilities. */
@FunctionalInterface
public interface BotRosterFactory {
    List<BotSeat> create(String roomCode, int botCount, int maximumPlayers);
}
