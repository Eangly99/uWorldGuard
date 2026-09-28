// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.managers;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import java.util.Set;

/**
 * The regions changed and removed since some point. uWorldGuard saves whole worlds and never builds
 * one of these itself; it is here for consumers that pass them around.
 *
 * <p>Holds the sets it is given; {@link #addAll} writes into them.
 */
public final class RegionDifference {

    private final Set<ProtectedRegion> changed;
    private final Set<ProtectedRegion> removed;

    public RegionDifference(final Set<ProtectedRegion> changed, final Set<ProtectedRegion> removed) {
        this.changed = changed;
        this.removed = removed;
    }

    public Set<ProtectedRegion> getChanged() {
        return changed;
    }

    public Set<ProtectedRegion> getRemoved() {
        return removed;
    }

    public boolean containsChanges() {
        return !changed.isEmpty() || !removed.isEmpty();
    }

    public void addAll(final RegionDifference other) {
        changed.addAll(other.changed);
        removed.addAll(other.removed);
    }
}
