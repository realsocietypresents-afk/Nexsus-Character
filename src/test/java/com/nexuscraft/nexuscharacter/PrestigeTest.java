package com.nexuscraft.nexuscharacter;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.nexuscraft.nexuscharacter.BukkitKit.ALCH;
import static com.nexuscraft.nexuscharacter.BukkitKit.ANGLER;
import static com.nexuscraft.nexuscharacter.BukkitKit.CUISINE;
import static com.nexuscraft.nexuscharacter.BukkitKit.LODE;
import static com.nexuscraft.nexuscharacter.BukkitKit.MOMENTUM;
import static com.nexuscraft.nexuscharacter.BukkitKit.REDSTONE;
import static com.nexuscraft.nexuscharacter.BukkitKit.SMELT;
import static com.nexuscraft.nexuscharacter.BukkitKit.xpAt;
import static com.nexuscraft.nexuscharacter.Kit.expect;

/** Prestige: who may, what it wipes, what it gives, and the confirmation. */
public class PrestigeTest {

    public static void main(String[] args) {
        rules();
        performing();
        escalation();
        quiet();
        edges();
        confirmation();
        Kit.finish();
    }

    static BukkitKit.TestPlayer master(BukkitKit.Rig rig, int mastered) {
        BukkitKit.TestPlayer ann = BukkitKit.join("Ann");
        ann.perms.add("nexuscharacter.prestige");
        String[] keys = {LODE, SMELT, MOMENTUM, REDSTONE};
        double[] bases = {8.0, 6.0, 6.0, 10.0};
        for (int i = 0; i < mastered; i++) {
            ann.setXp(keys[i], xpAt(40, bases[i]));
        }
        return ann;
    }

    static BukkitKit.Rig rig(YamlConfiguration y) {
        BukkitKit.reset();
        BukkitKit.installFamily();
        return y == null ? new BukkitKit.Rig() : new BukkitKit.Rig(y);
    }

    static void rules() {
        BukkitKit.Rig rig = rig(null);
        BukkitKit.TestPlayer ann = master(rig, 2);
        expect(rig.prestige.masteryLine(BukkitKit.find(rig.registry, "lode")) == 40, "80% of 50 is 40");
        expect(rig.prestige.masteryLine(BukkitKit.find(rig.registry, "cuisine")) == 32, "80% of 40 is 32");
        expect(rig.prestige.masteryLine(BukkitKit.find(rig.registry, "alchemy")) == 16, "80% of 20 is 16");
        Prestige.Check ch = rig.prestige.check(ann);
        expect(!ch.ok() && ch.mastered().size() == 2 && ch.needed() == 3 && ch.reason().contains("2 of the 3"), "two of three: " + ch.reason());
        ann.setXp(MOMENTUM, xpAt(39, 6.0));
        expect(rig.prestige.check(ann).mastered().size() == 2, "level 39 of 50 is not mastered");
        ann.setXp(MOMENTUM, xpAt(40, 6.0));
        ch = rig.prestige.check(ann);
        expect(ch.ok() && ch.mastered().size() == 3 && ch.needed() == 3 && ch.stars() == 0, "three at the line: ready");
        expect(ch.mastered().stream().map(SkillDef::id).toList().equals(List.of("lode", "smelting", "momentum")), "and it names them in order");
    }

    static void performing() {
        YamlConfiguration y = BukkitKit.shipped();
        y.set("prestige.reward-commands", List.of("give %player% diamond %stars%", "/say %player% shines"));
        BukkitKit.Rig rig = rig(y);
        BukkitKit.TestPlayer ann = master(rig, 3);
        ann.setXp(CUISINE, xpAt(15, 10.0));
        rig.service.refresh(ann);
        expect(rig.store.rec(ann.getUniqueId()).perks.contains("hearty-digger"), "she holds Hearty Digger first");
        double before = ann.getAttribute(Effect.attribute("MAX_HEALTH")).getValue();
        expect(before == 22.0, "22 health: " + before);
        rig.now.set(5_000_000L);
        ann.clear();
        Prestige.Outcome out = rig.prestige.perform(ann);
        expect(out.done() && out.stars() == 1 && out.wiped().size() == 3, "prestige works: " + out.message());
        expect(ann.xp(LODE) == null && ann.xp(SMELT) == null && ann.xp(MOMENTUM) == null, "the mastered skills are wiped");
        expect(ann.xp(CUISINE) == xpAt(15, 10.0), "an unmastered skill is untouched");
        CharacterStore.Rec rec = rig.store.rec(ann.getUniqueId());
        expect(rec.prestige == 1 && rec.prestigeAt == 5_000_000L, "a star, with the time");
        expect(rec.levels.get("lode") == 0 && rec.levels.get("cuisine") == 15, "levels remembered as wiped");
        expect(rig.service.snapshot(ann.getUniqueId()).rows().get(0).level() == 0, "the sheet shows Mining not started");
        expect(!rec.perks.contains("hearty-digger") && rec.perks.contains("veterans-resolve"), "Hearty Digger is lost (Mining is 0); Veteran's Resolve arrives");
        expect(ann.text().contains("Perk lost: Hearty Digger") && ann.text().contains("Perk unlocked: Veteran's Resolve"), "and she is told: " + ann.text());
        double after = ann.getAttribute(Effect.attribute("MAX_HEALTH")).getValue();
        expect(Math.abs(after - 22.2) < 1e-9, "Veteran's Resolve +2 hearts-of-health, 10% stronger for one star: " + after);
        expect(Bukkit.DISPATCHED.contains("give Ann diamond 1") && Bukkit.DISPATCHED.contains("say Ann shines"),
                "reward commands run with the name and star count, a leading slash is fine: " + Bukkit.DISPATCHED);
        expect(Bukkit.BROADCASTS.size() == 1 && Kit.strip(Bukkit.BROADCASTS.get(0)).equals("[Sheet] Ann has reached Prestige 1 ★☆☆☆☆"), "announced: " + Bukkit.BROADCASTS);
        expect(rig.store.dirty() == false, "saved at once (a star is not something to lose in a crash)");
        rig.restart();
        expect(rig.store.rec(ann.getUniqueId()).prestige == 1, "and it survived a restart");

        // not allowed twice in a row: she needs four now
        out = rig.prestige.perform(ann);
        expect(!out.done() && out.message().contains("0 of the 4"), "the second star asks for four: " + out.message());
    }

