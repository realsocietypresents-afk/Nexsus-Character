package com.nexuscraft.nexuscharacter;

import java.util.List;
import java.util.Map;

/** A perk from config.yml: what it needs and what it gives. */
record PerkDef(String id, String name, String description, Map<String, Integer> requires, int minPrestige, int minTotal, List<Effect> effects) {
}
