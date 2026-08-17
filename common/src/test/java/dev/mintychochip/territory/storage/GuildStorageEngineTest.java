package dev.mintychochip.territory.storage;

import dev.mintychochip.territory.model.BlockPos;
import dev.mintychochip.territory.model.Boundary;
import dev.mintychochip.territory.model.FacilityType;
import dev.mintychochip.territory.model.Government;
import dev.mintychochip.territory.model.SettlementFacility;
import dev.mintychochip.territory.model.Territory;
import dev.mintychochip.territory.model.ZoneType;
import dev.mintychochip.territory.registry.FacilityRegistry;
import dev.mintychochip.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Access and mutation rules for the guild item bank. */
class GuildStorageEngineTest {
    private static final OpaqueItemPayload STONE =
            new OpaqueItemPayload("paper-itemstack-bytes-v1", "fp", "c3RvbmU=");

    private UUID member;
    private UUID officer;
    private UUID outsider;
    private GuildStorageEngine engine;

    @BeforeEach
    void setUp() {
        member = UUID.randomUUID();
        officer = UUID.randomUUID();
        outsider = UUID.randomUUID();
        Territory territory = new Territory(
                "t1", "Territory", "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0), new BlockPos(100, 0),
                        new BlockPos(100, 100), new BlockPos(0, 100))),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), "guild-1");
        TerritoryRegistry territories = new TerritoryRegistry(List.of(territory));
        FacilityRegistry facilities = new FacilityRegistry(territories);
        facilities.register(new SettlementFacility(
                "vault", "Vault", "t1", FacilityType.STORAGE, "world", 5, 64, 5));
        facilities.register(new SettlementFacility(
                "market", "Market", "t1", FacilityType.TRADING_POST, "world", 6, 64, 5));
        FakeAccess access = new FakeAccess(Set.of(member, officer), Set.of(officer));
        engine = new GuildStorageEngine(territories, facilities, access, new MemoryGuildStorageStore());
    }

    @Test
    void deniesMissingFacilityAndWrongType() {
        assertEquals(StorageStatus.DENIED_NO_FACILITY,
                engine.open(member, "world", 1, 64, 1).status());
        assertEquals(StorageStatus.DENIED_WRONG_TYPE,
                engine.open(member, "world", 6, 64, 5).status());
    }

    @Test
    void deniesOutsiderAndAllowsMemberOpen() {
        assertEquals(StorageStatus.DENIED_NOT_RESIDENT,
                engine.open(outsider, "world", 5, 64, 5).status());
        StorageResult opened = engine.open(member, "world", 5, 64, 5);
        assertEquals(StorageStatus.OPENED, opened.status());
        assertNotNull(opened.snapshot());
        assertEquals(54, opened.snapshot().capacitySlots());
        assertTrue(opened.snapshot().canDeposit());
        assertFalse(opened.snapshot().canWithdraw());
    }

    @Test
    void memberMayDepositButNotWithdraw() {
        assertEquals(StorageStatus.DEPOSITED,
                engine.deposit(member, "world", 5, 64, 5, 0, STONE).status());
        assertEquals(StorageStatus.DENIED_NO_PERMISSION,
                engine.withdraw(member, "world", 5, 64, 5, 0).status());
        StorageResult taken = engine.withdraw(officer, "world", 5, 64, 5, 0);
        assertEquals(StorageStatus.WITHDRAWN, taken.status());
        assertEquals(STONE, taken.item());
    }

    @Test
    void secondViewerIsRejectedUntilClose() {
        assertEquals(StorageStatus.OPENED, engine.open(member, "world", 5, 64, 5).status());
        assertEquals(StorageStatus.DENIED_IN_USE, engine.open(officer, "world", 5, 64, 5).status());
        assertEquals(StorageStatus.CLOSED, engine.close(member, "guild-1").status());
        assertEquals(StorageStatus.OPENED, engine.open(officer, "world", 5, 64, 5).status());
    }

    @Test
    void saveRejectsStaleRevision() {
        StorageResult opened = engine.open(officer, "world", 5, 64, 5);
        assertEquals(StorageStatus.SAVED, engine.save(officer, "guild-1",
                opened.snapshot().revision(), List.of(new StorageSlot(3, STONE))).status());
        assertEquals(StorageStatus.CONFLICT, engine.save(officer, "guild-1",
                opened.snapshot().revision(), List.of()).status());
    }

    private static final class FakeAccess implements GuildStorageAccess {
        private final Set<UUID> residents;
        private final Set<UUID> officers;

        private FakeAccess(Set<UUID> residents, Set<UUID> officers) {
            this.residents = new HashSet<>(residents);
            this.officers = new HashSet<>(officers);
        }

        @Override
        public boolean isResident(UUID playerId, String guildId) {
            return "guild-1".equals(guildId) && residents.contains(playerId);
        }

        @Override
        public boolean canDeposit(UUID playerId, String guildId) {
            return isResident(playerId, guildId);
        }

        @Override
        public boolean canWithdraw(UUID playerId, String guildId) {
            return "guild-1".equals(guildId) && officers.contains(playerId);
        }
    }
}
