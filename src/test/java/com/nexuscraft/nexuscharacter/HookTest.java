package com.nexuscraft.nexuscharacter;

import com.nexuscraft.nexuscharacter.adapters.CharacterHook;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.nexuscraft.nexuscharacter.Kit.expect;

/** The drop-in adapter other plugins copy: it must work without a compile-time link, and survive NexusCharacter loading late or never. */
public class HookTest {

    public static void main(String[] args) {
        present();
        late();
        never();
        refused();
        Kit.finish();
    }

    static void present() {
        BukkitKit.reset();
        BukkitKit.Rig rig = new BukkitKit.Rig();
        Bukkit.getServicesManager().register(CharacterApi.class, new ApiImpl(rig.service), rig.plugin, ServicePriority.Normal);
        BukkitKit.Named guild = new BukkitKit.Named("NexusCraftersGuild");
        AtomicInteger wiped = new AtomicInteger();
        CharacterHook.register(guild, "smithing", "Smithing", 40, id -> 12, id -> wiped.incrementAndGet());
        CharacterService.Row row = rig.service.snapshot(UUID.randomUUID()).rows().get(0);
        expect(row.def().id().equals("smithing") && row.def().name().equals("Smithing") && row.max() == 40 && row.level() == 12,
                "registered at once through the service: " + rig.registry.available());
        expect(row.def().resettable() && row.def().source().equals("NexusCraftersGuild"), "with its reset, and credited to its plugin");
        expect(rig.registry.reset(UUID.randomUUID(), row.def()) && wiped.get() == 1, "the reset reaches the plugin");
        expect(BukkitScheduler.LATER.isEmpty(), "nothing to retry");

        CharacterHook.register(guild, "plain", "Plain", 5, id -> 1, null);
        expect(!BukkitKit.find(rig.registry, "plain").resettable(), "no reset given: not resettable");
    }

    static void late() {
        BukkitKit.reset();
        BukkitKit.Rig rig = new BukkitKit.Rig();
        BukkitKit.Named guild = new BukkitKit.Named("NexusCraftersGuild");
        CharacterHook.register(guild, "smithing", "Smithing", 40, id -> 12, null);
        expect(rig.registry.available().isEmpty(), "NexusCharacter not loaded yet: nothing registered");
        expect(BukkitScheduler.DELAYS.equals(java.util.List.of(1L, 100L, 600L)), "it will look again after 1 tick, 5 s and 30 s: " + BukkitScheduler.DELAYS);
        Bukkit.getServicesManager().register(CharacterApi.class, new ApiImpl(rig.service), rig.plugin, ServicePriority.Normal);
        BukkitScheduler.LATER.get(0).run();
        expect(rig.registry.available().size() == 1, "NexusCharacter turned up: registered on the next look");
        BukkitScheduler.LATER.get(1).run();
        BukkitScheduler.LATER.get(2).run();
        expect(rig.registry.available().size() == 1, "later looks do nothing more");
    }

    static void never() {
        BukkitKit.reset();
        BukkitKit.Named guild = new BukkitKit.Named("NexusCraftersGuild");
        CharacterHook.register(guild, "smithing", "Smithing", 40, id -> 12, null);
        for (Runnable r : BukkitScheduler.LATER) {
            r.run();
        }
        expect(Bukkit.getServicesManager().load(CharacterApi.class) == null, "still not installed: nothing happened, nothing crashed");
    }

    static void refused() {
        BukkitKit.reset();
        BukkitKit.Rig rig = new BukkitKit.Rig();
        Bukkit.getServicesManager().register(CharacterApi.class, new ApiImpl(rig.service), rig.plugin, ServicePriority.Normal);
        BukkitKit.Named guild = new BukkitKit.Named("NexusCraftersGuild");
        CharacterHook.register(guild, "bad id!", "Smithing", 40, id -> 12, null);
        expect(rig.registry.available().isEmpty(), "a refused registration (bad id) registers nothing");
        expect(BukkitScheduler.LATER.size() == 3, "and is retried like any failure");
    }
}
