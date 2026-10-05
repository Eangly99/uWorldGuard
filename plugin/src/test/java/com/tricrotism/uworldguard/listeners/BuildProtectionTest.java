package com.tricrotism.uworldguard.listeners;

import com.sk89q.worldguard.bukkit.protection.events.DisallowedPVPEvent;
import com.tricrotism.uworldguard.config.Bypass;
import com.tricrotism.uworldguard.config.EventGate;
import com.tricrotism.uworldguard.flags.Flags;
import com.tricrotism.uworldguard.flags.State;
import com.tricrotism.uworldguard.region.ProtectedCuboidRegion;
import com.tricrotism.uworldguard.region.GlobalProtectedRegion;
import com.tricrotism.uworldguard.region.RegionContainerImpl;
import com.tricrotism.uworldguard.region.RegionManager;
import com.tricrotism.uworldguard.region.RegionQuery;
import com.tricrotism.uworldguard.storage.RegionStore;
import com.tricrotism.uworldguard.text.MessageService;
import com.tricrotism.uworldguard.util.BlockVector3;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.entity.FishHookMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Build and PvP protection against a mock server, asserting event cancellation rather than
 * simulating the engine's block changes or fishing-rod pulls.
 */
class BuildProtectionTest {

    private static final RegionStore NO_STORE = new RegionStore() {
        @Override
        public void load(final String worldName, final RegionManager manager) {}

        @Override
        public void save(final String worldName, final RegionManager manager) {}
    };

    private ServerMock server;
    private PluginMock plugin;
    private WorldMock world;
    private RegionContainerImpl container;
    private BuildProtectionListener listener;
    private MessageService messages;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("uWorldGuard");
        world = server.addSimpleWorld("world");

        final File dataFolder = plugin.getDataFolder();
        assertTrue(dataFolder.exists() || dataFolder.mkdirs());
        Files.writeString(new File(dataFolder, "messages.yml").toPath(),
            "cooldown-seconds: 3\nmessages:\n  no-permission: \"<red>You cannot do that here.\"\n",
            StandardCharsets.UTF_8);

