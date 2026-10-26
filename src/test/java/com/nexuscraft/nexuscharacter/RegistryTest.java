package com.nexuscraft.nexuscharacter;

import com.nexusuniverse.magic.WizardLevelAPI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.nexuscraft.nexuscharacter.BukkitKit.CUISINE;
import static com.nexuscraft.nexuscharacter.BukkitKit.LODE;
import static com.nexuscraft.nexuscharacter.BukkitKit.xpAt;
import static com.nexuscraft.nexuscharacter.Kit.expect;

/** The shared skill registry other plugins use, the service they see, and the NexusMagic bridge. */
public class RegistryTest {

    public static void main(String[] args) {
        registering();
        ownership();
        api();
        magic();
        Kit.finish();
    }

    static void registering() {
        BukkitKit.reset();
        BukkitKit.installFamily();
        BukkitKit.Rig rig = new BukkitKit.Rig();
        CharacterApi api = new ApiImpl(rig.service);
        BukkitKit.Named fisher = new BukkitKit.Named("DeepFish");
        expect(api.register(fisher, "Deep-Sea", "Deep Sea", 30, id -> 7, null), "a good registration is accepted");
        expect(api.skills().contains("deep-sea"), "ids are lowercased");
        expect(api.skills().size() == 8, "it sits beside the seven installed: " + api.skills());

        expect(!api.register(null, "a", "A", 5, id -> 1, null), "no owner");
        expect(!api.register(fisher, null, "A", 5, id -> 1, null), "no id");
        expect(!api.register(fisher, "", "A", 5, id -> 1, null), "empty id");
        expect(!api.register(fisher, "has space", "A", 5, id -> 1, null), "bad id: space");
        expect(!api.register(fisher, "a".repeat(33), "A", 5, id -> 1, null), "bad id: too long");
        expect(api.register(fisher, "a".repeat(32), "A", 5, id -> 1, null), "32 characters is fine");
        expect(!api.register(fisher, "UPPER_ok", null, 5, id -> 1, null) && !api.register(fisher, "x", "  ", 5, id -> 1, null), "no name");
        expect(!api.register(fisher, "x", "X", 0, id -> 1, null), "a top level of 0");
        expect(!api.register(fisher, "x", "X", 5, null, null), "no way to read the level");
        expect(api.register(fisher, "x_y-9", "X", 1, id -> 1, null), "underscores, dashes and digits are fine");

        // re-registering by the same plugin replaces (a reload)
        expect(api.register(fisher, "deep-sea", "Deeper Sea", 40, id -> 9, null), "the owner may register again");
        CharacterService.Row row = rig.service.snapshot(UUID.randomUUID()).rows().stream().filter(r -> r.def().id().equals("deep-sea")).findFirst().orElseThrow();
        expect(row.def().name().equals("Deeper Sea") && row.max() == 40 && row.level() == 9, "and the new details win");

        // a level above the top is held to the top; negative means unreadable
        api.register(fisher, "over", "Over", 10, id -> 999, null);
        api.register(fisher, "under", "Under", 10, id -> -5, null);
        UUID anyone = UUID.randomUUID();
        expect(api.level(anyone, "over") == 10, "too high is held to the top level");
        expect(api.level(anyone, "under") == 0, "negative is unreadable, so 0 for a stranger");
        expect(api.level(anyone, "nothing") == 0, "an unknown skill is 0");

        // a registration beats a family skill of the same id and keeps its place
        BukkitKit.Named custom = new BukkitKit.Named("MyMining");
        expect(api.register(custom, "lode", "Deep Mining", 99, id -> 77, null), "a plugin may take over a family id");
        List<SkillDef> all = rig.registry.available();
        expect(all.get(0).id().equals("lode") && all.get(0).name().equals("Deep Mining"), "it replaces the family skill in the same place: " + all.get(0).name());
        expect(api.level(anyone, "lode") == 77, "and is what is read");
        api.unregisterAll(custom);
        expect(rig.registry.available().get(0).name().equals("Mining"), "withdrawn, the family skill comes back");
    }