    static void escalation() {
        BukkitKit.Rig rig = rig(null);
        BukkitKit.TestPlayer ann = master(rig, 4);
        rig.store.rec(ann.getUniqueId()).prestige = 1;
        Prestige.Check ch = rig.prestige.check(ann);
        expect(ch.ok() && ch.needed() == 4, "star two needs 3 + 1 = 4 mastered, and she has them");
        rig.store.rec(ann.getUniqueId()).prestige = 3;
        ch = rig.prestige.check(ann);
        expect(!ch.ok() && ch.needed() == 6, "star four needs 6: " + ch.needed());

        // capped at how many skills there are to master
        rig.store.rec(ann.getUniqueId()).prestige = 4;
        ch = rig.prestige.check(ann);
        expect(ch.needed() == 7, "star five would need 7: all seven resettable skills (not more than exist): " + ch.needed());

        YamlConfiguration y = BukkitKit.shipped();
        y.set("prestige.skills-needed", 50);
        BukkitKit.Rig big = rig(y);
        BukkitKit.TestPlayer bo = master(big, 4);
        expect(big.prestige.check(bo).needed() == 7, "asking for 50 is capped at the 7 that exist");
        bo.setXp(CUISINE, xpAt(32, 10.0));
        bo.setXp(ALCH, xpAt(16, 14.0));
        bo.setXp(ANGLER, xpAt(16, 12.0));
        expect(big.prestige.check(bo).ok(), "all seven mastered meets it");

        // without Skills: step 0 keeps every star at the same price
        y = BukkitKit.shipped();
        y.set("prestige.skills-needed-step", 0);
        BukkitKit.Rig flat = rig(y);
        BukkitKit.TestPlayer cy = master(flat, 3);
        flat.store.rec(cy.getUniqueId()).prestige = 3;
        expect(flat.prestige.check(cy).needed() == 3 && flat.prestige.check(cy).ok(), "a step of 0 keeps the price at 3");

        // a different mastery line
        y = BukkitKit.shipped();
        y.set("prestige.mastery-fraction", 0.5);
        BukkitKit.Rig half = rig(y);
        expect(half.prestige.masteryLine(BukkitKit.find(half.registry, "lode")) == 25 && half.prestige.masteryLine(BukkitKit.find(half.registry, "alchemy")) == 10, "mastery-fraction 0.5");
        y = BukkitKit.shipped();
        y.set("prestige.mastery-fraction", 0.75);
        BukkitKit.Rig rounding = rig(y);
        expect(rounding.prestige.masteryLine(BukkitKit.find(rounding.registry, "lode")) == 38, "37.5 rounds up to 38: a skill is not mastered a level early");
    }

    static void quiet() {
        YamlConfiguration y = BukkitKit.shipped();
        y.set("prestige.broadcast", false);
        BukkitKit.Rig rig = rig(y);
        BukkitKit.TestPlayer ann = master(rig, 3);
        expect(rig.prestige.perform(ann).done() && Bukkit.BROADCASTS.isEmpty(), "broadcast: false keeps it to the player");
    }

