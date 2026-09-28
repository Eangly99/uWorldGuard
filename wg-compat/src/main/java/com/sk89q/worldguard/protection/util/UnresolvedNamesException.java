// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.util;

/**
 * Thrown by {@link DomainInputResolver} when a player name could not be turned into a UUID.
 */
public class UnresolvedNamesException extends Exception {

    public UnresolvedNamesException() {
    }

    public UnresolvedNamesException(final String message) {
        super(message);
    }

    public UnresolvedNamesException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public UnresolvedNamesException(final Throwable cause) {
        super(cause);
    }
}
