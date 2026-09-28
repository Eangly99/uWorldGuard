// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.util.profile.util;

/**
 * Converts between the dashed and undashed text forms of a UUID.
 */
public final class UUIDs {

    private static final int UNDASHED_LENGTH = 32;

    private UUIDs() {
    }

    /**
     * @return {@code uuid} with dashes inserted
     * @throws IllegalArgumentException when {@code uuid} is not 32 characters
     */
    public static String addDashes(final String uuid) {
        if (uuid.length() != UNDASHED_LENGTH) {
            throw new IllegalArgumentException("Not an undashed UUID: " + uuid);
        }
        return uuid.substring(0, 8) + '-' + uuid.substring(8, 12) + '-' + uuid.substring(12, 16)
            + '-' + uuid.substring(16, 20) + '-' + uuid.substring(20);
    }

    public static String stripDashes(final String uuid) {
        return uuid.replace("-", "");
    }
}
