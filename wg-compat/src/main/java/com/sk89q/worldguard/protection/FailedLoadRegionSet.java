// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection;

import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.MapFlag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import java.util.*;

/**
 * A virtual set standing in for a world whose regions could not be loaded: every state flag answers
 * {@code DENY}, so nothing is unprotected by accident, and every other flag its default. uWorldGuard
 * never produces one; it answers an empty set while a world is still loading.
 */
public class FailedLoadRegionSet extends AbstractRegionSet {

    private static final FailedLoadRegionSet INSTANCE = new FailedLoadRegionSet();

    private FailedLoadRegionSet() {
    }

    public static FailedLoadRegionSet getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean isVirtual() {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <V> V queryValue(final RegionAssociable subject, final Flag<V> flag) {
        return flag instanceof StateFlag ? (V) StateFlag.State.DENY : flag.getDefault();
    }

    @Override
    public <V, K> V queryMapValue(final RegionAssociable subject, final MapFlag<K, V> flag, final K key) {
        return null;
    }

    @Override
    public <V, K> V queryMapValue(
        final RegionAssociable subject, final MapFlag<K, V> flag, final K key, final Flag<V> fallback
    ) {
        return fallback == null ? null : queryValue(subject, fallback);
    }

    @Override
    public <V> Collection<V> queryAllValues(final RegionAssociable subject, final Flag<V> flag) {
        final V value = queryValue(subject, flag);
        return value == null ? List.of() : List.of(value);
    }

    @Override
    public boolean isOwnerOfAll(final LocalPlayer player) {
        return false;
    }

    @Override
    public boolean isMemberOfAll(final LocalPlayer player) {
        return false;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public Set<ProtectedRegion> getRegions() {
        return Collections.emptySet();
    }

    @Override
    public Iterator<ProtectedRegion> iterator() {
        return Collections.emptyIterator();
    }
}
