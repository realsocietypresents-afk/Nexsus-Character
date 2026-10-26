package com.nexuscraft.nexuscharacter;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Join and quit keep each player's effects right; orb experience gets the experience perks. */
public final class CharacterListener implements Listener {

    private final CharacterService service;

    CharacterListener(CharacterService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        service.onJoin(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        service.onQuit(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExp(PlayerExpChangeEvent e) {
        if (service.config().enabled && service.config().perksEnabled) {
            e.setAmount(service.boostXp(e.getPlayer().getUniqueId(), e.getAmount()));
        }
    }
}
