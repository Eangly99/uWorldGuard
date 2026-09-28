// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.util;

public final class MathUtils {

    private MathUtils() {
    }

    /**
     * @return {@code a * b}
     * @throws ArithmeticException when the product overflows a {@code long}
     */
    public static long checkedMultiply(final long a, final long b) {
        return Math.multiplyExact(a, b);
    }
}
