// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.bukkit.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Asks WorldGuard to (re)initialise a player's session. uWorldGuard initialises sessions from its
 * own join handling and never fires this; it exists so listeners and callers compiled against it
 * link.
 */
public class ProcessPlayerEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;

    public ProcessPlayerEvent(final Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