    static void ownership() {
        BukkitKit.reset();
        BukkitKit.installFamily();
        BukkitKit.Rig rig = new BukkitKit.Rig();
        CharacterApi api = new ApiImpl(rig.service);
        BukkitKit.Named one = new BukkitKit.Named("One");
        BukkitKit.Named two = new BukkitKit.Named("Two");
        expect(api.register(one, "shared", "One's", 10, id -> 1, null), "first owner");
        expect(!api.register(two, "shared", "Two's", 10, id -> 2, null), "a second plugin cannot take the id while the first is running");
        expect(api.level(UUID.randomUUID(), "shared") == 1, "the first one stays");
        one.enabled = false;
        expect(!api.skills().contains("shared"), "a disabled owner's skill leaves the sheet");
        expect(api.register(two, "shared", "Two's", 10, id -> 2, null), "and its id is free");
        expect(api.level(UUID.randomUUID(), "shared") == 2, "the new owner is read");
        api.unregisterAll(one);
        expect(api.skills().contains("shared"), "unregistering someone else's leaves yours alone");
        api.unregisterAll(two);
        expect(!api.skills().contains("shared"), "unregistering your own removes it");
        api.unregisterAll(two);
        expect(true, "twice is fine");

        // an exception in a skill is contained
        AtomicInteger calls = new AtomicInteger();
        two.enabled = true;
        api.register(two, "flaky", "Flaky", 10, id -> {
            calls.incrementAndGet();
            throw new IllegalStateException("nope");
        }, id -> {
            throw new IllegalStateException("nope too");
        });
        expect(api.level(UUID.randomUUID(), "flaky") == 0, "a skill that throws reads as 0 for a stranger and does not break the sheet");
        expect(calls.get() > 0, "it was asked");
        SkillDef flaky = BukkitKit.find(rig.registry, "flaky");
        expect(rig.registry.read(UUID.randomUUID(), flaky) == -1, "unreadable");
        expect(!rig.registry.reset(UUID.randomUUID(), flaky), "a reset that throws reports failure");
        expect(!rig.registry.reset(UUID.randomUUID(), BukkitKit.find(rig.registry, "nothing")), "an unknown skill cannot be reset");
        api.register(two, "fixed", "Fixed", 10, id -> 4, id -> { });
        expect(rig.registry.reset(UUID.randomUUID(), BukkitKit.find(rig.registry, "fixed")), "a working reset reports success");
        api.register(two, "noreset", "No Reset", 10, id -> 4, null);
        expect(!BukkitKit.find(rig.registry, "noreset").resettable() && !rig.registry.reset(UUID.randomUUID(), BukkitKit.find(rig.registry, "noreset")), "no reset given: cannot be reset");
        expect(BukkitKit.find(rig.registry, null) == null && BukkitKit.find(rig.registry, "FIXED") != null, "find ignores case and survives null");
    }

    static void api() {
        BukkitKit.reset();
        BukkitKit.installFamily();
        BukkitKit.Rig rig = new BukkitKit.Rig();
        CharacterApi api = new ApiImpl(rig.service);
        BukkitKit.TestPlayer ann = BukkitKit.join("Ann");
        ann.setXp(LODE, xpAt(15, 8.0));
        ann.setXp(CUISINE, xpAt(15, 10.0));
        expect(api.level(ann.getUniqueId(), "lode") == 15 && api.level(ann.getUniqueId(), "LODE") == 15 && api.level(ann.getUniqueId(), "cuisine") == 15, "levels");
        expect(api.totalLevel(ann.getUniqueId()) == 30, "total");
        expect(api.prestige(ann.getUniqueId()) == 0, "no stars");
        expect(api.hasPerk(ann.getUniqueId(), "hearty-digger") && api.hasPerk(ann.getUniqueId(), "HEARTY-DIGGER"), "perk held (any case)");
        expect(!api.hasPerk(ann.getUniqueId(), "lamplighter") && !api.hasPerk(ann.getUniqueId(), null), "perk not held; null is safe");
        expect(api.perks(ann.getUniqueId()).equals(java.util.Set.of("hearty-digger")), "perk set");
        rig.store.rec(ann.getUniqueId()).prestige = 1;
        expect(api.prestige(ann.getUniqueId()) == 1 && api.perks(ann.getUniqueId()).contains("veterans-resolve"), "a star counts and unlocks Veteran's Resolve");
        expect(api.skills().containsAll(List.of("lode", "cuisine")) && !api.skills().contains("magic"), "skills lists what is installed");
        expect(api.skills().iterator().next().equals("lode"), "in the configured order");
    }

