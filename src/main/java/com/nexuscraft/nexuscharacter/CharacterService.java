package com.nexuscraft.nexuscharacter;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Reads every skill, decides which perks each player holds, and keeps the effects on them up to date. */
final class CharacterService {

    /** One line of the sheet. {@code live} is false when the level is the last one seen (the player is offline). */
    record Row(SkillDef def, int level, int max, boolean live) {
    }

    /** A player's whole sheet at one moment. */
    record Snapshot(List<Row> rows, int total, int prestige, List<PerkEngine.Status> perks) {

        int active() {
            int n = 0;
            for (PerkEngine.Status s : perks) {
                if (s.active()) {
                    n++;
                }
            }
            return n;
        }
    }

    /** A line of the leaderboard. */
    record Top(UUID id, String name, int prestige, int total) {
    }

    private final JavaPlugin plugin;
    private CharacterConfig config;
    private final SkillRegistry registry;
    private final CharacterStore store;
    private final PerkApplier applier;
    private final LongSupplier clock;
    private final Map<UUID, Double> xpBonus = new HashMap<>();
    private final Map<UUID, Double> xpCarry = new HashMap<>();

    CharacterService(JavaPlugin plugin, CharacterConfig config, SkillRegistry registry, CharacterStore store, PerkApplier applier, LongSupplier clock) {
        this.plugin = plugin;
        this.config = config;
        this.registry = registry;
        this.store = store;
        this.applier = applier;
        this.clock = clock;
    }

    CharacterConfig config() {
        return config;
    }

    void setConfig(CharacterConfig config) {
        this.config = config;
    }

    SkillRegistry registry() {
        return registry;
    }

    CharacterStore store() {
        return store;
    }

    long now() {
        return clock.getAsLong();
    }

    int prestigeOf(UUID id) {
        CharacterStore.Rec r = store.all().get(id);
        return r == null ? 0 : r.prestige;
    }

    /** How much bigger attribute and experience perks are for this many stars. */
    double scale(int prestige) {
        return 1.0 + prestige * config.strengthPerStar;
    }

    /** The player's sheet now: live levels when they are online, the last ones seen otherwise. */
    Snapshot snapshot(UUID id) {
        CharacterStore.Rec rec = store.all().get(id);
        boolean online = Bukkit.getPlayer(id) != null;
        List<Row> rows = new ArrayList<>();
        Map<String, Integer> known = new LinkedHashMap<>();
        int total = 0;
        for (SkillDef d : registry.available()) {
            int max = registry.max(d);
            int lv = registry.read(id, d);
            boolean live = lv >= 0;
            if (!live) {
                Integer cached = rec == null ? null : rec.levels.get(d.id());
                lv = cached == null ? 0 : Math.min(cached, max);
                if (!online || cached != null) {
                    known.put(d.id(), lv);
                }
            } else {
                known.put(d.id(), lv);
            }
            total += lv;
            rows.add(new Row(d, lv, max, live));
        }
        int prestige = rec == null ? 0 : rec.prestige;
        return new Snapshot(rows, total, prestige, PerkEngine.evaluate(config.perks.values(), known, total, prestige));
    }

    /** Brings one online player up to date: remembers their levels, works out their perks, tells them what changed and applies the effects. */
    void refresh(Player p) {
        if (!config.enabled) {
            return;
        }
        UUID id = p.getUniqueId();
        CharacterStore.Rec rec = store.rec(id);
        boolean changed = false;
        if (!p.getName().equals(rec.name)) {
            rec.name = p.getName();
            changed = true;
        }
        rec.seen = clock.getAsLong();
        Snapshot s = snapshot(id);
        for (Row r : s.rows()) {
            if (r.live() && !Integer.valueOf(r.level()).equals(rec.levels.get(r.def().id()))) {
                rec.levels.put(r.def().id(), r.level());
                changed = true;
            }
        }
        if (config.perksEnabled) {
            changed |= refreshPerks(p, rec, s);
        } else {
            applier.clear(p);
            xpBonus.remove(id);
        }
        if (changed) {
            store.touch();
        }
    }

