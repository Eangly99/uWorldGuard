package com.tricrotism.uworldguard.gui;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Tracks players the GUI is waiting on for a typed chat value. {@link ChatInputListener} consumes
 * the next chat message from such a player and feeds it back to the registered callback, or runs the
 * cancel action when they type {@code cancel}.
 */
@NullMarked
public final class ChatInputService {

    /**
     * What to do with the typed value, and what to do if the player types {@code cancel} instead.
     */
    public record Prompt(Consumer<String> onValue, Runnable onCancel) {}

    private final Map<UUID, Prompt> pending = new ConcurrentHashMap<>();

    public void await(final UUID player, final Consumer<String> onValue, final Runnable onCancel) {
        pending.put(player, new Prompt(onValue, onCancel));
    }

    public @Nullable Prompt take(final UUID player) {
        return pending.remove(player);
    }

    public void cancel(final UUID player) {
        pending.remove(player);
    }
}
