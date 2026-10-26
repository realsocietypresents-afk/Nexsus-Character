package com.nexuscraft.nexuscharacter;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.plugin.PluginManager;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.nexuscraft.nexuscharacter.BukkitKit.CUISINE;
import static com.nexuscraft.nexuscharacter.BukkitKit.LODE;
import static com.nexuscraft.nexuscharacter.BukkitKit.REDSTONE;
import static com.nexuscraft.nexuscharacter.BukkitKit.xpAt;
import static com.nexuscraft.nexuscharacter.Kit.expect;

/** Reading real skill XP off players, working out perks, applying and removing their effects. */
public class ServiceTest {

    static final Attribute HEALTH = Effect.attribute("MAX_HEALTH");

    public static void main(String[] args) {
        reading();
        perksComeAndGo();
        prestigeScaling();
        potions();
        effectEdges();
        missingPlugins();
        offline();
        joinQuit();
        switches();
        top();
        Kit.finish();
    }

    static BukkitKit.TestPlayer ann(BukkitKit.Rig rig) {
        BukkitKit.TestPlayer ann = BukkitKit.join("Ann");
        ann.setXp(LODE, xpAt(15, 8.0));
        ann.setXp(CUISINE, xpAt(15, 10.0));
        return ann;
    }

    static double health(BukkitKit.TestPlayer p) {
        return p.getAttribute(HEALTH).getValue();
    }

    static int mods(BukkitKit.TestPlayer p, Attribute a) {
        return p.getAttribute(a).getModifiers().size();
    }

    static void reading() {
        BukkitKit.reset();
        BukkitKit.installFamily();
        BukkitKit.Rig rig = new BukkitKit.Rig();
        BukkitKit.TestPlayer ann = ann(rig);
        CharacterService.Snapshot s = rig.service.snapshot(ann.getUniqueId());
        expect(s.rows().size() == 7, "seven xp skills are installed, Wizardry is not: " + s.rows().size());
        CharacterService.Row lode = s.rows().get(0);
        expect(lode.def().id().equals("lode") && lode.level() == 15 && lode.max() == 50 && lode.live(), "Mining 15/50 read from the player's own data");
        expect(s.rows().get(2).level() == 15 && s.rows().get(2).max() == 40, "Cooking 15/40");
        expect(s.rows().get(1).level() == 0, "a skill never touched is 0, not started");
        expect(s.total() == 30 && s.prestige() == 0, "total level is the sum");

        // each plugin's own settings are asked, live
        BukkitKit.Named lodePlugin = (BukkitKit.Named) Bukkit.getPluginManager().getPlugin("NexusLode");
        lodePlugin.getConfig().set("skill.max-level", 10);
        s = rig.service.snapshot(ann.getUniqueId());
        expect(s.rows().get(0).level() == 10 && s.rows().get(0).max() == 10, "NexusLode's own top level is honoured at once");
        lodePlugin.getConfig().set("skill.max-level", 50);
        lodePlugin.getConfig().set("skill.xp-base", 16.0);
        s = rig.service.snapshot(ann.getUniqueId());
        expect(s.rows().get(0).level() < 15 && s.rows().get(0).level() > 8, "and its own curve (a steeper xp-base means a lower level): " + s.rows().get(0).level());
        lodePlugin.getConfig().set("skill.xp-base", 8.0);
        expect(rig.service.snapshot(ann.getUniqueId()).rows().get(0).level() == 15, "restored");

        // garbage in the player data
        ann.setXp(LODE, 0);
        expect(rig.service.snapshot(ann.getUniqueId()).rows().get(0).level() == 0, "an explicit 0 xp is not started, not level 1");
        ann.setXp(LODE, -50);
        expect(rig.service.snapshot(ann.getUniqueId()).rows().get(0).level() == 0, "negative xp reads as not started");
        ann.setXp(LODE, xpAt(15, 8.0));

        // a plugin that is not enabled is not on the sheet
        lodePlugin.enabled = false;
        expect(rig.service.snapshot(ann.getUniqueId()).rows().size() == 6, "a disabled plugin's skill disappears");
        lodePlugin.enabled = true;
        PluginManager.PLUGINS.remove(lodePlugin);
        expect(rig.service.snapshot(ann.getUniqueId()).rows().size() == 6, "so does one that is not installed");
        PluginManager.PLUGINS.add(lodePlugin);

        // the config's own copy is used when the plugin cannot be asked
        lodePlugin.getConfig().set("skill.max-level", null);
        lodePlugin.getConfig().set("skill.xp-base", null);
        expect(rig.service.snapshot(ann.getUniqueId()).rows().get(0).level() == 15, "with no settings to ask, config.yml's numbers are used");
    }

