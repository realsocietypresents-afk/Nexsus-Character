package com.nexuscraft.nexuscharacter;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Every setting, read once from config.yml. A reload builds a new one. */
final class CharacterConfig {

    /** One skill the sheet reads straight from a family plugin. */
    record SkillCfg(String id, String plugin, String type, String name, String xpKey, int maxLevel, double xpBase, boolean reset) {
    }

    final boolean enabled;
    final boolean messages;
    final int refreshSeconds;
    final int topSize;
    final boolean perksEnabled;
    final boolean prestigeEnabled;
    final int maxStars;
    final double masteryFraction;
    final int skillsNeeded;
    final int skillsNeededStep;
    final double strengthPerStar;
    final int confirmSeconds;
    final boolean broadcast;
    final List<String> rewardCommands;
    final List<SkillCfg> skills = new ArrayList<>();
    final Map<String, PerkDef> perks = new LinkedHashMap<>();
    /** Things in config.yml that could not be used, for the log and /sheet reload. */
    final List<String> problems = new ArrayList<>();

    CharacterConfig(FileConfiguration c) {
        this.enabled = c.getBoolean("enabled", true);
        this.messages = c.getBoolean("messages", true);
        this.refreshSeconds = Math.max(1, Math.min(60, c.getInt("refresh-seconds", 5)));
        this.topSize = Math.max(1, Math.min(25, c.getInt("top-size", 10)));
        this.perksEnabled = c.getBoolean("perks-enabled", true);
        this.prestigeEnabled = c.getBoolean("prestige.enabled", true);
        this.maxStars = Math.max(1, Math.min(10, c.getInt("prestige.max-stars", 5)));
        this.masteryFraction = Math.max(0.1, Math.min(1.0, c.getDouble("prestige.mastery-fraction", 0.8)));
        this.skillsNeeded = Math.max(1, c.getInt("prestige.skills-needed", 3));
        this.skillsNeededStep = Math.max(0, c.getInt("prestige.skills-needed-step", 1));
        this.strengthPerStar = Math.max(0.0, Math.min(2.0, c.getDouble("prestige.strength-per-star", 0.1)));
        this.confirmSeconds = Math.max(5, Math.min(300, c.getInt("prestige.confirm-seconds", 30)));
        this.broadcast = c.getBoolean("prestige.broadcast", true);
        this.rewardCommands = new ArrayList<>(c.getStringList("prestige.reward-commands"));
        readSkills(c);
        readPerks(c);
    }

    private void readSkills(FileConfiguration c) {
        ConfigurationSection all = c.getConfigurationSection("skills");
        if (all == null) {
            return;
        }
        for (String id : all.getKeys(false)) {
            ConfigurationSection s = c.getConfigurationSection("skills." + id);
            String key = id.toLowerCase(Locale.ROOT);
            if (s == null || !key.matches("[a-z0-9_-]{1,32}")) {
                problems.add("skills." + id + " is not usable (ids are lowercase letters, digits, - and _)");
                continue;
            }
            if (!s.getBoolean("enabled", true)) {
                continue;
            }
            String plugin = s.getString("plugin", "");
            String type = s.getString("type", "xp").toLowerCase(Locale.ROOT);
            if (plugin.isBlank()) {
                problems.add("skills." + id + " has no plugin: name");
                continue;
            }
            if (!type.equals("xp") && !type.equals("wizard-service")) {
                problems.add("skills." + id + ".type must be xp or wizard-service");
                continue;
            }
            String xpKey = s.getString("xp-key", "");
            if (type.equals("xp") && xpKey.isBlank()) {
                problems.add("skills." + id + " needs an xp-key like nexuslode:lode_xp");
                continue;
            }
            skills.add(new SkillCfg(key, plugin, type, s.getString("name", id), xpKey, Math.max(1, s.getInt("max-level", 50)),
                    Math.max(1.0, s.getDouble("xp-base", 10.0)), s.getBoolean("reset", true)));
        }
    }

    private void readPerks(FileConfiguration c) {
        ConfigurationSection all = c.getConfigurationSection("perks");
        if (all == null) {
            return;
        }
        for (String id : all.getKeys(false)) {
            ConfigurationSection s = c.getConfigurationSection("perks." + id);
            String key = id.toLowerCase(Locale.ROOT);
            if (s == null || !key.matches("[a-z0-9_-]{1,40}")) {
                problems.add("perks." + id + " is not usable (ids are lowercase letters, digits, - and _)");
                continue;
            }
            Map<String, Integer> requires = new LinkedHashMap<>();
            ConfigurationSection req = c.getConfigurationSection("perks." + id + ".requires");
            if (req != null) {
                for (String skill : req.getKeys(false)) {
                    requires.put(skill.toLowerCase(Locale.ROOT), Math.max(1, req.getInt(skill, 1)));
                }
            }
            int minPrestige = Math.max(0, s.getInt("min-prestige", 0));
            int minTotal = Math.max(0, s.getInt("min-total", 0));
            if (requires.isEmpty() && minPrestige == 0 && minTotal == 0) {
                problems.add("perks." + id + " asks for nothing (add requires:, min-prestige: or min-total:), so everyone would have it");
                continue;
            }
            List<Effect> effects = new ArrayList<>();
            for (String line : s.getStringList("effects")) {
                try {
                    effects.add(Effect.parse(line));
                } catch (IllegalArgumentException e) {
                    problems.add("perks." + id + ": " + e.getMessage());
                }
            }
            if (effects.isEmpty()) {
                problems.add("perks." + id + " has no usable effects; skipped");
                continue;
            }
            perks.put(key, new PerkDef(key, s.getString("name", id), s.getString("description", ""), requires, minPrestige, minTotal,
                    List.copyOf(effects)));
        }
    }
}
