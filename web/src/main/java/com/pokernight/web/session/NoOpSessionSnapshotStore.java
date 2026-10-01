package com.pokernight.web.session;

import java.util.List;

public final class NoOpSessionSnapshotStore implements SessionSnapshotStore {
    @Override public List<SessionSnapshot> loadAll() { return List.of(); }
    @Override public void saveAll(List<SessionSnapshot> sessions) { }
}