    static void perksComeAndGo() {
        BukkitKit.reset();
        BukkitKit.installFamily();
        BukkitKit.Rig rig = new BukkitKit.Rig();
        BukkitKit.TestPlayer ann = ann(rig);
        expect(health(ann) == 20.0, "starts at 20 health");
        rig.service.refresh(ann);
        expect(health(ann) == 22.0, "Hearty Digger (Mining 15 + Cooking 15) adds a heart: " + health(ann));
        expect(ann.text().contains("Perk unlocked: Hearty Digger") && ann.text().contains("never goes hungry"), "she is told, with the flavour text: " + ann.text());
        expect(rig.store.rec(ann.getUniqueId()).perks.equals(Set.of("hearty-digger")), "and it is remembered");
        expect(rig.store.rec(ann.getUniqueId()).levels.get("lode") == 15 && rig.store.rec(ann.getUniqueId()).name.equals("Ann"), "so are her levels and name");
        expect(rig.store.dirty(), "ready to save");
        expect(rig.store.rec(ann.getUniqueId()).seen == 1_000_000L, "and when she was last seen");

        ann.clear();
        rig.service.refresh(ann);
        rig.service.refresh(ann);
        expect(health(ann) == 22.0 && mods(ann, HEALTH) == 1, "refreshing again does not stack the bonus: " + health(ann) + ", " + mods(ann, HEALTH));
        expect(ann.messages.isEmpty(), "and says nothing");

        // one level short: lose it
        ann.setXp(LODE, xpAt(15, 8.0) - 1);
        rig.service.refresh(ann);
        expect(health(ann) == 20.0 && mods(ann, HEALTH) == 0, "Mining 14: the heart is gone");
        expect(ann.text().contains("Perk lost: Hearty Digger"), "and she is told: " + ann.text());
        expect(rig.store.rec(ann.getUniqueId()).perks.isEmpty(), "no longer held");

        // and back
        ann.clear();
        ann.setXp(LODE, xpAt(15, 8.0));
        rig.service.refresh(ann);
        expect(health(ann) == 22.0 && ann.text().contains("Perk unlocked"), "earned again");

        // two perks at once, and a total-level perk
        ann.setXp(REDSTONE, xpAt(15, 10.0));
        ann.setXp(LODE, xpAt(20, 8.0));
        ann.clear();
        rig.service.refresh(ann);
        expect(ann.has(PotionEffectType.HASTE), "Tunnel Engineer (Mining 20 + Engineering 15) is a steady haste");
        expect(rig.store.rec(ann.getUniqueId()).perks.equals(Set.of("hearty-digger", "tunnel-engineer")), "both held: " + rig.store.rec(ann.getUniqueId()).perks);
        expect(ann.text().contains("Perk unlocked: Tunnel Engineer") && !ann.text().contains("Hearty"), "only the new one is announced");

        // someone else's modifier on the same attribute is never touched
        ann.getAttribute(HEALTH).addModifier(new AttributeModifier(new NamespacedKey(Bukkit.getPluginManager().getPlugin("NexusLode"), "other"), 4,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        ann.setXp(CUISINE, 0L);
        rig.service.refresh(ann);
        expect(health(ann) == 24.0 && mods(ann, HEALTH) == 1, "losing the perk removes only our modifier: " + health(ann));

        // messages off: effects still work
        BukkitKit.reset();
        BukkitKit.installFamily();
        org.bukkit.configuration.file.YamlConfiguration y = BukkitKit.shipped();
        y.set("messages", false);
        BukkitKit.Rig quiet = new BukkitKit.Rig(y);
        BukkitKit.TestPlayer bo = ann(quiet);
        quiet.service.refresh(bo);
        expect(health(bo) == 22.0 && bo.messages.isEmpty(), "messages: false is silent but the perk still works");
    }

    static void prestigeScaling() {
        BukkitKit.reset();
        BukkitKit.installFamily();
        BukkitKit.Rig rig = new BukkitKit.Rig();
        BukkitKit.TestPlayer ann = ann(rig);
        rig.store.rec(ann.getUniqueId()).prestige = 2;
        rig.service.refresh(ann);
        // Hearty Digger +2 and Veteran's Resolve +2, each 20% stronger at two stars
        expect(Math.abs(health(ann) - 24.8) < 1e-9, "two stars: both hearts are 20% bigger: " + health(ann));
        expect(rig.store.rec(ann.getUniqueId()).perks.contains("veterans-resolve") && !rig.store.rec(ann.getUniqueId()).perks.contains("master-of-all"),
                "Veteran's Resolve at one star, Master of All needs three");
        int total = 0;
        for (int i = 0; i < 10; i++) {
            total += rig.service.boostXp(ann.getUniqueId(), 10);
        }
        expect(total == 106, "5% x 1.2 = 6% more orb xp, with fractions carried so small orbs still add up: " + total);
        expect(rig.service.boostXp(ann.getUniqueId(), 0) == 0 && rig.service.boostXp(ann.getUniqueId(), -3) == -3, "nothing and negatives pass through");
        expect(rig.service.boostXp(java.util.UUID.randomUUID(), 50) == 50, "a stranger gets no bonus");
        rig.store.rec(ann.getUniqueId()).prestige = 1;
        rig.service.refresh(ann);
        BukkitKit.Rig carryRig = rig;
        expect(carryRig.service.boostXp(ann.getUniqueId(), 10) == 10, "5.5% of 10 is 0.55: nothing yet, a fraction is carried");
        carryRig.service.onQuit(ann);
        carryRig.service.onJoin(ann);
        expect(carryRig.service.boostXp(ann.getUniqueId(), 10) == 10, "a fraction carried before she left does not follow her back: " + "fresh start");
        rig.store.rec(ann.getUniqueId()).prestige = 2;
        rig.service.refresh(ann);
        expect(rig.service.scale(0) == 1.0 && Math.abs(rig.service.scale(5) - 1.5) < 1e-9, "scale");

        rig.store.rec(ann.getUniqueId()).prestige = 3;
        rig.service.refresh(ann);
        expect(Bukkit.getPlayer(ann.getUniqueId()).getAttribute(Effect.attribute("LUCK")).getValue() == 1.3, "three stars: Master of All gives +1 luck, 30% bigger");
        expect(Bukkit.getPlayer(ann.getUniqueId()).getAttribute(Effect.attribute("ATTACK_DAMAGE")).getValue() == 1.65, "and its second effect too: " + ann.getAttribute(Effect.attribute("ATTACK_DAMAGE")).getValue());
        rig.store.rec(ann.getUniqueId()).prestige = 0;
        rig.service.refresh(ann);
        expect(health(ann) == 22.0 && ann.getAttribute(Effect.attribute("LUCK")).getValue() == 0.0, "back to no stars: everything shrinks or goes");
        expect(rig.service.boostXp(ann.getUniqueId(), 1000) == 1000, "no xp perk, no bonus");
    }

    static void potions() {
        BukkitKit.reset();
        BukkitKit.installFamily();
        BukkitKit.Rig rig = new BukkitKit.Rig();
        BukkitKit.TestPlayer ann = ann(rig);
        ann.setXp(LODE, xpAt(25, 8.0));
        ann.setXp(BukkitKit.ALCH, xpAt(15, 14.0));
        rig.service.refresh(ann);
        PotionEffect nv = ann.getPotionEffect(PotionEffectType.NIGHT_VISION);
        expect(nv != null && nv.getAmplifier() == 0 && nv.isAmbient() && !nv.hasParticles() && nv.getDuration() == PerkApplier.POTION_TICKS,
                "Lamplighter: a quiet, ambient night vision of 400 ticks");

        // topped up only when it has run down
        PotionEffect half = new PotionEffect(PotionEffectType.NIGHT_VISION, 350, 0, true, false);
        ann.addPotionEffect(half);
        rig.service.refresh(ann);
        expect(ann.getPotionEffect(PotionEffectType.NIGHT_VISION) == half, "350 ticks left: left alone");
        ann.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 100, 0, true, false));
        rig.service.refresh(ann);
        expect(ann.getPotionEffect(PotionEffectType.NIGHT_VISION).getDuration() == 400, "100 ticks left: topped up before it can flash");

        // lose the perk: our dose is taken off
        ann.setXp(LODE, xpAt(24, 8.0));
        rig.service.refresh(ann);
        expect(!ann.has(PotionEffectType.NIGHT_VISION), "perk gone, effect gone");

        // ...but a potion the player drank is never touched
        ann.setXp(LODE, xpAt(25, 8.0));
        rig.service.refresh(ann);
        ann.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 6000, 0, false, true));
        ann.setXp(LODE, xpAt(24, 8.0));
        rig.service.refresh(ann);
        expect(ann.has(PotionEffectType.NIGHT_VISION) && ann.getPotionEffect(PotionEffectType.NIGHT_VISION).getDuration() == 6000,
                "a drunk night vision outlives the perk");
    }

    static void effectEdges() {
        BukkitKit.reset();
        BukkitKit.installFamily();
        org.bukkit.configuration.file.YamlConfiguration y = new org.bukkit.configuration.file.YamlConfiguration();
        y.set("perks.p1.requires.lode", 1);
        y.set("perks.p1.effects", List.of("potion:HASTE:0", "attribute:LUCK:1:add"));
        y.set("perks.p2.requires.lode", 1);
        y.set("perks.p2.effects", List.of("potion:HASTE:1"));
        y.set("skills.lode.plugin", "NexusLode");
        y.set("skills.lode.xp-key", "nexuslode:lode_xp");
        BukkitKit.Rig rig = new BukkitKit.Rig(y);
        BukkitKit.TestPlayer ann = BukkitKit.join("Ann");
        ann.setXp(LODE, xpAt(2, 8.0));
        rig.service.refresh(ann);
        expect(ann.getPotionEffect(PotionEffectType.HASTE).getAmplifier() == 1, "two perks giving the same potion: the stronger one wins");
        Attribute luck = Effect.attribute("LUCK");
        Attribute armor = Effect.attribute("ARMOR");
        expect(ann.getAttribute(luck).getValue() == 1.0, "luck from p1");

        // the same perk, the same slot, but now a different attribute (an edited config)
        y.set("perks.p1.effects", List.of("potion:HASTE:0", "attribute:ARMOR:1:add"));
        rig.service.setConfig(new CharacterConfig(y));
        rig.service.refresh(ann);
        expect(ann.getAttribute(luck).getValue() == 0.0 && mods(ann, luck) == 0, "the old attribute is cleaned off when the slot changes: " + ann.getAttribute(luck).getValue());
        expect(ann.getAttribute(armor).getValue() == 1.0, "and the new one is on");

        // same number, different way of applying it
        y.set("perks.p1.effects", List.of("potion:HASTE:0", "attribute:ARMOR:1:multiply"));
        rig.service.setConfig(new CharacterConfig(y));
        rig.service.refresh(ann);
        AttributeModifier changed = ann.getAttribute(armor).getModifiers().iterator().next();
        expect(ann.getAttribute(armor).getModifiers().size() == 1 && changed.getOperation() == AttributeModifier.Operation.MULTIPLY_SCALAR_1,
                "add -> multiply with the same amount still updates the modifier");

        // a stronger level of an effect we already hold replaces it at once
        y.set("perks.p2.effects", List.of("potion:HASTE:2"));
        rig.service.setConfig(new CharacterConfig(y));
        rig.service.refresh(ann);
        expect(ann.getPotionEffect(PotionEffectType.HASTE).getAmplifier() == 2, "a perk edited to a higher potion level takes effect at the next refresh, not when the old dose runs low");
    }

    static void missingPlugins() {
        BukkitKit.reset();
        BukkitKit.installFamily();
        BukkitKit.Rig rig = new BukkitKit.Rig();
        BukkitKit.TestPlayer ann = ann(rig);
        rig.service.refresh(ann);
        expect(health(ann) == 22.0, "perk held");
        ann.clear();
        BukkitKit.Named cuisine = (BukkitKit.Named) Bukkit.getPluginManager().getPlugin("NexusCuisine");
        PluginManager.PLUGINS.remove(cuisine);
        rig.service.refresh(ann);
        expect(health(ann) == 20.0, "with NexusCuisine gone the bonus is not given");
        expect(ann.messages.isEmpty(), "but she is not told she 'lost' it: a plugin missing is not her fault");
        expect(rig.store.rec(ann.getUniqueId()).perks.contains("hearty-digger"), "and it is still remembered");
        PluginManager.PLUGINS.add(cuisine);
        rig.service.refresh(ann);
        expect(health(ann) == 22.0 && ann.messages.isEmpty(), "plugin back: perk back, no new 'unlocked' message");
        expect(rig.service.snapshot(ann.getUniqueId()).perks().stream().filter(PerkEngine.Status::unknown).count() == 0, "nothing unknown now");

        // a skill that throws is unreadable, not zero
        rig.registry.register(rig.plugin, "broken", "Broken", 10, id -> {
            throw new IllegalStateException("boom");
        }, null);
        org.bukkit.configuration.file.YamlConfiguration y = BukkitKit.shipped();
        y.set("perks.needs-broken.requires.broken", 5);
        y.set("perks.needs-broken.effects", List.of("xp:10"));
        CharacterConfig c = new CharacterConfig(y);
        rig.service.setConfig(c);
        CharacterService.Snapshot s = rig.service.snapshot(ann.getUniqueId());
        PerkEngine.Status st = s.perks().stream().filter(p -> p.perk().id().equals("needs-broken")).findFirst().orElseThrow();
        expect(!st.active() && st.unknown(), "a perk on a skill that throws is unknown, not lost");
        expect(s.rows().stream().anyMatch(r -> r.def().id().equals("broken") && r.level() == 0 && !r.live()), "the sheet shows it as unreadable, level 0");
    }

    static void offline() {
        BukkitKit.reset();
        BukkitKit.installFamily();
        BukkitKit.Rig rig = new BukkitKit.Rig();
        BukkitKit.TestPlayer ann = ann(rig);
        rig.service.refresh(ann);
        rig.service.onQuit(ann);
        Bukkit.PLAYERS.remove(ann.getUniqueId());
        CharacterService.Snapshot s = rig.service.snapshot(ann.getUniqueId());
        expect(s.rows().get(0).level() == 15 && !s.rows().get(0).live(), "offline: last known Mining 15, marked as not live");
        expect(s.total() == 30 && s.active() == 1, "offline: total and perks come from the saved levels");
        expect(rig.service.snapshot(java.util.UUID.randomUUID()).total() == 0, "a stranger is all zeros");
        PerkEngine.Status stranger = rig.service.snapshot(java.util.UUID.randomUUID()).perks().get(0);
        expect(!stranger.unknown() && stranger.gaps().size() == 2, "and counts as level 0 in every installed skill, not as 'unknown': " + stranger.gaps());
        BukkitKit.Named lodePlugin = (BukkitKit.Named) Bukkit.getPluginManager().getPlugin("NexusLode");
        lodePlugin.getConfig().set("skill.max-level", 10);
        expect(rig.service.snapshot(ann.getUniqueId()).rows().get(0).level() == 10, "a last-known level above a lowered top level is held to it");
        lodePlugin.getConfig().set("skill.max-level", 50);

        // survives a restart
        rig.store.save();
        Bukkit.PLAYERS.clear();
        rig.restart();
        s = rig.service.snapshot(ann.getUniqueId());
        expect(s.rows().get(0).level() == 15 && s.active() == 1, "after a restart the sheet is still there");
        expect(rig.service.top(5).get(0).name().equals("Ann"), "with her name");
    }

    static void joinQuit() {
        BukkitKit.reset();
        BukkitKit.installFamily();
        BukkitKit.Rig rig = new BukkitKit.Rig();
        BukkitKit.TestPlayer ann = ann(rig);
        // a bonus left behind by a crash, and one that belongs to someone else
        NamespacedKey stale = new NamespacedKey(rig.plugin, "perk_old_0");
        NamespacedKey foreign = new NamespacedKey(Bukkit.getPluginManager().getPlugin("NexusLode"), "perk_other_0");
        NamespacedKey ours = new NamespacedKey(rig.plugin, "unrelated");
        for (NamespacedKey k : new NamespacedKey[]{stale, foreign, ours}) {
            ann.getAttribute(HEALTH).addModifier(new AttributeModifier(k, 1, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        }
        rig.service.onJoin(ann);
        List<NamespacedKey> left = new ArrayList<>();
        for (AttributeModifier m : ann.getAttribute(HEALTH).getModifiers()) {
            left.add(m.getKey());
        }
        expect(!left.contains(stale), "joining clears a leftover perk bonus of ours");
        expect(left.contains(foreign) && left.contains(ours), "and only that: another plugin's, and our non-perk modifiers, stay");
        expect(health(ann) == 20.0 + 1 + 1 + 2, "a fresh perk is applied on join: " + health(ann));

        ann.getAttribute(HEALTH).removeModifier(new AttributeModifier(foreign, 0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        ann.setXp(LODE, xpAt(25, 8.0));
        ann.setXp(BukkitKit.ALCH, xpAt(15, 14.0));
        rig.service.refresh(ann);
        expect(ann.has(PotionEffectType.NIGHT_VISION), "night vision on");
        rig.service.onQuit(ann);
        expect(!ann.has(PotionEffectType.NIGHT_VISION), "quitting takes our potion off");
        boolean any = false;
        for (AttributeModifier m : ann.getAttribute(HEALTH).getModifiers()) {
            any |= m.getKey().getKey().startsWith("perk_");
        }
        expect(!any, "and our attribute bonuses, so nothing is left in the save file");
        expect(rig.store.rec(ann.getUniqueId()).levels.get("lode") == 25, "her levels were remembered on the way out");

        // join, change, tick
        BukkitKit.TestPlayer bo = BukkitKit.join("Bo");
        rig.service.onJoin(bo);
        expect(health(bo) == 20.0, "a newcomer has nothing");
        bo.setXp(LODE, xpAt(15, 8.0));
        bo.setXp(CUISINE, xpAt(15, 10.0));
        rig.service.tick();
        expect(health(bo) == 22.0 && bo.text().contains("Perk unlocked"), "the timer notices a level-up within one tick");
        rig.service.clearAll();
        expect(health(bo) == 20.0, "clearAll takes it off everyone");
    }

    static void switches() {
        BukkitKit.reset();
        BukkitKit.installFamily();
        org.bukkit.configuration.file.YamlConfiguration y = BukkitKit.shipped();
        y.set("perks-enabled", false);
        BukkitKit.Rig rig = new BukkitKit.Rig(y);
        BukkitKit.TestPlayer ann = ann(rig);
        rig.service.refresh(ann);
        expect(health(ann) == 20.0 && ann.messages.isEmpty() && rig.store.rec(ann.getUniqueId()).perks.isEmpty(), "perks-enabled: false gives nothing");
        expect(rig.store.rec(ann.getUniqueId()).levels.get("lode") == 15, "but levels are still remembered");
        expect(rig.service.boostXp(ann.getUniqueId(), 100) == 100, "and no xp bonus");

        y = BukkitKit.shipped();
        y.set("enabled", false);
        BukkitKit.Rig off = new BukkitKit.Rig(y);
        BukkitKit.TestPlayer bo = ann(off);
        off.service.refresh(bo);
        expect(health(bo) == 20.0 && !off.store.known(bo.getUniqueId()), "enabled: false does nothing at all");

        // perks switched off live, with a perk already on
        BukkitKit.Rig live = new BukkitKit.Rig();
        BukkitKit.TestPlayer cy = ann(live);
        live.service.refresh(cy);
        expect(health(cy) == 22.0, "on");
        live.service.setConfig(new CharacterConfig(y));
        y.set("enabled", true);
        y.set("perks-enabled", false);
        live.service.setConfig(new CharacterConfig(y));
        live.service.refresh(cy);
        expect(health(cy) == 20.0, "switched off on reload: the bonus is taken off at the next refresh");
    }

    static void top() {
        BukkitKit.reset();
        BukkitKit.installFamily();
        BukkitKit.Rig rig = new BukkitKit.Rig();
        BukkitKit.TestPlayer ann = ann(rig);
        BukkitKit.TestPlayer bo = BukkitKit.join("Bo");
        bo.setXp(LODE, xpAt(40, 8.0));
        BukkitKit.TestPlayer cy = BukkitKit.join("Cy");
        cy.setXp(LODE, xpAt(5, 8.0));
        for (BukkitKit.TestPlayer p : new BukkitKit.TestPlayer[]{ann, bo, cy}) {
            rig.service.refresh(p);
        }
        rig.store.rec(cy.getUniqueId()).prestige = 1;
        List<CharacterService.Top> t = rig.service.top(10);
        expect(t.get(0).name().equals("Cy") && t.get(1).name().equals("Bo") && t.get(2).name().equals("Ann"),
                "prestige first (Cy), then total level (Bo 40 beats Ann 30): " + t);
        expect(rig.service.top(2).size() == 2, "limited to the asked number");
        Map<java.util.UUID, CharacterStore.Rec> all = rig.store.all();
        all.get(ann.getUniqueId()).levels.put("gone-plugin", 999);
        expect(rig.service.top(10).stream().filter(x -> x.name().equals("Ann")).findFirst().orElseThrow().total() == 30,
                "a skill that is no longer installed does not count towards the total");
    }
}
