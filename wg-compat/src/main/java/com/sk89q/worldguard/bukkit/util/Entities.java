// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.bukkit.util;

import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.entity.minecart.RideableMinecart;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Entity classification helpers, answered from Paper's entity interfaces.
 */
public final class Entities {

    private static final int MAX_SHOOTER_DEPTH = 8;

    private Entities() {
    }

    public static boolean isTamed(final Entity entity) {
        return entity instanceof Tameable tameable && tameable.isTamed();
    }

    public static boolean isTNTBased(final Entity entity) {
        return entity instanceof TNTPrimed || entity instanceof ExplosiveMinecart;
    }

    public static boolean isFireball(final EntityType type) {
        return is(type, Fireball.class);
    }

    /**
     * Whether right-clicking the entity mounts it: boats, rideable minecarts, horse-like mounts and
     * saddled pigs or striders.
     */
    public static boolean isRiddenOnUse(final Entity entity) {
        return entity instanceof Boat
            || entity instanceof RideableMinecart
            || entity instanceof AbstractHorse
            || entity instanceof Steerable steerable && steerable.hasSaddle();
    }

    /**
     * Boats and minecarts. Living mounts are not counted.
     */
    public static boolean isVehicle(final EntityType type) {
        return isBoat(type) || isMinecart(type);
    }

    public static boolean isBoat(final EntityType type) {
        return is(type, Boat.class);
    }

    public static boolean isMinecart(final EntityType type) {
        return is(type, Minecart.class);
    }

    /**
     * Follows projectile shooters and TNT sources back to the entity that started the chain.
     *
     * @return the originating entity, or {@code entity} itself when it has no known source
     */
    public static Entity getShooter(final Entity entity) {
        Entity current = entity;
        for (int depth = 0; depth < MAX_SHOOTER_DEPTH; depth++) {
            final Entity next;
            if (current instanceof Projectile projectile) {
                final ProjectileSource source = projectile.getShooter();
                next = source instanceof Entity shooter ? shooter : null;
            } else if (current instanceof TNTPrimed tnt) {
                next = tnt.getSource();
            } else {
                next = null;
            }
            if (next == null || next == current) {
                return current;
            }
            current = next;
        }
        return current;
    }

    public static boolean isHostile(final Entity entity) {
        return entity instanceof Enemy;
    }

    /**
     * A living, non-player entity that is not hostile, such as an animal or villager.
     */
    public static boolean isNonHostile(final Entity entity) {
        return entity instanceof LivingEntity
            && !(entity instanceof Player)
            && !(entity instanceof ArmorStand)
            && !(entity instanceof Enemy);
    }

    public static boolean isAmbient(final Entity entity) {
        return entity instanceof Ambient;
    }

    /**
     * Villager-type NPCs, and entities another plugin has tagged with {@code NPC} metadata.
     */
    public static boolean isNPC(final Entity entity) {
        return entity instanceof NPC || entity.hasMetadata("NPC");
    }

    public static boolean isNonPlayerCreature(final Entity entity) {
        return entity instanceof LivingEntity
            && !(entity instanceof Player)
            && !(entity instanceof ArmorStand);
    }

    /**
     * Whether interacting with the entity changes the world: hanging entities and armor stands.
     */
    public static boolean isConsideredBuildingIfUsed(final Entity entity) {
        return entity instanceof Hanging || entity instanceof ArmorStand;
    }

    public static boolean isPotionArrow(final Entity entity) {
        return entity instanceof Arrow arrow
            && (arrow.getBasePotionType() != null || arrow.hasCustomEffects());
    }

    public static boolean isAoECloud(final EntityType type) {
        return type == EntityType.AREA_EFFECT_CLOUD;
    }

    public static boolean isPluginSpawning(final CreatureSpawnEvent.SpawnReason reason) {
        return reason == CreatureSpawnEvent.SpawnReason.CUSTOM
            || reason == CreatureSpawnEvent.SpawnReason.DEFAULT;
    }

    /**
     * The flag that governs explosions caused by {@code entity}.
     */
    public static StateFlag getExplosionFlag(final Entity entity) {
        if (entity instanceof Creeper) {
            return Flags.CREEPER_EXPLOSION;
        }
        if (entity instanceof EnderDragon) {
            return Flags.ENDERDRAGON_BLOCK_DAMAGE;
        }
        if (entity instanceof Wither || entity instanceof WitherSkull) {
            return Flags.WITHER_DAMAGE;
        }
        if (isTNTBased(entity)) {
            return Flags.TNT;
        }
        if (entity instanceof BreezeWindCharge) {
            return Flags.BREEZE_WIND_CHARGE;
        }
        if (entity instanceof WindCharge) {
            return Flags.WIND_CHARGE_BURST;
        }
        if (entity instanceof Fireball) {
            return Flags.GHAST_FIREBALL;
        }
        return Flags.OTHER_EXPLOSION;
    }

    private static boolean is(final EntityType type, final Class<?> kind) {
        final Class<? extends Entity> entityClass = type == null ? null : type.getEntityClass();
        return entityClass != null && kind.isAssignableFrom(entityClass);
    }
}
