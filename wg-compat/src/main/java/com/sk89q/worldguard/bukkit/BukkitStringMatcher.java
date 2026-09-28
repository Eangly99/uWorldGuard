// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.bukkit;

import com.sk89q.minecraft.util.commands.CommandException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.internal.platform.StringMatcher;
import com.tricrotism.uworldguard.wgcompat.PlayerWrapping;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Matches worlds and online players by name for command arguments.
 *
 * <p>Worlds: an exact name, or {@code #main}/{@code #normal}, {@code #nether}, {@code #end} for the
 * first loaded world of that environment, or {@code #player:<name>} for that player's world.
 * Players: {@code *} for everyone online, otherwise an exact name, falling back to every name that
 * starts with the filter.
 */
public class BukkitStringMatcher implements StringMatcher {

    public BukkitStringMatcher() {
    }

    @Override
    public World matchWorld(final Actor sender, final String filter) throws CommandException {
        if (filter.startsWith("#")) {
            final String macro = filter.substring(1).toLowerCase(Locale.ROOT);
            if (macro.equals("main") || macro.equals("normal")) {
                return byEnvironment(org.bukkit.World.Environment.NORMAL);
            }
            if (macro.equals("nether")) {
                return byEnvironment(org.bukkit.World.Environment.NETHER);
            }
            if (macro.equals("end")) {
                return byEnvironment(org.bukkit.World.Environment.THE_END);
            }
            if (macro.startsWith("player:")) {
                final Player player = Bukkit.getPlayerExact(filter.substring("#player:".length()));
                if (player == null) {
                    throw new CommandException("No player by that name found.");
                }
                return BukkitAdapter.adapt(player.getWorld());
            }
            throw new CommandException("Invalid world identifier: " + filter);
        }
        final World world = getWorldByName(filter);
        if (world == null) {
            throw new CommandException("No world by that exact name found.");
        }
        return world;
    }

    @Override
    public List<LocalPlayer> matchPlayerNames(final String filter) {
        final Player exact = Bukkit.getPlayerExact(filter);
        if (exact != null) {
            return List.of(wrap(exact));
        }
        final String prefix = filter.toLowerCase(Locale.ROOT);
        final List<LocalPlayer> matches = new ArrayList<>(2);
        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                matches.add(wrap(player));
            }
        }
        return matches;
    }

    @Override
    public Iterable<? extends LocalPlayer> matchPlayers(final Actor source, final String filter)
        throws CommandException {
        if (filter.equals("*")) {
            final Collection<? extends Player> online = Bukkit.getOnlinePlayers();
            final List<LocalPlayer> all = new ArrayList<>(online.size());
            for (final Player player : online) {
                all.add(wrap(player));
            }
            return checkPlayerMatch(all);
        }
        return checkPlayerMatch(matchPlayerNames(filter));
    }

    @Override
    public Actor matchPlayerOrConsole(final Actor sender, final String filter) throws CommandException {
        if (filter.equalsIgnoreCase("#console") || filter.equalsIgnoreCase("*console*")
            || filter.equals("!")) {
            return BukkitAdapter.adapt(Bukkit.getConsoleSender());
        }
        return matchSinglePlayer(sender, filter);
    }

    @Override
    public World getWorldByName(final String worldName) {
        final org.bukkit.World world = Bukkit.getWorld(worldName);
        return world == null ? null : BukkitAdapter.adapt(world);
    }

    /**
     * Expands {@code %name%}, {@code %id%}, {@code %online%} and, for a player, {@code %world%}.
     */
    @Override
    public String replaceMacros(final Actor sender, final String message) {
        String result = message
            .replace("%name%", sender.getName())
            .replace("%id%", sender.getUniqueId().toString())
            .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
        final CommandSender bukkit = PlayerWrapping.unwrap(sender);
        if (bukkit instanceof Player player) {
            result = result.replace("%world%", player.getWorld().getName());
        }
        return result;
    }

    private static World byEnvironment(final org.bukkit.World.Environment environment) throws CommandException {
        for (final org.bukkit.World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == environment) {
                return BukkitAdapter.adapt(world);
            }
        }
        throw new CommandException("No " + environment.name().toLowerCase(Locale.ROOT) + " world found.");
    }

    private static LocalPlayer wrap(final Player player) {
        return (LocalPlayer) PlayerWrapping.wrap(player);
    }
}
