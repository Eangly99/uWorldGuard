package com.tricrotism.uworldguard.listeners;

import com.tricrotism.uworldguard.config.Bypass;
import com.tricrotism.uworldguard.config.EventGate;
import com.tricrotism.uworldguard.flags.Flags;
import com.tricrotism.uworldguard.flags.State;
import com.tricrotism.uworldguard.region.GlobalProtectedRegion;
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
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.EntityKnockbackByEntityEvent;
import org.bukkit.event.entity.EntityKnockbackEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.entity.WindChargeMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.StringReader;
import java.nio.file.Files;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("removal")
class WindChargeProtectionTest {

    private static final RegionStore NO_STORE = new RegionStore() {
        @Override
        public void load(final String worldName, final RegionManager manager) {}

        @Override
        public void save(final String worldName, final RegionManager manager) {}
    };

    private ServerMock server;
    private PluginMock plugin;
    private WorldMock world;
    private PlayerMock thrower;
    private PlayerMock victim;
    private RegionManager manager;
    private GlobalProtectedRegion global;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.loadWith(PluginMock.class, new PluginDescriptionFile(new StringReader("""
            name: uWorldGuard
            version: 1.0
            main: org.mockbukkit.mockbukkit.plugin.PluginMock
            authors: [test]
            """)));
        Files.createDirectories(plugin.getDataFolder().toPath());
        Files.writeString(plugin.getDataFolder().toPath().resolve("messages.yml"), "messages: {}\n");
        final RegionContainerImpl container = new RegionContainerImpl(plugin, NO_STORE);
        container.loadAll();
        manager = container.get(world);
        assertNotNull(manager);
        global = new GlobalProtectedRegion();
        global.setFlag(Flags.PVP, State.DENY);
        global.setFlag(Flags.WIND_CHARGE, State.DENY);
        global.setFlag(Flags.OTHER_EXPLOSION, State.DENY);
        manager.addRegion(global);
        thrower = server.addPlayer();
        victim = server.addPlayer();
        thrower.teleport(new Location(world, 0, 64, 0));
        victim.teleport(new Location(world, 16, 64, 16));
        EventGate.load(new YamlConfiguration(), server.getLogger());
        server.getPluginManager().registerEvents(
            new ItemUseListener(container, container.createQuery(), new MessageService(plugin)), plugin);
        server.getPluginManager().registerEvents(new EntityListener(container, container.createQuery()), plugin);
    }

    @AfterEach
    void tearDown() {
        Bypass.clear(thrower.getUniqueId());
        EventGate.load(new YamlConfiguration(), server.getLogger());
        MockBukkit.unmock();
    }

    private WindChargeMock windCharge() {
        final WindChargeMock charge = new WindChargeMock(server, UUID.randomUUID());
        charge.setShooter(thrower);
        return charge;
    }

    private boolean pushed(final PlayerMock target, final Entity source, final EntityKnockbackEvent.KnockbackCause cause) {
        final Vector knockback = new Vector(1, 1, 0);
        final EntityKnockbackByEntityEvent sourceEvent = new EntityKnockbackByEntityEvent(
            target, source, cause, 1, knockback, target.getVelocity().add(knockback));
        server.getPluginManager().callEvent(sourceEvent);

        // Paper fires the source event first, then attributes its own event to the shooter.
        final io.papermc.paper.event.entity.EntityKnockbackEvent paperEvent =
            new com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent(
                target, thrower, io.papermc.paper.event.entity.EntityKnockbackEvent.Cause.valueOf(cause.name()),
                1, sourceEvent.getFinalKnockback().subtract(target.getVelocity()));
        paperEvent.setCancelled(sourceEvent.isCancelled());
        server.getPluginManager().callEvent(paperEvent);
        return !paperEvent.isCancelled();
    }

    @Test
    void globalDenialStopsAnotherPlayersWindChargeExplosion() {
        assertFalse(pushed(victim, windCharge(), EntityKnockbackEvent.KnockbackCause.EXPLOSION));
    }

    @Test
    void globalDenialPreservesTheThrowersOwnBoost() {
        assertTrue(pushed(thrower, windCharge(), EntityKnockbackEvent.KnockbackCause.EXPLOSION));
    }

    @Test
    void allowedWindChargesStillPushOtherPlayers() {
        global.setFlag(Flags.WIND_CHARGE, State.ALLOW);
        assertTrue(pushed(victim, windCharge(), EntityKnockbackEvent.KnockbackCause.EXPLOSION));
    }

    @Test
    void victimRegionOverridesGlobalDenialEvenWhenTheThrowerIsOutside() {
        final ProtectedCuboidRegion arena = new ProtectedCuboidRegion("arena",
            BlockVector3.at(8, 0, 8), BlockVector3.at(32, 255, 32));
        arena.setFlag(Flags.WIND_CHARGE, State.ALLOW);
        manager.addRegion(arena);

        assertTrue(pushed(victim, windCharge(), EntityKnockbackEvent.KnockbackCause.EXPLOSION));
    }

    @Test
    void activeBypassAllowsWindChargeKnockback() {
        thrower.addAttachment(plugin, Bypass.NODE, true);
        assertTrue(Bypass.toggle(thrower));

        assertTrue(pushed(victim, windCharge(), EntityKnockbackEvent.KnockbackCause.EXPLOSION));
    }

    @Test
    void windChargeDenialDoesNotCancelUnrelatedKnockback() {
        assertTrue(pushed(victim, thrower, EntityKnockbackEvent.KnockbackCause.ENTITY_ATTACK));
        assertTrue(pushed(victim, thrower, EntityKnockbackEvent.KnockbackCause.EXPLOSION));
    }

    private boolean blockHit(final Block block) {
        final ProjectileHitEvent event = new ProjectileHitEvent(windCharge(), null, block, BlockFace.NORTH);
        server.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    private boolean blockChange(final Block block) {
        final EntityChangeBlockEvent event = new EntityChangeBlockEvent(windCharge(), block,
            Material.AIR.createBlockData());
        server.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    @Test
    void deniedWindChargeBlockEffectsCoverDirectHitsAndBlockChanges() {
        final Block block = world.getBlockAt(16, 64, 16);
        for (final Material type : new Material[] {
            Material.POINTED_DRIPSTONE, Material.CHORUS_FLOWER, Material.DECORATED_POT
        }) {
            block.setType(type);
            assertFalse(blockHit(block), type.name());
            assertFalse(blockChange(block), type.name());
        }
        // Cancelling a block callback must not introduce a ban on the thrower's knockback.
        assertTrue(pushed(thrower, windCharge(), EntityKnockbackEvent.KnockbackCause.EXPLOSION));
    }

    @Test
    void allowedWindChargeBlockEffectsRemainAvailable() {
        global.setFlag(Flags.OTHER_EXPLOSION, State.ALLOW);
        final Block block = world.getBlockAt(16, 64, 16);
        block.setType(Material.POINTED_DRIPSTONE);
        assertTrue(blockHit(block));
        assertTrue(blockChange(block));
    }

    @Test
    void windChargeBlockEffectsCheckTheHitBlockRatherThanTheShooter() {
        global.setFlag(Flags.OTHER_EXPLOSION, State.ALLOW);
        final ProtectedCuboidRegion spawn = new ProtectedCuboidRegion("spawn",
            BlockVector3.at(8, 0, 8), BlockVector3.at(32, 255, 32));
        spawn.setFlag(Flags.OTHER_EXPLOSION, State.DENY);
        manager.addRegion(spawn);
        final Block block = world.getBlockAt(16, 64, 16);
        block.setType(Material.POINTED_DRIPSTONE);
        assertFalse(blockHit(block));
        assertFalse(blockChange(block));
        assertTrue(blockHit(world.getBlockAt(500, 64, 500)));
    }

    @Test
    void windChargeBlockEffectsHonorTheirEventGates() {
        final YamlConfiguration config = new YamlConfiguration();
        config.set("worlds.world.events.disabled",
            java.util.List.of("ProjectileHitEvent", "EntityChangeBlockEvent"));
        EventGate.load(config, server.getLogger());
        final Block block = world.getBlockAt(16, 64, 16);
        assertTrue(blockHit(block));
        assertTrue(blockChange(block));
    }
}
