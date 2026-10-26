package com.nexuscraft.nexuscharacter;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;

import java.util.List;

import static com.nexuscraft.nexuscharacter.BukkitKit.CUISINE;
import static com.nexuscraft.nexuscharacter.BukkitKit.LODE;
import static com.nexuscraft.nexuscharacter.BukkitKit.REDSTONE;
import static com.nexuscraft.nexuscharacter.BukkitKit.xpAt;
import static com.nexuscraft.nexuscharacter.Kit.expect;

/** /sheet in all its forms, including what other people may see and what only admins may do. */
public class CommandTest {

    public static void main(String[] args) {
        sheet();
        others();
        perksPage();
        listings();
        admin();
        tabs();
        Kit.finish();
    }

    static BukkitKit.Rig rig() {
        BukkitKit.reset();
        BukkitKit.installFamily();
        return new BukkitKit.Rig();
    }

    static BukkitKit.TestPlayer ann(BukkitKit.Rig rig) {
        BukkitKit.TestPlayer ann = BukkitKit.join("Ann");
        ann.setXp(LODE, xpAt(15, 8.0));
        ann.setXp(CUISINE, xpAt(15, 10.0));
        ann.setXp(REDSTONE, xpAt(3, 10.0));
        rig.service.refresh(ann);
        ann.clear();
        return ann;
    }

    static void sheet() {
        BukkitKit.Rig rig = rig();
        BukkitKit.TestPlayer ann = ann(rig);
        String t = rig.say(ann);
        expect(t.contains("Ann's Character Sheet"), "titled with her name");
        expect(t.contains("Prestige ☆☆☆☆☆ | Total level 33 | Perks 1/10"), "the summary line: " + t);
        expect(t.contains("Mining 15/50 [###-------]"), "Mining with a bar (15/50 is 30%): " + t);
        expect(t.contains("Cooking 15/40 [####------]"), "Cooking (37.5% rounds to 4 of 10)");
        expect(t.contains("Engineering 3/50 [#---------]"), "a beginner has a sliver of a bar");
        expect(t.contains("Not started: Smelting, Alchemy, Fishing, Traversal"), "untouched skills are one tidy line: " + t);
        expect(t.contains("✔ Hearty Digger (+1 heart)"), "perks she holds, with what they do");
        expect(t.contains("Next: Tunnel Engineer needs Mining 20 (you: 15), Engineering 15 (you: 3)"), "the nearest locked perk and what it needs: " + t);
        expect(!t.contains("Veteran") && !t.contains("Master of All"), "prestige-only perks are not dangled in front of her");
        expect(!t.contains("MAX"), "no MAX yet");

        ann.setXp(LODE, xpAt(50, 8.0));
        t = rig.say(ann);
        expect(t.contains("Mining 50/50 [##########] MAX"), "the top is marked: " + t);

        // more perks than fit
        BukkitKit.reset();
        BukkitKit.installFamily();
        YamlConfiguration many = BukkitKit.shipped();
        for (int i = 0; i < 7; i++) {
            many.set("perks.extra" + i + ".min-total", 1);
            many.set("perks.extra" + i + ".name", "Extra " + i);
            many.set("perks.extra" + i + ".effects", List.of("xp:1"));
        }
        BukkitKit.Rig crowded = new BukkitKit.Rig(many);
        BukkitKit.TestPlayer di = ann(crowded);
        t = crowded.say(di);
        expect(t.contains("…and 3 more") && t.contains("Extra 0") && t.contains("Extra 3") && !t.contains("Extra 4"), "five perks are listed, the rest counted (8 held: Hearty Digger + 7 extras): " + t);
        rig = rig();
        ann = ann(rig);

        // /sheet and its aliases hit the same executor; the console must name someone
        expect(rig.sayConsole().contains("Usage: /sheet <player>"), "console without a name: usage");
        rig.console.perms.add("nexuscharacter.view-others");
        expect(rig.sayConsole("Ann").contains("Ann's Character Sheet"), "console may look at someone");

        // no skills at all
        BukkitKit.reset();
        BukkitKit.Rig empty = new BukkitKit.Rig();
        BukkitKit.TestPlayer bo = BukkitKit.join("Bo");
        t = empty.say(bo);
        expect(t.contains("No skills are installed yet") && !t.contains("Skills\n"), "nothing installed: " + t);

        // perks off
        BukkitKit.reset();
        BukkitKit.installFamily();
        YamlConfiguration y = BukkitKit.shipped();
        y.set("perks-enabled", false);
        BukkitKit.Rig noPerks = new BukkitKit.Rig(y);
        BukkitKit.TestPlayer cy = ann(noPerks);
        t = noPerks.say(cy);
        expect(!t.contains("Perks") && !t.contains("✔") && t.contains("Mining 15/50"), "perks-enabled: false hides the perk part but not the skills: " + t);
        expect(noPerks.say(cy, "perks").contains("switched off"), "and the perks page says why");
    }

