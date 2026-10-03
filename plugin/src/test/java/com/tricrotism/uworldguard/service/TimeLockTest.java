package com.tricrotism.uworldguard.service;

import com.tricrotism.uworldguard.flags.Flags;
import com.tricrotism.uworldguard.region.ProtectedCuboidRegion;
import com.tricrotism.uworldguard.region.RegionContainerImpl;
import com.tricrotism.uworldguard.region.RegionManager;
import com.tricrotism.uworldguard.storage.RegionStore;
import com.tricrotism.uworldguard.util.BlockVector3;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A time-lock stops applying the moment the player is no longer in a region that sets it, including
 * when they leave by changing world.
 */
class TimeLockTest {

    private static final RegionStore NO_STORE = new RegionStore() {
        @Override
        public void load(final String worldName, final RegionManager manager) {}

        @Override
        public void save(final String worldName, final RegionManager manager) {}
    };

    private ServerMock server;
    private WorldMock spawn;
    private WorldMock overworld;
    private ProtectedCuboidRegion locked;
    private PlayerTickService service;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        final PluginMock plugin = MockBukkit.createMockPlugin("uWorldGuard");
        spawn = server.addSimpleWorld("spawn");
        overworld = server.addSimpleWorld("world");

        final RegionContainerImpl container = new RegionContainerImpl(plugin, NO_STORE);
        container.loadAll();
        final RegionManager manager = container.get(spawn);
        assertNotNull(manager);
        locked = new ProtectedCuboidRegion("spawn", BlockVector3.at(-32, 0, -32), BlockVector3.at(32, 255, 32));
        locked.setFlag(Flags.TIME_LOCK, "11750");
        manager.addRegion(locked);

        player = server.addPlayer();
        player.teleport(new Location(spawn, 0, 64, 0));
        service = new PlayerTickService(plugin, container, container.createQuery());
        service.start();
        server.getScheduler().performTicks(20);
        assertEquals(11750L, player.getPlayerTime());
        assertFalse(player.isPlayerTimeRelative());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void changingToAWorldWithoutTimeLockResetsTheSky() {
        player.teleport(new Location(overworld, 0, 64, 0));
        server.getScheduler().performTicks(20);

        assertTrue(player.isPlayerTimeRelative(), "player should follow the world clock again");
        assertEquals(0L, player.getPlayerTime());
    }

    @Test
    void removingTheLastTimeLockResetsTheSky() {
        locked.setFlag(Flags.TIME_LOCK, null);
        server.getScheduler().performTicks(20);

        assertTrue(player.isPlayerTimeRelative(), "player should follow the world clock again");
    }
}
