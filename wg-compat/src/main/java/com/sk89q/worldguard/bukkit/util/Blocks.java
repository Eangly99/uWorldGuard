// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.bukkit.util;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Chest;

import java.util.List;

public final class Blocks {

    private Blocks() {
    }

    /**
     * The other halves of a multi-block structure {@code block} belongs to: the far half of a bed or
     * a double chest, and the other half of a door, tall plant or other two-high block.
     *
     * @return the connected blocks, not including {@code block}; empty when it stands alone
     */
    public static List<Block> getConnected(final Block block) {
        final BlockData data = block.getBlockData();
        if (data instanceof Bed bed) {
            final BlockFace toOther = bed.getPart() == Bed.Part.FOOT ? bed.getFacing() : bed.getFacing().getOppositeFace();
            return List.of(block.getRelative(toOther));
        }
        if (data instanceof Chest chest && chest.getType() != Chest.Type.SINGLE) {
            final BlockFace facing = chest.getFacing();
            final BlockFace toOther = chest.getType() == Chest.Type.LEFT
                ? rotateClockwise(facing)
                : rotateClockwise(facing).getOppositeFace();
            return List.of(block.getRelative(toOther));
        }
        if (data instanceof Bisected bisected) {
            return List.of(block.getRelative(
                bisected.getHalf() == Bisected.Half.BOTTOM ? BlockFace.UP : BlockFace.DOWN));
        }
        return List.of();
    }

    private static BlockFace rotateClockwise(final BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> face;
        };
    }
}
