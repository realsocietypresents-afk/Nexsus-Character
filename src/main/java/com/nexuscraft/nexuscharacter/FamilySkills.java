package com.nexuscraft.nexuscharacter;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Reads the Nexus skill plugins without changing them. They all keep a player's skill XP as one number on the player ({@code nexuslode:lode_xp}
 * and so on) and turn it into a level with the same curve, {@code xp-base * level^1.5}, so the level is worked out here from the XP and from
 * the other plugin's own {@code skill.max-level} / {@code skill.xp-base}.
 */
final class FamilySkills {

    static final String WIZARD_API = "com.nexusuniverse.magic.WizardLevelAPI";

    private FamilySkills() {
    }

    /** XP needed to reach {@code level} (the family curve). */
    static long xpForLevel(int level, double base) {
        return Math.round(base * Math.pow(level, 1.5));
    }

    /** The level (1..max) that this much XP reaches. */
    static int levelFor(long xp, int max, double base) {
        int level = 1;
        while (level < max && xp >= xpForLevel(level + 1, base)) {
            level++;
        }
        return level;
    }

    static List<SkillDef> build(List<CharacterConfig.SkillCfg> cfgs, Logger log) {
        List<SkillDef> out = new ArrayList<>();
        for (CharacterConfig.SkillCfg c : cfgs) {
            SkillDef d = "wizard-service".equals(c.type()) ? wizard(c) : xp(c, log);
            if (d != null) {
                out.add(d);
            }
        }
        return out;
    }

    private static boolean running(String plugin) {
        Plugin p = Bukkit.getPluginManager().getPlugin(plugin);
        return p != null && p.isEnabled();
    }

    private static SkillDef xp(CharacterConfig.SkillCfg c, Logger log) {
        NamespacedKey key = NamespacedKey.fromString(c.xpKey());
        if (key == null) {
            log.warning("skills." + c.id() + ".xp-key '" + c.xpKey() + "' is not a valid key (like nexuslode:lode_xp); skipped.");
            return null;
        }
        return new SkillDef(c.id(), c.name(),
                () -> limits(c)[0] < 1 ? 1 : (int) limits(c)[0],
                id -> {
                    Player p = Bukkit.getPlayer(id);
                    if (p == null) {
                        return -1;
                    }
                    Long xp = p.getPersistentDataContainer().get(key, PersistentDataType.LONG);
                    if (xp == null || xp <= 0) {
                        return 0;
                    }
                    double[] l = limits(c);
                    return levelFor(xp, (int) l[0], l[1]);
                },
                c.reset() ? id -> {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) {
                        p.getPersistentDataContainer().remove(key);
                    }
                } : null,
                () -> running(c.plugin()), c.plugin());
    }

    /** {max level, xp base}: the other plugin's own live settings, or ours if it cannot be asked. */
    private static double[] limits(CharacterConfig.SkillCfg c) {
        int max = c.maxLevel();
        double base = c.xpBase();
        Plugin p = Bukkit.getPluginManager().getPlugin(c.plugin());
        if (p != null) {
            FileConfiguration f = p.getConfig();
            max = Math.max(1, f.getInt("skill.max-level", max));
            base = Math.max(1.0, f.getDouble("skill.xp-base", base));
        }
        return new double[]{max, base};
    }

    /** NexusMagic publishes its wizard level as a service; this asks it by reflection (no reset: wizard levels belong to NexusMagic). */
    private static SkillDef wizard(CharacterConfig.SkillCfg c) {
        return new SkillDef(c.id(), c.name(),
                () -> {
                    Object max = ask(c.plugin(), "maxLevel", new Class<?>[0]);
                    return max instanceof Integer i ? Math.max(1, i) : c.maxLevel();
                },
                id -> {
                    if (Bukkit.getPlayer(id) == null) {
                        return -1;
                    }
                    Object level = ask(c.plugin(), "level", new Class<?>[]{UUID.class}, id);
                    return level instanceof Integer i ? i : -1;
                },
                null, () -> running(c.plugin()), c.plugin());
    }

    /** Calls a method of NexusMagic's WizardLevelAPI service; null if it is not there or fails. */
    private static Object ask(String pluginName, String method, Class<?>[] types, Object... args) {
        Plugin p = Bukkit.getPluginManager().getPlugin(pluginName);
        if (p == null) {
            return null;
        }
        try {
            Class<?> type = Class.forName(WIZARD_API, true, p.getClass().getClassLoader());
            Object api = Bukkit.getServicesManager().load(type);
            return api == null ? null : type.getMethod(method, types).invoke(api, args);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError absent) {
            return null;
        }
    }
}
