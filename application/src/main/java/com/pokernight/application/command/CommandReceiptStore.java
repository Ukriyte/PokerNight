package com.pokernight.application.command;

import java.util.List;

public interface CommandReceiptStore {
    List<PersistedCommand> loadAll();
    void saveAll(List<PersistedCommand> commands);
}
