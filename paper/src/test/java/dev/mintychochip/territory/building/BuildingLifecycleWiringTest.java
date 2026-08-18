package dev.mintychochip.territory.building;

import dev.mintychochip.guilds.GuildsPlugin;
import dev.mintychochip.territory.registry.FacilityRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildingLifecycleWiringTest {
    @Test
    void pluginExposesBuildingRuntimeContracts() throws Exception {
        Method facilities = GuildsPlugin.class.getMethod("getFacilities");
        Method mutations = GuildsPlugin.class.getMethod("getFacilityMutations");
        Method command = GuildsPlugin.class.getMethod("getBuildingCommand");
        Method travel = GuildsPlugin.class.getMethod("getWaystoneTravelService");

        assertEquals(FacilityRegistry.class, facilities.getReturnType());
        assertEquals(FacilityMutationService.class, mutations.getReturnType());
        assertEquals(dev.mintychochip.guilds.commands.brigadier.BuildingCommand.class, command.getReturnType());
        assertEquals(WaystoneTravelService.class, travel.getReturnType());
    }
}
