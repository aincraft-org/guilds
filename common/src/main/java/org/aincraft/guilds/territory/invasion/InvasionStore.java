package org.aincraft.guilds.territory.invasion;

import java.util.Collection;

public interface InvasionStore {
    Collection<InvasionRecord> load();
    void save(Collection<InvasionRecord> records);
}
