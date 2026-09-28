// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.util;

public final class Enums {

    private Enums() {
    }

    /**
     * The first constant whose name equals one of {@code values}, tried in order.
     *
     * @return the constant, or {@code null} when none matches
     */
    public static <T extends Enum<T>> T findByValue(final Class<T> enumType, final String... values) {
        final T[] constants = enumType.getEnumConstants();
        for (final String value : values) {
            for (final T constant : constants) {
                if (constant.name().equals(value)) {
                    return constant;
                }
            }
        }
        return null;
    }

    /**
     * Like {@link #findByValue(Class, String...)}, but ignoring case, underscores and spaces.
     *
     * @return the constant, or {@code null} when none matches
     */
    public static <T extends Enum<T>> T findFuzzyByValue(final Class<T> enumType, final String... values) {
        final T[] constants = enumType.getEnumConstants();
        for (final String value : values) {
            if (value == null) {
                continue;
            }
            for (final T constant : constants) {
                if (fuzzyEquals(constant.name(), value)) {
                    return constant;
                }
            }
        }
        return null;
    }

    private static boolean fuzzyEquals(final String name, final String value) {
        int i = 0;
        int j = 0;
        final int n = name.length();
        final int m = value.length();
        while (true) {
            while (i < n && isSeparator(name.charAt(i))) {
                i++;
            }
            while (j < m && isSeparator(value.charAt(j))) {
                j++;
            }
            if (i == n || j == m) {
                return i == n && j == m;
            }
            if (Character.toLowerCase(name.charAt(i)) != Character.toLowerCase(value.charAt(j))) {
                return false;
            }
            i++;
            j++;
        }
    }

    private static boolean isSeparator(final char c) {
        return c == '_' || c == ' ';
    }
}
