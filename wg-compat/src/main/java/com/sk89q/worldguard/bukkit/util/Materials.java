// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.bukkit.util;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * Material classification helpers.
 *
 * <p>Vanilla tags are read per call, because a tag resolves against the running server. Sets that
 * follow from material names alone are built once when the class loads.
 */
public final class Materials {

    private static final Map<Material, EntityType> ENTITY_BY_ITEM = new EnumMap<>(Material.class);
    private static final Map<EntityType, Material> ITEM_BY_ENTITY = new EnumMap<>(EntityType.class);
    private static final Map<Material, EntityType> ENTITY_BY_SPAWN_EGG = new EnumMap<>(Material.class);
    private static final Map<Material, Material> BUCKET_CONTENTS = new EnumMap<>(Material.class);
    private static final Set<Material> MINECARTS = EnumSet.noneOf(Material.class);
    private static final Set<Material> CORALS = EnumSet.noneOf(Material.class);
    private static final Set<Material> VINES = EnumSet.noneOf(Material.class);
    private static final Set<Material> WAXED_COPPER = EnumSet.noneOf(Material.class);
    private static final Set<Material> UNWAXED_COPPER = EnumSet.noneOf(Material.class);

    private static final Set<Material> MUSHROOMS = EnumSet.of(Material.RED_MUSHROOM, Material.BROWN_MUSHROOM);
    private static final Set<Material> WATER = EnumSet.of(Material.WATER, Material.BUBBLE_COLUMN);
    private static final Set<Material> PISTONS = EnumSet.of(
        Material.PISTON, Material.STICKY_PISTON, Material.PISTON_HEAD, Material.MOVING_PISTON);
    private static final Set<Material> AMETHYST_GROWTH = EnumSet.of(
        Material.BUDDING_AMETHYST, Material.SMALL_AMETHYST_BUD, Material.MEDIUM_AMETHYST_BUD,
        Material.LARGE_AMETHYST_BUD, Material.AMETHYST_CLUSTER);
    private static final Set<Material> SCULK_GROWTH = EnumSet.of(
        Material.SCULK, Material.SCULK_VEIN, Material.SCULK_SENSOR, Material.SCULK_SHRIEKER,
        Material.CALIBRATED_SCULK_SENSOR);
    private static final Set<Material> EXTRA_CROPS = EnumSet.of(
        Material.NETHER_WART, Material.COCOA, Material.SWEET_BERRY_BUSH, Material.PITCHER_CROP,
        Material.TORCHFLOWER_CROP, Material.MELON_STEM, Material.PUMPKIN_STEM,
        Material.ATTACHED_MELON_STEM, Material.ATTACHED_PUMPKIN_STEM);
    private static final Set<Material> INVENTORY_BLOCKS = EnumSet.of(
        Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL, Material.FURNACE,
        Material.BLAST_FURNACE, Material.SMOKER, Material.HOPPER, Material.DROPPER, Material.DISPENSER,
        Material.BREWING_STAND, Material.CRAFTER, Material.CHISELED_BOOKSHELF, Material.DECORATED_POT,
        Material.JUKEBOX, Material.LECTERN, Material.CAMPFIRE, Material.SOUL_CAMPFIRE);
    private static final Set<Material> USE_BLOCKS = EnumSet.of(
        Material.LEVER, Material.CRAFTING_TABLE, Material.CARTOGRAPHY_TABLE, Material.FLETCHING_TABLE,
        Material.SMITHING_TABLE, Material.LOOM, Material.STONECUTTER, Material.GRINDSTONE, Material.BELL,
        Material.ENCHANTING_TABLE, Material.BEACON, Material.RESPAWN_ANCHOR);
    private static final Set<Material> RIGHT_CLICK_MODIFIED = EnumSet.of(
        Material.LEVER, Material.REPEATER, Material.COMPARATOR, Material.DAYLIGHT_DETECTOR,
        Material.NOTE_BLOCK, Material.CAKE, Material.COMPOSTER, Material.JUKEBOX, Material.LECTERN,
        Material.CHISELED_BOOKSHELF, Material.DECORATED_POT, Material.RESPAWN_ANCHOR,
        Material.SWEET_BERRY_BUSH, Material.CAVE_VINES, Material.CAVE_VINES_PLANT, Material.DRAGON_EGG,
        Material.BEEHIVE, Material.BEE_NEST, Material.CAULDRON, Material.WATER_CAULDRON);
    private static final Set<Material> LEFT_CLICK_MODIFIED = EnumSet.of(
        Material.NOTE_BLOCK, Material.DRAGON_EGG);
    private static final Set<Material> BUILDING_IF_USED = EnumSet.of(
        Material.REPEATER, Material.COMPARATOR, Material.DAYLIGHT_DETECTOR, Material.NOTE_BLOCK,
        Material.CAKE, Material.COMPOSTER, Material.RESPAWN_ANCHOR, Material.SWEET_BERRY_BUSH,
        Material.CAVE_VINES, Material.CAVE_VINES_PLANT, Material.CHISELED_BOOKSHELF, Material.DECORATED_POT);
    private static final Set<Material> APPLIED_ITEMS = EnumSet.of(
        Material.BONE_MEAL, Material.FLINT_AND_STEEL, Material.FIRE_CHARGE, Material.HONEYCOMB,
        Material.ENDER_EYE, Material.INK_SAC, Material.GLOW_INK_SAC, Material.GLASS_BOTTLE,
        Material.SHEARS, Material.BRUSH, Material.BUCKET);
    private static final Set<Material> TILLABLE = EnumSet.of(
        Material.DIRT, Material.GRASS_BLOCK, Material.DIRT_PATH, Material.COARSE_DIRT, Material.ROOTED_DIRT);
    private static final Set<Material> PATHABLE = EnumSet.of(
        Material.GRASS_BLOCK, Material.DIRT, Material.PODZOL, Material.MYCELIUM, Material.COARSE_DIRT,
        Material.ROOTED_DIRT, Material.CAMPFIRE, Material.SOUL_CAMPFIRE);
    private static final Set<Material> SHEARABLE = EnumSet.of(
        Material.PUMPKIN, Material.BEEHIVE, Material.BEE_NEST, Material.TRIPWIRE);

