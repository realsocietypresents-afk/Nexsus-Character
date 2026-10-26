package com.nexuscraft.nexuscharacter;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Puts the effects of a player's perks on them, and takes them off again. Attribute bonuses carry a key of ours so they can always be told apart. */
final class PerkApplier {

    /** How long one dose of a potion perk lasts; it is topped up well before it runs out, so it never flashes. */
    static final int POTION_TICKS = 400;
    private static final int TOP_UP_BELOW = 300;
    private static final String[] COMMON = {"MAX_HEALTH", "MOVEMENT_SPEED", "ARMOR", "ATTACK_DAMAGE", "LUCK", "KNOCKBACK_RESISTANCE", "ATTACK_SPEED"};

    private static final class Applied {
        final Map<NamespacedKey, Attribute> mods = new HashMap<>();
        final Set<PotionEffectType> potions = new HashSet<>();
    }

    private final Plugin plugin;
    private final String namespace;
    private final Map<UUID, Applied> applied = new HashMap<>();

    PerkApplier(Plugin plugin) {
        this.plugin = plugin;
        this.namespace = new NamespacedKey(plugin, "perk").getNamespace();
    }

    /** Makes the player carry exactly these perks, with attribute sizes multiplied by {@code scale}. */
    void apply(Player p, List<PerkDef> active, double scale) {
        Applied st = applied.computeIfAbsent(p.getUniqueId(), k -> new Applied());
        Map<NamespacedKey, Effect.Attr> wantMods = new LinkedHashMap<>();
        Map<PotionEffectType, Integer> wantPots = new LinkedHashMap<>();
        for (PerkDef perk : active) {
            for (int i = 0; i < perk.effects().size(); i++) {
                Effect e = perk.effects().get(i);
                if (e instanceof Effect.Attr a) {
                    wantMods.put(new NamespacedKey(plugin, "perk_" + perk.id() + "_" + i), a);
                } else if (e instanceof Effect.Potion po) {
                    wantPots.merge(po.type(), po.amplifier(), Math::max);
                }
            }
        }
        for (Map.Entry<NamespacedKey, Attribute> e : new ArrayList<>(st.mods.entrySet())) {
            Effect.Attr want = wantMods.get(e.getKey());
            if (want == null || want.attribute() != e.getValue()) {
                removeModifier(p, e.getValue(), e.getKey());
                st.mods.remove(e.getKey());
            }
        }
        for (Map.Entry<NamespacedKey, Effect.Attr> e : wantMods.entrySet()) {
            Effect.Attr a = e.getValue();
            AttributeInstance inst = p.getAttribute(a.attribute());
            if (inst == null) {
                continue;
            }
            double amount = a.amount() * scale;
            AttributeModifier have = find(inst, e.getKey());
            if (have == null || have.getAmount() != amount || have.getOperation() != a.op()) {
                if (have != null) {
                    inst.removeModifier(have);
                }
                inst.addModifier(new AttributeModifier(e.getKey(), amount, a.op(), EquipmentSlotGroup.ANY));
            }
            st.mods.put(e.getKey(), a.attribute());
        }
        for (PotionEffectType t : new ArrayList<>(st.potions)) {
            if (!wantPots.containsKey(t)) {
                dropPotion(p, t);
                st.potions.remove(t);
            }
        }
        for (Map.Entry<PotionEffectType, Integer> e : wantPots.entrySet()) {
            PotionEffect cur = p.getPotionEffect(e.getKey());
            if (cur == null || cur.getDuration() < TOP_UP_BELOW || cur.getAmplifier() < e.getValue()) {
                p.addPotionEffect(new PotionEffect(e.getKey(), POTION_TICKS, e.getValue(), true, false));
            }
            st.potions.add(e.getKey());
        }
    }

    /** Takes everything of ours off the player (they left, or perks are off). */
    void clear(Player p) {
        Applied st = applied.remove(p.getUniqueId());
        if (st != null) {
            for (Map.Entry<NamespacedKey, Attribute> e : st.mods.entrySet()) {
                removeModifier(p, e.getValue(), e.getKey());
            }
            for (PotionEffectType t : st.potions) {
                dropPotion(p, t);
            }
        }
        scrub(p);
    }

    /** Removes any perk bonus of ours the player is still carrying from before (a crash, a removed perk). Run on join. */
    void scrub(Player p) {
        Set<Attribute> candidates = new HashSet<>();
        for (String n : COMMON) {
            Attribute a = Effect.attribute(n);
            if (a != null) {
                candidates.add(a);
            }
        }
        Applied st = applied.get(p.getUniqueId());
        if (st != null) {
            candidates.addAll(st.mods.values());
        }
        for (Attribute a : candidates) {
            AttributeInstance inst = p.getAttribute(a);
            if (inst == null) {
                continue;
            }
            for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
                NamespacedKey k = m.getKey();
                boolean ours = k.getNamespace().equals(namespace) && k.getKey().startsWith("perk_");
                if (ours && (st == null || !st.mods.containsKey(k))) {
                    inst.removeModifier(m);
                }
            }
        }
    }

    private static AttributeModifier find(AttributeInstance inst, NamespacedKey key) {
        for (AttributeModifier m : inst.getModifiers()) {
            if (m.getKey().equals(key)) {
                return m;
            }
        }
        return null;
    }

    private static void removeModifier(Player p, Attribute a, NamespacedKey key) {
        AttributeInstance inst = p.getAttribute(a);
        if (inst != null) {
            AttributeModifier m = find(inst, key);
            if (m != null) {
                inst.removeModifier(m);
            }
        }
    }

    /** Only takes off a potion that is one of our quiet, short doses: never one the player drank. */
    private static void dropPotion(Player p, PotionEffectType t) {
        PotionEffect cur = p.getPotionEffect(t);
        if (cur != null && cur.isAmbient() && cur.getDuration() <= POTION_TICKS) {
            p.removePotionEffect(t);
        }
    }
}
