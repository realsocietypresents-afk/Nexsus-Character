package com.nexuscraft.nexuscharacter;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static com.nexuscraft.nexuscharacter.BukkitKit.CUISINE;
import static com.nexuscraft.nexuscharacter.BukkitKit.LODE;
import static com.nexuscraft.nexuscharacter.BukkitKit.xpAt;
import static com.nexuscraft.nexuscharacter.Kit.expect;

/** Start-up wiring, the published service, saving, reload and shutdown. */
public class PluginTest {

    public static void main(String[] args) throws Exception {
        BukkitKit.reset();
        BukkitKit.installFamily();
        BukkitKit.TestPlayer ann = BukkitKit.join("Ann");
        ann.setXp(LODE, xpAt(15, 8.0));
        ann.setXp(CUISINE, xpAt(15, 10.0));

        NexusCharacterPlugin plugin = new NexusCharacterPlugin();
        plugin.onEnable();
        PluginCommand sheet = plugin.getCommand("sheet");
        expect(sheet.getExecutor() instanceof SheetCommand && sheet.getTabCompleter() instanceof SheetCommand, "/sheet is wired");
        expect(PluginManager.REGISTERED.stream().anyMatch(CharacterListener.class::isInstance), "the listener is registered");
        CharacterApi api = Bukkit.getServicesManager().load(CharacterApi.class);
        expect(api != null && api.skills().size() == 7, "the API is published and sees the installed skills");
        expect(BukkitScheduler.PERIODS.size() == 2 && BukkitScheduler.PERIODS.contains(100L) && BukkitScheduler.PERIODS.contains(6000L),
                "two timers: refresh every 5 s (100 ticks) and save: " + BukkitScheduler.PERIODS);
        expect(api.hasPerk(ann.getUniqueId(), "hearty-digger"), "the API answers for a player already online");
        expect(ann.getAttribute(ServiceTest.HEALTH).getValue() == 22.0, "players already online when the plugin starts (a reload) get their perks at once");

        // the refresh timer notices a level-up
        Runnable refresh = BukkitScheduler.REPEATING.get(0);
        ann.setXp(LODE, xpAt(5, 8.0));
        refresh.run();
        expect(ann.getAttribute(ServiceTest.HEALTH).getValue() == 20.0, "the timer re-reads skills");

        // a skill plugin registers through the service
        BukkitKit.Named other = new BukkitKit.Named("Fisher");
        expect(api.register(other, "deep", "Deep Sea", 10, id -> 6, null) && api.skills().contains("deep"), "registering through the published service works");

        // saving
        File dir = org.bukkit.plugin.java.JavaPlugin.DATA_FOLDER;
        Runnable saver = BukkitScheduler.REPEATING.get(1);
        saver.run();
        File players = new File(dir, "players.yml");
        expect(players.isFile() && Files.readString(players.toPath()).contains(ann.getUniqueId().toString()), "the save timer writes players.yml");
        long stamp = players.lastModified();
        Thread.sleep(20);
        saver.run();
        expect(players.lastModified() == stamp, "and does not rewrite it when nothing changed");

        // reload: new config, registered skills stay
        plugin.getConfig().set("perks-enabled", false);
        List<String> problems = reload(plugin);
        expect(problems.isEmpty(), "reload reports no problems");
        expect(ann.getAttribute(ServiceTest.HEALTH).getValue() == 20.0, "reload with perks off");
        expect(api.skills().contains("deep"), "a reload keeps skills other plugins registered");
        plugin.getConfig().set("perks.broken.requires.lode", 5);
        plugin.getConfig().set("perks.broken.effects", List.of("nonsense"));
        problems = reload(plugin);
        expect(problems.size() == 2 && String.join("|", problems).contains("nonsense"), "reload hands back config problems: " + problems);
        plugin.getConfig().set("perks-enabled", true);
        plugin.getConfig().set("perks.broken", null);
        plugin.getConfig().set("perks.broken.requires.lode", null);
        plugin.getConfig().set("perks.broken.effects", null);
        ann.setXp(LODE, xpAt(15, 8.0));
        reload(plugin);
        expect(ann.getAttribute(ServiceTest.HEALTH).getValue() == 22.0, "perks back on");

        // a skill hidden in the config disappears on reload
        expect(api.skills().contains("lode"), "Mining is on the sheet");
        plugin.getConfig().set("skills.lode.enabled", false);
        reload(plugin);
        expect(!api.skills().contains("lode") && api.skills().contains("cuisine"), "skills.lode.enabled: false and a reload takes it off the sheet");
        plugin.getConfig().set("skills.lode.enabled", true);
        reload(plugin);

        // shutdown
        plugin.onDisable();
        expect(Bukkit.getServicesManager().load(CharacterApi.class) == null, "stopping withdraws the API");
        expect(BukkitScheduler.HANDLES.stream().allMatch(h -> h.isCancelled()), "and cancels the timers");
        expect(ann.getAttribute(ServiceTest.HEALTH).getValue() == 20.0, "and takes every perk off, so none is left in the save files");
        expect(Files.readString(players.toPath()).contains("hearty-digger"), "and saves what players had");
        plugin.onDisable();
        expect(true, "stopping twice is fine");

        // the shipped files agree with the code
        String yml = Files.readString(new File("src/main/resources/plugin.yml").toPath());
        for (String must : List.of("main: com.nexuscraft.nexuscharacter.NexusCharacterPlugin", "sheet:", "aliases: [character, charsheet]",
                "nexuscharacter.view-others:", "nexuscharacter.prestige:", "nexuscharacter.admin:",
                "softdepend: [NexusLode, NexusSmelting, NexusCuisine, NexusAlchemy, NexusAngler, NexusMomentum, NexusRedstone, NexusMagic]")) {
            expect(yml.contains(must), "plugin.yml has " + must);
        }
        // every family skill in config.yml is a soft dependency, so it loads first
        for (CharacterConfig.SkillCfg s : new CharacterConfig(BukkitKit.shipped()).skills) {
            expect(yml.contains(s.plugin()), "plugin.yml soft-depends on " + s.plugin());
        }

        // starting with a damaged config does not stop it
        BukkitKit.reset();
        NexusCharacterPlugin bad = new NexusCharacterPlugin();
        bad.getConfig().set("skills.x.plugin", "Nope");
        bad.onEnable();
        expect(Bukkit.getServicesManager().load(CharacterApi.class) != null, "a skill with no xp-key is reported and skipped; the plugin still starts");
        bad.onDisable();
        Kit.finish();
    }

    static List<String> reload(NexusCharacterPlugin plugin) throws Exception {
        java.lang.reflect.Method m = NexusCharacterPlugin.class.getDeclaredMethod("reloadAll");
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> r = (List<String>) m.invoke(plugin);
        return r;
    }
}