    /** A pretend NexusMagic. */
    static final class FakeWizard implements WizardLevelAPI {
        int level = 12;
        int max = 30;
        boolean broken;

        @Override public boolean isEnabled() { return true; }
        @Override public int maxLevel() { return max; }
        @Override public int level(UUID playerId) {
            if (broken) {
                throw new IllegalStateException("magic is down");
            }
            return level;
        }
    }

    static void magic() {
        BukkitKit.reset();
        BukkitKit.installFamily();
        BukkitKit.Named magicPlugin = BukkitKit.install("NexusMagic", 1, 1);
        FakeWizard wizard = new FakeWizard();
        Bukkit.getServicesManager().register(WizardLevelAPI.class, wizard, magicPlugin, ServicePriority.Normal);
        BukkitKit.Rig rig = new BukkitKit.Rig();
        BukkitKit.TestPlayer ann = BukkitKit.join("Ann");
        CharacterService.Row row = rig.service.snapshot(ann.getUniqueId()).rows().stream().filter(r -> r.def().id().equals("magic")).findFirst().orElseThrow();
        expect(row.level() == 12 && row.max() == 30 && row.live() && row.def().name().equals("Wizardry"), "Wizardry 12/30 comes from NexusMagic's service");
        expect(!row.def().resettable(), "and cannot be reset");
        wizard.level = 99;
        expect(rig.service.snapshot(ann.getUniqueId()).rows().stream().filter(r -> r.def().id().equals("magic")).findFirst().orElseThrow().level() == 30,
                "above its own top level is held to it");
        wizard.max = 50;
        expect(rig.service.snapshot(ann.getUniqueId()).rows().stream().filter(r -> r.def().id().equals("magic")).findFirst().orElseThrow().max() == 50,
                "its top level is asked live");
        wizard.broken = true;
        row = rig.service.snapshot(ann.getUniqueId()).rows().stream().filter(r -> r.def().id().equals("magic")).findFirst().orElseThrow();
        expect(row.level() == 0 && !row.live(), "a service that throws is unreadable, not a crash");
        wizard.broken = false;

        // offline: unreadable, so the last known level is used
        rig.service.refresh(ann);
        Bukkit.PLAYERS.remove(ann.getUniqueId());
        wizard.level = 3;
        row = rig.service.snapshot(ann.getUniqueId()).rows().stream().filter(r -> r.def().id().equals("magic")).findFirst().orElseThrow();
        expect(row.level() == 50 && !row.live(), "offline players keep their last known Wizardry (50, read while online): " + row.level());

        // service withdrawn: falls back to config.yml's top level and nobody can be read
        Bukkit.getServicesManager().unregisterAll(magicPlugin);
        BukkitKit.TestPlayer bo = BukkitKit.join("Bo");
        row = rig.service.snapshot(bo.getUniqueId()).rows().stream().filter(r -> r.def().id().equals("magic")).findFirst().orElseThrow();
        expect(row.level() == 0 && !row.live() && row.max() == 30, "no service: top level from config.yml, level unreadable");
        PluginRemoved.run(rig, magicPlugin, bo);
    }

    /** NexusMagic not installed at all. */
    static final class PluginRemoved {
        static void run(BukkitKit.Rig rig, BukkitKit.Named magicPlugin, BukkitKit.TestPlayer bo) {
            org.bukkit.plugin.PluginManager.PLUGINS.remove(magicPlugin);
            expect(rig.service.snapshot(bo.getUniqueId()).rows().stream().noneMatch(r -> r.def().id().equals("magic")), "not installed: not on the sheet");
            YamlConfiguration y = BukkitKit.shipped();
            y.set("skills.magic.enabled", false);
            org.bukkit.plugin.PluginManager.PLUGINS.add(magicPlugin);
            BukkitKit.Rig off = new BukkitKit.Rig(y);
            expect(off.service.snapshot(bo.getUniqueId()).rows().stream().noneMatch(r -> r.def().id().equals("magic")), "skills.magic.enabled: false hides it");
        }
    }
}
