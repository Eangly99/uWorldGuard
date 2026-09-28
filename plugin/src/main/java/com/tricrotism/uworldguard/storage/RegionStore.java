package com.tricrotism.uworldguard.storage;

import com.tricrotism.uworldguard.region.RegionManager;
import com.tricrotism.uworldguard.region.SpatialIndex;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Persistence backend for regions, one logical store per world. Implementations must be
 * safe to call off the main thread (loading/saving is done on the async scheduler).
 * The default is {@link YamlRegionStore}; a SQL backend is an optional, disabled-by-default
 * implementation behind this same interface.
 */
@NullMarked
public interface RegionStore {

    /**
     * Load all regions for a world into {@code manager}.
     */
    void load(String worldName, RegionManager manager) throws Exception;

    /**
     * Persist all regions currently in {@code manager}.
     */
    void save(String worldName, RegionManager manager) throws Exception;

    /**
     * The index that should answer {@code manager}'s point lookups, or {@code null} to keep its own
     * chunk cache. Asked once per world load, after {@link #load} has filled the manager.
     */
    default @Nullable SpatialIndex index(final String worldName, final RegionManager manager) {
        return null;
    }

    /**
     * The world's manager is gone. Drop anything {@link #index} kept for it.
     */
    default void unload(final String worldName) {}

    /**
     * Release anything the backend registered outside this plugin, after the final save. A file-backed
     * store holds nothing, so the default does nothing.
     */
    default void close() {}
}
