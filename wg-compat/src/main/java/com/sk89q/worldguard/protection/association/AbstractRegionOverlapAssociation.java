// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.association;

import com.sk89q.worldguard.domains.Association;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Associates a non-player actor (a piston, a dispenser, flowing liquid) by where it stands: it is an
 * {@link Association#OWNER} of a target region it also stands in, or one that shares a
 * {@code nonplayer-protection-domains} entry with a region it stands in, and a
 * {@link Association#NON_MEMBER} otherwise.
 *
 * <p>With {@code useMaxPriority}, only the source regions at the highest priority count, so an actor
 * standing in a low-priority parent does not reach into a higher-priority child.
 */
public abstract class AbstractRegionOverlapAssociation implements RegionAssociable {

    protected Set<ProtectedRegion> source;

    private final boolean useMaxPriority;
    private int maxPriority = Integer.MIN_VALUE;
    private Set<String> domains = Set.of();

    protected AbstractRegionOverlapAssociation(final Set<ProtectedRegion> source, final boolean useMaxPriority) {
        this.source = source;
        this.useMaxPriority = useMaxPriority;
    }

    /**
     * Recomputes the highest source priority and the source's protection domains. Subclasses that
     * assign {@link #source} after construction call this once they have.
     */
    protected void calcMaxPriority() {
        int highest = Integer.MIN_VALUE;
        for (final ProtectedRegion region : source) {
            highest = Math.max(highest, region.getPriority());
        }
        maxPriority = highest;

        Set<String> collected = null;
        for (final ProtectedRegion region : source) {
            if (useMaxPriority && region.getPriority() != highest) {
                continue;
            }
            final Set<String> regionDomains = region.getFlag(Flags.NONPLAYER_PROTECTION_DOMAINS);
            if (regionDomains != null && !regionDomains.isEmpty()) {
                if (collected == null) {
                    collected = new HashSet<>();
                }
                collected.addAll(regionDomains);
            }
        }
        domains = collected == null ? Set.of() : collected;
    }

    @Override
    public Association getAssociation(final List<ProtectedRegion> regions) {
        for (int i = 0, n = regions.size(); i < n; i++) {
            final ProtectedRegion region = regions.get(i);
            if (source.contains(region) && (!useMaxPriority || region.getPriority() == maxPriority)) {
                return Association.OWNER;
            }
            if (!domains.isEmpty()) {
                final Set<String> regionDomains = region.getFlag(Flags.NONPLAYER_PROTECTION_DOMAINS);
                if (regionDomains != null && !java.util.Collections.disjoint(domains, regionDomains)) {
                    return Association.OWNER;
                }
            }
        }
        return Association.NON_MEMBER;
    }
}
