package com.pokernight.web.operations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component("snapshotPersistence")
public final class PersistenceHealthIndicator implements HealthIndicator {
    private final boolean enabled;
    private final Path directory;

    public PersistenceHealthIndicator(
        @Value("${pokernight.persistence.enabled:false}") boolean enabled,
        @Value("${pokernight.persistence.directory:./data}") String directory
    ) {
        this.enabled = enabled;
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public Health health() {
        if (!enabled) return Health.up().withDetail("mode", "memory").build();
        boolean usable = Files.isDirectory(directory) && Files.isReadable(directory) && Files.isWritable(directory);
        return usable
            ? Health.up().withDetail("mode", "file").withDetail("directory", directory).build()
            : Health.down().withDetail("mode", "file").withDetail("directory", directory)
                .withDetail("reason", "directory is not readable and writable").build();
    }
}
