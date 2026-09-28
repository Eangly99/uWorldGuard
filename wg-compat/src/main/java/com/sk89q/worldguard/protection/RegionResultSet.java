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
import com.sk89q.worldguard.protection.util.NormativeOrders;
import com.tricrotism.uworldguard.wgcompat.FlagQueryAlgorithms;

import java.util.*;

/**
 * An {@link ApplicableRegionSet} over a list of regions, resolved by a {@link FlagValueCalculator}.
 *
 * <p>The shim builds these itself where the engine has no matching query, notably
 * {@code RegionManager.getApplicableRegions(ProtectedRegion)}, which asks for the regions
 * overlapping another region rather than a point. Consumers build them from regions they hold.
 * Point queries answer with {@code com.tricrotism.uworldguard.wgcompat.WrappedRegionSet} instead,
 * which the engine resolves without wrapping regions.
 */
public class RegionResultSet extends AbstractRegionSet {

    private final List<ProtectedRegion> regions;
    private final ProtectedRegion globalRegion;
    private final FlagValueCalculator calculator;

    private Set<ProtectedRegion> regionSet;

    /**
     * Sorts a copy of {@code applicable}; the caller's list is left as it was.
     */
    public RegionResultSet(final List<ProtectedRegion> applicable, final ProtectedRegion globalRegion) {
        this(applicable, globalRegion, false);
    }

    public RegionResultSet(final Set<ProtectedRegion> applicable, final ProtectedRegion globalRegion) {
        this(NormativeOrders.fromSet(applicable), globalRegion, true);
    }

    /**
     * @param sorted whether {@code applicable} is already in {@link NormativeOrders} order, in which
     *               case it is used as given
     */
    public RegionResultSet(
        final List<ProtectedRegion> applicable, final ProtectedRegion globalRegion, final boolean sorted
    ) {
        if (sorted) {
            this.regions = applicable;
        } else {
            final List<ProtectedRegion> copy = new ArrayList<>(applicable);
            NormativeOrders.sort(copy);
            this.regions = copy;
        }
        this.globalRegion = globalRegion;
        this.calculator = new FlagValueCalculator(regions, globalRegion);
    }

    public static RegionResultSet fromSortedList(
        final List<ProtectedRegion> regions, final ProtectedRegion globalRegion
    ) {
        return new RegionResultSet(regions, globalRegion, true);
    }

    @Override
    public boolean isVirtual() {
        return false;
    }

    @Override
    public int size() {
        return regions.size();
    }

    @Override
    public Set<ProtectedRegion> getRegions() {
        Set<ProtectedRegion> cached = regionSet;
        if (cached == null) {
            cached = new LinkedHashSet<>(regions);
            regionSet = cached;
        }
        return cached;
    }

    @Override
    public Iterator<ProtectedRegion> iterator() {
        return regions.iterator();
    }

    @Override
    public boolean isMemberOfAll(final LocalPlayer player) {
        final UUID uniqueId = player.getUniqueId();
        for (int i = 0, n = regions.size(); i < n; i++) {
            if (!regions.get(i).isMember(uniqueId)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isOwnerOfAll(final LocalPlayer player) {
        final UUID uniqueId = player.getUniqueId();
        for (int i = 0, n = regions.size(); i < n; i++) {
            if (!regions.get(i).isOwner(uniqueId)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public StateFlag.State queryState(final RegionAssociable subject, final StateFlag... flags) {
        return calculator.queryState(subject, flags);
    }

    /**
     * Internal: whether {@code subject} may build across these regions, the shim's entry point for
     * {@code RegionQuery.testBuild}.
     */
    public boolean uwgCanBuild(final RegionAssociable subject) {
        return FlagQueryAlgorithms.canBuild(regions, globalRegion,
            FlagQueryAlgorithms.association(subject, regions));
    }

    @Override
    public <V> V queryValue(final RegionAssociable subject, final Flag<V> flag) {
        return calculator.queryValue(subject, flag);
    }

    @Override
    public <V> Collection<V> queryAllValues(final RegionAssociable subject, final Flag<V> flag) {
        return calculator.queryAllValues(subject, flag);
    }

    @Override
    public <V, K> V queryMapValue(final RegionAssociable subject, final MapFlag<K, V> flag, final K key) {
        return calculator.queryMapValue(subject, flag, key, null);
    }

    @Override
    public <V, K> V queryMapValue(
        final RegionAssociable subject, final MapFlag<K, V> flag, final K key, final Flag<V> fallback
    ) {
        return calculator.queryMapValue(subject, flag, key, fallback);
    }
}
