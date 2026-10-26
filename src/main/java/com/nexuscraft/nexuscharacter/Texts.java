package com.nexuscraft.nexuscharacter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Everything the plugin says. */
final class Texts {

    private Texts() {
    }

    /** Filled and empty stars: {@code ★★☆☆☆}. */
    static String stars(int have, int max) {
        StringBuilder s = new StringBuilder("§e");
        for (int i = 0; i < max; i++) {
            if (i == have) {
                s.append("§8");
            }
            s.append(i < have ? '★' : '☆');
        }
        return s.toString();
    }

    /** A level bar like {@code [#####-----]}. */
    static String bar(int level, int max, int width) {
        int filled = max <= 0 ? 0 : (int) Math.round(Math.max(0.0, Math.min(1.0, level / (double) max)) * width);
        return "§8[§a" + "#".repeat(filled) + "§8" + "-".repeat(width - filled) + "]";
    }

    /** The main sheet. */
    static List<String> sheet(String name, CharacterService.Snapshot s, CharacterConfig c, boolean offline) {
        List<String> out = new ArrayList<>();
        out.add("§6§m     §r §6§l" + name + "'s Character Sheet §6§m     ");
        out.add("§7Prestige " + stars(s.prestige(), c.maxStars) + " §8| §7Total level §f" + s.total()
                + (c.perksEnabled ? " §8| §7Perks §a" + s.active() + "§7/" + s.perks().size() : ""));
        if (s.rows().isEmpty()) {
            out.add("§7No skills are installed yet. Skills appear here as the skill plugins load.");
            return out;
        }
        out.add("§e§lSkills" + (offline ? " §8(last seen)" : ""));
        List<String> untouched = new ArrayList<>();
        for (CharacterService.Row r : s.rows()) {
            if (r.level() <= 0) {
                if (!r.live() && !offline) {
                    out.add(" §f" + r.def().name() + " §8(unreadable right now)");
                } else {
                    untouched.add(r.def().name());
                }
                continue;
            }
            boolean top = r.level() >= r.max();
            out.add(" §f" + r.def().name() + " §a" + r.level() + "§7/" + r.max() + " " + bar(r.level(), r.max(), 10)
                    + (top ? " §6MAX" : "") + (!r.live() && !offline ? " §8(unreadable)" : ""));
        }
        if (!untouched.isEmpty()) {
            out.add(" §8Not started: " + String.join(", ", untouched));
        }
        if (c.perksEnabled && !s.perks().isEmpty()) {
            out.add("§e§lPerks §8- §7/sheet perks shows them all");
            double scale = 1.0 + s.prestige() * c.strengthPerStar;
            int shown = 0;
            for (PerkEngine.Status st : s.perks()) {
                if (st.active() && shown < 5) {
                    out.add(" §a✔ §f" + st.perk().name() + " §7" + effects(st.perk(), scale));
                    shown++;
                }
            }
            if (s.active() > shown) {
                out.add(" §8…and " + (s.active() - shown) + " more");
            }
            if (s.active() == 0) {
                out.add(" §8None yet. Level two skills together to unlock one.");
            }
            Map<String, String> names = names(s);
            List<PerkEngine.Status> next = closest(s, 2);
            for (PerkEngine.Status st : next) {
                out.add(" §7Next: §f" + st.perk().name() + " §7needs " + needs(st, names));
            }
        }
        return out;
    }

    /** Every perk, held or not. */
    static List<String> perks(String name, CharacterService.Snapshot s, CharacterConfig c) {
        List<String> out = new ArrayList<>();
        out.add("§6[Sheet] §f" + name + "'s perks §7(" + s.active() + " of " + s.perks().size() + ")");
        if (!c.perksEnabled) {
            out.add("§7Perks are switched off on this server.");
            return out;
        }
        double scale = 1.0 + s.prestige() * c.strengthPerStar;
        Map<String, String> names = names(s);
        for (PerkEngine.Status st : s.perks()) {
            PerkDef p = st.perk();
            if (st.active()) {
                out.add(" §a✔ §f" + p.name() + " §7" + effects(p, scale));
            } else if (st.unknown()) {
                out.add(" §8? " + p.name() + " §8needs a skill that is not installed: " + missing(p, names));
            } else {
                out.add(" §8✘ §7" + p.name() + " §8needs " + needs(st, names));
            }
            if (!p.description().isEmpty()) {
                out.add("     §8" + p.description());
            }
        }
        return out;
    }

