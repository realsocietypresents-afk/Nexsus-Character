package com.nexuscraft.nexuscharacter;

import org.bukkit.plugin.Plugin;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/** The service other plugins see. */
final class ApiImpl implements CharacterApi {

    private final CharacterService service;

    ApiImpl(CharacterService service) {
        this.service = service;
    }

    @Override
    public boolean register(Plugin owner, String id, String name, int maxLevel, ToIntFunction<UUID> level, Consumer<UUID> reset) {
        return service.registry().register(owner, id, name, maxLevel, level, reset);
    }

    @Override
    public void unregisterAll(Plugin owner) {
        service.registry().unregisterAll(owner);
    }

    @Override
    public Set<String> skills() {
        Set<String> out = new LinkedHashSet<>();
        for (SkillDef d : service.registry().available()) {
            out.add(d.id());
        }
        return out;
    }

    @Override
    public int level(UUID player, String skill) {
        for (CharacterService.Row r : service.snapshot(player).rows()) {
            if (r.def().id().equalsIgnoreCase(skill)) {
                return r.level();
            }
        }
        return 0;
    }

    @Override
    public int totalLevel(UUID player) {
        return service.snapshot(player).total();
    }

    @Override
    public int prestige(UUID player) {
        return service.prestigeOf(player);
    }

    @Override
    public boolean hasPerk(UUID player, String perk) {
        return perk != null && perks(player).contains(perk.toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public Set<String> perks(UUID player) {
        Set<String> out = new LinkedHashSet<>();
        for (PerkEngine.Status s : service.snapshot(player).perks()) {
            if (s.active()) {
                out.add(s.perk().id());
            }
        }
        return out;
    }
}
