package com.azoth.territory.upkeep;

import java.io.IOException;
import java.util.Collection;

/** Durable snapshot boundary for recurring upkeep state. */
public interface UpkeepStore {
    Collection<UpkeepState> load() throws IOException;

    void save(Collection<UpkeepState> states) throws IOException;
}
