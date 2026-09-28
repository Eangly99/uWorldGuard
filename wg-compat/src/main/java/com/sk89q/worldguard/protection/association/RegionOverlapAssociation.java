// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.association;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import java.util.Set;

/**
 * An {@link AbstractRegionOverlapAssociation} over a source set the caller already has.
 */
public class RegionOverlapAssociation extends AbstractRegionOverlapAssociation {

    public RegionOverlapAssociation(final Set<ProtectedRegion> source) {
        this(source, false);
    }

    public RegionOverlapAssociation(final Set<ProtectedRegion> source, final boolean useMaxPriority) {
        super(source, useMaxPriority);
        calcMaxPriority();
    }
}
