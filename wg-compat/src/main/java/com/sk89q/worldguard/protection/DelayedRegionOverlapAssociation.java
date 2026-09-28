// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection;

import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.protection.regions.RegionQuery;

/**
 * @deprecated use {@link com.sk89q.worldguard.protection.association.DelayedRegionOverlapAssociation}.
 */
@Deprecated
public class DelayedRegionOverlapAssociation
    extends com.sk89q.worldguard.protection.association.DelayedRegionOverlapAssociation {

    public DelayedRegionOverlapAssociation(final RegionQuery query, final Location location) {
        super(query, location);
    }
}
