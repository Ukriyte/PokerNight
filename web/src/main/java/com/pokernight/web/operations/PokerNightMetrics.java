package com.pokernight.web.operations;

import com.pokernight.application.room.RoomManager;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public final class PokerNightMetrics {
    public PokerNightMetrics(MeterRegistry registry, RoomManager rooms) {
        Gauge.builder("pokernight.rooms.active", rooms, RoomManager::activeRoomCount)
            .description("Number of rooms held by this application instance")
            .register(registry);
    }
}
