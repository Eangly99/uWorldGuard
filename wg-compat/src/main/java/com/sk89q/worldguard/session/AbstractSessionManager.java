// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.session;

/**
 * The shared base of WorldGuard's session managers. Present so a consumer that names or casts to
 * this type links; the behaviour lives in {@code com.tricrotism.uworldguard.wgcompat.SessionBridge}.
 */
public abstract class AbstractSessionManager implements SessionManager {

    /**
     * Ticks between session ticks. uWorldGuard ticks sessions once a second from its own player
     * tick, so this is informational.
     */
    public static final int RUN_DELAY = 20;

    /**
     * How long an idle session is kept, in minutes. Informational: uWorldGuard drops a session when
     * its player leaves.
     */
    public static final long SESSION_LIFETIME = 10;

    public AbstractSessionManager() {
    }
}