    static void others() {
        BukkitKit.Rig rig = rig();
        BukkitKit.TestPlayer ann = ann(rig);
        BukkitKit.TestPlayer bo = BukkitKit.join("Bo");
        bo.setXp(LODE, xpAt(40, 8.0));
        rig.service.refresh(bo);
        bo.clear();
        expect(rig.say(bo, "Ann").contains("only look at your own sheet"), "without the permission: refused");
        expect(rig.say(bo, "bo").contains("Bo's Character Sheet"), "but your own name (any case) is fine");
        bo.perms.add("nexuscharacter.view-others");
        String t = rig.say(bo, "ann");
        expect(t.contains("Ann's Character Sheet") && t.contains("Mining 15/50"), "with it: Ann's sheet (any case): " + t);
        expect(rig.say(bo, "Nobody").contains("Nobody called Nobody"), "unknown name");

        // offline players are looked up by the name saved last time they were seen
        rig.service.onQuit(ann);
        Bukkit.PLAYERS.remove(ann.getUniqueId());
        t = rig.say(bo, "ANN");
        expect(t.contains("Ann's Character Sheet") && t.contains("Skills (last seen)") && t.contains("Mining 15/50") && t.contains("Perks 1/10"),
                "offline: the sheet from last time, marked: " + t);
        expect(!t.contains("unreadable"), "offline skills are not shown as unreadable");

        // a skill that throws is shown as such to someone online
        BukkitKit.Named owner = new BukkitKit.Named("Owner");
        rig.registry.register(owner, "flaky", "Flaky", 10, id -> {
            throw new IllegalStateException("x");
        }, null);
        bo.setXp(BukkitKit.ALCH, xpAt(5, 14.0));
        t = rig.say(bo, "bo");
        expect(t.contains("Flaky (unreadable right now)") && !t.contains("Not started: Flaky") && !t.contains(", Flaky"), "a skill that cannot be read is said to be unreadable, not 'not started': " + t);
        rig.registry.register(owner, "steady", "Steady", 10, id -> 4, null);
        expect(rig.say(bo, "bo").contains("Steady 4/10"), "a registered skill appears on the sheet beside the rest");
    }

