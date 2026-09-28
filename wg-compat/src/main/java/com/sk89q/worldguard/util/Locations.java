// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.util;

import com.sk89q.worldedit.util.Location;

public final class Locations {

    private Locations() {
    }

    /**
     * @return whether the two locations fall in different blocks or different extents
     */
    public static boolean isDifferentBlock(final Location a, final Location b) {
        if (a == null || b == null) {
            return a != b;
        }
        return a.getBlockX() != b.getBlockX()
            || a.getBlockY() != b.getBlockY()
            || a.getBlockZ() != b.getBlockZ()
            || !a.getExtent().equals(b.getExtent());
    }
}
