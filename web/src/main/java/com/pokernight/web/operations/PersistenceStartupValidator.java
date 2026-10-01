package com.pokernight.web.operations;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public final class PersistenceStartupValidator {
    private final boolean enabled;
    private final Path directory;

    public PersistenceStartupValidator(
        @Value("${pokernight.persistence.enabled:false}") boolean enabled,
        @Value("${pokernight.persistence.directory:./data}") String directory
    ) {
        this.enabled = enabled;
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    @PostConstruct
    void validate() {
        if (!enabled) return;
        try { Files.createDirectories(directory); }
        catch (IOException exception) {
            throw new IllegalStateException("cannot create persistence directory " + directory, exception);
        }
        if (!Files.isReadable(directory) || !Files.isWritable(directory)) {
            throw new IllegalStateException("persistence directory must be readable and writable: " + directory);
        }
    }
}
