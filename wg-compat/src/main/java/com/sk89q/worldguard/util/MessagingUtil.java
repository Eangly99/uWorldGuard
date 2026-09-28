// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.util;

import com.sk89q.worldguard.LocalPlayer;

public final class MessagingUtil {

    private MessagingUtil() {
    }

    /**
     * Sends each line of {@code message} to the player's chat. Blank input sends nothing.
     */
    public static void sendStringToChat(final LocalPlayer player, final String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        for (final String line : message.split("\n", -1)) {
            player.printRaw(line);
        }
    }

    /**
     * Shows {@code message} as a title; a second line, if present, becomes the subtitle.
     */
    public static void sendStringToTitle(final LocalPlayer player, final String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        final int newline = message.indexOf('\n');
        if (newline < 0) {
            player.sendTitle(message, "");
        } else {
            player.sendTitle(message.substring(0, newline), message.substring(newline + 1));
        }
    }
}
