// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.internal.platform;

import com.sk89q.minecraft.util.commands.CommandException;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.LocalPlayer;

import java.util.Iterator;
import java.util.List;

/**
 * Resolves the world and player names a command argument refers to. uWorldGuard's implementation is
 * {@code com.sk89q.worldguard.bukkit.BukkitStringMatcher}.
 */
public interface StringMatcher {

    World matchWorld(Actor sender, String filter) throws CommandException;

    List<LocalPlayer> matchPlayerNames(String filter);

    /**
     * @return {@code players} unchanged
     * @throws CommandException when nothing matched
     */
    default Iterable<? extends LocalPlayer> checkPlayerMatch(final List<? extends LocalPlayer> players)
        throws CommandException {
        if (players == null || players.isEmpty()) {
            throw new CommandException("No players matched query.");
        }
        return players;
    }

    Iterable<? extends LocalPlayer> matchPlayers(Actor source, String filter) throws CommandException;

    /**
     * The first player {@link #matchPlayers(Actor, String)} finds.
     *
     * @throws CommandException when nothing matched
     */
    default LocalPlayer matchSinglePlayer(final Actor sender, final String filter) throws CommandException {
        final Iterator<? extends LocalPlayer> players = matchPlayers(sender, filter).iterator();
        if (!players.hasNext()) {
            throw new CommandException("No players matched query.");
        }
        return players.next();
    }

    Actor matchPlayerOrConsole(Actor sender, String filter) throws CommandException;

    default Iterable<LocalPlayer> matchPlayers(final LocalPlayer player) {
        return List.of(player);
    }

    World getWorldByName(String worldName);

    String replaceMacros(Actor sender, String message);
}
