package com.tricrotism.uworldguard.event;

import com.tricrotism.uworldguard.region.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A region was changed outside uWorldGuard, through the server's own region API (UniverseSpigot),
 * by another plugin or by an edit to the server's region files. uWorldGuard has already applied it,
 * so unlike {@link RegionChangeEvent} this is a report, not a veto.
 *
 * <p>Threading: fires on the thread the server reports the change on, which UniverseSpigot documents
 * as the main thread. Check {@link #isAsynchronous()}.
 */
@NullMarked
public class RegionExternalChangeEvent extends Event {

    public enum Kind {CREATED, CHANGED, REMOVED}

    private static final HandlerList HANDLERS = new HandlerList();

    private final World world;
    private final String regionId;
    private final Kind kind;
    private final @Nullable ProtectedRegion before;
    private final @Nullable ProtectedRegion after;
    private final List<String> changes;

    public RegionExternalChangeEvent(
        final World world, final String regionId, final Kind kind,
        final @Nullable ProtectedRegion before, final @Nullable ProtectedRegion after, final List<String> changes
    ) {
        super(!Bukkit.isPrimaryThread());
        this.world = world;
        this.regionId = regionId;
        this.kind = kind;
        this.before = before;
        this.after = after;
        this.changes = List.copyOf(changes);
    }

    public World getWorld() {
        return world;
    }

    public String getRegionId() {
        return regionId;
    }

    public Kind getKind() {
        return kind;
    }

    /**
     * The region as uWorldGuard held it before the change, or {@code null} for a creation. It is no
     * longer in the world's manager.
     */
    public @Nullable ProtectedRegion getBefore() {
        return before;
    }

    /**
     * The region now in the world's manager, or {@code null} for a removal.
     */
    public @Nullable ProtectedRegion getAfter() {
        return after;
    }

    /**
     * What differs, for {@link Kind#CHANGED}: {@code shape}, {@code priority}, {@code parent},
     * {@code owners}, {@code members}, or {@code flag:<name>} per flag value or group qualifier. Empty
     * for a creation or removal.
     */
    public List<String> getChanges() {
        return changes;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
