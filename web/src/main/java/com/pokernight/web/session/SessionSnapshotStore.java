package com.pokernight.web.session;

import java.util.List;

public interface SessionSnapshotStore {
    List<SessionSnapshot> loadAll();
    void saveAll(List<SessionSnapshot> sessions);
}
