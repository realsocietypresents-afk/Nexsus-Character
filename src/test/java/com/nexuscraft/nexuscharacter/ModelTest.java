package com.nexuscraft.nexuscharacter;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.potion.PotionEffectType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.nexuscraft.nexuscharacter.Kit.expect;

/** The pure arithmetic: the family's level curve, perk rules, effect lines and the words. */
public class ModelTest {

    public static void main(String[] args) {
        curve();
        perkRules();
        effects();
        words();
        Kit.finish();
    }

    static void curve() {
        // the same numbers NexusLode's own MiningSkill gives: xp-base * level^1.5, rounded
        expect(FamilySkills.xpForLevel(1, 8.0) == 8, "level 1 costs the base");
        expect(FamilySkills.xpForLevel(4, 8.0) == 64, "level 4 costs 8*8");
        expect(FamilySkills.xpForLevel(15, 8.0) == 465, "level 15 costs 465");
        expect(FamilySkills.levelFor(0, 50, 8.0) == 1, "no xp is level 1 (the sheet shows 0 for 'not started' itself)");
        expect(FamilySkills.levelFor(464, 50, 8.0) == 14, "one xp short of 15 is 14");
        expect(FamilySkills.levelFor(465, 50, 8.0) == 15, "exactly enough is 15");
        expect(FamilySkills.levelFor(Long.MAX_VALUE, 50, 8.0) == 50, "capped at the top level");
        expect(FamilySkills.levelFor(Long.MAX_VALUE, 1, 8.0) == 1, "a top level of 1 is always 1");
        for (int lv = 2; lv <= 40; lv++) {
            long need = FamilySkills.xpForLevel(lv, 10.0);
            expect(FamilySkills.levelFor(need, 40, 10.0) == lv && FamilySkills.levelFor(need - 1, 40, 10.0) == lv - 1,
                    "every level boundary is exact: " + lv);
        }
    }