    static String effects(PerkDef p, double scale) {
        List<String> parts = new ArrayList<>();
        for (Effect e : p.effects()) {
            parts.add(e.describe(scale));
        }
        return "(" + String.join(", ", parts) + ")";
    }

    /** The unmet requirements, like {@code Mining 15 (you: 12), Cooking 15 (you: 3)}. */
    static String needs(PerkEngine.Status st, Map<String, String> names) {
        List<String> parts = new ArrayList<>();
        for (PerkEngine.Gap g : st.gaps()) {
            parts.add(names.getOrDefault(g.skill(), g.skill()) + " " + g.need() + " (you: " + g.have() + ")");
        }
        if (st.prestigeShort()) {
            parts.add("Prestige " + st.perk().minPrestige());
        }
        if (st.totalShort()) {
            parts.add("total level " + st.perk().minTotal());
        }
        return String.join(", ", parts);
    }

    private static String missing(PerkDef p, Map<String, String> names) {
        List<String> parts = new ArrayList<>();
        for (String id : p.requires().keySet()) {
            if (!names.containsKey(id)) {
                parts.add(id);
            }
        }
        return String.join(", ", parts);
    }

    private static Map<String, String> names(CharacterService.Snapshot s) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        for (CharacterService.Row r : s.rows()) {
            out.put(r.def().id(), r.def().name());
        }
        return out;
    }

    /** The locked perks nearest to being earned (not ones that need a missing plugin, nor prestige-only ones). */
    static List<PerkEngine.Status> closest(CharacterService.Snapshot s, int n) {
        List<PerkEngine.Status> pool = new ArrayList<>();
        for (PerkEngine.Status st : s.perks()) {
            if (!st.active() && !st.unknown() && !st.gaps().isEmpty() && !st.prestigeShort()) {
                pool.add(st);
            }
        }
        pool.sort(java.util.Comparator.comparingDouble(PerkEngine.Status::distance));
        return pool.size() > n ? new ArrayList<>(pool.subList(0, n)) : pool;
    }

    static List<String> top(List<CharacterService.Top> top, CharacterConfig c) {
        List<String> out = new ArrayList<>();
        out.add("§6[Sheet] §fTop characters §7(prestige first, then total level)");
        if (top.isEmpty()) {
            out.add("§7Nobody has a sheet yet.");
        }
        int i = 1;
        for (CharacterService.Top t : top) {
            out.add(" §7" + i++ + ". §f" + t.name() + " " + (t.prestige() > 0 ? stars(t.prestige(), c.maxStars) + " " : "") + "§7total §f" + t.total());
        }
        return out;
    }

    /** The prestige page: what it asks, what it costs, what it gives. */
    static List<String> prestige(CharacterService service, Prestige pr, org.bukkit.entity.Player p) {
        CharacterConfig c = service.config();
        Prestige.Check ch = pr.check(p);
        int stars = ch.stars();
        List<String> out = new ArrayList<>();
        out.add("§6[Sheet] §ePrestige " + stars(stars, c.maxStars) + " §7(" + stars + " of " + c.maxStars + ")");
        if (!c.prestigeEnabled || stars >= c.maxStars || ch.needed() == 0) {
            out.add("§7" + ch.reason());
            return out;
        }
        out.add("§7The next star asks you to master §f" + ch.needed() + " §7skills (§f" + Math.round(c.masteryFraction * 100) + "% §7of the top level).");
        for (SkillDef d : service.registry().available()) {
            if (!d.resettable()) {
                continue;
            }
            int lv = service.registry().read(p.getUniqueId(), d);
            int line = pr.masteryLine(d);
            boolean ok = lv >= line;
            out.add(" " + (ok ? "§a✔ " : "§8✘ ") + "§f" + d.name() + " §7" + Math.max(lv, 0) + "§7/" + line + (ok ? " §7(will reset to 0)" : ""));
        }
        double now = stars * c.strengthPerStar * 100;
        double then = (stars + 1) * c.strengthPerStar * 100;
        out.add("§7A star makes your attribute and experience perks stronger: §f+" + Math.round(now) + "% §7now, §a+" + Math.round(then) + "% §7after.");
        if (ch.ok()) {
            out.add("§eYou can prestige now. §7Every skill marked above goes back to level 0. Type §f/sheet prestige confirm §7within "
                    + c.confirmSeconds + " seconds.");
        } else {
            out.add("§7" + ch.reason());
        }
        return out;
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
