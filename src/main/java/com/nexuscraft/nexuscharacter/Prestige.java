package com.nexuscraft.nexuscharacter;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Prestige: master enough skills and you may wipe those skills for a permanent star, which makes every attribute and experience perk stronger. */
final class Prestige {

    /** @param needed how many mastered skills this star asks for; @param mastered the skills at or above the mastery line; they are the ones a prestige wipes */
    record Check(boolean ok, String reason, int stars, int needed, List<SkillDef> mastered) {
    }

    /** What a prestige did. */
    record Outcome(boolean done, String message, int stars, List<SkillDef> wiped) {
    }

    private final CharacterService service;

    Prestige(CharacterService service) {
        this.service = service;
    }

    /** The level that counts as mastered for this skill. */
    int masteryLine(SkillDef d) {
        return Math.max(1, (int) Math.ceil(service.registry().max(d) * service.config().masteryFraction));
    }

    Check check(Player p) {
        CharacterConfig c = service.config();
        int stars = service.prestigeOf(p.getUniqueId());
        if (!c.prestigeEnabled) {
            return new Check(false, "Prestige is switched off on this server.", stars, 0, List.of());
        }
        if (stars >= c.maxStars) {
            return new Check(false, "You already have every star (" + c.maxStars + ").", stars, 0, List.of());
        }
        List<SkillDef> wipeable = new ArrayList<>();
        for (SkillDef d : service.registry().available()) {
            if (d.resettable()) {
                wipeable.add(d);
            }
        }
        if (wipeable.isEmpty()) {
            return new Check(false, "None of the installed skills can be reset, so there is nothing to prestige.", stars, 0, List.of());
        }
        int needed = Math.max(1, Math.min(c.skillsNeeded + stars * c.skillsNeededStep, wipeable.size()));
        List<SkillDef> mastered = new ArrayList<>();
        for (SkillDef d : wipeable) {
            if (service.registry().read(p.getUniqueId(), d) >= masteryLine(d)) {
                mastered.add(d);
            }
        }
        if (mastered.size() < needed) {
            return new Check(false, "You have mastered " + mastered.size() + " of the " + needed + " skills the next star asks for.", stars, needed, mastered);
        }
        return new Check(true, "", stars, needed, mastered);
    }

    Outcome perform(Player p) {
        Check ch = check(p);
        if (!ch.ok()) {
            return new Outcome(false, ch.reason(), ch.stars(), List.of());
        }
        UUID id = p.getUniqueId();
        List<SkillDef> wiped = new ArrayList<>();
        for (SkillDef d : ch.mastered()) {
            if (service.registry().reset(id, d)) {
                wiped.add(d);
            }
        }
        if (wiped.size() < ch.needed()) {
            service.refresh(p);
            return new Outcome(false, "Only " + wiped.size() + " of the " + ch.needed() + " skills could be reset, so no star was given. Ask an admin to check the server log.",
                    ch.stars(), wiped);
        }
        CharacterStore.Rec rec = service.store().rec(id);
        rec.prestige = ch.stars() + 1;
        rec.prestigeAt = service.now();
        service.store().touch();
        service.refresh(p);
        service.store().save();
        CharacterConfig c = service.config();
        for (String cmd : c.rewardCommands) {
            String line = cmd.replace("%player%", p.getName()).replace("%stars%", String.valueOf(rec.prestige));
            if (!line.isBlank()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line.startsWith("/") ? line.substring(1) : line);
            }
        }
        if (c.broadcast) {
            Bukkit.broadcastMessage("§6[Sheet] §e" + p.getName() + " §7has reached §ePrestige " + rec.prestige + " §7" + Texts.stars(rec.prestige, c.maxStars));
        }
        return new Outcome(true, "", rec.prestige, wiped);
    }
}
