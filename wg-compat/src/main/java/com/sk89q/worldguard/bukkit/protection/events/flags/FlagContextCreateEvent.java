// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.bukkit.protection.events.flags;

import com.sk89q.worldguard.protection.flags.FlagContext;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a {@link FlagContext} is being built, so a plugin can add objects its own flags need
 * while parsing. Asynchronous when built off a tick thread.
 */
public class FlagContextCreateEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final FlagContext.FlagContextBuilder builder;

    public FlagContextCreateEvent(final FlagContext.FlagContextBuilder builder) {
        super(!Bukkit.isPrimaryThread());
        this.builder = builder;
    }

    /**
     * Adds {@code value} under {@code key} unless something already claimed that key.
     *
     * @return whether the value was added
     */
    public boolean addObject(final String key, final Object value) {
        return builder.tryAddToMap(key, value);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