    static {
        for (final Material material : Material.values()) {
            final String name = material.name();
            if (name.startsWith("LEGACY_")) {
                continue;
            }
            if (name.endsWith("_SPAWN_EGG")) {
                final EntityType type = entityType(name.substring(0, name.length() - "_SPAWN_EGG".length()));
                if (type != null) {
                    ENTITY_BY_SPAWN_EGG.put(material, type);
                }
            }
            if (material == Material.MINECART || name.endsWith("_MINECART")) {
                MINECARTS.add(material);
            }
            if (name.contains("CORAL")) {
                CORALS.add(material);
            }
            if (name.contains("VINE")) {
                VINES.add(material);
            }
            if (material.isBlock() && name.startsWith("WAXED_")) {
                WAXED_COPPER.add(material);
            }
            if (material.isBlock() && Material.getMaterial("WAXED_" + name) != null) {
                UNWAXED_COPPER.add(material);
            }
            if (name.endsWith("_BUCKET")) {
                final Material contents = switch (material) {
                    case LAVA_BUCKET -> Material.LAVA;
                    case POWDER_SNOW_BUCKET -> Material.POWDER_SNOW;
                    case MILK_BUCKET -> null;
                    default -> Material.WATER;
                };
                if (contents != null) {
                    BUCKET_CONTENTS.put(material, contents);
                }
            }
        }
        for (final EntityType type : EntityType.values()) {
            final Class<?> entityClass = type.getEntityClass();
            if (entityClass == null) {
                continue;
            }
            final boolean living = LivingEntity.class.isAssignableFrom(entityClass);
            final Material item = type == EntityType.LEASH_KNOT ? Material.LEAD
                : living && type != EntityType.ARMOR_STAND ? null
                : Material.getMaterial(type.name());
            if (item != null && item.isItem()) {
                ITEM_BY_ENTITY.put(type, item);
                ENTITY_BY_ITEM.put(item, type);
            }
        }
    }

    private Materials() {
    }

