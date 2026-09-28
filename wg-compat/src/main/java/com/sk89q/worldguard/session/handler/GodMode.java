// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.session.handler;

import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.session.Session;

/**
 * Ordering anchor for the {@code godmode} flag. Inert; see the package documentation.
 *
 * <p>The god-mode state is held so a consumer reads back what it set, but uWorldGuard does not act
 * on it: god mode is a WorldGuard command feature, not region protection.
 */
public class GodMode extends Handler {

    public static final Factory FACTORY = new Factory();

    private volatile boolean godMode;

    public GodMode(final Session session) {
        super(session);
    }

    public boolean hasGodMode(final LocalPlayer player) {
        return godMode;
    }

    public void setGodMode(final LocalPlayer player, final boolean godMode) {
        this.godMode = godMode;
    }

    @Override
    public StateFlag.State getInvincibility(final LocalPlayer player) {
        return godMode ? StateFlag.State.ALLOW : null;
    }

    /**
     * Sets god mode on the session's {@code GodMode} handler.
     *
     * @return false when the session has no such handler
     */
    public static boolean set(final LocalPlayer player, final Session session, final boolean value) {
        final GodMode handler = session.getHandler(GodMode.class);
        if (handler == null) {
            return false;
        }
        handler.setGodMode(player, value);
        return true;
    }

    public static class Factory extends Handler.Factory<GodMode> {
        @Override
        public GodMode create(final Session session) {
            return new GodMode(session);
        }
    }
}