    static Map<String, Integer> levels(Object... kv) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], (Integer) kv[i + 1]);
        }
        return m;
    }

    static PerkDef perk(String id, Map<String, Integer> requires, int minPrestige, int minTotal) {
        return new PerkDef(id, id, "", requires, minPrestige, minTotal, List.of(new Effect.Xp(10)));
    }

    static void perkRules() {
        PerkDef both = perk("both", levels("a", 10, "b", 5), 0, 0);
        PerkDef star = perk("star", levels(), 2, 0);
        PerkDef total = perk("total", levels(), 0, 30);

        List<PerkEngine.Status> s = PerkEngine.evaluate(List.of(both), levels("a", 10, "b", 5), 15, 0);
        expect(s.get(0).active() && s.get(0).gaps().isEmpty(), "exactly the levels asked for is enough");
        s = PerkEngine.evaluate(List.of(both), levels("a", 10, "b", 4), 14, 0);
        expect(!s.get(0).active() && s.get(0).gaps().size() == 1 && s.get(0).gaps().get(0).skill().equals("b")
                && s.get(0).gaps().get(0).need() == 5 && s.get(0).gaps().get(0).have() == 4, "one level short names the skill");
        expect(!s.get(0).unknown(), "a short level is not 'unknown'");
        s = PerkEngine.evaluate(List.of(both), levels("a", 1, "b", 1), 2, 0);
        expect(s.get(0).gaps().size() == 2, "both gaps are listed");
        s = PerkEngine.evaluate(List.of(both), levels("a", 10), 10, 0);
        expect(!s.get(0).active() && s.get(0).unknown(), "a skill that is not installed makes the perk unknown, not lost");
        s = PerkEngine.evaluate(List.of(both), levels("a", 3), 3, 0);
        expect(!s.get(0).active() && !s.get(0).unknown(), "a short skill AND a missing one is just short");

        s = PerkEngine.evaluate(List.of(star), levels(), 0, 1);
        expect(!s.get(0).active() && s.get(0).prestigeShort(), "one star is not two");
        s = PerkEngine.evaluate(List.of(star), levels(), 0, 2);
        expect(s.get(0).active(), "two stars is two");
        s = PerkEngine.evaluate(List.of(total), levels("a", 29), 29, 0);
        expect(!s.get(0).active() && s.get(0).totalShort(), "29 is not 30");
        s = PerkEngine.evaluate(List.of(total), levels("a", 30), 30, 0);
        expect(s.get(0).active(), "30 is 30");

        PerkEngine.Status near = PerkEngine.evaluate(List.of(both), levels("a", 9, "b", 5), 14, 0).get(0);
        PerkEngine.Status far = PerkEngine.evaluate(List.of(both), levels("a", 1, "b", 1), 2, 0).get(0);
        expect(near.distance() < far.distance(), "a perk one level away is nearer than one far away");
        expect(PerkEngine.evaluate(List.of(both), levels("a", 10, "b", 5), 15, 0).get(0).distance() == 0, "an earned perk is no distance away");
    }

    static void effects() {
        Effect e = Effect.parse("potion:HASTE:0");
        expect(e instanceof Effect.Potion p && p.type() == PotionEffectType.HASTE && p.amplifier() == 0, "potion:HASTE:0");
        e = Effect.parse(" Potion : night_vision ");
        expect(e instanceof Effect.Potion p && p.type() == PotionEffectType.NIGHT_VISION, "spaces and case do not matter");
        e = Effect.parse("potion:SPEED:99");
        expect(e instanceof Effect.Potion p && p.amplifier() == 9, "levels are held to 0..9");
        e = Effect.parse("attribute:MAX_HEALTH:2:add");
        expect(e instanceof Effect.Attr a && a.attribute() == Attribute.GENERIC_MAX_HEALTH && a.amount() == 2
                && a.op() == AttributeModifier.Operation.ADD_NUMBER, "attribute:MAX_HEALTH:2:add resolves GENERIC_MAX_HEALTH");
        e = Effect.parse("attribute:MOVEMENT_SPEED:0.04:scalar");
        expect(e instanceof Effect.Attr a && a.op() == AttributeModifier.Operation.ADD_SCALAR, "scalar");
        e = Effect.parse("attribute:ARMOR:0.1:multiply");
        expect(e instanceof Effect.Attr a && a.op() == AttributeModifier.Operation.MULTIPLY_SCALAR_1, "multiply");
        e = Effect.parse("attribute:LUCK:1");
        expect(e instanceof Effect.Attr a && a.op() == AttributeModifier.Operation.ADD_NUMBER, "add is the default");
        e = Effect.parse("xp:12.5");
        expect(e instanceof Effect.Xp x && x.percent() == 12.5, "xp:12.5");

        for (String bad : new String[]{"", "potion", "potion:NOPE", "potion:HASTE:x", "potion:HASTE:1:2", "attribute:MAX_HEALTH", "attribute:NOPE:1",
                "attribute:LUCK:abc", "attribute:LUCK:0", "attribute:LUCK:NaN", "attribute:LUCK:1:sideways", "xp", "xp:0", "xp:-5", "xp:501", "xp:NaN",
                "fly:1", "xp:1:2"}) {
            boolean threw = false;
            try {
                Effect.parse(bad);
            } catch (IllegalArgumentException ex) {
                threw = !ex.getMessage().isBlank();
            }
            expect(threw, "'" + bad + "' is refused with a reason");
        }
        expect(Effect.attribute("MAX_HEALTH") == Attribute.GENERIC_MAX_HEALTH && Effect.attribute("NOPE") == null, "attribute lookup");

        expect(Effect.parse("attribute:MAX_HEALTH:2:add").describe(1.0).equals("+1 heart"), "2 health is a heart");
        expect(Effect.parse("attribute:MAX_HEALTH:2:add").describe(1.5).equals("+1.5 hearts"), "scaled by 1.5 it is 1.5 hearts");
        expect(Effect.parse("attribute:MOVEMENT_SPEED:0.04:scalar").describe(1.0).equals("+4% movement speed"), "scalar as a percentage");
        expect(Effect.parse("attribute:LUCK:1:add").describe(1.0).equals("+1 luck"), "luck");
        expect(Effect.parse("xp:10").describe(1.2).equals("+12% experience"), "xp scaled");
        expect(Effect.parse("potion:HASTE:0").describe(5.0).equals("haste"), "potions do not scale or show a level I");
        expect(Effect.parse("potion:HASTE:1").describe(1.0).equals("haste II"), "a level II potion says so");
    }

    static void words() {
        expect(Texts.stars(2, 5).replaceAll("§.", "").equals("★★☆☆☆"), "two of five stars");
        expect(Texts.stars(0, 3).replaceAll("§.", "").equals("☆☆☆"), "no stars");
        expect(Texts.stars(5, 5).replaceAll("§.", "").equals("★★★★★"), "all stars");
        expect(Kit.strip(Texts.bar(5, 10, 10)).equals("[#####-----]"), "half a bar");
        expect(Kit.strip(Texts.bar(0, 10, 10)).equals("[----------]"), "empty bar");
        expect(Kit.strip(Texts.bar(10, 10, 10)).equals("[##########]"), "full bar");
        expect(Kit.strip(Texts.bar(99, 10, 10)).equals("[##########]"), "too much is still a full bar");
        expect(Kit.strip(Texts.bar(1, 0, 10)).equals("[----------]"), "a top level of 0 does not divide by zero");
    }
}
