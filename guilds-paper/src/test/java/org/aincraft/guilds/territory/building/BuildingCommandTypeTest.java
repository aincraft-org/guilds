package org.aincraft.guilds.territory.building;

import org.aincraft.guilds.territory.model.FacilityType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BuildingCommandTypeTest {
    @Test
    void parseTypeAcceptsBankAliases() {
        assertEquals(FacilityType.BANK, BuildingCommand.parseType("bank"));
        assertEquals(FacilityType.BANK, BuildingCommand.parseType("BANK"));
        assertEquals(FacilityType.BANK, BuildingCommand.parseType("guild-bank"));
        assertNotNull(BuildingCommand.parseType("storage"));
    }
}
