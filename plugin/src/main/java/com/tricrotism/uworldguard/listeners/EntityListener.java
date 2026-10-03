package com.tricrotism.uworldguard.listeners;

import com.destroystokyo.paper.event.entity.EntityZapEvent;
import com.sk89q.worldguard.bukkit.protection.events.DisallowedPVPEvent;
import com.sk89q.worldguard.bukkit.util.Events;
import com.tricrotism.uworldguard.config.Bypass;
import com.tricrotism.uworldguard.config.EventGate;
import com.tricrotism.uworldguard.flags.Flags;
import com.tricrotism.uworldguard.flags.StateFlag;
import com.tricrotism.uworldguard.region.ApplicableRegionSet;
import com.tricrotism.uworldguard.region.RegionContainerImpl;
import com.tricrotism.uworldguard.region.RegionQuery;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.raid.RaidTriggerEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Enforces mob-spawning and deny-spawn, the explosion flags, mob grief (enderman, ravager, wither,
 * ender dragon), mob-damage, damage-animals, firework-damage, lightning, potion-splash,
 * item-frame/painting/armor-stand destruction, and mob-drops / exp-drops. Also pvp for thrown and
 * lingering potions, and block-break for blocks a projectile shatters.
 */
@NullMarked
public final class EntityListener implements Listener {

    private final RegionContainerImpl container;
    private final RegionQuery query;

