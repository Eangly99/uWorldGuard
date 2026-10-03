package com.tricrotism.uworldguard.gui;

import com.tricrotism.uworldguard.event.RegionMembershipChangeEvent;
import com.tricrotism.uworldguard.region.EditResult;
import com.tricrotism.uworldguard.region.ProtectedRegion;
import com.tricrotism.uworldguard.region.RegionEditorImpl;
import com.tricrotism.uworldguard.region.RegionManager;
import com.tricrotism.uworldguard.text.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import xyz.xenondevs.invui.gui.Markers;
import xyz.xenondevs.invui.gui.PagedGui;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemBuilder;
import xyz.xenondevs.invui.window.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * InvUI owner/member editor for a region. Owners and members are listed as heads; clicking one twice
 * removes it. The two add buttons prompt for a name in chat. Domain edits are thread-safe.
 */
@NullMarked
public final class MembersMenu {

    private final Plugin plugin;
    private final World world;
    private final RegionManager manager;
    private final ProtectedRegion region;
    private final String regionId;
    private final ChatInputService chatInput;
    private final Consumer<Player> back;
    private @Nullable PagedGui<Item> gui;

    /**
     * @param back reopens the region page this menu was reached from
     */
    public MembersMenu(final Plugin plugin, final World world, final RegionManager manager,
                       final ProtectedRegion region, final ChatInputService chatInput, final Consumer<Player> back) {
        this.plugin = plugin;
        this.world = world;
        this.manager = manager;
        this.region = region;
        this.regionId = region.getId();
        this.chatInput = chatInput;
        this.back = back;
    }

    public void open(final Player player) {
        final PagedGui<Item> built = PagedGui.itemsBuilder()
            .setStructure(
                "x x x x x x x x x",
                "x x x x x x x x x",
                "x x x x x x x x x",
                "x x x x x x x x x",
                "x x x x x x x x x",
                "< B . O . M . C >")
            .addIngredient('x', Markers.CONTENT_LIST_SLOT_HORIZONTAL)
            .addIngredient('<', new PageButtons.Previous())
            .addIngredient('>', new PageButtons.Next())
            .addIngredient('B', MenuItems.back("region", back))
            .addIngredient('O', addItem(true))
            .addIngredient('M', addItem(false))
            .addIngredient('C', MenuItems.close())
            .setContent(entries())
            .build();
        this.gui = built;

        Window.builder()
            .setViewer(player)
            .setTitle(Messages.format("<dark_gray>Members: <aqua><id>",
                Placeholder.unparsed("id", region.getId())))
            .setUpperGui(built)
            .build()
            .open();
    }

    private List<Item> entries() {
        final List<Item> items = new ArrayList<>();
        for (final UUID uuid : region.getOwners().getPlayers()) {
            items.add(entry(uuid, true));
        }
        for (final UUID uuid : region.getMembers().getPlayers()) {
            items.add(entry(uuid, false));
        }
        return items;
    }

    private Item entry(final UUID uuid, final boolean owner) {
        return MenuItems.confirm(Material.PLAYER_HEAD,
            Messages.format("<!i><yellow><name>", Placeholder.unparsed("name", nameOf(uuid))),
            List.of(
                Messages.format(owner
                    ? "<!i><gray>Owner <dark_gray>(can build and manage the region)"
                    : "<!i><gray>Member <dark_gray>(can build in the region)"),
                Messages.format("<!i><dark_gray>Click twice to remove")),
            clicker -> {
                if (MenuItems.denied(clicker, MenuItems.MEMBERS)) {
                    return;
                }
                final EditResult result = new RegionEditorImpl(world, manager)
                    .removePlayer(region, role(owner), uuid, clicker);
                if (result == EditResult.NOT_FOUND) {
                    clicker.sendMessage(Messages.format("<red>Region <aqua><id></aqua> no longer exists.",
                        Placeholder.unparsed("id", regionId)));
                    return;
                }
                if (gui != null) {
                    gui.setContent(entries());
                }
            });
    }

    private Item addItem(final boolean owner) {
        return Item.builder()
            .setItemProvider(new ItemBuilder(owner ? Material.GOLDEN_HELMET : Material.LEATHER_HELMET)
                .setName(Messages.format(owner ? "<!i><green>Add owner" : "<!i><green>Add member"))
                .addLoreLines(
                    Messages.format(owner
                        ? "<!i><gray>Owners can build and manage the region"
                        : "<!i><gray>Members can build in the region"),
                    Messages.format("<!i><dark_gray>Click, then type a player name")))
            .addClickHandler((item, click) -> promptAdd(click.player(), owner))
            .build();
    }

    private static RegionMembershipChangeEvent.Role role(final boolean owner) {
        return owner ? RegionMembershipChangeEvent.Role.OWNER : RegionMembershipChangeEvent.Role.MEMBER;
    }

    private void promptAdd(final Player player, final boolean owner) {
        if (MenuItems.denied(player, MenuItems.MEMBERS)) {
            return;
        }
        MenuItems.prompt(player, chatInput,
            Messages.format(owner ? "<gray>Type the name of the player to add as an owner."
                : "<gray>Type the name of the player to add as a member."),
            null, name ->
            Bukkit.getAsyncScheduler().runNow(plugin, task -> {
                final OfflinePlayer target = Bukkit.getOfflinePlayer(name);
                if (!target.isOnline() && !target.hasPlayedBefore()) {
                    player.sendMessage(Messages.format("<red>No player named <aqua><player></aqua> "
                        + "has played here.", Placeholder.unparsed("player", name)));
                    player.getScheduler().run(plugin, t -> open(player), null);
                    return;
                }
                switch (new RegionEditorImpl(world, manager)
                    .addPlayer(region, role(owner), target.getUniqueId(), player)) {
                    case NOT_FOUND -> {
                        player.sendMessage(Messages.format("<red>Region <aqua><id></aqua> no longer exists.",
                            Placeholder.unparsed("id", regionId)));
                        return;
                    }
                    case APPLIED, UNCHANGED -> player.sendMessage(Messages.format(
                        "<green>Added <aqua><player></aqua> as <role>.",
                        Placeholder.unparsed("player", name),
                        Placeholder.unparsed("role", owner ? "owner" : "member")));
                    default -> {
                    }
                }
                player.getScheduler().run(plugin, t -> open(player), null);
            }), () -> open(player));
    }

    /**
     * A display name for a trusted player. Online is a field read; offline is not resolved.
     *
     * <p>{@code OfflinePlayer.getName()} reads and decompresses that player's {@code .dat} off disk
     * despite looking like a getter, and this runs once per entry every time the menu is built —
     * including the rebuild after each removal click, on the thread the click arrived on. A short
     * uuid is a worse label than a name, but it is not worth blocking a tick per member to avoid.
     */
    private static String nameOf(final UUID uuid) {
        final Player online = Bukkit.getPlayer(uuid);
        return online != null ? online.getName() : uuid.toString().substring(0, 8);
    }
}
