package org.aincraft.guilds.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import org.aincraft.guilds.models.Resident;
import org.aincraft.guilds.services.ResidentService;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResidentArgumentTypeTest {
    @Test
    void parsesResidentKnownOnlyToPersistentServiceWhenOffline() throws Exception {
        UUID playerId = UUID.randomUUID();
        ResidentService residents = mock(ResidentService.class);
        when(residents.getResident("offline-user"))
                .thenReturn(Optional.of(new Resident(playerId, "offline-user")));

        ResidentArgumentType argument = ResidentArgumentType.resident(residents);

        assertEquals("offline-user", argument.parse(new StringReader("offline-user")));
        verify(residents).getResident("offline-user");
    }

    @Test
    void suggestsOfflineResidentsFromPersistentSearch() {
        ResidentService residents = mock(ResidentService.class);
        when(residents.searchResidents("off", 50))
                .thenReturn(java.util.List.of(new Resident(UUID.randomUUID(), "offline-user")));
        ResidentArgumentType argument = ResidentArgumentType.resident(residents);

        var suggestions = argument.listSuggestions(
                null, new SuggestionsBuilder("off", "off", 0)).join();

        assertEquals("offline-user", suggestions.getList().getFirst().getText());
    }

    @Test
    void rejectsUnknownPersistentResidentWithoutInventingAnOfflineUuid() {
        ResidentService residents = mock(ResidentService.class);
        when(residents.getResident("never-seen")).thenReturn(Optional.empty());

        ResidentArgumentType argument = ResidentArgumentType.resident(residents);

        assertThrows(CommandSyntaxException.class, () -> argument.parse(new StringReader("never-seen")));
        verify(residents).getResident("never-seen");
    }
}
