package com.azoth.territory.building;

import com.azoth.territory.AzothTerritoryPlugin;
import com.azoth.territory.registry.FacilityRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildingLifecycleWiringTest {
    @Test
    void pluginExposesBuildingRuntimeContracts() throws Exception {
        Method facilities = AzothTerritoryPlugin.class.getMethod("getFacilities");
        Method mutations = AzothTerritoryPlugin.class.getMethod("getFacilityMutations");
        Method command = AzothTerritoryPlugin.class.getMethod("getBuildingCommand");
        Method travel = AzothTerritoryPlugin.class.getMethod("getWaystoneTravelService");

        assertEquals(FacilityRegistry.class, facilities.getReturnType());
        assertEquals(FacilityMutationService.class, mutations.getReturnType());
        assertEquals(BuildingCommand.class, command.getReturnType());
        assertEquals(WaystoneTravelService.class, travel.getReturnType());
    }
}
