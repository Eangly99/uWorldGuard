// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.association;

import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.domains.Association;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionQuery;

import java.util.List;

/**
 * An {@link AbstractRegionOverlapAssociation} whose source is the regions at {@code location},
 * queried on first use. Handlers build one per event and most are never asked, so the query is
 * skipped for them.
 *
 * <p>Not thread-safe: use it on the thread that created it, as event handlers do.
 */
public class DelayedRegionOverlapAssociation extends AbstractRegionOverlapAssociation {

    private final RegionQuery query;
    private final Location location;

    public DelayedRegionOverlapAssociation(final RegionQuery query, final Location location) {
        this(query, location, false);
    }

    public DelayedRegionOverlapAssociation(
        final RegionQuery query, final Location location, final boolean useMaxPriority
    ) {
        super(null, useMaxPriority);
        this.query = query;
        this.location = location;
    }

    @Override
    public Association getAssociation(final List<ProtectedRegion> regions) {
        if (source == null) {
            source = query.getApplicableRegions(location).getRegions();
            calcMaxPriority();
        }
        return super.getAssociation(regions);
    }
}
