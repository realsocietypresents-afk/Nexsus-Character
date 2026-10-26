package com.nexuscraft.nexuscharacter;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * NexusCharacter: one sheet for all your skills. Reads the Nexus skill plugins (and anything registered with the shared skill registry),
 * grants cross-skill perks, and offers prestige to veterans.
 */
public final class NexusCharacterPlugin extends JavaPlugin {

    private CharacterConfig config;
    private SkillRegistry registry;
    private CharacterStore store;
    private CharacterService service;
    private PerkApplier applier;
    private SheetCommand command;
    private BukkitTask ticker;
    private BukkitTask saver;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.config = new CharacterConfig(getConfig());
        this.registry = new SkillRegistry(getLogger());
        registry.setFamily(FamilySkills.build(config.skills, getLogger()));
        this.store = new CharacterStore(new File(getDataFolder(), "players.yml"), getLogger());
        store.load();
        this.applier = new PerkApplier(this);
        this.service = new CharacterService(this, config, registry, store, applier, System::currentTimeMillis);
        getServer().getPluginManager().registerEvents(new CharacterListener(service), this);
        this.command = new SheetCommand(service, new Prestige(service));
        command.setReload(this::reloadAll);
        getCommand("sheet").setExecutor(command);
        getCommand("sheet").setTabCompleter(command);
        getServer().getServicesManager().register(CharacterApi.class, new ApiImpl(service), this, ServicePriority.Normal);
        long every = config.refreshSeconds * 20L;
        this.ticker = getServer().getScheduler().runTaskTimer(this, service::tick, 40L, every);
        this.saver = getServer().getScheduler().runTaskTimer(this, service::saveIfDirty, 6000L, 6000L);
        for (Player p : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            service.onJoin(p);
        }
        for (String problem : config.problems) {
            getLogger().warning("config.yml: " + problem);
        }
        getLogger().info("NexusCharacter enabled -- " + registry.available().size() + " skill(s), " + config.perks.size() + " perk(s).");
    }

    @Override
    public void onDisable() {
        for (BukkitTask t : new BukkitTask[]{ticker, saver}) {
            if (t != null) {
                t.cancel();
            }
        }
        if (service != null) {
            for (Player p : new ArrayList<>(Bukkit.getOnlinePlayers())) {
                service.refresh(p);
            }
            service.clearAll();
        }
        if (store != null) {
            store.save();
        }
        Bukkit.getServicesManager().unregisterAll(this);
    }

    /** Re-reads config.yml; the skills other plugins registered stay. Returns what in the file could not be used. */
    private List<String> reloadAll() {
        reloadConfig();
        this.config = new CharacterConfig(getConfig());
        service.setConfig(config);
        registry.setFamily(FamilySkills.build(config.skills, getLogger()));
        if (!config.perksEnabled) {
            service.clearAll();
        }
        for (Player p : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            service.refresh(p);
        }
        return config.problems;
    }
}