    /**
     * The item that places {@code type}, such as a boat, minecart, armor stand or item frame.
     *
     * @return the item, or {@code null} when the entity has no item form
     */
    public static Material getRelatedMaterial(final EntityType type) {
        return ITEM_BY_ENTITY.get(type);
    }

    /**
     * @return the entity {@code material} places, or {@code null}
     * @see #getRelatedMaterial(EntityType)
     */
    public static EntityType getRelatedEntity(final Material material) {
        return ENTITY_BY_ITEM.get(material);
    }

    /**
     * @return the block a filled bucket places, or {@code null} for an empty or milk bucket
     */
    public static Material getBucketBlockMaterial(final Material type) {
        return BUCKET_CONTENTS.get(type);
    }

    public static boolean isMushroom(final Material material) {
        return MUSHROOMS.contains(material);
    }

    public static boolean isLeaf(final Material material) {
        return Tag.LEAVES.isTagged(material);
    }

    public static boolean isLiquid(final Material material) {
        return isWater(material) || isLava(material);
    }

    public static boolean isWater(final Material material) {
        return WATER.contains(material);
    }

    public static boolean isLava(final Material material) {
        return material == Material.LAVA;
    }

    public static boolean isPortal(final Material material) {
        return Tag.PORTALS.isTagged(material);
    }

    public static boolean isRailBlock(final Material material) {
        return Tag.RAILS.isTagged(material);
    }

    public static boolean isPistonBlock(final Material material) {
        return PISTONS.contains(material);
    }

    public static boolean isMinecart(final Material material) {
        return MINECARTS.contains(material);
    }

    public static boolean isBoat(final Material material) {
        return Tag.ITEMS_BOATS.isTagged(material) || Tag.ITEMS_CHEST_BOATS.isTagged(material);
    }

    public static boolean isShulkerBox(final Material material) {
        return Tag.SHULKER_BOXES.isTagged(material);
    }

    /**
     * Blocks that hold items a player can take out.
     */
    public static boolean isInventoryBlock(final Material material) {
        return INVENTORY_BLOCKS.contains(material) || isShulkerBox(material);
    }

    public static boolean isSpawnEgg(final Material material) {
        return ENTITY_BY_SPAWN_EGG.containsKey(material);
    }

    /**
     * @return the entity a spawn egg spawns, or {@code null} when {@code material} is not one
     */
    public static EntityType getEntitySpawnEgg(final Material material) {
        return ENTITY_BY_SPAWN_EGG.get(material);
    }

    public static boolean isBed(final Material material) {
        return Tag.BEDS.isTagged(material);
    }

    public static boolean isAnvil(final Material material) {
        return Tag.ANVIL.isTagged(material);
    }

    /**
     * Live and dead coral blocks, plants and fans.
     */
    public static boolean isCoral(final Material material) {
        return CORALS.contains(material);
    }

    public static boolean isCrop(final Material material) {
        return Tag.CROPS.isTagged(material) || EXTRA_CROPS.contains(material);
    }

    public static boolean isVine(final Material material) {
        return VINES.contains(material);
    }

    /**
     * Blocks governed by the {@code use} flag: doors, trapdoors, gates, buttons, pressure plates,
     * levers and utility stations.
     */
    public static boolean isUseFlagApplicable(final Material material) {
        return USE_BLOCKS.contains(material)
            || Tag.DOORS.isTagged(material)
            || Tag.TRAPDOORS.isTagged(material)
            || Tag.FENCE_GATES.isTagged(material)
            || Tag.BUTTONS.isTagged(material)
            || Tag.PRESSURE_PLATES.isTagged(material)
            || isAnvil(material);
    }

    /**
     * Whether clicking the block changes its state or contents.
     *
     * @param rightClick {@code true} for a right click, {@code false} for a left click
     */
    public static boolean isBlockModifiedOnClick(final Material material, final boolean rightClick) {
        if (!rightClick) {
            return LEFT_CLICK_MODIFIED.contains(material);
        }
        return RIGHT_CLICK_MODIFIED.contains(material)
            || isInventoryBlock(material)
            || Tag.WOODEN_DOORS.isTagged(material)
            || Tag.WOODEN_TRAPDOORS.isTagged(material)
            || Tag.FENCE_GATES.isTagged(material)
            || Tag.BUTTONS.isTagged(material)
            || Tag.CANDLE_CAKES.isTagged(material)
            || Tag.FLOWER_POTS.isTagged(material);
    }

