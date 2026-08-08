package com.azoth.territory.standing;

import com.azoth.territory.PostgresTestDatabase;
import com.azoth.territory.model.BlockPos;
import com.azoth.territory.model.Boundary;
import com.azoth.territory.model.Government;
import com.azoth.territory.model.Territory;
import com.azoth.territory.model.ZoneType;
import com.azoth.territory.permission.AllianceBody;
import com.azoth.territory.permission.GovernanceRegistry;
import com.azoth.territory.permission.GovernanceSource;
import com.azoth.territory.permission.GuildBody;
import com.azoth.territory.permission.GuildToggles;
import com.azoth.territory.persist.PostgresDatabase;
import com.azoth.territory.registry.TerritoryRegistry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HarvestBonusListenerTest {

    private TerritoryRegistry territories;
    private TestGovernanceSource source;
    private GovernanceRegistry governance;
    private PostgresDatabase database;
    private StandingEngine engine;
    private HarvestBonusListener listener;
    private World world;
    private Player owner;
    private UUID ownerId;

    @BeforeEach
    void setUp() throws Exception {
        territories = new TerritoryRegistry();
        source = new TestGovernanceSource();
        governance = new GovernanceRegistry(territories, source);
        database = PostgresTestDatabase.open();
        engine = new StandingEngine(governance, StandingConfig.defaults(),
                new PostgresStandingStore(database), Logger.getLogger("test"));
        listener = new HarvestBonusListener(governance, engine);
        world = mock(World.class);
        when(world.getName()).thenReturn("world");

        ownerId = UUID.randomUUID();
        source.putGuild(new GuildBody("everfall-town", "Everfall Town",
                Government.monarchy("m:everfall-town"), List.of(ownerId.toString()),
                GuildToggles.defaults(), Map.of()));
        territories.register(new Territory("everfall", "Everfall", "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0), new BlockPos(100, 0),
                        new BlockPos(100, 100), new BlockPos(0, 100))),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), "everfall-town"));

        owner = mock(Player.class);
        when(owner.getUniqueId()).thenReturn(ownerId);
        when(owner.getLocation()).thenReturn(new Location(world, 10, 64, 10));
    }

    @AfterEach
    void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    private void accrueToTier3() {
        for (int i = 0; i < 30; i++) {
            engine.accrue("everfall", "everfall-town", StandingSource.PVP_KILL);
        }
    }

    /** Minimal in-memory GovernanceSource (common's FakeGovernanceSource is test-only). */
    private static final class TestGovernanceSource implements GovernanceSource {
        private GuildBody guild;

        void putGuild(GuildBody guild) {
            this.guild = guild;
        }

        @Override
        public Optional<GuildBody> guild(String guildId) {
            return guild != null && guild.id().equals(guildId) ? Optional.of(guild) : Optional.empty();
        }

        @Override
        public List<GuildBody> guildsForMember(String holderId) {
            return guild != null && guild.containsMember(holderId) ? List.of(guild) : List.of();
        }

        @Override
        public Optional<AllianceBody> allianceContainingGuild(String guildId) {
            return Optional.empty();
        }

        @Override
        public List<GuildBody> allGuilds() {
            return guild == null ? List.of() : List.of(guild);
        }

        @Override
        public List<AllianceBody> allAlliances() {
            return List.of();
        }
    }

    private Block insideBlock() {
        Block block = mock(Block.class);
        when(block.getLocation()).thenReturn(new Location(world, 10, 64, 10));
        when(block.getWorld()).thenReturn(world);
        when(block.getType()).thenReturn(Material.DIAMOND_ORE);
        return block;
    }

    private ItemStack mockedStack(int amount) {
        ItemStack stack = mock(ItemStack.class);
        java.util.concurrent.atomic.AtomicInteger stored = new java.util.concurrent.atomic.AtomicInteger(amount);
        when(stack.getAmount()).thenAnswer(inv -> stored.get());
        org.mockito.Mockito.doAnswer(inv -> {
            stored.set(inv.getArgument(0));
            return null;
        }).when(stack).setAmount(anyInt());
        when(stack.getType()).thenReturn(Material.DIAMOND);

        // clone() returns a fresh mock whose amount is the last value set on it,
        // so the listener's `extra.setAmount(bonus)` is observable by verifiers.
        ItemStack clone = mock(ItemStack.class);
        java.util.concurrent.atomic.AtomicInteger cloneStored = new java.util.concurrent.atomic.AtomicInteger(0);
        when(clone.getAmount()).thenAnswer(inv -> cloneStored.get());
        org.mockito.Mockito.doAnswer(inv -> {
            cloneStored.set(inv.getArgument(0));
            return null;
        }).when(clone).setAmount(anyInt());
        when(stack.clone()).thenReturn(clone);
        return stack;
    }

    private void stubInventory(Player player) {
        PlayerInventory inv = mock(PlayerInventory.class);
        ItemStack empty = mockedStack(0);
        when(inv.getItemInMainHand()).thenReturn(empty);
        when(player.getInventory()).thenReturn(inv);
    }

    @Test
    void blockBreak_multipliesBaseDropsForOwnerMember() {
        accrueToTier3(); // harvest multiplier 1.5

        Block block = insideBlock();
        ItemStack base = mockedStack(2);
        when(block.getDrops()).thenReturn(List.of(base));
        stubInventory(owner);

        BlockBreakEvent event = new BlockBreakEvent(block, owner);
        listener.onBlockBreak(event);

        // Base 2 diamonds * 1.5 → bonus = floor(2 * 0.5) = 1 extra diamond dropped.
        verify(world).dropItemNaturally(any(Location.class), argThat(s -> s.getAmount() == 1));
    }

    @Test
    void blockBreak_outsiderGetsNoMultiplier() {
        UUID outsiderId = UUID.randomUUID();
        Player outsider = mock(Player.class);
        when(outsider.getUniqueId()).thenReturn(outsiderId);
        when(outsider.getLocation()).thenReturn(new Location(world, 10, 64, 10));
        stubInventory(outsider);

        accrueToTier3();

        Block block = insideBlock();
        ItemStack base = mockedStack(2);
        when(block.getDrops()).thenReturn(List.of(base));
        BlockBreakEvent event = new BlockBreakEvent(block, outsider);
        listener.onBlockBreak(event);

        verify(world, never()).dropItemNaturally(any(), any());
    }

    @Test
    void blockBreak_bonusScalesWithStackAmount() {
        accrueToTier3(); // harvest multiplier 1.5

        Block block = insideBlock();
        ItemStack base = mockedStack(4);
        when(block.getDrops()).thenReturn(List.of(base));
        stubInventory(owner);

        BlockBreakEvent event = new BlockBreakEvent(block, owner);
        listener.onBlockBreak(event);

        // Base 4 diamonds * 1.5 → bonus = floor(4 * 0.5) = 2 extra diamonds.
        verify(world).dropItemNaturally(any(Location.class), argThat(s -> s.getAmount() == 2));
    }
}
