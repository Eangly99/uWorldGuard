package com.tricrotism.uworldguard.flags;

import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;

/**
 * A typed region flag. {@code T} is the runtime value type stored on a region.
 * Subclasses define how the value round-trips to/from a storage-friendly object
 * (String, Number, Boolean) and how it parses from command input.
 */
@NullMarked
public abstract class Flag<T> {

    private final String name;
    private @Nullable FlagCategory category;
    private int index = -1;

    protected Flag(final String name) {
        this.name = name;
    }

    public final String getName() {
        return name;
    }

    /**
     * Dense registration index, unique per flag and stable for the JVM's lifetime. Lets callers that
     * track sets of flags use a bitset keyed on this instead of hashing — see
     * {@code RegionManager.anyRegionUses}. {@code -1} until registered.
     */
    public final int getIndex() {
        return index;
    }

    /**
     * Assigned once at registration.
     */
    final void setIndex(final int index) {
        this.index = index;
    }

    /**
     * The menu grouping this flag belongs to, or {@code null} if uncategorised.
     */
    public final @Nullable FlagCategory getCategory() {
        return category;
    }

    /**
     * Assigned once at registration.
     */
    final void setCategory(final FlagCategory category) {
        this.category = category;
    }

    /**
     * Parse a value from command-line input, or {@code null} if it cannot be parsed.
     */
    public abstract @Nullable T parse(String input);

    /**
     * Parse a value, telling the flag who is setting it. Built-in flags parse the same either way and
     * inherit this; flags bridged from the WorldGuard API need the sender, because that API hands its
     * flags a parse context and a custom flag is free to ask that context who the setter is — without
     * one, every such flag rejects every input.
     *
     * <p>{@code sender} is {@code null} when the value did not come from a player.
     */
    public @Nullable T parse(final String input, final @Nullable Player sender) {
        return parse(input);
    }

    /**
     * A short description of what this flag accepts, for menus and command help, or {@code null} to
     * let the caller infer it from the flag's type. Only flags whose type is not visible to the
     * caller — bridged WorldGuard flags, which all share one engine class — need to answer this.
     */
    public @Nullable String getValueHint() {
        return null;
    }

    /**
     * Values this flag accepts that a caller can offer as completions, or empty when they are not a
     * closed set. Distinct from {@link #getValueHint()}, which is prose for a human — "a number" is a
     * usable hint and an unusable completion. Built-in flags are matched on their type by the command
     * layer and inherit this; bridged WorldGuard flags all share one engine class, so they answer for
     * themselves.
     */
    public List<String> getValueSuggestions() {
        return List.of();
    }

    /**
     * Convert a stored object (from YAML/JSON/SQL) into a value, or {@code null} if invalid.
     */
    public abstract @Nullable T unmarshal(Object stored);

    /**
     * Convert a value into a storage-friendly object (String, Number, Boolean, List).
     */
    public abstract Object marshal(T value);

    /**
     * {@code value} written the way {@link #parse} reads it, for pre-filling an edit so the operator
     * changes the value instead of retyping it. Lists come out comma-separated.
     */
    public String toInput(final T value) {
        final Object marshalled = marshal(value);
        if (!(marshalled instanceof Collection<?> entries)) {
            return String.valueOf(marshalled);
        }
        final StringJoiner joined = new StringJoiner(",");
        for (final Object entry : entries) {
            joined.add(String.valueOf(entry));
        }
        return joined.toString();
    }

    @Override
    public final boolean equals(final Object o) {
        return o instanceof Flag<?> other && name.equals(other.name);
    }

    @Override
    public final int hashCode() {
        return name.hashCode();
    }

    @Override
    public final String toString() {
        return name;
    }
}
