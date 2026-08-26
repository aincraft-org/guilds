package dev.mintychochip.territory.invasion;

import java.util.Collection;

public interface InvasionStore {
    Collection<InvasionRecord> load();
    void save(Collection<InvasionRecord> records);
}
