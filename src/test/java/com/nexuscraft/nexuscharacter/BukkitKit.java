package com.nexuscraft.nexuscharacter;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Test doubles for the server: players that remember what they were told, fake skill plugins, a clock. */
final class BukkitKit {

    private BukkitKit() {
    }

    static final class TestPlugin extends JavaPlugin {
    }

    /** A plugin with a chosen name (NexusLode and friends), and its own config. */
    static final class Named extends JavaPlugin {
        private final String name;
        boolean enabled = true;

        Named(String name) {
            this.name = name;
        }

        @Override public String getName() { return name; }
        @Override public boolean isEnabled() { return enabled; }
    }

    static class TestPlayer extends Player {
        final String name;
        final List<String> messages = new ArrayList<>();
        final Set<String> perms = new HashSet<>();

        TestPlayer(String name) {
            this.name = name;
        }

        @Override public String getName() { return name; }
        @Override public boolean isOnline() { return true; }
        @Override public boolean hasPermission(String node) { return perms.contains(node); }
        @Override public void sendMessage(String m) { messages.add(m); }

        String text() {
            return Kit.strip(String.join("\n", messages));
        }

        boolean has(org.bukkit.potion.PotionEffectType type) {
            return hasPotionEffect(type);
        }

        void clear() {
            messages.clear();
        }

        void setXp(String key, long xp) {
            getPersistentDataContainer().set(NamespacedKey.fromString(key), PersistentDataType.LONG, xp);
        }

        Long xp(String key) {
            return getPersistentDataContainer().get(NamespacedKey.fromString(key), PersistentDataType.LONG);
        }
    }

    /** A command sender that is not a player. */
    static final class Console implements org.bukkit.command.CommandSender {
        final List<String> out = new ArrayList<>();
        final Set<String> perms = new HashSet<>();

        @Override public boolean hasPermission(String node) { return perms.contains(node); }
        @Override public void sendMessage(String message) { out.add(message); }
        @Override public void sendMessage(net.kyori.adventure.text.Component message) { }
        @Override public String getName() { return "CONSOLE"; }

        String text() {
            return Kit.strip(String.join("\n", out));
        }
    }

    static File reset() {
        Bukkit.WORLDS.clear();
        Bukkit.PLAYERS.clear();
        Bukkit.DISPATCHED.clear();
        Bukkit.BROADCASTS.clear();
        BukkitScheduler.LATER.clear();
        BukkitScheduler.REPEATING.clear();
        BukkitScheduler.HANDLES.clear();
        BukkitScheduler.DELAYS.clear();
        BukkitScheduler.PERIODS.clear();
        PluginManager.REGISTERED.clear();
        PluginManager.PLUGINS.clear();
        Bukkit.getServicesManager().clear();
        return freshDir();
    }

    /** A new, empty data folder for the plugin under test. */
    static File freshDir() {
        try {
            File dir = Files.createTempDirectory("nxcharacter").toFile();
            JavaPlugin.DATA_FOLDER = dir;
            return dir;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The shipped config.yml, loaded through the sandbox's own file format (see tools/yaml-to-stub.py). */
    static YamlConfiguration shipped() {
        String path = System.getProperty("shipped");
        if (path == null) {
            throw new IllegalStateException("run through tools/run-tests.sh: -Dshipped is missing");
        }
        return YamlConfiguration.loadConfiguration(new File(path));
    }

    static World world() {
        return Bukkit.createWorld(new WorldCreator("world"));
    }

    static TestPlayer join(String name) {
        TestPlayer p = new TestPlayer(name);
        p.stubSetLocation(new Location(world(), 100, 70, 100));
        Bukkit.PLAYERS.put(p.getUniqueId(), p);
        return p;
    }

    /** Installs a pretend skill plugin with its own top level and curve. */
    static Named install(String pluginName, int maxLevel, double base) {
        Named n = new Named(pluginName);
        n.getConfig().set("skill.max-level", maxLevel);
        n.getConfig().set("skill.xp-base", base);
        PluginManager.PLUGINS.add(n);
        return n;
    }

    /** Installs all seven xp skills with the shipped defaults. */
    static void installFamily() {
        install("NexusLode", 50, 8.0);
        install("NexusSmelting", 50, 6.0);
        install("NexusCuisine", 40, 10.0);
        install("NexusAlchemy", 20, 14.0);
        install("NexusAngler", 20, 12.0);
        install("NexusMomentum", 50, 6.0);
        install("NexusRedstone", 50, 10.0);
    }

    static final String LODE = "nexuslode:lode_xp";
    static final String CUISINE = "nexuscuisine:cuisine_xp";
    static final String SMELT = "nexussmelting:smelting_xp";
    static final String ALCH = "nexusalchemy:alchemy_xp";
    static final String ANGLER = "nexusangler:angler_xp";
    static final String MOMENTUM = "nexusmomentum:momentum_xp";
    static final String REDSTONE = "nexusredstone:engineer_xp";

    /** The XP that puts a skill exactly on this level. */
    static long xpAt(int level, double base) {
        return FamilySkills.xpForLevel(level, base);
    }

    /** A skill by id (any case), or null. */
    static SkillDef find(SkillRegistry registry, String id) {
        if (id == null) {
            return null;
        }
        for (SkillDef d : registry.available()) {
            if (d.id().equalsIgnoreCase(id)) {
                return d;
            }
        }
        return null;
    }

    static Command command(String name) {
        return new Command() {
            @Override
            public String getName() {
                return name;
            }
        };
    }

    /** One running server: the plugin pieces, wired as NexusCharacterPlugin wires them, and a clock that only moves when told to. */
    static final class Rig {
        final File dir = freshDir();
        final TestPlugin plugin = new TestPlugin();
        final AtomicLong now = new AtomicLong(1_000_000L);
        CharacterConfig config;
        SkillRegistry registry;
        CharacterStore store;
        PerkApplier applier;
        CharacterService service;
        Prestige prestige;
        SheetCommand command;
        final Console console = new Console();

        Rig() {
            this(shipped());
        }

        Rig(YamlConfiguration cfg) {
            Bukkit.CONSOLE = console;
            build(cfg);
        }

        void build(YamlConfiguration cfg) {
            this.config = new CharacterConfig(cfg);
            this.registry = new SkillRegistry(plugin.getLogger());
            registry.setFamily(FamilySkills.build(config.skills, plugin.getLogger()));
            this.store = new CharacterStore(new File(dir, "players.yml"), plugin.getLogger());
            store.load();
            this.applier = new PerkApplier(plugin);
            this.service = new CharacterService(plugin, config, registry, store, applier, now::get);
            this.prestige = new Prestige(service);
            this.command = new SheetCommand(service, prestige);
        }

        /** Pretend the server restarted: same data folder, brand new objects. */
        Rig restart() {
            build(shipped());
            return this;
        }

        List<String> run(TestPlayer p, String... args) {
            p.clear();
            command.onCommand(p, command("sheet"), "sheet", args);
            return new ArrayList<>(p.messages);
        }

        String say(TestPlayer p, String... args) {
            run(p, args);
            return p.text();
        }

        String sayConsole(String... args) {
            console.out.clear();
            command.onCommand(console, command("sheet"), "sheet", args);
            return console.text();
        }
    }
}
