package com.tricrotism.uworldguard.listeners;

import com.tricrotism.uworldguard.flags.Flags;
import com.tricrotism.uworldguard.flags.State;
import com.tricrotism.uworldguard.region.ProtectedCuboidRegion;
import com.tricrotism.uworldguard.region.RegionContainerImpl;
import com.tricrotism.uworldguard.region.RegionManager;
import com.tricrotism.uworldguard.storage.RegionStore;
import com.tricrotism.uworldguard.util.BlockVector3;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.AreaEffectCloudMock;
import org.mockbukkit.mockbukkit.entity.ArrowMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.entity.SplashPotionMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Attacks that reach a region without a direct hit: splash and lingering potions against
 * {@code pvp: deny}, and a player's arrow shattering a block in a claim it has no rights in.
 */
class IndirectAttackTest {

    private static final RegionStore NO_STORE = new RegionStore() {
        @Override
        public void load(final String worldName, final RegionManager manager) {}

        @Override
        public void save(final String worldName, final RegionManager manager) {}
    };

    private ServerMock server;
    private WorldMock world;
    private RegionContainerImpl container;
    private EntityListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        container = new RegionContainerImpl(MockBukkit.createMockPlugin("uWorldGuard"), NO_STORE);
        container.loadAll();
        listener = new EntityListener(container, container.createQuery());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ProtectedCuboidRegion claim(final String id) {
        final RegionManager manager = container.get(world);
        assertNotNull(manager);
        final ProtectedCuboidRegion region = new ProtectedCuboidRegion(id,
            BlockVector3.at(0, 0, 0), BlockVector3.at(32, 255, 32));
        manager.addRegion(region);
        return region;
    }

    private PlayerMock playerAt(final int x, final int z) {
        final PlayerMock player = server.addPlayer();
        player.setLocation(new Location(world, x + 0.5, 64, z + 0.5));
        return player;
    }

    private double splash(final PlayerMock thrower, final PlayerMock victim, final PotionEffectType type) {
        final SplashPotionMock potion = new SplashPotionMock(server, UUID.randomUUID());
        potion.setShooter(thrower);
        final PotionMeta meta = potion.getPotionMeta();
        meta.addCustomEffect(new PotionEffect(type, 200, 0), true);
        potion.setPotionMeta(meta);
        final Map<LivingEntity, Double> affected = new HashMap<>();
        affected.put(victim, 1.0);
        final PotionSplashEvent event = new PotionSplashEvent(potion, null, null, null, affected);
        listener.onPotionSplash(event);
        return event.getIntensity(victim);
    }

    @Test
    void aSplashPoisonCannotReachAPlayerWherePvpIsDenied() {
        claim("spawn").setFlag(Flags.PVP, State.DENY);
        final PlayerMock attacker = playerAt(500, 500);
        final PlayerMock victim = playerAt(16, 16);

        assertEquals(0.0, splash(attacker, victim, PotionEffectType.POISON));
    }

    @Test
    void aBeneficialSplashStillReachesAPlayerWherePvpIsDenied() {
        claim("spawn").setFlag(Flags.PVP, State.DENY);
        final PlayerMock thrower = playerAt(500, 500);
        final PlayerMock ally = playerAt(16, 16);

        assertEquals(1.0, splash(thrower, ally, PotionEffectType.REGENERATION));
    }

    @Test
    void aSplashPoisonStillLandsOutsideTheRegion() {
        claim("spawn").setFlag(Flags.PVP, State.DENY);
        final PlayerMock attacker = playerAt(500, 500);
        final PlayerMock victim = playerAt(510, 510);

        assertEquals(1.0, splash(attacker, victim, PotionEffectType.POISON));
    }

    @Test
    void aLingeringCloudDropsPlayersWherePvpIsDenied() {
        claim("spawn").setFlag(Flags.PVP, State.DENY);
        final PlayerMock attacker = playerAt(500, 500);
        final PlayerMock inside = playerAt(16, 16);
        final PlayerMock outside = playerAt(40, 40);

        final AreaEffectCloudMock cloud = new AreaEffectCloudMock(server, UUID.randomUUID());
        cloud.setSource(attacker);
        cloud.addCustomEffect(new PotionEffect(PotionEffectType.POISON, 200, 0), true);
        final List<LivingEntity> affected = new ArrayList<>(List.of(inside, outside));
        final AreaEffectCloudApplyEvent event = new AreaEffectCloudApplyEvent(cloud, affected);
        listener.onCloudApply(event);

        assertFalse(event.getAffectedEntities().contains(inside), "the player in the pvp: deny region");
        assertTrue(event.getAffectedEntities().contains(outside), "the player beyond it");
    }

    private boolean arrowBreaks(final PlayerMock shooter, final int x, final int z) {
        final Block pot = world.getBlockAt(x, 64, z);
        pot.setType(Material.DECORATED_POT);
        final ArrowMock arrow = new ArrowMock(server, UUID.randomUUID());
        arrow.setShooter(shooter);
        final EntityChangeBlockEvent event = new EntityChangeBlockEvent(arrow, pot, Material.AIR.createBlockData());
        listener.onMobGrief(event);
        return !event.isCancelled();
    }

    @Test
    void aStrangersArrowCannotBreakAPotInAClaim() {
        final PlayerMock owner = playerAt(16, 16);
        claim("plot").getOwners().addPlayer(owner.getUniqueId());
        final PlayerMock stranger = playerAt(500, 500);

        assertFalse(arrowBreaks(stranger, 16, 16));
    }

    @Test
    void theOwnersArrowMayBreakTheirOwnPot() {
        final PlayerMock owner = playerAt(16, 16);
        claim("plot").getOwners().addPlayer(owner.getUniqueId());

        assertTrue(arrowBreaks(owner, 16, 16));
    }

    @Test
    void anArrowMayBreakAPotInTheWilderness() {
        claim("plot");
        final PlayerMock stranger = playerAt(500, 500);

        assertTrue(arrowBreaks(stranger, 500, 500));
    }
}
