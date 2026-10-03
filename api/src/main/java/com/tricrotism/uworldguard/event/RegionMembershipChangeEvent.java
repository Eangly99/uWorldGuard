package com.tricrotism.uworldguard.event;

import com.tricrotism.uworldguard.region.ProtectedRegion;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Fired before a player or a permission group is added to or removed from a region's owners or
 * members. Exactly one of {@link #getPlayer()} and {@link #getGroup()} is set. Cancelling leaves the
 * domain untouched.
 *
 * <p>Usually asynchronous: resolving a typed name to a UUID reads player data off disk, so the
 * command and the menu's add prompt do that off the region thread and this fires there. Removing
 * someone from the menu fires on the clicker's region thread instead. Check
 * {@link #isAsynchronous()} before touching the Bukkit API from a listener.
 *
 * <p>Fired for the edit as asked for, whether or not it changes anything — adding someone who is
 * already an owner still fires.
 *
 * @see RegionChangeEvent
 */
@NullMarked
public class RegionMembershipChangeEvent extends RegionChangeEvent {

    /**
     * Which of a region's two trust lists an edit touches.
     */
    public enum Role {
        OWNER,
        MEMBER
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final Role role;
    private final @Nullable UUID player;
    private final @Nullable String group;
    private final boolean adding;

    public RegionMembershipChangeEvent(
        final World world, final ProtectedRegion region, final Role role, final UUID player,
        final boolean adding, final @Nullable CommandSender actor
    ) {
        super(world, region, actor);
        this.role = role;
        this.player = player;
        this.group = null;
        this.adding = adding;
    }

    /**
     * A permission group being trusted or untrusted, the {@code g:} form of the member commands.
     */
    public RegionMembershipChangeEvent(
        final World world, final ProtectedRegion region, final Role role, final String group,
        final boolean adding, final @Nullable CommandSender actor
    ) {
        super(world, region, actor);
        this.role = role;
        this.player = null;
        this.group = group;
        this.adding = adding;
    }

    /**
     * Whether the edit touches the owners or the members.
     */
    public Role getRole() {
        return role;
    }

    /**
     * The player being added or removed. Resolved from a name, so they need not be online. Null when
     * the edit is to a group, see {@link #getGroup()}.
     */
    public @Nullable UUID getPlayer() {
        return player;
    }

    /**
     * The permission group being added or removed, or null when the edit is to a player.
     */
    public @Nullable String getGroup() {
        return group;
    }

    /**
     * True for an addition, false for a removal.
     */
    public boolean isAdding() {
        return adding;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
