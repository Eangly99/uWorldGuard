package com.tricrotism.uworldguard.gui;

import com.tricrotism.uworldguard.region.*;
import com.tricrotism.uworldguard.selection.Selection;
import com.tricrotism.uworldguard.selection.SelectionService;
import com.tricrotism.uworldguard.text.Messages;
import com.tricrotism.uworldguard.util.BlockVector3;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.gui.Markers;
import xyz.xenondevs.invui.gui.PagedGui;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemBuilder;
import xyz.xenondevs.invui.window.Window;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * InvUI region browser for one world. The list is sorted by name and searchable; clicking a region
 * opens its page, where every action is a labelled button: flags, owners and members, priority,
 * teleport, and delete, which takes a second click to confirm. Every submenu has a way back.
 */
@NullMarked
public final class RegionMenu {

    private static final Comparator<ProtectedRegion> BY_NAME =
        Comparator.comparing(region -> region.getId().toLowerCase(Locale.ROOT));

    private final Plugin plugin;
    private final World world;
    private final RegionManager manager;
    private final SelectionService selection;
    private final ChatInputService chatInput;
    private String search = "";
    private int page;

    public RegionMenu(final Plugin plugin, final World world, final RegionManager manager,
                      final SelectionService selection, final ChatInputService chatInput) {
        this.plugin = plugin;
        this.world = world;
        this.manager = manager;
        this.selection = selection;
        this.chatInput = chatInput;
    }

    public void open(final Player player) {
        final PagedGui<Item> gui = PagedGui.itemsBuilder()
            .setStructure(
                "x x x x x x x x x",
                "x x x x x x x x x",
                "x x x x x x x x x",
                "x x x x x x x x x",
                "x x x x x x x x x",
                "< S . . N . . C >")
            .addIngredient('x', Markers.CONTENT_LIST_SLOT_HORIZONTAL)
            .addIngredient('<', new PageButtons.Previous())
            .addIngredient('>', new PageButtons.Next())
            .addIngredient('S', searchItem())
            .addIngredient('N', createItem())
            .addIngredient('C', MenuItems.close())
            .setContent(listItems())
            .build();
        gui.setPage(page);
        gui.addPageChangeHandler((_, now) -> page = now);

        Window.builder()
            .setViewer(player)
            .setTitle(search.isEmpty()
                ? Messages.format("<dark_gray>Regions: <aqua><world>", Placeholder.unparsed("world", world.getName()))
                : Messages.format("<dark_gray>Regions matching <aqua><query>", Placeholder.unparsed("query", search)))
            .setUpperGui(gui)
            .build()
            .open();
    }

    private List<Item> listItems() {
        final List<ProtectedRegion> regions = new ArrayList<>(manager.getRegions());
        if (!search.isEmpty()) {
            regions.removeIf(region -> !region.getId().toLowerCase(Locale.ROOT).contains(search));
        }
        regions.sort(BY_NAME);
        final List<Item> items = new ArrayList<>(regions.size());
        for (final ProtectedRegion region : regions) {
            items.add(Item.builder()
                .setItemProvider(_ -> summary(region).addLoreLines(Component.empty(),
                    Messages.format("<!i><dark_gray>Click to manage")))
                .addClickHandler((_, click) -> openRegion(click.player(), region))
                .build());
        }
        return items;
    }

    /**
     * The region's name and what it is, shared by the list and the top of its own page.
     */
    private ItemBuilder summary(final ProtectedRegion region) {
        return new ItemBuilder(materialFor(region.getType()))
            .setName(Messages.format("<!i><yellow><id>", Placeholder.unparsed("id", region.getId())))
            .addLoreLines(
                Messages.format("<!i><gray>Shape: <white><type>",
                    Placeholder.unparsed("type", region.getType().name().toLowerCase(Locale.ROOT))),
                Messages.format("<!i><gray>Priority: <white><priority>",
                    Placeholder.unparsed("priority", Integer.toString(region.getPriority()))),
                Messages.format("<!i><gray>Owners: <white><owners></white>  Members: <white><members>",
                    Placeholder.unparsed("owners", Integer.toString(region.getOwners().size())),
                    Placeholder.unparsed("members", Integer.toString(region.getMembers().size()))),
                Messages.format("<!i><gray>Flags set: <white><flags>",
                    Placeholder.unparsed("flags", Integer.toString(region.getFlags().size()))));
    }