    static void perksPage() {
        BukkitKit.Rig rig = rig();
        BukkitKit.TestPlayer ann = ann(rig);
        String t = rig.say(ann, "perks");
        expect(t.contains("Ann's perks (1 of 10)"), "heading: " + t);
        expect(t.contains("✔ Hearty Digger (+1 heart)") && t.contains("A miner who can cook never goes hungry underground."), "held perk with its flavour text");
        expect(t.contains("✘ Tunnel Engineer needs Mining 20 (you: 15), Engineering 15 (you: 3)"), "locked perk: what is missing");
        expect(t.contains("✘ Grand Artisan needs total level 200") && t.contains("✘ Veteran's Resolve needs Prestige 1"), "total and prestige perks too");
        expect(t.contains("✘ Trail Provisions needs Traversal 15 (you: 0), Cooking 10") == false, "only the unmet parts of a perk are listed");
        expect(t.contains("Trail Provisions needs Traversal 15 (you: 0)"), "Cooking 15 already covers Cooking 10: " + t);

        // a perk whose skill plugin is missing is marked, not failed
        ann.setXp(LODE, xpAt(20, 8.0));
        PluginManager.PLUGINS.removeIf(p -> p.getName().equals("NexusRedstone"));
        t = rig.say(ann, "perks");
        expect(t.contains("? Tunnel Engineer needs a skill that is not installed: redstone"), "unknown perk: " + t);

        // other people's perks
        BukkitKit.TestPlayer bo = BukkitKit.join("Bo");
        expect(rig.say(bo, "perks", "Ann").contains("only look at your own"), "needs the permission to see others'");
        bo.perms.add("nexuscharacter.view-others");
        expect(rig.say(bo, "perks", "Ann").contains("Ann's perks (1 of 10)"), "and shows theirs");
        expect(rig.say(bo, "perks").contains("Bo's perks (0 of 10)"), "bare: your own");
        expect(rig.sayConsole("perks").contains("Usage"), "console must name someone");
    }

    static void listings() {
        BukkitKit.Rig rig = rig();
        BukkitKit.TestPlayer ann = ann(rig);
        BukkitKit.TestPlayer bo = BukkitKit.join("Bo");
        bo.setXp(LODE, xpAt(40, 8.0));
        rig.service.refresh(bo);
        rig.store.rec(bo.getUniqueId()).prestige = 2;
        String t = rig.say(ann, "top");
        expect(t.contains("Top characters"), "heading");
        int boAt = t.indexOf("1. Bo ★★☆☆☆ total 40");
        int annAt = t.indexOf("2. Ann total 33");
        expect(boAt >= 0 && annAt > boAt, "Bo (two stars) above Ann: " + t);
        BukkitKit.reset();
        expect(new BukkitKit.Rig().say(BukkitKit.join("Zed"), "top").contains("Nobody has a sheet yet"), "empty board");

        rig = rig();
        ann = ann(rig);
        t = rig.say(ann, "skills");
        expect(t.contains("Mining (lode) from NexusLode, top level 50") && t.contains("Cooking (cuisine) from NexusCuisine, top level 40"), "skills listing: " + t);
        expect(!t.contains("cannot be reset"), "all seven can be reset");
        BukkitKit.Named magic = BukkitKit.install("NexusMagic", 1, 1);
        Bukkit.getServicesManager().register(com.nexusuniverse.magic.WizardLevelAPI.class, new RegistryTest.FakeWizard(), magic, org.bukkit.plugin.ServicePriority.Normal);
        expect(rig.say(ann, "skills").contains("Wizardry (magic) from NexusMagic, top level 30, cannot be reset"), "Wizardry says it cannot be reset");
        BukkitKit.reset();
        expect(new BukkitKit.Rig().say(BukkitKit.join("Zed"), "skills").contains("None. Install a skill plugin"), "no skills: says what to do");

        expect(rig.say(ann, "help").contains("/sheet perks"), "help");
    }

