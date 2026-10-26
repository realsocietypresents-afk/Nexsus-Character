package com.nexuscraft.nexuscharacter;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/** What the plugin remembers about each player: prestige stars, the last levels seen (so offline players still have a sheet) and the perks they hold. */
final class CharacterStore {

    /** One player's record. */
    static final class Rec {
        String name = "";
        int prestige;
        long prestigeAt;
        long seen;
        final Map<String, Integer> levels = new LinkedHashMap<>();
        final Set<String> perks = new LinkedHashSet<>();
    }

    private final File file;
    private final Logger log;
    private final Map<UUID, Rec> recs = new HashMap<>();
    private boolean dirty;

    CharacterStore(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    Rec rec(UUID id) {
        return recs.computeIfAbsent(id, k -> new Rec());
    }

    boolean known(UUID id) {
        return recs.containsKey(id);
    }

    Map<UUID, Rec> all() {
        return recs;
    }

    void touch() {
        dirty = true;
    }

    boolean dirty() {
        return dirty;
    }

    void load() {
        recs.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String key : y.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException notAPlayer) {
                continue;
            }
            Rec r = new Rec();
            r.name = y.getString(key + ".name", "");
            r.prestige = Math.max(0, y.getInt(key + ".prestige", 0));
            r.prestigeAt = y.getLong(key + ".prestige-at", 0L);
            r.seen = y.getLong(key + ".seen", 0L);
            for (String pair : y.getStringList(key + ".levels")) {
                int eq = pair.lastIndexOf('=');
                if (eq > 0) {
                    try {
                        r.levels.put(pair.substring(0, eq), Math.max(0, Integer.parseInt(pair.substring(eq + 1))));
                    } catch (NumberFormatException skip) {
                        // a damaged entry is dropped; it is rebuilt on the next refresh
                    }
                }
            }
            r.perks.addAll(y.getStringList(key + ".perks"));
            recs.put(id, r);
        }
        dirty = false;
    }

    void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Rec> e : recs.entrySet()) {
            String k = e.getKey().toString();
            Rec r = e.getValue();
            y.set(k + ".name", r.name);
            y.set(k + ".prestige", r.prestige);
            y.set(k + ".prestige-at", r.prestigeAt);
            y.set(k + ".seen", r.seen);
            List<String> levels = new ArrayList<>();
            for (Map.Entry<String, Integer> l : r.levels.entrySet()) {
                levels.add(l.getKey() + "=" + l.getValue());
            }
            y.set(k + ".levels", levels);
            y.set(k + ".perks", new ArrayList<>(r.perks));
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("cannot create " + parent);
            }
            File tmp = new File(file.getPath() + ".tmp");
            y.save(tmp);
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
        } catch (IOException e) {
            log.warning("Could not save " + file.getName() + ": " + e.getMessage());
        }
    }
}
