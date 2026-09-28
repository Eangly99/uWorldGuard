// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.bukkit;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.bukkit.cause.Cause;
import com.sk89q.worldguard.domains.Association;
import com.sk89q.worldguard.protection.association.Associables;
import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.tricrotism.uworldguard.wgcompat.PlayerWrapping;
import com.tricrotism.uworldguard.wgcompat.WgCompatBridge;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.*;

import java.util.EnumSet;
import java.util.Set;

/**
 * Asks whether a cause may place, break or use something, from the regions at the target.
 *
 * <p>WorldGuard answers by firing its own block and entity events and seeing whether anything
 * cancels them. uWorldGuard's protection listens to Bukkit's events, not those, so this queries the
 * region engine directly: a player in bypass passes, otherwise the build check runs with the flag
 * WorldGuard applies to that action. What it does not see is a third plugin that cancels
 * WorldGuard's events for its own reasons.
 */
public class ProtectionQuery {

    private static final Set<Material> CONTAINERS = EnumSet.of(
        Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL, Material.HOPPER, Material.DROPPER,
        Material.DISPENSER, Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
        Material.BREWING_STAND, Material.CHISELED_BOOKSHELF, Material.CRAFTER, Material.DECORATED_POT,
        Material.LECTERN, Material.JUKEBOX);

    public ProtectionQuery() {
    }

    public boolean testBlockPlace(final Object cause, final Location location, final Material newMaterial) {
        return testBuild(cause, location, Flags.BLOCK_PLACE);
    }

    public boolean testBlockBreak(final Object cause, final Block block) {
        return testBuild(cause, block.getLocation(), Flags.BLOCK_BREAK);
    }

    public boolean testBlockInteract(final Object cause, final Block block) {
        final Material type = block.getType();
        final boolean container = CONTAINERS.contains(type)
            || Tag.SHULKER_BOXES.isTagged(type)
            || Tag.COPPER_CHESTS.isTagged(type);
        return testBuild(cause, block.getLocation(), container ? Flags.CHEST_ACCESS : Flags.USE);
    }

    public boolean testEntityPlace(final Object cause, final Location location, final EntityType type) {
        final Class<? extends Entity> entityClass = type.getEntityClass();
        final boolean vehicle = entityClass != null
            && (Boat.class.isAssignableFrom(entityClass) || Minecart.class.isAssignableFrom(entityClass));
        return testBuild(cause, location, vehicle ? Flags.PLACE_VEHICLE : Flags.BLOCK_PLACE);
    }

    public boolean testEntityDestroy(final Object cause, final Entity entity) {
        final boolean vehicle = entity instanceof Boat || entity instanceof Minecart;
        return testBuild(cause, entity.getLocation(), vehicle ? Flags.DESTROY_VEHICLE : Flags.BLOCK_BREAK);
    }

    public boolean testEntityInteract(final Object cause, final Entity entity) {
        return testBuild(cause, entity.getLocation(), entity instanceof Vehicle ? Flags.RIDE : Flags.INTERACT);
    }

    /**
     * Player targets follow {@code pvp}, which only an explicit deny refuses. Hostile mobs may always
     * be hit. Animals follow {@code damage-animals}; anything else needs build access.
     */
    public boolean testEntityDamage(final Object cause, final Entity entity) {
        if (entity instanceof Enemy) {
            return true;
        }
        final Location location = entity.getLocation();
        if (location.getWorld() == null) {
            return true;
        }
        final Player player = Cause.create(cause).getFirstPlayer();
        if (player != null && WgCompatBridge.hasBypass(player)) {
            return true;
        }
        final com.sk89q.worldedit.util.Location target = BukkitAdapter.adapt(location);
        final RegionAssociable subject = subject(player);
        if (entity instanceof Player) {
            return RegionQuery.UWG_SHARED.queryState(target, subject, Flags.PVP) != StateFlag.State.DENY;
        }
        if (entity instanceof Animals) {
            return RegionQuery.UWG_SHARED.testBuild(target, subject, Flags.DAMAGE_ANIMALS);
        }
        return RegionQuery.UWG_SHARED.testBuild(target, subject);
    }

    private static boolean testBuild(final Object cause, final Location location, final StateFlag flag) {
        if (location == null || location.getWorld() == null) {
            return true;
        }
        final Player player = Cause.create(cause).getFirstPlayer();
        if (player != null && WgCompatBridge.hasBypass(player)) {
            return true;
        }
        return RegionQuery.UWG_SHARED.testBuild(BukkitAdapter.adapt(location), subject(player), flag);
    }

    // simplified: a non-player cause is treated as a non-member everywhere. WorldGuard associates a
    // block or entity cause with the regions at its origin; if a consumer needs that, resolve the
    // origin's regions and pass a RegionOverlapAssociation.
    private static RegionAssociable subject(final Player player) {
        if (player == null) {
            return Associables.constant(Association.NON_MEMBER);
        }
        return (RegionAssociable) PlayerWrapping.wrap(player);
    }
}
