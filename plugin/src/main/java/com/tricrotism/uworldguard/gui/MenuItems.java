package com.tricrotism.uworldguard.gui;

import com.tricrotism.uworldguard.text.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemBuilder;

import java.util.List;
import java.util.function.Consumer;

/**
 * Shared GUI items and the chat prompt every menu uses.
 */
@NullMarked final class MenuItems {

    static final String DEFINE = "uworldguard.region.define";
    static final String REMOVE = "uworldguard.region.remove";
    static final String FLAG = "uworldguard.region.flag";
    static final String MEMBERS = "uworldguard.region.members";
    static final String PRIORITY = "uworldguard.region.priority";
    static final String TELEPORT = "uworldguard.region.teleport";

    /**
     * How long a destructive button stays armed after its first click.
     */
    private static final long CONFIRM_MILLIS = 5_000L;

    private MenuItems() {
    }

    /**
     * Whether {@code player} may not do this, telling them so when they may not.
     *
     * <p>Opening the browser is one node, {@code uworldguard.menu}, but the things it does from there
     * are four, each with its own node on the command that does the same thing. Without this check,
     * granting someone the menu granted them define, remove, flag and members with it.
     */
    static boolean denied(final Player player, final String node) {
        if (player.hasPermission(node)) {
            return false;
        }
        player.sendMessage(Messages.format("<red>You don't have permission to do that."));
        return true;
    }

    static Item close() {
        return Item.builder()
            .setItemProvider(new ItemBuilder(Material.BARRIER).setName(Messages.format("<!i><red>Close")))
            .addClickHandler((item, click) -> click.player().closeInventory())
            .build();
    }

    static Item back(final String to, final Consumer<Player> open) {
        return Item.builder()
            .setItemProvider(new ItemBuilder(Material.OAK_DOOR)
                .setName(Messages.format("<!i><yellow>Back to <to>", Placeholder.unparsed("to", to))))
            .addClickHandler((_, click) -> open.accept(click.player()))
            .build();
    }

    /**
     * Closes the menu and asks for a value in chat. Typing {@code cancel} runs {@code back}, which
     * reopens the menu the player came from. When {@code current} is given, a clickable line puts it
     * in the chat box so the player edits it instead of retyping it.
     */
    static void prompt(
        final Player player, final ChatInputService chatInput, final Component question,
        final @Nullable String current, final Consumer<String> onValue, final Runnable back
    ) {
        player.closeInventory();
        player.sendMessage(question);
        if (current != null && !current.isEmpty()) {
            player.sendMessage(Messages.format("<dark_gray>» <yellow><u>Click to edit the current value</u>")
                .clickEvent(ClickEvent.suggestCommand(current))
                .hoverEvent(HoverEvent.showText(Messages.format("<gray>Puts <white><value></white> in your chat box",
                    Placeholder.unparsed("value", current)))));
        }
        player.sendMessage(Messages.format("<dark_gray>» Type <red>cancel</red> to go back."));
        chatInput.await(player.getUniqueId(), onValue, back);
    }

    /**
     * A button for something that cannot be undone. The first click arms it and turns it red; only a
     * second click within five seconds runs {@code action}, so one stray click cannot delete anything.
     */
    static Item confirm(
        final Material material, final Component name, final List<Component> lore, final Consumer<Player> action
    ) {
        final long[] armedUntil = {0L};
        return Item.builder()
            .setItemProvider(_ -> System.currentTimeMillis() < armedUntil[0]
                ? new ItemBuilder(Material.RED_CONCRETE)
                .setName(Messages.format("<!i><red><b>Click again to confirm"))
                .addLoreLines(Messages.format("<!i><gray>This cannot be undone."))
                : new ItemBuilder(material).setName(name).addLoreLines(lore.toArray(Component[]::new)))
            .addClickHandler((item, click) -> {
                if (System.currentTimeMillis() < armedUntil[0]) {
                    armedUntil[0] = 0L;
                    action.accept(click.player());
                } else {
                    armedUntil[0] = System.currentTimeMillis() + CONFIRM_MILLIS;
                }
                item.notifyWindows();
            })
            .build();
    }
}
