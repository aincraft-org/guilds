package org.aincraft.guilds.alliances;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllianceProposalStoreTest {

    private static final UUID MAYOR_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID MAYOR_C = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    private AllianceProposalStore store;

    @BeforeEach
    void setUp() {
        store = new AllianceProposalStore();
    }

    @Test
    void proposeRecordsPendingAllianceWithoutCommitting() {
        AllianceProposal proposal = store.propose("Pact", "guild-a", MAYOR_A, "guild-b");

        assertEquals("Pact", proposal.name());
        assertEquals("guild-a", proposal.proposingGuildId());
        assertEquals(MAYOR_A, proposal.proposingMayorUuid());
        assertEquals(Set.of("guild-a"), proposal.acceptedGuildIds());
        assertEquals(Set.of("guild-b"), proposal.invitedGuildIds());
        assertTrue(store.get("Pact").isPresent());
        assertTrue(store.isGuildBusy("guild-a"));
        assertTrue(store.isGuildBusy("guild-b"));
    }

    @Test
    void targetAcceptCommitsWhenMinimumIsTwo() {
        store.propose("Pact", "guild-a", MAYOR_A, "guild-b");

        AllianceProposalStore.AcceptOutcome outcome = store.accept("Pact", "guild-b", 2);

        assertTrue(outcome.committed());
        assertEquals(Set.of("guild-a", "guild-b"), outcome.proposal().acceptedGuildIds());
        assertTrue(store.get("Pact").isEmpty());
        assertFalse(store.isGuildBusy("guild-a"));
        assertFalse(store.isGuildBusy("guild-b"));
    }

    @Test
    void acceptDoesNotCommitUntilMinimumGuildsAreReached() {
        store.propose("Pact", "guild-a", MAYOR_A, "guild-b");

        AllianceProposalStore.AcceptOutcome first = store.accept("Pact", "guild-b", 3);

        assertFalse(first.committed());
        assertEquals(Set.of("guild-a", "guild-b"), first.proposal().acceptedGuildIds());
        assertTrue(store.get("Pact").isPresent());

        store.invite("Pact", "guild-a", "guild-c");
        AllianceProposalStore.AcceptOutcome second = store.accept("Pact", "guild-c", 3);

        assertTrue(second.committed());
        assertEquals(Set.of("guild-a", "guild-b", "guild-c"), second.proposal().acceptedGuildIds());
        assertTrue(store.get("Pact").isEmpty());
    }

    @Test
    void rejectSelfTargetDuplicateNameAndBusyGuild() {
        store.propose("Pact", "guild-a", MAYOR_A, "guild-b");

        assertThrows(IllegalArgumentException.class,
                () -> store.propose("Other", "guild-a", MAYOR_A, "guild-a"));
        assertThrows(IllegalArgumentException.class,
                () -> store.propose("Pact", "guild-c", MAYOR_C, "guild-d"));
        assertThrows(IllegalArgumentException.class,
                () -> store.propose("Other", "guild-c", MAYOR_C, "guild-b"));
        assertThrows(IllegalArgumentException.class,
                () -> store.accept("Pact", "guild-a", 2));
        assertThrows(IllegalArgumentException.class,
                () -> store.invite("Pact", "guild-b", "guild-c"));
    }
}
