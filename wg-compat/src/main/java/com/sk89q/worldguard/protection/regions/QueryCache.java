// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.regions;

import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;

/**
 * Pass-through: every call asks the manager directly and nothing is retained.
 *
 * <p>uWorldGuard answers a point query from its own spatial index, with the flag-use bitset
 * rejecting most lookups before a set is built. A per-location cache in front of that would cost a
 * map lookup and an entry per distinct block queried, and would need invalidating on every region
 * edit, for no gain.
 */
public class QueryCache {

    public QueryCache() {
    }

    public ApplicableRegionSet queryContains(
        final RegionManager manager, final Location location, final RegionQuery.QueryOption option
    ) {
        return manager.getApplicableRegions(location.toVector().toBlockPoint(), option);
    }

    /**
     * No-op: nothing is cached.
     */
    public void invalidateAll() {
    }
}