    /**
     * Whether using {@code item} on {@code block} changes the block, such as bone meal, fire, a
     * bucket, dye, a spawn egg, or a tool that tills, strips or scrapes it.
     */
    public static boolean isItemAppliedToBlock(final Material item, final Material block) {
        return APPLIED_ITEMS.contains(item)
            || BUCKET_CONTENTS.containsKey(item)
            || isSpawnEgg(item)
            || item.name().endsWith("_DYE")
            || isToolApplicable(item, block);
    }

    /**
     * Blocks whose use counts as building: redstone components, food and growth blocks, pots.
     */
    public static boolean isConsideredBuildingIfUsed(final Material material) {
        return BUILDING_IF_USED.contains(material)
            || Tag.CANDLE_CAKES.isTagged(material)
            || Tag.FLOWER_POTS.isTagged(material);
    }

    /**
     * Whether any of the effects harms the entity it lands on.
     */
    public static boolean hasDamageEffect(final Collection<PotionEffect> effects) {
        for (final PotionEffect effect : effects) {
            if (isHarmful(effect.getType())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isArmor(final Material material) {
        return material == Material.ELYTRA
            || Tag.ITEMS_HEAD_ARMOR.isTagged(material)
            || Tag.ITEMS_CHEST_ARMOR.isTagged(material)
            || Tag.ITEMS_LEG_ARMOR.isTagged(material)
            || Tag.ITEMS_FOOT_ARMOR.isTagged(material);
    }

    /**
     * Whether {@code tool} changes {@code block} when used on it: a hoe tilling soil, a shovel making
     * a path or dousing a campfire, an axe stripping a log or scraping copper, shears on a pumpkin
     * or hive, or honeycomb waxing copper.
     */
    public static boolean isToolApplicable(final Material tool, final Material block) {
        if (Tag.ITEMS_HOES.isTagged(tool)) {
            return TILLABLE.contains(block);
        }
        if (Tag.ITEMS_SHOVELS.isTagged(tool)) {
            return PATHABLE.contains(block);
        }
        if (Tag.ITEMS_AXES.isTagged(tool)) {
            return Tag.LOGS.isTagged(block) || isWaxedCopper(block) || isUnwaxedCopper(block);
        }
        if (tool == Material.SHEARS) {
            return SHEARABLE.contains(block);
        }
        if (tool == Material.HONEYCOMB) {
            return isUnwaxedCopper(block);
        }
        return false;
    }

    public static boolean isFire(final Material material) {
        return Tag.FIRE.isTagged(material);
    }

    public static boolean isWaxedCopper(final Material material) {
        return WAXED_COPPER.contains(material);
    }

    /**
     * Copper blocks that have a waxed form.
     */
    public static boolean isUnwaxedCopper(final Material material) {
        return UNWAXED_COPPER.contains(material);
    }

    public static boolean isAmethystGrowth(final Material material) {
        return AMETHYST_GROWTH.contains(material);
    }

    public static boolean isSculkGrowth(final Material material) {
        return SCULK_GROWTH.contains(material);
    }

    private static boolean isHarmful(final PotionEffectType type) {
        return type == PotionEffectType.INSTANT_DAMAGE
            || type == PotionEffectType.POISON
            || type == PotionEffectType.WITHER
            || type == PotionEffectType.SLOWNESS
            || type == PotionEffectType.WEAKNESS
            || type == PotionEffectType.MINING_FATIGUE
            || type == PotionEffectType.NAUSEA
            || type == PotionEffectType.BLINDNESS
            || type == PotionEffectType.HUNGER
            || type == PotionEffectType.LEVITATION
            || type == PotionEffectType.DARKNESS
            || type == PotionEffectType.INFESTED
            || type == PotionEffectType.OOZING
            || type == PotionEffectType.WEAVING
            || type == PotionEffectType.WIND_CHARGED;
    }

    private static EntityType entityType(final String name) {
        for (final EntityType type : EntityType.values()) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        return null;
    }
}
