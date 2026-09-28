// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.tricrotism.uworldguard.wgcompat;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;

import java.nio.file.Path;

/**
 * uWorldGuard's implementation of WorldGuard's platform interface.
 *
 * <p>Instantiated lazily by {@code com.sk89q.worldguard.WorldGuard#getPlatform()}, so a server
 * without WorldEdit never loads it.
 */
public final class UwgPlatform extends com.sk89q.worldguard.bukkit.BukkitWorldGuardPlatform {

    public static final UwgPlatform INSTANCE = new UwgPlatform();

    private UwgPlatform() {
    }

    @Override
    public String getPlatformName() {
        return "uWorldGuard (Paper)";
    }

    @Override
    public String getPlatformVersion() {
        return WgCompatBridge.plugin().getPluginMeta().getVersion();
    }

    @Override
    public com.sk89q.worldguard.protection.regions.RegionContainer getRegionContainer() {
        return CompatRegionContainer.INSTANCE;
    }

    @Override
    public com.sk89q.worldguard.session.SessionManager getSessionManager() {
        return SessionBridge.INSTANCE;
    }

    @Override
    public com.sk89q.worldguard.bukkit.BukkitConfigurationManager getGlobalStateManager() {
        return CompatConfigurationManager.INSTANCE;
    }

    @Override
    public Path getConfigDir() {
        return WgCompatBridge.plugin().getDataFolder().toPath();
    }

    @Override
    public com.sk89q.worldedit.world.gamemode.GameMode getDefaultGameMode() {
        return com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(Bukkit.getDefaultGameMode());
    }

    @Override
    public void broadcastNotification(final String message) {
        Bukkit.broadcast(Component.text(message), "uworldguard.notify");
    }

    /**
     * Fires {@code FlagContextCreateEvent} on the calling thread, only when something listens for
     * it, so an unobserved flag parse costs one array length read.
     */
    @Override
    public void notifyFlagContextCreate(
        final com.sk89q.worldguard.protection.flags.FlagContext.FlagContextBuilder flagContextBuilder) {
        if (com.sk89q.worldguard.bukkit.protection.events.flags.FlagContextCreateEvent.getHandlerList()
            .getRegisteredListeners().length == 0) {
            return;
        }
        Bukkit.getPluginManager().callEvent(
            new com.sk89q.worldguard.bukkit.protection.events.flags.FlagContextCreateEvent(flagContextBuilder));
    }

    @Override
    public void load() {
    }

    @Override
    public void unload() {
    }

    @Override
    @Deprecated(forRemoval = true)
    @SuppressWarnings("removal")
    public void stackPlayerInventory(final com.sk89q.worldguard.LocalPlayer localPlayer) {
        CompatDiagnostics.stub("WorldGuardPlatform.stackPlayerInventory");
    }
}
