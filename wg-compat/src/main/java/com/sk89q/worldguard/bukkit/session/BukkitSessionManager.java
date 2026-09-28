// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.bukkit.session;

import com.sk89q.worldguard.bukkit.event.player.ProcessPlayerEvent;
import com.sk89q.worldguard.session.AbstractSessionManager;
import org.bukkit.event.Listener;

/**
 * The session manager type WorldGuard hands out on Bukkit, which consumers cast
 * {@code getSessionManager()} to. The instance is
 * {@code com.tricrotism.uworldguard.wgcompat.SessionBridge}.
 *
 * <p>WorldGuard drives sessions from a repeating task ({@link #run()}) and a listener
 * ({@link #onPlayerProcess}). uWorldGuard drives them from its own movement tracker and player tick,
 * so both are no-ops and this listener is never registered.
 */
public abstract class BukkitSessionManager extends AbstractSessionManager implements Runnable, Listener {

    public BukkitSessionManager() {
    }

    public void onPlayerProcess(final ProcessPlayerEvent event) {
    }

    @Override
    public void run() {
    }

    public abstract void shutdown();
}