    private Item searchItem() {
        return Item.builder()
            .setItemProvider(_ -> new ItemBuilder(Material.OAK_SIGN)
                .setName(Messages.format("<!i><yellow>Search"))
                .addLoreLines(search.isEmpty()
                    ? new Component[]{Messages.format("<!i><dark_gray>Click, then type part of a region name")}
                    : new Component[]{
                    Messages.format("<!i><gray>Showing: <white><query>", Placeholder.unparsed("query", search)),
                    Messages.format("<!i><dark_gray>Left-click <gray>new search"),
                    Messages.format("<!i><dark_gray>Right-click <gray>show all regions")}))
            .addClickHandler((_, click) -> {
                if (click.clickType().isRightClick()) {
                    search = "";
                    page = 0;
                    open(click.player());
                    return;
                }
                MenuItems.prompt(click.player(), chatInput,
                    Messages.format("<gray>Type part of a region name, e.g. <white>spawn</white>."), null,
                    raw -> {
                        search = raw.trim().toLowerCase(Locale.ROOT);
                        page = 0;
                        open(click.player());
                    }, () -> open(click.player()));
            })
            .build();
    }

    /**
     * One region's page. Every action is a labelled button instead of a click combination to
     * memorise, and the global region, which has no area, simply has no teleport or delete.
     */
    private void openRegion(final Player player, final ProtectedRegion region) {
        final boolean global = region.getType() == RegionType.GLOBAL;
        final Gui gui = Gui.builder()
            .setStructure(
                ". . . . I . . . .",
                ". F . M . P . T .",
                "B . . . . . . . D")
            .addIngredient('I', Item.simple(_ -> summary(region)))
            .addIngredient('F', flagsItem(region))
            .addIngredient('M', membersItem(region))
            .addIngredient('P', priorityItem(region))
            .addIngredient('T', global ? unavailable("Teleport", "The global region covers the whole world")
                : teleportItem(region))
            .addIngredient('D', global ? unavailable("Delete", "The global region cannot be deleted")
                : deleteItem(region))
            .addIngredient('B', MenuItems.back("regions", this::open))
            .build();
        Window.builder()
            .setViewer(player)
            .setTitle(Messages.format("<dark_gray>Region: <aqua><id>", Placeholder.unparsed("id", region.getId())))
            .setUpperGui(gui)
            .build()
            .open();
    }

    private Item flagsItem(final ProtectedRegion region) {
        return Item.builder()
            .setItemProvider(_ -> new ItemBuilder(Material.WRITABLE_BOOK)
                .setName(Messages.format("<!i><yellow>Flags"))
                .addLoreLines(
                    Messages.format("<!i><gray>What players can and can't do here"),
                    Messages.format("<!i><gray><white><count></white> set",
                        Placeholder.unparsed("count", Integer.toString(region.getFlags().size())))))
            .addClickHandler((_, click) ->
                new FlagMenu(world, manager, region, chatInput, viewer -> openRegion(viewer, region)).open(click.player()))
            .build();
    }

    private Item membersItem(final ProtectedRegion region) {
        return Item.builder()
            .setItemProvider(_ -> new ItemBuilder(Material.PLAYER_HEAD)
                .setName(Messages.format("<!i><yellow>Owners & members"))
                .addLoreLines(
                    Messages.format("<!i><gray>Who can build here"),
                    Messages.format("<!i><gray><white><owners></white> owners, <white><members></white> members",
                        Placeholder.unparsed("owners", Integer.toString(region.getOwners().size())),
                        Placeholder.unparsed("members", Integer.toString(region.getMembers().size())))))
            .addClickHandler((_, click) -> new MembersMenu(plugin, world, manager, region, chatInput,
                viewer -> openRegion(viewer, region)).open(click.player()))
            .build();
    }

    private Item priorityItem(final ProtectedRegion region) {
        return Item.builder()
            .setItemProvider(_ -> new ItemBuilder(Material.COMPARATOR)
                .setName(Messages.format("<!i><yellow>Priority: <white><priority>",
                    Placeholder.unparsed("priority", Integer.toString(region.getPriority()))))
                .addLoreLines(
                    Messages.format("<!i><gray>Where regions overlap, the higher"),
                    Messages.format("<!i><gray>priority's flags win"),
                    Messages.format("<!i><dark_gray>Click to change")))
            .addClickHandler((_, click) -> promptPriority(click.player(), region))
            .build();
    }

    private void promptPriority(final Player player, final ProtectedRegion region) {
        if (MenuItems.denied(player, MenuItems.PRIORITY)) {
            return;
        }
        MenuItems.prompt(player, chatInput,
            Messages.format("<gray>Type the new priority for <aqua><id></aqua>, a whole number.",
                Placeholder.unparsed("id", region.getId())),
            Integer.toString(region.getPriority()),
            raw -> {
                try {
                    final int priority = Integer.parseInt(raw.trim());
                    if (new RegionEditorImpl(world, manager).setPriority(region, priority, player).isApplied()) {
                        player.sendMessage(Messages.format("<green>Priority set to <white><priority></white>.",
                            Placeholder.unparsed("priority", Integer.toString(priority))));
                    }
                } catch (final NumberFormatException e) {
                    player.sendMessage(Messages.format("<red>That isn't a whole number."));
                }
                openRegion(player, region);
            }, () -> openRegion(player, region));
    }

