// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.util;

import com.google.common.base.Predicate;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import java.util.Collection;

/**
 * Adds every region it is applied to into a collection, optionally with each region's parent chain.
 * Always answers {@code true}, so an index walk driven by it visits every match.
 */
public class RegionCollectionConsumer implements Predicate<ProtectedRegion> {

    private final Collection<? super ProtectedRegion> collection;
    private final boolean addParents;

    public RegionCollectionConsumer(final Collection<? super ProtectedRegion> collection, final boolean addParents) {
        this.collection = collection;
        this.addParents = addParents;
    }

    @Override
    public boolean apply(final ProtectedRegion region) {
        collection.add(region);
        if (addParents) {
            for (ProtectedRegion parent = region.getParent(); parent != null; parent = parent.getParent()) {
                collection.add(parent);
            }
        }
        return true;
    }
}
