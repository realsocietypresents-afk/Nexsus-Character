package com.nexuscraft.nexuscharacter;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;

import static com.nexuscraft.nexuscharacter.Kit.expect;

/** The shipped config.yml says what the README says, and bad edits are reported instead of crashing. */
public class ConfigTest {

    public static void main(String[] args) {
        shipped();
        clamps();
        badSkills();
        badPerks();
        Kit.finish();
    }

    static void shipped() {
        BukkitKit.reset();
        CharacterConfig c = new CharacterConfig(BukkitKit.shipped());
        expect(c.problems.isEmpty(), "the shipped config has no problems: " + c.problems);
        expect(c.skills.size() == 8, "eight skills ship: " + c.skills.size());
        List<String> ids = c.skills.stream().map(CharacterConfig.SkillCfg::id).toList();
        expect(ids.equals(List.of("lode", "smelting", "cuisine", "alchemy", "angler", "momentum", "redstone", "magic")), "in the documented order: " + ids);
        CharacterConfig.SkillCfg lode = c.skills.get(0);
        expect(lode.plugin().equals("NexusLode") && lode.xpKey().equals("nexuslode:lode_xp") && lode.maxLevel() == 50 && lode.xpBase() == 8.0
                && lode.reset() && lode.type().equals("xp") && lode.name().equals("Mining"), "lode");
        CharacterConfig.SkillCfg magic = c.skills.get(7);
        expect(magic.type().equals("wizard-service") && !magic.reset() && magic.plugin().equals("NexusMagic"), "magic cannot be reset and is read from its service");
        expect(c.perks.keySet().equals(new java.util.LinkedHashSet<>(List.of("hearty-digger", "trail-provisions", "furnace-brewer", "tunnel-engineer",
                "tide-runner", "lucky-hands", "lamplighter", "grand-artisan", "veterans-resolve", "master-of-all"))), "ten perks ship: " + c.perks.keySet());
        PerkDef hearty = c.perks.get("hearty-digger");
        expect(hearty.requires().equals(java.util.Map.of("lode", 15, "cuisine", 15)) && hearty.effects().size() == 1, "hearty digger");
        expect(c.perks.get("grand-artisan").minTotal() == 200 && c.perks.get("grand-artisan").requires().isEmpty(), "grand artisan asks for total level only");
        expect(c.perks.get("master-of-all").minPrestige() == 3 && c.perks.get("master-of-all").effects().size() == 2, "master of all needs 3 stars");
        expect(c.enabled && c.messages && c.perksEnabled && c.prestigeEnabled && c.broadcast, "switches are on");
        expect(c.refreshSeconds == 5 && c.topSize == 10 && c.maxStars == 5 && c.skillsNeeded == 3 && c.skillsNeededStep == 1
                && c.masteryFraction == 0.8 && c.strengthPerStar == 0.1 && c.confirmSeconds == 30 && c.rewardCommands.isEmpty(), "numbers");
        // every skill a shipped perk asks for is a skill that ships
        for (PerkDef p : c.perks.values()) {
            for (String need : p.requires().keySet()) {
                expect(ids.contains(need), p.id() + " asks for a skill that exists: " + need);
            }
        }
    }

    static void clamps() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("refresh-seconds", 0);
        y.set("top-size", 500);
        y.set("prestige.max-stars", 99);
        y.set("prestige.mastery-fraction", 0.0);
        y.set("prestige.skills-needed", 0);
        y.set("prestige.skills-needed-step", -4);
        y.set("prestige.strength-per-star", 50.0);
        y.set("prestige.confirm-seconds", 1);
        CharacterConfig c = new CharacterConfig(y);
        expect(c.refreshSeconds == 1 && c.topSize == 25 && c.maxStars == 10 && c.masteryFraction == 0.1 && c.skillsNeeded == 1
                && c.skillsNeededStep == 0 && c.strengthPerStar == 2.0 && c.confirmSeconds == 5, "silly numbers are held to sane ones");
        expect(c.skills.isEmpty() && c.perks.isEmpty() && c.problems.isEmpty(), "an empty file is fine: no skills, no perks");
        y = new YamlConfiguration();
        y.set("refresh-seconds", 9999);
        expect(new CharacterConfig(y).refreshSeconds == 60, "and the top end too");
    }

    static void badSkills() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("skills.good.plugin", "NexusLode");
        y.set("skills.good.xp-key", "nexuslode:lode_xp");
        y.set("skills.Bad Id.plugin", "X");
        y.set("skills.noplugin.xp-key", "a:b");
        y.set("skills.nokey.plugin", "NexusLode");
        y.set("skills.weird.plugin", "NexusLode");
        y.set("skills.weird.type", "magic-beans");
        y.set("skills.off.plugin", "NexusLode");
        y.set("skills.off.xp-key", "a:b");
        y.set("skills.off.enabled", false);
        y.set("skills.UPPER.plugin", "NexusLode");
        y.set("skills.UPPER.xp-key", "a:b");
        CharacterConfig c = new CharacterConfig(y);
        expect(c.skills.size() == 2 && c.skills.get(0).id().equals("good") && c.skills.get(1).id().equals("upper"), "only the usable skills load; ids are lowercased: " + c.skills);
        expect(c.problems.size() == 4, "four problems reported (bad id, no plugin, no key, bad type): " + c.problems);
        String all = String.join("|", c.problems);
        expect(all.contains("Bad Id") && all.contains("noplugin") && all.contains("nokey") && all.contains("weird"), "each names the skill: " + all);
        expect(c.skills.get(0).maxLevel() == 50 && c.skills.get(0).xpBase() == 10.0 && c.skills.get(0).reset() && c.skills.get(0).name().equals("good"),
                "defaults: max 50, base 10, resettable, named by its id");
    }

    static void badPerks() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("perks.ok.requires.lode", 5);
        y.set("perks.ok.effects", List.of("xp:10", "potion:NOPE", "fly:1"));
        y.set("perks.free.effects", List.of("xp:10"));
        y.set("perks.noeffects.requires.lode", 5);
        y.set("perks.allbad.requires.lode", 5);
        y.set("perks.allbad.effects", List.of("xp"));
        y.set("perks.Bad Id.requires.lode", 5);
        y.set("perks.Bad Id.effects", List.of("xp:5"));
        y.set("perks.zero.requires.lode", 0);
        y.set("perks.zero.effects", List.of("xp:5"));
        CharacterConfig c = new CharacterConfig(y);
        expect(c.perks.keySet().equals(java.util.Set.of("ok", "zero")), "usable perks only: " + c.perks.keySet());
        expect(c.perks.get("ok").effects().size() == 1, "the good effect of a perk with bad ones still works");
        expect(c.perks.get("zero").requires().get("lode") == 1, "a requirement of 0 would be free, so it is held to 1");
        String all = String.join("|", c.problems);
        expect(all.contains("potion") && all.contains("fly") && all.contains("free") && all.contains("noeffects") && all.contains("allbad") && all.contains("Bad Id"),
                "every mistake is named: " + all);
        expect(c.perks.get("ok").name().equals("ok") && c.perks.get("ok").description().isEmpty(), "name defaults to the id");
    }
}
