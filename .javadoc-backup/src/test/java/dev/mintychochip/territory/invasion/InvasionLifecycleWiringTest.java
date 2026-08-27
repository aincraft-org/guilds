package dev.mintychochip.territory.invasion;

import dev.mintychochip.territory.AzothTerritoryPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InvasionLifecycleWiringTest {
    @Test
    void pluginRetainsAndCancelsBossBarReconciliationTask() throws Exception {
        Field task = AzothTerritoryPlugin.class.getDeclaredField("invasionBossBarTask");
        assertTrue(BukkitTask.class.isAssignableFrom(task.getType()));
        String source = source();
        assertTrue(source.contains("this.invasionBossBarTask = getServer().getScheduler().runTaskTimer("));
        assertTrue(source.contains("invasionBossBarTask.cancel();"));
        assertTrue(source.contains("invasionBossBarTask = null;"));
    }

    private static String source() throws Exception {
        Path path = Path.of("src/main/java/dev/mintychochip/territory/AzothTerritoryPlugin.java");
        assertTrue(Files.isRegularFile(path));
        return Files.readString(path);
    }
}
