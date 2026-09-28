// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.util;

import java.util.Locale;

/**
 * A name that compares case-insensitively while keeping its original spelling.
 */
public final class Normal {

    private final String name;
    private final String normal;

    private Normal(final String name) {
        this.name = name;
        this.normal = normalize(name);
    }

    public static String normalize(final String name) {
        return name == null ? null : name.toLowerCase(Locale.ROOT);
    }

    public static Normal normal(final String name) {
        return new Normal(name);
    }

    public String getName() {
        return name;
    }

    public String getNormal() {
        return normal;
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof Normal that && java.util.Objects.equals(normal, that.normal);
    }

    @Override
    public int hashCode() {
        return normal == null ? 0 : normal.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
