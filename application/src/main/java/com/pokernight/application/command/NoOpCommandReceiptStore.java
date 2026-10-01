package com.pokernight.application.command;

import java.util.List;

public final class NoOpCommandReceiptStore implements CommandReceiptStore {
    @Override public List<PersistedCommand> loadAll() { return List.of(); }
    @Override public void saveAll(List<PersistedCommand> commands) { }
}
