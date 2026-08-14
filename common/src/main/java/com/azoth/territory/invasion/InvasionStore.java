package com.azoth.territory.invasion;

import java.util.Collection;

public interface InvasionStore {
    Collection<InvasionRecord> load();
    void save(Collection<InvasionRecord> records);
}
