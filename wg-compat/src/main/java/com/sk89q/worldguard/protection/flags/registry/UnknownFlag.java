// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.flags.registry;

import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.FlagContext;
import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;

/**
 * A flag found in stored data with no registered definition. It carries the raw value through
 * {@link #unmarshal} and {@link #marshal} unchanged, so the value survives until the plugin that
 * owns the flag registers it. It cannot be set from input.
 */
public class UnknownFlag extends Flag<Object> {

    public UnknownFlag(final String name) {
        super(name);
    }

    @Override
    public Object parseInput(final FlagContext context) throws InvalidFlagFormat {
        throw new InvalidFlagFormat("The flag " + getName() + " is not registered");
    }

    @Override
    public Object unmarshal(final Object o) {
        return o;
    }

    @Override
    public Object marshal(final Object o) {
        return o;
    }
}
