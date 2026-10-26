package com.nexuscraft.nexuscharacter;

import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/**
 * The shared skill registry, published on Bukkit's ServicesManager. A plugin that has a skill registers it once; NexusCharacter then shows
 * it on the sheet, counts it towards perks and prestige, and (if a reset is given) can wipe it when a player prestiges.
 *
 * <p>Plugins that do not want a compile-time dependency can call {@link #register} by reflection: see {@code adapters/CharacterHook.java}
 * in the NexusCharacter download for a drop-in class that does exactly that.
 */
public interface CharacterApi {

    /**
     * Adds (or replaces, for the same owner) a skill.
     *
     * @param owner    the plugin that owns the skill
     * @param id       short lowercase id such as {@code mining} (letters, digits, '_' and '-', up to 32)
     * @param name     what the sheet calls it
     * @param maxLevel the top level
     * @param level    the player's level: 0 = not started, -1 = cannot be read right now (for example an offline player)
     * @param reset    wipes the player's progress (only called for an online player, when they prestige), or null if it cannot be reset
     * @return false if the details are unusable, or another plugin already owns that id
     */
    boolean register(Plugin owner, String id, String name, int maxLevel, ToIntFunction<UUID> level, Consumer<UUID> reset);

    /** Removes every skill this plugin registered. */
    void unregisterAll(Plugin owner);

    /** Ids of the skills on the sheet right now. */
    Set<String> skills();

    /** The player's level in that skill: 0 if not started or unknown. Offline players give their last known level. */
    int level(UUID player, String skill);

    /** All of the player's levels added together. */
    int totalLevel(UUID player);

    /** How many prestige stars the player has. */
    int prestige(UUID player);

    /** True if the player has earned that perk (ids come from config.yml). */
    boolean hasPerk(UUID player, String perk);

    /** Ids of every perk the player has earned. */
    Set<String> perks(UUID player);
}
