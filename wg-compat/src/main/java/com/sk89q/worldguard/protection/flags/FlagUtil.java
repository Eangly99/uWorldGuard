// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.flags;

import java.util.HashMap;
import java.util.Map;

/**
 * Flag map conversion for storage.
 */
public final class FlagUtil {

    private FlagUtil() {
    }

    /**
     * Each flag's value in its stored form, keyed by flag name. Entries whose flag marshals to
     * {@code null} are left out.
     */
    public static Map<String, Object> marshal(final Map<Flag<?>, Object> flags) {
        final Map<String, Object> marshalled = new HashMap<>(Math.max(4, flags.size() * 2));
        for (final Map.Entry<Flag<?>, Object> entry : flags.entrySet()) {
            final Object value = marshal(entry.getKey(), entry.getValue());
            if (value != null) {
                marshalled.put(entry.getKey().getName(), value);
            }
        }
        return marshalled;
    }

    @SuppressWarnings("unchecked")
    private static <T> Object marshal(final Flag<T> flag, final Object value) {
        return flag.marshal((T) value);
    }
}
