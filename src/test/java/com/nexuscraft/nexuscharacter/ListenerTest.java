package com.nexuscraft.nexuscharacter;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import static com.nexuscraft.nexuscharacter.BukkitKit.CUISINE;
import static com.nexuscraft.nexuscharacter.BukkitKit.LODE;
import static com.nexuscraft.nexuscharacter.BukkitKit.xpAt;
import static com.nexuscraft.nexuscharacter.Kit.expect;

/** What the events do. */
public class ListenerTest {

    public static void main(String[] args) {
        BukkitKit.reset();
        BukkitKit.installFamily();
        BukkitKit.Rig rig = new BukkitKit.Rig();
        CharacterListener l = new CharacterListener(rig.service);
        BukkitKit.TestPlayer ann = BukkitKit.join("Ann");
        ann.setXp(LODE, xpAt(15, 8.0));
        ann.setXp(CUISINE, xpAt(15, 10.0));

        l.onJoin(new PlayerJoinEvent(ann, "joined"));
        expect(ann.getAttribute(ServiceTest.HEALTH).getValue() == 22.0 && ann.text().contains("Perk unlocked: Hearty Digger"), "joining brings her perks");
        expect(rig.store.known(ann.getUniqueId()), "and starts her record");

        // orb experience
        rig.store.rec(ann.getUniqueId()).prestige = 1;
        rig.service.refresh(ann);
        int total = 0;
        for (int i = 0; i < 20; i++) {
            PlayerExpChangeEvent e = new PlayerExpChangeEvent(ann, 10);
            l.onExp(e);
            total += e.getAmount();
        }
        expect(total == 200 + 11, "Veteran's Resolve: 5% x 1.1 = 5.5% more orb xp over twenty orbs of 10 is 11 more: " + total);

        l.onQuit(new PlayerQuitEvent(ann, "left"));
        expect(ann.getAttribute(ServiceTest.HEALTH).getValue() == 20.0, "quitting takes the perks off");
        PlayerExpChangeEvent after = new PlayerExpChangeEvent(ann, 10);
        l.onExp(after);
        expect(after.getAmount() == 10, "and the experience bonus stops");

        // switched off
        YamlConfiguration y = BukkitKit.shipped();
        y.set("perks-enabled", false);
        BukkitKit.Rig off = new BukkitKit.Rig(y);
        CharacterListener lo = new CharacterListener(off.service);
        off.store.rec(ann.getUniqueId()).prestige = 1;
        PlayerExpChangeEvent e = new PlayerExpChangeEvent(ann, 10);
        lo.onExp(e);
        expect(e.getAmount() == 10, "perks off: no experience bonus");
        // perks switched off on a reload, before the next refresh has cleared the bonus
        BukkitKit.reset();
        BukkitKit.installFamily();
        BukkitKit.Rig live = new BukkitKit.Rig();
        BukkitKit.TestPlayer bo = BukkitKit.join("Bo");
        live.store.rec(bo.getUniqueId()).prestige = 1;
        live.service.refresh(bo);
        CharacterListener ll = new CharacterListener(live.service);
        PlayerExpChangeEvent before = new PlayerExpChangeEvent(bo, 1000);
        ll.onExp(before);
        expect(before.getAmount() > 1000, "Veteran's Resolve is giving bonus xp");
        YamlConfiguration y2 = BukkitKit.shipped();
        y2.set("perks-enabled", false);
        live.service.setConfig(new CharacterConfig(y2));
        PlayerExpChangeEvent switched = new PlayerExpChangeEvent(bo, 1000);
        ll.onExp(switched);
        expect(switched.getAmount() == 1000, "the moment perks are switched off the bonus stops, without waiting for a refresh");
        y2.set("perks-enabled", true);
        y2.set("enabled", false);
        live.service.setConfig(new CharacterConfig(y2));
        PlayerExpChangeEvent disabled = new PlayerExpChangeEvent(bo, 1000);
        ll.onExp(disabled);
        expect(disabled.getAmount() == 1000, "and so does switching the whole plugin off");
        Bukkit.PLAYERS.clear();
        Kit.finish();
    }
}
