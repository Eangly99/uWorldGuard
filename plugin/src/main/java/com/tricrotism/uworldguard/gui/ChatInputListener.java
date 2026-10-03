package com.tricrotism.uworldguard.gui;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;

/**
 * Captures the next chat message from a player the GUI is awaiting input from. Chat fires async, so
 * the callback (which touches Bukkit state and reopens a menu) is dispatched to the player's entity
 * scheduler. Typing {@code cancel} runs the prompt's cancel action instead, which puts the player back
 * in the menu they came from.
 */
@NullMarked
public final class ChatInputListener implements Listener {

    private final Plugin plugin;
    private final ChatInputService service;

    public ChatInputListener(final Plugin plugin, final ChatInputService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(final AsyncChatEvent event) {
        final Player player = event.getPlayer();
        final ChatInputService.Prompt prompt = service.take(player.getUniqueId());
        if (prompt == null) return;

        event.setCancelled(true);
        final String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        player.getScheduler().run(plugin, _ -> {
            if (message.trim().equalsIgnoreCase("cancel")) {
                prompt.onCancel().run();
            } else {
                prompt.onValue().accept(message);
            }
        }, null);
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        service.cancel(event.getPlayer().getUniqueId());
    }
}