    private Item teleportItem(final ProtectedRegion region) {
        return Item.builder()
            .setItemProvider(new ItemBuilder(Material.ENDER_PEARL)
                .setName(Messages.format("<!i><yellow>Teleport here"))
                .addLoreLines(Messages.format("<!i><gray>To the top of the region's centre")))
            .addClickHandler((_, click) -> {
                if (MenuItems.denied(click.player(), MenuItems.TELEPORT)) {
                    return;
                }
                click.player().closeInventory();
                teleport(click.player(), region);
            })
            .build();
    }

    private Item deleteItem(final ProtectedRegion region) {
        return MenuItems.confirm(Material.TNT,
            Messages.format("<!i><red>Delete region"),
            List.of(
                Messages.format("<!i><gray>Removes the region and all its flags"),
                Messages.format("<!i><dark_gray>Click twice to delete")),
            player -> {
                if (MenuItems.denied(player, MenuItems.REMOVE)) {
                    return;
                }
                if (new RegionEditorImpl(world, manager).remove(region.getId(), player).isApplied()) {
                    player.sendMessage(Messages.format("<green>Deleted region <aqua><id></aqua>.",
                        Placeholder.unparsed("id", region.getId())));
                    open(player);
                }
            });
    }

    private static Item unavailable(final String action, final String reason) {
        return Item.simple(new ItemBuilder(Material.GRAY_DYE)
            .setName(Messages.format("<!i><dark_gray><action>", Placeholder.unparsed("action", action)))
            .addLoreLines(Messages.format("<!i><dark_gray><reason>", Placeholder.unparsed("reason", reason))));
    }

    private static Material materialFor(final RegionType type) {
        return switch (type) {
            case CUBOID -> Material.GRASS_BLOCK;
            case POLYGON -> Material.MAP;
            case CYLINDER -> Material.CAULDRON;
            case SPHERE -> Material.SLIME_BALL;
            case GLOBAL -> Material.BEACON;
            case POLYHEDRON -> Material.AMETHYST_SHARD;
            case COMPOSITE -> Material.BRICKS;
            case CARVED -> Material.CARVED_PUMPKIN;
        };
    }

    private void teleport(final Player player, final ProtectedRegion region) {
        final BlockVector3 min = region.getMinimumPoint();
        final BlockVector3 max = region.getMaximumPoint();
        final Location target = new Location(world,
            (min.x() + max.x()) / 2.0 + 0.5, max.y() + 1, (min.z() + max.z()) / 2.0 + 0.5);
        player.teleportAsync(target);
    }

    private Item createItem() {
        return Item.builder()
            .setItemProvider(new ItemBuilder(Material.EMERALD)
                .setName(Messages.format("<!i><green>Create region"))
                .addLoreLines(
                    Messages.format("<!i><gray>A box from your current selection"),
                    Messages.format("<!i><dark_gray>Click, then type a name")))
            .addClickHandler((_, click) -> promptCreate(click.player()))
            .build();
    }

    private void promptCreate(final Player player) {
        if (MenuItems.denied(player, MenuItems.DEFINE)) {
            return;
        }
        final Selection sel = selection.getSelection(player);
        if (sel == null) {
            player.sendMessage(Messages.format("<red>Make a selection first, with the wand or WorldEdit."));
            return;
        }
        MenuItems.prompt(player, chatInput,
            Messages.format("<gray>Type a name for the new region. Letters, numbers, <white>_</white> and <white>-</white>."),
            null, name -> {
                final @Nullable ProtectedRegion created = create(player, name.trim(), sel);
                if (created != null) {
                    openRegion(player, created);
                } else {
                    open(player);
                }
            }, () -> open(player));
    }

    private @Nullable ProtectedRegion create(final Player player, final String name, final Selection sel) {
        if (!ProtectedRegion.isValidId(name)) {
            player.sendMessage(Messages.format("<red>Region names may only use letters, digits, "
                    + "<aqua>_</aqua> and <aqua>-</aqua>, up to <aqua><max></aqua> characters.",
                Placeholder.unparsed("max", Integer.toString(ProtectedRegion.MAX_ID_LENGTH))));
            return null;
        }
        final ProtectedRegion region = new ProtectedCuboidRegion(name, sel.min(), sel.max());
        switch (new RegionEditorImpl(world, manager).create(region, player)) {
            case APPLIED -> {
                player.sendMessage(Messages.format("<green>Created region <aqua><id></aqua>.",
                    Placeholder.unparsed("id", name)));
                return region;
            }
            case ALREADY_EXISTS -> player.sendMessage(Messages.format(
                "<red>A region named <aqua><id></aqua> already exists.", Placeholder.unparsed("id", name)));
            default -> {
            }
        }
        return null;
    }
}