    public EntityListener(final RegionContainerImpl container, final RegionQuery query) {
        this.container = container;
        this.query = query;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(final CreatureSpawnEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        final CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        final boolean natural = isNaturalSpawn(reason);
        final boolean copperGolem = reason == CreatureSpawnEvent.SpawnReason.BUILD_COPPERGOLEM;
        final World world = event.getEntity().getWorld();
        final boolean denySpawn = query.usesFlag(world, Flags.DENY_SPAWN);
        if (!denySpawn
            && !(natural && query.usesFlag(world, Flags.MOB_SPAWNING))
            && !(copperGolem && query.usesFlag(world, Flags.COPPER_GOLEM))) {
            return;
        }

        final ApplicableRegionSet set = query.getApplicableRegions(event.getEntity());

        if (denySpawn && set.flagSetContains(Flags.DENY_SPAWN, event.getEntityType())) {
            event.setCancelled(true);
            return;
        }

        if (copperGolem) {
            if (!set.testState(Flags.COPPER_GOLEM)) {
                event.setCancelled(true);
            }
            return;
        }

        if (natural && !set.testState(Flags.MOB_SPAWNING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(final EntityExplodeEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        if (event.blockList().isEmpty()) {
            return;
        }
        final StateFlag flag = explosionFlag(event.getEntity());
        if (!query.usesFlag(event.getEntity().getWorld(), flag)) {
            return;
        }

        query.removeDenied(event.getEntity().getWorld(), event.blockList(), flag);
    }

    /**
     * Mobs that rearrange blocks by touching them rather than by exploding: endermen lifting blocks,
     * ravagers trampling leaves and crops, the wither and the dragon carving through terrain.
     *
     * <p>Projectiles land here too: an arrow or trident shattering a decorated pot, pointed dripstone
     * or a chorus flower. A player's shot is a block break by that player, membership included. Any
     * other shooter only meets an explicit {@code block-break} deny.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobGrief(final EntityChangeBlockEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        final Entity entity = event.getEntity();
        final StateFlag flag = griefFlag(entity);
        if (flag == null) {
            if (entity instanceof Projectile projectile) {
                onProjectileChange(event, projectile);
            }
            return;
        }
        if (!query.usesFlag(event.getBlock().getWorld(), flag)) {
            return;
        }
        if (!query.testState(event.getBlock(), flag)) {
            event.setCancelled(true);
        }
    }

    private void onProjectileChange(final EntityChangeBlockEvent event, final Projectile projectile) {
        final Block block = event.getBlock();
        if (projectile.getShooter() instanceof Player shooter) {
            if (!query.getApplicableRegions(block).testBuild(shooter.getUniqueId(), Flags.BLOCK_BREAK)
                && !Bypass.has(shooter)) {
                event.setCancelled(true);
            }
            return;
        }
        if (query.usesFlag(block.getWorld(), Flags.BLOCK_BREAK) && !query.testState(block, Flags.BLOCK_BREAK)) {
            event.setCancelled(true);
        }
    }

    /**
     * Lightning is what actually starts the fire and converts the mobs it hits, so denying the strike
     * itself is the only point that covers every consequence.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLightning(final LightningStrikeEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        if (query.usesFlag(event.getWorld(), Flags.LIGHTNING)
            && !query.testState(event.getLightning(), Flags.LIGHTNING)) {
            event.setCancelled(true);
        }
    }

    /**
     * Splash and lingering potions. Cancelling outright would also destroy a beneficial potion thrown
     * by its owner, so instead every affected entity has its intensity zeroed — the bottle still
     * breaks and the particles still show, but nothing inside the region is affected.
     *
     * <p>The affected entities are copied because {@code setIntensity} writes back into the live
     * collection being walked, so the registry check comes first: with the flag unused nowhere on the
     * server, the handler costs a bitset test per world and copies nothing.
     *
     * <p>A harmful potion thrown by a player is also {@code pvp}: poison or slowness reaching another
     * player where pvp is denied to the thrower has its intensity zeroed for that player. Beneficial
     * potions still reach allies.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPotionSplash(final PotionSplashEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        final boolean splashFlag = container.anyRegionUses(Flags.POTION_SPLASH);
        final ThrownPotion potion = event.getPotion();
        final Player thrower = container.anyRegionUses(Flags.PVP)
            && potion.getShooter() instanceof Player player && harmful(potion.getEffects()) ? player : null;
        if (!splashFlag && thrower == null) {
            return;
        }

        for (final LivingEntity affected : List.copyOf(event.getAffectedEntities())) {
            if (splashFlag && !query.testState(affected, Flags.POTION_SPLASH)) {
                event.setIntensity(affected, 0.0);
            } else if (thrower != null && affected instanceof Player defender
                && pvpDenied(thrower, defender, event)) {
                event.setIntensity(affected, 0.0);
            }
        }
    }

    /**
     * Lingering clouds re-apply every few ticks, long after the splash event, so {@code pvp} is
     * checked again here. Victims are dropped from the affected list, which also covers instant
     * damage since the cloud only applies effects to what is left in it.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCloudApply(final AreaEffectCloudApplyEvent event) {
        if (EventGate.disabled(event) || !container.anyRegionUses(Flags.PVP)) {
            return;
        }
        final AreaEffectCloud cloud = event.getEntity();
        if (!(cloud.getSource() instanceof Player attacker) || !harmful(cloud)) {
            return;
        }
        event.getAffectedEntities().removeIf(affected ->
            affected instanceof Player defender && pvpDenied(attacker, defender, event));
    }

    /**
     * The {@code pvp} test shared by every indirect attack. Hitting yourself is never pvp.
     */
    private boolean pvpDenied(final Player attacker, final Player defender, final Event cause) {
        return !attacker.equals(defender)
            && !query.getApplicableRegions(defender).testState(Flags.PVP, attacker.getUniqueId())
            && !Bypass.has(attacker)
            && !Events.fireAndTestCancel(new DisallowedPVPEvent(attacker, defender, cause));
    }

    private static boolean harmful(final AreaEffectCloud cloud) {
        final PotionType base = cloud.getBasePotionType();
        return (base != null && harmful(base.getPotionEffects()))
            || (cloud.hasCustomEffects() && harmful(cloud.getCustomEffects()));
    }

    private static boolean harmful(final Collection<PotionEffect> effects) {
        for (final PotionEffect effect : effects) {
            if (effect.getType().getEffectCategory() == PotionEffectType.Category.HARMFUL) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLingeringPotionSplash(final LingeringPotionSplashEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        final AreaEffectCloud cloud = event.getAreaEffectCloud();
        if (query.usesFlag(cloud.getWorld(), Flags.POTION_SPLASH)
            && !query.testState(cloud, Flags.POTION_SPLASH)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(final EntityDamageByEntityEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        final Entity victim = event.getEntity();
        final Entity damager = event.getDamager();

        // lingering harming damage names the cloud, not a projectile, so the pvp listener misses it
        if (damager instanceof AreaEffectCloud cloud && victim instanceof Player defender
            && cloud.getSource() instanceof Player attacker && container.anyRegionUses(Flags.PVP)
            && pvpDenied(attacker, defender, event)) {
            event.setCancelled(true);
            return;
        }

        if (victim instanceof Player && damager instanceof Mob
            && query.usesFlag(victim.getWorld(), Flags.MOB_DAMAGE)
            && !query.testState(victim, Flags.MOB_DAMAGE)) {
            event.setCancelled(true);
            return;
        }

        if (damager instanceof Firework
            && query.usesFlag(victim.getWorld(), Flags.FIREWORK_DAMAGE)
            && !query.testState(victim, Flags.FIREWORK_DAMAGE)) {
            event.setCancelled(true);
            return;
        }

        if (victim instanceof Animals) {
            final Player attacker = resolveAttacker(damager);
            if (attacker != null
                && !query.getApplicableRegions(victim)
                .testBuild(attacker.getUniqueId(), Flags.DAMAGE_ANIMALS)
                && !Bypass.has(attacker)) {
                event.setCancelled(true);
            }
            return;
        }

        final StateFlag flag = victim instanceof ItemFrame
            ? Flags.ENTITY_ITEM_FRAME_DESTROY
            : victim instanceof ArmorStand ? Flags.ENTITY_ARMOR_STAND_DESTROY : null;
        if (flag != null) {
            final Player attacker = resolveAttacker(damager);
            final ApplicableRegionSet at = query.getApplicableRegions(victim);
            final boolean allowed = attacker != null
                ? at.testBuild(attacker.getUniqueId(), flag)
                : at.testState(flag, null);
            if (!allowed && (attacker == null || !Bypass.has(attacker))) {
                event.setCancelled(true);
            }
            return;
        }

        if (victim instanceof LivingEntity && !(victim instanceof Enemy) && !(victim instanceof Player)) {
            final Player attacker = resolveAttacker(damager);
            if (attacker != null
                && !query.getApplicableRegions(victim).canBuild(attacker.getUniqueId())
                && !Bypass.has(attacker)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Mob conversions — zombie to drowned, villager to witch, a creaking waking up. The result is a
     * different mob than the region's owner allowed in, so it is worth its own gate.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTransform(final EntityTransformEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        final Entity entity = event.getEntity();
        if (query.usesFlag(entity.getWorld(), Flags.ENTITY_TRANSFORM)
            && !query.testState(entity, Flags.ENTITY_TRANSFORM)) {
            event.setCancelled(true);
        }
    }

    /**
     * A lightning strike converting something — pig to zombified piglin, villager to witch, creeper to
     * charged. {@link EntityZapEvent} subclasses {@code EntityTransformEvent} but declares its own
     * handler list, so {@link #onTransform} never sees it; without this handler the most recognisable
     * transform in the game is the one {@code entity-transform} misses.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onZap(final EntityZapEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        final Entity entity = event.getEntity();
        if (query.usesFlag(entity.getWorld(), Flags.ENTITY_TRANSFORM)
            && !query.testState(entity, Flags.ENTITY_TRANSFORM)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreed(final EntityBreedEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        final LivingEntity breeder = event.getBreeder();
        final ApplicableRegionSet set = query.getApplicableRegions(event.getEntity());
        if (breeder instanceof Player player) {
            if (set.testState(Flags.BREED, player.getUniqueId()) || Bypass.has(player)) {
                return;
            }
        } else if (set.testState(Flags.BREED)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTame(final EntityTameEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        final ApplicableRegionSet set = query.getApplicableRegions(event.getEntity());
        if (event.getOwner() instanceof Player player) {
            if (set.testState(Flags.TAME, player.getUniqueId()) || Bypass.has(player)) {
                return;
            }
        } else if (set.testState(Flags.TAME)) {
            return;
        }
        event.setCancelled(true);
    }

    /**
     * Zombies breaking down doors. This event extends {@code EntityChangeBlockEvent}, but
     * {@link #onMobGrief} resolves no flag for a zombie, so the two never both act on one break.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDoorBreak(final EntityBreakDoorEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        final Block block = event.getBlock();
        if (query.usesFlag(block.getWorld(), Flags.DOOR_BREAK) && !query.testState(block, Flags.DOOR_BREAK)) {
            event.setCancelled(true);
        }
    }

    /**
     * Whether a raid may start here — the one flag a spawn town usually wants.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRaidTrigger(final RaidTriggerEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        final Player player = event.getPlayer();
        if (query.usesFlag(player.getWorld(), Flags.RAID) && !query.testState(player, Flags.RAID)) {
            event.setCancelled(true);
        }
    }

    /**
     * Setting something alight. Burn damage arrives later as a plain {@code FIRE_TICK}
     * {@code EntityDamageEvent} with no attacker attached, so {@code pvp} and {@code mob-damage} —
     * which both key on the damager — never see it. Fire aspect and flame bows were therefore a way
     * to keep hurting players in a {@code pvp: deny} region; this stops the ignition instead.
     *
     * <p>Because this is the {@code pvp} flag by another route, a plugin that overrides the flag
     * through {@link DisallowedPVPEvent} overrides the ignition too. WorldGuard fires that event
     * from the damage path alone, having no combustion handler to fire it from.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombustByEntity(final EntityCombustByEntityEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        final Entity victim = event.getEntity();
        final Entity source = event.getCombuster();
        if (!(victim instanceof Player defender)) {
            return;
        }
        if (source instanceof Mob) {
            if (!query.testState(victim, Flags.MOB_DAMAGE)) {
                event.setCancelled(true);
            }
            return;
        }
        final Player attacker = resolveAttacker(source);
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        if (!query.getApplicableRegions(victim).testState(Flags.PVP, attacker.getUniqueId())
            && !Bypass.has(attacker)
            && !Events.fireAndTestCancel(new DisallowedPVPEvent(attacker, defender, event))) {
            event.setCancelled(true);
        }
    }

    private static @Nullable Player resolveAttacker(final Entity source) {
        if (source instanceof Player player) {
            return player;
        }
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    /**
     * Protects item frames and paintings from being broken by an entity — a player, a skeleton's
     * arrow, a creeper blast. Block-break protection does not cover these: they are entities, so
     * without this they stay destroyable inside an otherwise protected region.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(final HangingBreakByEntityEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        final Hanging hanging = event.getEntity();
        final StateFlag flag = hanging instanceof ItemFrame
            ? Flags.ENTITY_ITEM_FRAME_DESTROY
            : hanging instanceof Painting ? Flags.ENTITY_PAINTING_DESTROY : null;
        if (flag == null) {
            return;
        }
        final Player remover = resolveAttacker(event.getRemover());
        final ApplicableRegionSet at = query.getApplicableRegions(hanging);
        if (remover != null
            ? at.testBuild(remover.getUniqueId(), flag)
            : at.testState(flag, null)) {
            return;
        }
        if (remover != null && Bypass.has(remover)) {
            return;
        }
        event.setCancelled(true);
    }

    /**
     * Enforces mob-drops / exp-drops. Players are excluded — their drops are governed by the
     * keep-inventory / keep-exp flags on {@code PlayerDeathEvent}, which is a subclass of this event.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(final EntityDeathEvent event) {
        if (event.getEntity() instanceof Player || EventGate.disabled(event)) {
            return;
        }
        final World world = event.getEntity().getWorld();
        final boolean mobDrops = query.usesFlag(world, Flags.MOB_DROPS);
        final boolean expDrops = query.usesFlag(world, Flags.EXP_DROPS);
        if (!mobDrops && !expDrops) {
            return;
        }
        final ApplicableRegionSet set = query.getApplicableRegions(event.getEntity());
        if (mobDrops && !set.testState(Flags.MOB_DROPS)) {
            event.getDrops().clear();
        }
        if (expDrops && !set.testState(Flags.EXP_DROPS)) {
            event.setDroppedExp(0);
        }
    }

    private static boolean isNaturalSpawn(final CreatureSpawnEvent.SpawnReason reason) {
        return switch (reason) {
            case NATURAL, SPAWNER, TRIAL_SPAWNER, REINFORCEMENTS, PATROL, RAID, JOCKEY, MOUNT,
                 VILLAGE_INVASION, TRAP -> true;
            default -> false;
        };
    }

    private static StateFlag explosionFlag(final Entity entity) {
        return switch (entity) {
            case Creeper _ -> Flags.CREEPER_EXPLOSION;
            case TNTPrimed _ -> Flags.TNT;
            case WitherSkull _, Wither _ -> Flags.WITHER_DAMAGE;
            case BreezeWindCharge _ -> Flags.BREEZE_CHARGE_EXPLOSION;
            case WindCharge _ -> Flags.OTHER_EXPLOSION;
            case Fireball _ -> Flags.GHAST_FIREBALL;
            case EnderDragon _ -> Flags.ENDERDRAGON_BLOCK_DAMAGE;
            default -> Flags.OTHER_EXPLOSION;
        };
    }

    private static @Nullable StateFlag griefFlag(final Entity entity) {
        return switch (entity) {
            case Enderman _ -> Flags.ENDERMAN_GRIEF;
            case Ravager _ -> Flags.RAVAGER_GRIEF;
            case Wither _ -> Flags.WITHER_DAMAGE;
            case EnderDragon _ -> Flags.ENDERDRAGON_BLOCK_DAMAGE;
            default -> null;
        };
    }

}
