package org.aincraft.guilds.territory.building;

import org.aincraft.guilds.GuildsPlugin;
import org.aincraft.guilds.territory.registry.FacilityRegistry;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void pluginExposesValidatorAndRouteLifecycleContracts() throws Exception {
        Method validator = GuildsPlugin.class.getMethod("getFastTravelFacilityValidator");
        Method routes = GuildsPlugin.class.getMethod("getBoatRouteService");

        assertEquals(FastTravelFacilityValidator.class, validator.getReturnType());
        assertEquals(org.aincraft.guilds.territory.building.boat.BoatRouteService.class,
                routes.getReturnType());
        String source = Files.readString(findPluginSource());
        assertBefore(source, "enableGuildsSubsystem();", "startBuildings();");
        assertBefore(source, "startWebIfEnabled();", "registerPlaceholderExpansion();");
        assertBefore(source, "guilds.wireFastTravel(fastTravelService, boatRouteService);",
                "new org.aincraft.guilds.territory.building.BuildingListener(");
        assertBefore(source, "fastTravelService.stopAsync()", "boatRouteService.close();");
        assertTrue(source.contains("preloadFastTravelSnapshots()"));
    }

    private static Path findPluginSource() {
        Path direct = Path.of("src/main/java/org/aincraft/guilds/GuildsPlugin.java");
        return Files.isRegularFile(direct)
                ? direct : Path.of("guilds-paper/src/main/java/org/aincraft/guilds/GuildsPlugin.java");
    }

    private static void assertBefore(String source, String first, String second) {
        int firstPosition = source.indexOf(first);
        int secondPosition = source.indexOf(second);
        assertTrue(firstPosition >= 0 && secondPosition >= 0 && firstPosition < secondPosition,
                first + " must precede " + second);
    }
}
