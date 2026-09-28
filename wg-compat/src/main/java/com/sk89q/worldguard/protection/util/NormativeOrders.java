// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.util;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The order flag resolution expects: priority descending, then id, which is
 * {@link ProtectedRegion}'s natural order.
 */
public final class NormativeOrders {

    private NormativeOrders() {
    }

    public static void sort(final List<ProtectedRegion> regions) {
        regions.sort(null);
    }

    public static List<ProtectedRegion> fromSet(final Set<ProtectedRegion> regions) {
        final List<ProtectedRegion> sorted = new ArrayList<>(regions);
        sorted.sort(null);
        return sorted;
    }
}
