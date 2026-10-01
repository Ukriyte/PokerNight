package com.pokernight.web.config;

import com.pokernight.application.command.IdempotentCommandExecutor;
import com.pokernight.application.command.CommandReceiptStore;
import com.pokernight.application.command.NoOpCommandReceiptStore;
import com.pokernight.application.room.RoomManager;
import com.pokernight.application.room.RoomGameService;
import com.pokernight.application.persistence.NoOpRoomSnapshotStore;
import com.pokernight.application.persistence.RoomSnapshotStore;
import com.pokernight.web.persistence.JsonRoomSnapshotStore;
import com.pokernight.web.persistence.JsonSessionSnapshotStore;
import com.pokernight.web.persistence.JsonCommandReceiptStore;
import com.pokernight.web.session.NoOpSessionSnapshotStore;
import com.pokernight.web.session.SessionSnapshotStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class ApplicationConfig {
    @Bean
    CommandReceiptStore commandReceiptStore(
        ObjectMapper json,
        @Value("${pokernight.persistence.enabled:false}") boolean enabled,
        @Value("${pokernight.persistence.directory:./data}") String directory
    ) {
        return enabled
            ? new JsonCommandReceiptStore(java.nio.file.Path.of(directory, "commands.json"), json)
            : new NoOpCommandReceiptStore();
    }

    @Bean
    RoomSnapshotStore roomSnapshotStore(
        ObjectMapper json,
        @Value("${pokernight.persistence.enabled:false}") boolean enabled,
        @Value("${pokernight.persistence.directory:./data}") String directory
    ) {
        return enabled
            ? new JsonRoomSnapshotStore(java.nio.file.Path.of(directory, "rooms"), json)
            : new NoOpRoomSnapshotStore();
    }

    @Bean
    SessionSnapshotStore sessionSnapshotStore(
        ObjectMapper json,
        @Value("${pokernight.persistence.enabled:false}") boolean enabled,
        @Value("${pokernight.persistence.directory:./data}") String directory
    ) {
        return enabled
            ? new JsonSessionSnapshotStore(java.nio.file.Path.of(directory, "sessions.json"), json)
            : new NoOpSessionSnapshotStore();
    }

    @Bean
    IdempotentCommandExecutor idempotentCommandExecutor(
        CommandReceiptStore store,
        @Value("${pokernight.idempotency.maximum-entries:10000}") int maximumEntries,
        @Value("${pokernight.idempotency.retention:PT24H}") String retention
    ) {
        return new IdempotentCommandExecutor(
            maximumEntries, java.time.Duration.parse(retention), java.time.Clock.systemUTC(), store
        );
    }

    @Bean
    RoomManager roomManager(RoomSnapshotStore snapshots) {
        return new RoomManager(snapshots);
    }

    @Bean
    RoomGameService roomGameService(RoomManager roomManager) {
        return new RoomGameService(roomManager);
    }

    @Bean
    TaskScheduler botTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("poker-bot-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.initialize();
        return scheduler;
    }
}