    private boolean refreshPerks(Player p, CharacterStore.Rec rec, Snapshot s) {
        List<PerkDef> active = new ArrayList<>();
        Set<String> activeIds = new LinkedHashSet<>();
        Set<String> unknownIds = new HashSet<>();
        for (PerkEngine.Status st : s.perks()) {
            if (st.active()) {
                active.add(st.perk());
                activeIds.add(st.perk().id());
            } else if (st.unknown()) {
                unknownIds.add(st.perk().id());
            }
        }
        Set<String> next = new LinkedHashSet<>(activeIds);
        for (String held : rec.perks) {
            if (unknownIds.contains(held)) {
                next.add(held);
            }
        }
        boolean changed = !next.equals(rec.perks);
        if (changed && config.messages) {
            for (String id : next) {
                if (!rec.perks.contains(id)) {
                    PerkDef d = config.perks.get(id);
                    p.sendMessage("§6[Sheet] §aPerk unlocked: §f" + d.name() + (d.description().isEmpty() ? "" : " §7- " + d.description()));
                }
            }
            for (String id : rec.perks) {
                PerkDef d = config.perks.get(id);
                if (d != null && !next.contains(id)) {
                    p.sendMessage("§6[Sheet] §cPerk lost: §f" + d.name() + " §7(a skill it needs dropped)");
                }
            }
        }
        rec.perks.clear();
        rec.perks.addAll(next);
        double scale = scale(rec.prestige);
        applier.apply(p, active, scale);
        double pct = 0;
        for (PerkDef d : active) {
            for (Effect e : d.effects()) {
                if (e instanceof Effect.Xp x) {
                    pct += x.percent();
                }
            }
        }
        if (pct > 0) {
            xpBonus.put(p.getUniqueId(), pct * scale);
        } else {
            xpBonus.remove(p.getUniqueId());
        }
        return changed;
    }

    /** Orb experience after the player's experience perks, carrying fractions so small orbs still add up. */
    int boostXp(UUID id, int amount) {
        double pct = xpBonus.getOrDefault(id, 0.0);
        if (pct <= 0 || amount <= 0) {
            return amount;
        }
        double carry = xpCarry.getOrDefault(id, 0.0) + amount * pct / 100.0;
        int extra = (int) Math.floor(carry + 1e-9);
        xpCarry.put(id, carry - extra);
        return amount + extra;
    }

    void onJoin(Player p) {
        applier.scrub(p);
        refresh(p);
    }

    void onQuit(Player p) {
        refresh(p);
        applier.clear(p);
        xpBonus.remove(p.getUniqueId());
        xpCarry.remove(p.getUniqueId());
    }

    void tick() {
        for (Player p : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            refresh(p);
        }
    }

    /** Takes every effect off everyone (shutdown, or perks switched off). */
    void clearAll() {
        for (Player p : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            applier.clear(p);
        }
        xpBonus.clear();
    }

    void saveIfDirty() {
        if (store.dirty()) {
            store.save();
        }
    }

    /** The leaderboard: most prestige first, then the highest total of current levels. */
    List<Top> top(int n) {
        Set<String> ids = new HashSet<>();
        for (SkillDef d : registry.available()) {
            ids.add(d.id());
        }
        List<Top> out = new ArrayList<>();
        for (Map.Entry<UUID, CharacterStore.Rec> e : store.all().entrySet()) {
            int total = 0;
            for (Map.Entry<String, Integer> l : e.getValue().levels.entrySet()) {
                if (ids.contains(l.getKey())) {
                    total += l.getValue();
                }
            }
            String name = e.getValue().name.isEmpty() ? "?" : e.getValue().name;
            out.add(new Top(e.getKey(), name, e.getValue().prestige, total));
        }
        out.sort(Comparator.comparingInt(Top::prestige).thenComparingInt(Top::total).reversed().thenComparing(Top::name));
        return out.size() > n ? new ArrayList<>(out.subList(0, n)) : out;
    }

    JavaPlugin plugin() {
        return plugin;
    }
}
