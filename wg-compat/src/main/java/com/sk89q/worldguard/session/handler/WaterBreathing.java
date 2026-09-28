// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.session.handler;

import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.session.Session;

/**
 * Ordering anchor for WorldGuard's water-breathing handler. Inert, and the one anchor with no
 * uWorldGuard flag behind it; see the package documentation.
 *
 * <p>The state is held so a consumer reads back what it set; uWorldGuard does not act on it.
 */
public class WaterBreathing extends Handler {

    public static final Factory FACTORY = new Factory();

    public boolean waterBreathing;

    public WaterBreathing(final Session session) {
        super(session);
    }

    public boolean hasWaterBreathing() {
        return waterBreathing;
    }

    public void setWaterBreathing(final boolean waterBreathing) {
        this.waterBreathing = waterBreathing;
    }

    /**
     * Sets water breathing on the session's {@code WaterBreathing} handler.
     *
     * @return false when the session has no such handler
     */
    public static boolean set(final LocalPlayer player, final Session session, final boolean value) {
        final WaterBreathing handler = session.getHandler(WaterBreathing.class);
        if (handler == null) {
            return false;
        }
        handler.setWaterBreathing(value);
        return true;
    }

    public static class Factory extends Handler.Factory<WaterBreathing> {
        @Override
        public WaterBreathing create(final Session session) {
            return new WaterBreathing(session);
        }
    }
}