    static void edges() {
        BukkitKit.Rig rig = rig(null);
        BukkitKit.TestPlayer ann = master(rig, 4);
        rig.store.rec(ann.getUniqueId()).prestige = 5;
        Prestige.Check ch = rig.prestige.check(ann);
        expect(!ch.ok() && ch.reason().contains("every star") && ch.stars() == 5, "at the top: " + ch.reason());
        expect(!rig.prestige.perform(ann).done(), "and perform refuses");
        expect(ann.xp(LODE) != null, "nothing was wiped");

        YamlConfiguration y = BukkitKit.shipped();
        y.set("prestige.enabled", false);
        BukkitKit.Rig off = rig(y);
        BukkitKit.TestPlayer bo = master(off, 4);
        expect(!off.prestige.check(bo).ok() && off.prestige.check(bo).reason().contains("switched off") && !off.prestige.perform(bo).done(), "prestige.enabled: false");
        expect(bo.xp(LODE) != null, "nothing wiped");

        // a skill with reset: false is not wiped and does not count
        y = BukkitKit.shipped();
        y.set("skills.lode.reset", false);
        BukkitKit.Rig keep = rig(y);
        BukkitKit.TestPlayer cy = master(keep, 3);
        Prestige.Check kc = keep.prestige.check(cy);
        expect(!kc.ok() && kc.mastered().size() == 2, "Mining cannot be reset, so it does not count: " + kc.mastered().size());
        cy.setXp(REDSTONE, xpAt(40, 10.0));
        expect(keep.prestige.perform(cy).done() && cy.xp(LODE) != null && cy.xp(REDSTONE) == null, "and it is left alone when she prestiges with the others");

        // nothing can be reset at all
        y = BukkitKit.shipped();
        for (String id : List.of("lode", "smelting", "cuisine", "alchemy", "angler", "momentum", "redstone")) {
            y.set("skills." + id + ".reset", false);
        }
        BukkitKit.Rig none = rig(y);
        BukkitKit.TestPlayer di = master(none, 4);
        expect(!none.prestige.check(di).ok() && none.prestige.check(di).reason().contains("nothing to prestige"), "no resettable skill: " + none.prestige.check(di).reason());

        // a reset that fails: no star
        YamlConfiguration tiny = new YamlConfiguration();
        tiny.set("prestige.skills-needed", 3);
        BukkitKit.Rig t = rig(tiny);
        BukkitKit.TestPlayer ed = BukkitKit.join("Ed");
        AtomicInteger wiped = new AtomicInteger();
        BukkitKit.Named owner = new BukkitKit.Named("Owner");
        t.registry.register(owner, "one", "One", 10, id -> 10, id -> wiped.incrementAndGet());
        t.registry.register(owner, "two", "Two", 10, id -> 10, id -> wiped.incrementAndGet());
        t.registry.register(owner, "three", "Three", 10, id -> 10, id -> {
            throw new IllegalStateException("cannot");
        });
        Prestige.Outcome out = t.prestige.perform(ed);
        expect(!out.done() && out.message().contains("Only 2 of the 3") && t.service.prestigeOf(ed.getUniqueId()) == 0, "a reset that fails means no star: " + out.message());
        expect(Bukkit.BROADCASTS.isEmpty() && Bukkit.DISPATCHED.isEmpty(), "and no announcement or reward");
    }

    static void confirmation() {
        BukkitKit.Rig rig = rig(null);
        BukkitKit.TestPlayer ann = master(rig, 2);

        String text = rig.say(ann, "prestige");
        expect(text.contains("Prestige ☆☆☆☆☆ (0 of 5)") && text.contains("master 3 skills (80% of the top level)"), "the preview: " + text);
        expect(text.contains("✔ Mining 40/40 (will reset to 0)") && text.contains("✘ Cooking 0/32") && text.contains("You have mastered 2 of the 3"), "marks what counts: " + text);
        expect(text.contains("+0% now, +10% after"), "says what a star gives");
        expect(!text.contains("/sheet prestige confirm"), "and does not offer to confirm when she cannot");
        expect(rig.say(ann, "prestige", "confirm").contains("Look first"), "no preview armed: told to look first");

        ann.setXp(MOMENTUM, xpAt(40, 6.0));
        text = rig.say(ann, "prestige");
        expect(text.contains("You can prestige now") && text.contains("/sheet prestige confirm") && text.contains("30 seconds"), "ready: " + text);
        rig.now.addAndGet(31_000);
        expect(rig.say(ann, "prestige", "confirm").contains("Look first") && ann.xp(LODE) != null, "too late: told to look again, nothing wiped");
        rig.say(ann, "prestige");
        rig.now.addAndGet(30_000);
        ann.perms.remove("nexuscharacter.prestige");
        expect(rig.say(ann, "prestige", "confirm").contains("may not prestige") && ann.xp(LODE) != null, "without the permission: refused");
        ann.perms.add("nexuscharacter.prestige");
        text = rig.say(ann, "prestige", "CONFIRM");
        expect(text.contains("You reached Prestige 1!") && text.contains("Reset: Mining, Smelting, Traversal") && ann.xp(LODE) == null, "in time: done (case does not matter): " + text);
        expect(rig.say(ann, "prestige", "confirm").contains("Look first"), "the confirmation is used up");

        // the console cannot
        expect(rig.sayConsole("prestige").contains("Only a player"), "console refused");

        // a star at the top
        rig.store.rec(ann.getUniqueId()).prestige = 5;
        text = rig.say(ann, "prestige");
        expect(text.contains("(5 of 5)") && text.contains("every star"), "top: " + text);
    }
}
