package com.tricrotism.uworldguard.listeners;

import com.tricrotism.uworldguard.flags.Flags;
import com.tricrotism.uworldguard.flags.State;
import com.tricrotism.uworldguard.region.ProtectedCuboidRegion;
import com.tricrotism.uworldguard.region.RegionContainerImpl;
import com.tricrotism.uworldguard.region.RegionManager;
import com.tricrotism.uworldguard.storage.RegionStore;
import com.tricrotism.uworldguard.text.MessageService;
import com.tricrotism.uworldguard.util.BlockVector3;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.inventory.InventoryMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Machinery reaching across a region border: a hopper pulling out of a region's chest from outside,
 * and a dispenser pouring fluid into a region it does not stand in.
 */
class MachineProtectionTest {

    private static final RegionStore NO_STORE = new RegionStore() {
        @Override
        public void load(final String worldName, final RegionManager manager) {}

        @Override
        public void save(final String worldName, final RegionManager manager) {}
    };

    private ServerMock server;
    private WorldMock world;
    private RegionContainerImpl container;
    private MachineListener listener;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        final PluginMock plugin = MockBukkit.createMockPlugin("uWorldGuard");
        world = server.addSimpleWorld("world");

        final File dataFolder = plugin.getDataFolder();
        assertTrue(dataFolder.exists() || dataFolder.mkdirs());
        Files.writeString(new File(dataFolder, "messages.yml").toPath(),
            "cooldown-seconds: 3\nmessages:\n  no-permission: \"<red>You cannot do that here.\"\n",
            StandardCharsets.UTF_8);

        container = new RegionContainerImpl(plugin, NO_STORE);
        container.loadAll();
        listener = new MachineListener(container, container.createQuery(), new MessageService(plugin));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ProtectedCuboidRegion claim(final String id, final int minY, final int maxY) {
        final RegionManager manager = container.get(world);
        assertNotNull(manager);
        final ProtectedCuboidRegion region = new ProtectedCuboidRegion(id,
            BlockVector3.at(0, minY, 0), BlockVector3.at(32, maxY, 32));
        manager.addRegion(region);
        return region;
    }

    private Inventory inventoryAt(final int x, final int y, final int z) {
        final Location location = new Location(world, x, y, z);
        return new InventoryMock(null, 27, InventoryType.CHEST) {
            @Override
            public Location getLocation() {
                return location;
            }
        };
    }

    private boolean transfer(final Inventory source, final Inventory destination) {
        final InventoryMoveItemEvent event = new InventoryMoveItemEvent(
            source, new ItemStack(Material.DIAMOND), destination, false);
        listener.onHopperTransfer(event);
        return !event.isCancelled();
    }

    @Test
    void aHopperBelowTheRegionCannotDrainAChestInside() {
        claim("vault", 60, 80).setFlag(Flags.HOPPER_TRANSFER, State.DENY);

        assertFalse(transfer(inventoryAt(16, 60, 16), inventoryAt(16, 59, 16)),
            "the chest is inside, the hopper under the region's floor is not");
    }

    @Test
    void aHopperChainCannotPushIntoTheRegion() {
        claim("vault", 60, 80).setFlag(Flags.HOPPER_TRANSFER, State.DENY);

        assertFalse(transfer(inventoryAt(16, 59, 16), inventoryAt(16, 60, 16)));
    }

    @Test
    void hoppersOutsideTheRegionStillRun() {
        claim("vault", 60, 80).setFlag(Flags.HOPPER_TRANSFER, State.DENY);

        assertTrue(transfer(inventoryAt(500, 64, 500), inventoryAt(500, 63, 500)));
    }

    private boolean dispense(final int x, final int z, final BlockFace facing, final Material item) {
        final Block block = world.getBlockAt(x, 64, z);
        block.setType(Material.DISPENSER);
        final Directional data = (Directional) block.getBlockData();
        data.setFacing(facing);
        block.setBlockData(data);
        final BlockDispenseEvent event = new BlockDispenseEvent(block, new ItemStack(item), new Vector());
        listener.onDispense(event);
        return !event.isCancelled();
    }

    @Test
    void aDispenserOutsideCannotPourLavaIntoAMembershipRegion() {
        claim("plot", 0, 255);

        assertFalse(dispense(-1, 16, BlockFace.EAST, Material.LAVA_BUCKET),
            "no flag is set, membership alone protects the region");
        assertFalse(dispense(-1, 17, BlockFace.EAST, Material.WATER_BUCKET));
    }

    @Test
    void aDispenserInsideTheRegionMayPourFluid() {
        claim("plot", 0, 255);

        assertTrue(dispense(16, 16, BlockFace.EAST, Material.WATER_BUCKET));
    }

    @Test
    void aDispenserOutsideMayStillShootIntoTheRegion() {
        claim("plot", 0, 255);

        assertTrue(dispense(-1, 16, BlockFace.EAST, Material.ARROW),
            "only fluid buckets become blocks, arrows answer to their own events");
    }

    @Test
    void anExplicitBlockPlaceAllowOpensTheRegionToFluid() {
        claim("plot", 0, 255).setFlag(Flags.BLOCK_PLACE, State.ALLOW);

        assertTrue(dispense(-1, 16, BlockFace.EAST, Material.LAVA_BUCKET));
    }

    @Test
    void aSharedDomainOpensTheRegionToFluid() {
        final RegionManager manager = container.get(world);
        assertNotNull(manager);
        final ProtectedCuboidRegion farm = claim("farm", 0, 255);
        final ProtectedCuboidRegion redstone = new ProtectedCuboidRegion("redstone",
            BlockVector3.at(-10, 0, 0), BlockVector3.at(-1, 255, 32));
        manager.addRegion(redstone);
        farm.setFlag(Flags.NONPLAYER_PROTECTION_DOMAINS, Set.of("works"));
        redstone.setFlag(Flags.NONPLAYER_PROTECTION_DOMAINS, Set.of("works"));

        assertTrue(dispense(-1, 16, BlockFace.EAST, Material.LAVA_BUCKET));
    }
}