    static void admin() {
        BukkitKit.Rig rig = rig();
        BukkitKit.TestPlayer ann = ann(rig);
        BukkitKit.TestPlayer bo = BukkitKit.join("Bo");
        expect(rig.say(bo, "reload").contains("No permission") && rig.say(bo, "setprestige", "Ann", "3").contains("No permission"), "players cannot reload or set stars");
        expect(rig.service.prestigeOf(ann.getUniqueId()) == 0, "and nothing changed");

        bo.perms.add("nexuscharacter.admin");
        rig.command.setReload(() -> List.of("perks.x asks for nothing", "skills.y has no plugin"));
        String t = rig.say(bo, "reload");
        expect(t.contains("reloaded") && t.contains("- perks.x asks for nothing") && t.contains("- skills.y has no plugin"), "reload lists problems: " + t);
        rig.command.setReload(List::of);
        t = rig.say(bo, "reload");
        expect(t.contains("reloaded") && !t.contains("- "), "or none");

        expect(rig.say(bo, "setprestige").contains("Usage"), "usage");
        expect(rig.say(bo, "setprestige", "Ann").contains("Usage"), "usage 2");
        expect(rig.say(bo, "setprestige", "Ann", "lots").contains("not a number"), "not a number");
        expect(rig.say(bo, "setprestige", "Nobody", "2").contains("Nobody called Nobody"), "nobody");
        t = rig.say(bo, "setprestige", "Ann", "2");
        expect(t.contains("Ann now has 2 prestige stars") && rig.service.prestigeOf(ann.getUniqueId()) == 2, "set: " + t);
        expect(ann.text().contains("Perk unlocked: Veteran's Resolve"), "an online player is brought up to date at once: " + ann.text());
        expect(rig.say(bo, "setprestige", "Ann", "1").contains("1 prestige star."), "singular");
        rig.say(bo, "setprestige", "Ann", "99");
        expect(rig.service.prestigeOf(ann.getUniqueId()) == 5, "held to the most stars");
        rig.say(bo, "setprestige", "Ann", "-4");
        expect(rig.service.prestigeOf(ann.getUniqueId()) == 0, "and not below none");
        expect(!rig.store.dirty(), "saved at once");

        // by saved name when offline
        rig.service.onQuit(ann);
        Bukkit.PLAYERS.remove(ann.getUniqueId());
        expect(rig.say(bo, "setprestige", "ann", "3").contains("Ann now has 3") && rig.service.prestigeOf(ann.getUniqueId()) == 3, "an offline player, by the name saved last time");

        rig.console.perms.add("nexuscharacter.admin");
        expect(rig.sayConsole("setprestige", "Ann", "1").contains("Ann now has 1") && rig.sayConsole("reload").contains("reloaded"), "console admin");
    }

    static void tabs() {
        BukkitKit.Rig rig = rig();
        BukkitKit.TestPlayer ann = ann(rig);
        BukkitKit.TestPlayer bo = BukkitKit.join("Bo");
        org.bukkit.command.Command c = BukkitKit.command("sheet");
        List<String> first = rig.command.onTabComplete(bo, c, "sheet", new String[]{""});
        expect(first.containsAll(List.of("perks", "prestige", "top", "skills", "help", "Ann", "Bo")) && !first.contains("reload"), "subcommands and players, no admin ones: " + first);
        bo.perms.add("nexuscharacter.admin");
        first = rig.command.onTabComplete(bo, c, "sheet", new String[]{"re"});
        expect(first.equals(List.of("reload")), "admin sees reload: " + first);
        expect(rig.command.onTabComplete(bo, c, "sheet", new String[]{"PR"}).equals(List.of("prestige")), "any case");
        expect(rig.command.onTabComplete(bo, c, "sheet", new String[]{"A"}).equals(List.of("Ann")), "player names");
        expect(rig.command.onTabComplete(bo, c, "sheet", new String[]{"prestige", "c"}).equals(List.of("confirm")), "prestige confirm");
        expect(rig.command.onTabComplete(bo, c, "sheet", new String[]{"perks", ""}).containsAll(List.of("Ann", "Bo")), "perks <player>");
        expect(rig.command.onTabComplete(bo, c, "sheet", new String[]{"setprestige", "B"}).equals(List.of("Bo")), "setprestige <player>");
        expect(rig.command.onTabComplete(bo, c, "sheet", new String[]{"top", ""}).isEmpty(), "nothing after top");
        expect(rig.command.onTabComplete(bo, c, "sheet", new String[]{"perks", "Ann", ""}).isEmpty(), "nothing at the third word");
        expect(rig.command.onTabComplete(bo, c, "sheet", new String[0]).size() > 5, "no words at all does not crash");
        expect(ann.messages.isEmpty(), "tab completion says nothing to anyone");
    }
}
