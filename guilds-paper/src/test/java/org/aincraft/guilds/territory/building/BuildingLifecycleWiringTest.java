package org.aincraft.guilds.territory.building;

import org.aincraft.guilds.GuildsPlugin;
import org.aincraft.guilds.territory.registry.FacilityRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildingLifecycleWiringTest {
    @Test
    void pluginExposesBuildingRuntimeContracts() throws Exception {
        Method facilities = GuildsPlugin.class.getMethod("getFacilities");
        Method mutations = GuildsPlugin.class.getMethod("getFacilityMutations");
        Method command = GuildsPlugin.class.getMethod("getBuildingCommand");
        Method travel = GuildsPlugin.class.getMethod("getFastTravelService");

        assertEquals(FacilityRegistry.class, facilities.getReturnType());
        assertEquals(FacilityMutationService.class, mutations.getReturnType());
        assertEquals(BuildingCommand.class, command.getReturnType());
        assertEquals(FastTravelService.class, travel.getReturnType());
    }
}
