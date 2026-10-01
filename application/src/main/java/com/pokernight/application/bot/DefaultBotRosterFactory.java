package com.pokernight.application.bot;

import java.util.ArrayList;
import java.util.List;

/** Places bots in the highest numbered seats, leaving low seats convenient for humans. */
public final class DefaultBotRosterFactory implements BotRosterFactory {
    @Override
    public List<BotSeat> create(String roomCode, int botCount, int maximumPlayers) {
        if (botCount < 0 || botCount >= maximumPlayers) throw new IllegalArgumentException("invalid bot count");
        List<BotSeat> bots = new ArrayList<>(botCount);
        for (int index = 0; index < botCount; index++) {
            int number = index + 1;
            bots.add(new BotSeat("bot-" + roomCode + "-" + number, "Bot " + number, maximumPlayers - number));
        }
        return List.copyOf(bots);
    }
}
