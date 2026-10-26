package com.nexuscraft.nexuscharacter;

import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/** Every skill the sheet knows: the ones read straight from the family plugins plus the ones plugins register. A registration wins over a family skill of the same id. */
final class SkillRegistry {

    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,32}");

    private final Logger log;
    private final Map<String, SkillDef> family = new LinkedHashMap<>();
    private final Map<String, SkillDef> registered = new LinkedHashMap<>();
    private final Map<String, String> owners = new HashMap<>();
    private final Map<String, Plugin> ownerPlugins = new HashMap<>();
    private final Set<String> complained = new HashSet<>();

    SkillRegistry(Logger log) {
        this.log = log;
    }

    void setFamily(List<SkillDef> defs) {
        family.clear();
        for (SkillDef d : defs) {
            family.put(d.id(), d);
        }
    }

    boolean register(Plugin owner, String id, String name, int maxLevel, ToIntFunction<UUID> level, Consumer<UUID> reset) {
        if (owner == null || id == null || name == null || level == null || maxLevel < 1) {
            return false;
        }
        String key = id.toLowerCase(Locale.ROOT);
        if (!ID.matcher(key).matches() || name.isBlank()) {
            return false;
        }
        Plugin current = ownerPlugins.get(key);
        if (current != null && current != owner && current.isEnabled()) {
            return false;
        }
        registered.put(key, new SkillDef(key, name.strip(), () -> maxLevel, level, reset, owner::isEnabled, owner.getName()));
        owners.put(key, owner.getName());
        ownerPlugins.put(key, owner);
        return true;
    }

    void unregisterAll(Plugin owner) {
        List<String> gone = new ArrayList<>();
        for (Map.Entry<String, Plugin> e : ownerPlugins.entrySet()) {
            if (e.getValue() == owner) {
                gone.add(e.getKey());
            }
        }
        for (String id : gone) {
            registered.remove(id);
            owners.remove(id);
            ownerPlugins.remove(id);
        }
    }

    /** Family skills (config order) then registered ones, a registration replacing the family skill of the same id; only those whose plugin is running. */
    List<SkillDef> available() {
        Map<String, SkillDef> merged = new LinkedHashMap<>(family);
        merged.putAll(registered);
        List<SkillDef> out = new ArrayList<>();
        for (SkillDef d : merged.values()) {
            if (isAvailable(d)) {
                out.add(d);
            }
        }
        return out;
    }

    private boolean isAvailable(SkillDef d) {
        try {
            return d.available().getAsBoolean();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    int max(SkillDef d) {
        try {
            return Math.max(1, d.maxLevel().getAsInt());
        } catch (RuntimeException | LinkageError e) {
            complain(d, e);
            return 1;
        }
    }

    /** The player's level (0..max), or -1 if it cannot be read right now. */
    int read(UUID player, SkillDef d) {
        try {
            int v = d.level().applyAsInt(player);
            return v < 0 ? -1 : Math.min(v, max(d));
        } catch (RuntimeException | LinkageError e) {
            complain(d, e);
            return -1;
        }
    }

    /** Wipes the skill; false if it cannot be wiped. */
    boolean reset(UUID player, SkillDef d) {
        if (d == null || d.reset() == null) {
            return false;
        }
        try {
            d.reset().accept(player);
            return true;
        } catch (RuntimeException | LinkageError e) {
            complain(d, e);
            return false;
        }
    }

    private void complain(SkillDef d, Throwable e) {
        if (complained.add(d.id())) {
            log.warning("Skill '" + d.id() + "' (" + d.source() + ") failed: " + e + " -- it will read as unavailable until it works again.");
        }
    }
}