        container = new RegionContainerImpl(plugin, NO_STORE);
        container.loadAll();
        messages = new MessageService(plugin);
        final RegionQuery query = container.createQuery();
        listener = new BuildProtectionListener(query, messages);
        server.getPluginManager().registerEvents(listener, plugin);
        EventGate.load(new YamlConfiguration(), server.getLogger());
    }

    @AfterEach
    void tearDown() {
        for (final org.bukkit.entity.Player player : server.getOnlinePlayers()) {
            Bypass.clear(player.getUniqueId());
        }
        EventGate.load(new YamlConfiguration(), server.getLogger());
        MockBukkit.unmock();
    }

    private RegionManager manager() {
        final RegionManager manager = container.get(world);
        assertNotNull(manager, "the world's regions load at enable");
        return manager;
    }

    private ProtectedCuboidRegion claim(final String id) {
        final ProtectedCuboidRegion region = new ProtectedCuboidRegion(id,
            BlockVector3.at(0, 0, 0), BlockVector3.at(32, 255, 32));
        manager().addRegion(region);
        return region;
    }

    private boolean breakBlock(final PlayerMock player, final int x, final int y, final int z) {
        final Block block = world.getBlockAt(x, y, z);
        block.setType(Material.STONE);
        final BlockBreakEvent event = new BlockBreakEvent(block, player);
        listener.onBreak(event);
        return !event.isCancelled();
    }

    @Test
    void aStrangerCannotBreakBlocksInAClaim() {
        claim("plot");
        final PlayerMock stranger = server.addPlayer();

        assertFalse(breakBlock(stranger, 16, 64, 16));
    }

    @Test
    void anOwnerCanBreakBlocksInTheirClaim() {
        final PlayerMock owner = server.addPlayer();
        claim("plot").getOwners().addPlayer(owner.getUniqueId());

        assertTrue(breakBlock(owner, 16, 64, 16));
    }

    @Test
    void wildernessIsNotProtected() {
        claim("plot");
        final PlayerMock stranger = server.addPlayer();

        assertTrue(breakBlock(stranger, 500, 64, 500));
    }

    @Test
    void anExplicitBlockBreakAllowOpensTheRegionToEveryone() {
        claim("spawn").setFlag(Flags.BLOCK_BREAK, State.ALLOW);
        final PlayerMock stranger = server.addPlayer();

        assertTrue(breakBlock(stranger, 16, 64, 16));
    }

    @Test
    void aDenyBlockBreakListOverridesMembership() {
        final PlayerMock owner = server.addPlayer();
        final ProtectedCuboidRegion region = claim("plot");
        region.getOwners().addPlayer(owner.getUniqueId());
        region.setFlag(Flags.DENY_BLOCK_BREAK, java.util.Set.of(Material.STONE));

        assertFalse(breakBlock(owner, 16, 64, 16));
    }

    @Test
    void aDeniedPlayerIsToldOnceWithinTheCooldown() {
        claim("plot");
        final PlayerMock stranger = server.addPlayer();

        breakBlock(stranger, 16, 64, 16);
        breakBlock(stranger, 16, 64, 17);

        assertNotNull(stranger.nextMessage(), "the first denial explains itself");
        assertEquals(null, stranger.nextMessage(), "the second is suppressed by the cooldown");
    }

    @Test
    void theEventGateSkipsAWorldThatOptedOut() {
        claim("plot");
        final PlayerMock stranger = server.addPlayer();
        final org.bukkit.configuration.file.YamlConfiguration config =
            new org.bukkit.configuration.file.YamlConfiguration();
        config.set("worlds.world.events.disabled", java.util.List.of("BlockBreakEvent"));
        com.tricrotism.uworldguard.config.EventGate.load(config, server.getLogger());
        try {
            assertTrue(breakBlock(stranger, 16, 64, 16), "the listener does not act in this world");
        } finally {
            com.tricrotism.uworldguard.config.EventGate.load(
                new org.bukkit.configuration.file.YamlConfiguration(), server.getLogger());
        }
    }

    private GlobalProtectedRegion denyGlobalPvp() {
        final GlobalProtectedRegion global = new GlobalProtectedRegion();
        global.setFlag(Flags.PVP, State.DENY);
        manager().addRegion(global);
        return global;
    }

    private PlayerFishEvent fish(final PlayerMock fisher, final Entity caught, final PlayerFishEvent.State state) {
        final FishHookMock hook = new FishHookMock(server, UUID.randomUUID());
        hook.setShooter(fisher);
        final PlayerFishEvent event = new PlayerFishEvent(fisher, caught, hook, EquipmentSlot.HAND, state);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void globalPvpDenialStopsFishingRodPlayerPulls() {
        denyGlobalPvp();
        assertTrue(fish(server.addPlayer(), server.addPlayer(), PlayerFishEvent.State.CAUGHT_ENTITY).isCancelled());
    }

    @Test
    void allowedPvpKeepsFishingRodPlayerPulls() {
        denyGlobalPvp().setFlag(Flags.PVP, State.ALLOW);
        assertFalse(fish(server.addPlayer(), server.addPlayer(), PlayerFishEvent.State.CAUGHT_ENTITY).isCancelled());
    }

    @Test
    void playerPullsAreCheckedAtTheVictimRatherThanTheFisher() {
        claim("spawn").setFlag(Flags.PVP, State.DENY);
        final PlayerMock fisher = server.addPlayer();
        final PlayerMock victim = server.addPlayer();
        fisher.teleport(new Location(world, 500, 64, 500));
        victim.teleport(new Location(world, 16, 64, 16));

        assertTrue(fish(fisher, victim, PlayerFishEvent.State.CAUGHT_ENTITY).isCancelled());
        fisher.teleport(new Location(world, 16, 64, 16));
        victim.teleport(new Location(world, 500, 64, 500));
        assertFalse(fish(fisher, victim, PlayerFishEvent.State.CAUGHT_ENTITY).isCancelled());
    }

    @Test
    void pvpDenialPreservesNormalFishingAndNonPlayerPulls() {
        denyGlobalPvp();
        final PlayerMock fisher = server.addPlayer();
        for (final PlayerFishEvent.State state : PlayerFishEvent.State.values()) {
            if (state != PlayerFishEvent.State.CAUGHT_ENTITY) {
                assertFalse(fish(fisher, null, state).isCancelled(), state.name());
            }
        }
        final Entity item = world.dropItem(new Location(world, 16, 64, 16),
            new org.bukkit.inventory.ItemStack(Material.COD));
        assertFalse(fish(fisher, item, PlayerFishEvent.State.CAUGHT_FISH).isCancelled());
        assertFalse(fish(fisher, item, PlayerFishEvent.State.CAUGHT_ENTITY).isCancelled());
    }

    @Test
    void fishingRodPlayerPullsHonorOnlyAnActiveBypass() {
        denyGlobalPvp();
        final PlayerMock fisher = server.addPlayer();
        final PlayerMock victim = server.addPlayer();
        fisher.addAttachment(plugin, Bypass.NODE, true);
        assertTrue(fish(fisher, victim, PlayerFishEvent.State.CAUGHT_ENTITY).isCancelled());
        assertTrue(Bypass.toggle(fisher));
        assertFalse(fish(fisher, victim, PlayerFishEvent.State.CAUGHT_ENTITY).isCancelled());
    }

    @Test
    void fishingRodPlayerPullsHonorCombatPluginOverrides() {
        denyGlobalPvp();
        final java.util.concurrent.atomic.AtomicBoolean overrideCalled = new java.util.concurrent.atomic.AtomicBoolean();
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onDisallowedPvp(final DisallowedPVPEvent event) {
                assertInstanceOf(PlayerFishEvent.class, event.getCause());
                overrideCalled.set(true);
                event.setCancelled(true);
            }
        }, plugin);
        assertFalse(fish(server.addPlayer(), server.addPlayer(), PlayerFishEvent.State.CAUGHT_ENTITY).isCancelled());
        assertTrue(overrideCalled.get());
    }

    @Test
    void fishingRodPlayerPullsHonorTheEventGate() {
        denyGlobalPvp();
        final YamlConfiguration config = new YamlConfiguration();
        config.set("worlds.world.events.disabled", java.util.List.of("PlayerFishEvent"));
        EventGate.load(config, server.getLogger());
        assertFalse(fish(server.addPlayer(), server.addPlayer(), PlayerFishEvent.State.CAUGHT_ENTITY).isCancelled());
    }
}
