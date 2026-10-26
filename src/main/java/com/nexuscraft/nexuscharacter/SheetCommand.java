package com.nexuscraft.nexuscharacter;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** /sheet: the one place all skills, perks and prestige live. */
public final class SheetCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("perks", "prestige", "top", "skills", "help");
    private static final List<String> ADMIN_SUBS = List.of("reload", "setprestige");

    private final CharacterService service;
    private final Prestige prestige;
    private final Map<UUID, Long> confirmUntil = new HashMap<>();
    private Supplier<List<String>> reload = List::of;

    SheetCommand(CharacterService service, Prestige prestige) {
        this.service = service;
        this.prestige = prestige;
    }

    void setReload(Supplier<List<String>> reload) {
        this.reload = reload;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "perks" -> perks(sender, args);
            case "prestige" -> prestige(sender, args);
            case "top" -> send(sender, Texts.top(service.top(service.config().topSize), service.config()));
            case "skills" -> skills(sender);
            case "reload" -> reload(sender);
            case "setprestige" -> setPrestige(sender, args);
            case "help" -> help(sender);
            default -> sheet(sender, args.length == 0 ? null : args[0]);
        }
        return true;
    }

    private static void send(CommandSender to, List<String> lines) {
        for (String l : lines) {
            to.sendMessage(l);
        }
    }

    /** Whose sheet: the sender's own (name null), or someone else's. Null when the answer was already sent. */
    private Target target(CommandSender sender, String name) {
        if (name == null) {
            if (sender instanceof Player p) {
                return new Target(p.getUniqueId(), p.getName(), false);
            }
            sender.sendMessage("§cUsage: /sheet <player>");
            return null;
        }
        if (!(sender instanceof Player p && p.getName().equalsIgnoreCase(name)) && !sender.hasPermission("nexuscharacter.view-others")) {
            sender.sendMessage("§cYou can only look at your own sheet.");
            return null;
        }
        Player online = Bukkit.getPlayer(name);
        if (online != null) {
            return new Target(online.getUniqueId(), online.getName(), false);
        }
        for (Map.Entry<UUID, CharacterStore.Rec> e : service.store().all().entrySet()) {
            if (e.getValue().name.equalsIgnoreCase(name)) {
                return new Target(e.getKey(), e.getValue().name, true);
            }
        }
        sender.sendMessage("§cNobody called " + name + " has a sheet here.");
        return null;
    }

    private record Target(UUID id, String name, boolean offline) {
    }

    private void sheet(CommandSender sender, String who) {
        Target t = target(sender, who);
        if (t != null) {
            send(sender, Texts.sheet(t.name(), service.snapshot(t.id()), service.config(), t.offline()));
        }
    }

    private void perks(CommandSender sender, String[] args) {
        Target t = target(sender, args.length > 1 ? args[1] : null);
        if (t != null) {
            send(sender, Texts.perks(t.name(), service.snapshot(t.id()), service.config()));
        }
    }

    private void prestige(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly a player can prestige.");
            return;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            send(sender, Texts.prestige(service, prestige, p));
            if (prestige.check(p).ok()) {
                confirmUntil.put(p.getUniqueId(), service.now() + service.config().confirmSeconds * 1000L);
            }
            return;
        }
        if (!sender.hasPermission("nexuscharacter.prestige")) {
            sender.sendMessage("§cYou may not prestige.");
            return;
        }
        Long until = confirmUntil.remove(p.getUniqueId());
        if (until == null || service.now() > until) {
            sender.sendMessage("§7Look first: type §f/sheet prestige §7to see exactly what it resets, then confirm within "
                    + service.config().confirmSeconds + " seconds.");
            return;
        }
        Prestige.Outcome out = prestige.perform(p);
        if (!out.done()) {
            sender.sendMessage("§c" + out.message());
            return;
        }
        List<String> names = new ArrayList<>();
        for (SkillDef d : out.wiped()) {
            names.add(d.name());
        }
        sender.sendMessage("§6[Sheet] §eYou reached Prestige " + out.stars() + "! §7Reset: " + String.join(", ", names) + ". Your perks are stronger now.");
    }

    private void skills(CommandSender sender) {
        SkillRegistry reg = service.registry();
        List<String> out = new ArrayList<>();
        out.add("§6[Sheet] §fSkills on this server");
        List<SkillDef> all = reg.available();
        if (all.isEmpty()) {
            out.add("§7None. Install a skill plugin (NexusLode, NexusCuisine, ...) or register one.");
        }
        for (SkillDef d : all) {
            out.add(" §f" + d.name() + " §8(" + d.id() + ") §7from " + d.source() + ", top level " + reg.max(d) + (d.resettable() ? "" : ", cannot be reset"));
        }
        send(sender, out);
    }

    private void help(CommandSender sender) {
        send(sender, List.of("§6[Sheet] §f/sheet §7- your sheet, §f/sheet <player>§7, §f/sheet perks§7, §f/sheet prestige§7, §f/sheet top§7, §f/sheet skills"));
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("nexuscharacter.admin")) {
            sender.sendMessage("§cNo permission.");
            return;
        }
        List<String> problems = reload.get();
        sender.sendMessage("§aNexusCharacter reloaded.");
        for (String p : problems) {
            sender.sendMessage("§e- " + p);
        }
    }

    private void setPrestige(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nexuscharacter.admin")) {
            sender.sendMessage("§cNo permission.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /sheet setprestige <player> <stars>");
            return;
        }
        int stars;
        try {
            stars = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c'" + args[2] + "' is not a number.");
            return;
        }
        stars = Math.max(0, Math.min(service.config().maxStars, stars));
        Player online = Bukkit.getPlayer(args[1]);
        UUID id = online == null ? null : online.getUniqueId();
        String name = online == null ? args[1] : online.getName();
        if (id == null) {
            for (Map.Entry<UUID, CharacterStore.Rec> e : service.store().all().entrySet()) {
                if (e.getValue().name.equalsIgnoreCase(args[1])) {
                    id = e.getKey();
                    name = e.getValue().name;
                }
            }
        }
        if (id == null) {
            sender.sendMessage("§cNobody called " + args[1] + " has a sheet here.");
            return;
        }
        service.store().rec(id).prestige = stars;
        service.store().touch();
        if (online != null) {
            service.refresh(online);
        }
        service.store().save();
        sender.sendMessage("§a" + name + " now has " + stars + " prestige star" + (stars == 1 ? "" : "s") + ".");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length <= 1) {
            String start = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            List<String> all = new ArrayList<>(SUBS);
            if (sender.hasPermission("nexuscharacter.admin")) {
                all.addAll(ADMIN_SUBS);
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                all.add(p.getName());
            }
            for (String s : all) {
                if (s.toLowerCase(Locale.ROOT).startsWith(start)) {
                    out.add(s);
                }
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String start = args[1].toLowerCase(Locale.ROOT);
            List<String> all = new ArrayList<>();
            if (sub.equals("prestige")) {
                all.add("confirm");
            } else if (sub.equals("perks") || sub.equals("setprestige")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    all.add(p.getName());
                }
            }
            for (String s : all) {
                if (s.toLowerCase(Locale.ROOT).startsWith(start)) {
                    out.add(s);
                }
            }
        }
        return out;
    }
}
