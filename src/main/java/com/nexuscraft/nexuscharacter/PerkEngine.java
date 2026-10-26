package com.nexuscraft.nexuscharacter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Works out which perks a player holds. Pure arithmetic: no server needed. */
final class PerkEngine {

    /** One missing requirement: the skill, what it needs, what the player has. */
    record Gap(String skill, int need, int have) {
    }

    /**
     * @param unknown a required skill is not installed or not readable, so this perk can be neither granted nor taken away right now
     * @param gaps    skill levels still short
     */
    record Status(PerkDef perk, boolean active, boolean unknown, List<Gap> gaps, boolean prestigeShort, boolean totalShort) {

        /** 0 when active; otherwise roughly how far off, for suggesting the nearest one. */
        double distance() {
            double d = 0;
            for (Gap g : gaps) {
                d += (g.need() - g.have()) / (double) g.need();
            }
            return d + (prestigeShort ? 1.0 : 0) + (totalShort ? 0.5 : 0);
        }
    }

    private PerkEngine() {
    }

    /** @param levels skill id -> level, only for skills that exist right now */
    static List<Status> evaluate(Iterable<PerkDef> perks, Map<String, Integer> levels, int total, int prestige) {
        List<Status> out = new ArrayList<>();
        for (PerkDef p : perks) {
            boolean unknown = false;
            List<Gap> gaps = new ArrayList<>();
            for (Map.Entry<String, Integer> need : p.requires().entrySet()) {
                Integer have = levels.get(need.getKey());
                if (have == null) {
                    unknown = true;
                } else if (have < need.getValue()) {
                    gaps.add(new Gap(need.getKey(), need.getValue(), have));
                }
            }
            boolean prestigeShort = prestige < p.minPrestige();
            boolean totalShort = total < p.minTotal();
            boolean active = !unknown && gaps.isEmpty() && !prestigeShort && !totalShort;
            out.add(new Status(p, active, unknown && gaps.isEmpty() && !prestigeShort && !totalShort, gaps, prestigeShort, totalShort));
        }
        return out;
    }
}
