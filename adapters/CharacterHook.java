// Drop this file into a plugin that has a skill, change the package line to your plugin's package, and call CharacterHook.register(...)
// once from onEnable. It needs no compile-time link to NexusCharacter (it finds it by name), so your plugin still starts, builds and works
// without it. Add NexusCharacter to your plugin.yml:   softdepend: [NexusCharacter]
package com.nexuscraft.nexuscharacter.adapters;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/** Shows one of this plugin's skills on NexusCharacter's /sheet, if NexusCharacter is installed. */
public final class CharacterHook {

    private static final String API = "com.nexuscraft.nexuscharacter.CharacterApi";

    private CharacterHook() {
    }

    /**
     * @param owner    your plugin ({@code this})
     * @param id       short lowercase id, for example {@code smithing}
     * @param name     what the sheet calls it, for example {@code Smithing}
     * @param maxLevel the top level
     * @param level    the player's level: 0 if not started, -1 if it cannot be read right now. Called often, so keep it cheap.
     * @param reset    wipes the player's progress when they prestige, or null if this skill must never be wiped
     */
    public static void register(Plugin owner, String id, String name, int maxLevel, ToIntFunction<UUID> level, Consumer<UUID> reset) {
        AtomicBoolean done = new AtomicBoolean(attempt(owner, id, name, maxLevel, level, reset));
        if (done.get()) {
            return;
        }
        // NexusCharacter may simply load after us: try again a moment later, then once more, then give up quietly
        long[] delays = {1L, 100L, 600L};
        for (int i = 0; i < delays.length; i++) {
            boolean last = i == delays.length - 1;
            try {
                owner.getServer().getScheduler().runTaskLater(owner, () -> {
                    if (!done.get() && attempt(owner, id, name, maxLevel, level, reset)) {
                        done.set(true);
                    }
                    if (last && !done.get()) {
                        owner.getLogger().info("NexusCharacter is not installed: '" + name + "' will not appear on /sheet.");
                    }
                }, delays[i]);
            } catch (RuntimeException pluginStopped) {
                return;
            }
        }
    }

    private static boolean attempt(Plugin owner, String id, String name, int maxLevel, ToIntFunction<UUID> level, Consumer<UUID> reset) {
        try {
            Class<?> api = Class.forName(API, true, owner.getClass().getClassLoader());
            Object service = Bukkit.getServicesManager().load(api);
            if (service == null) {
                return false;
            }
            Method register = api.getMethod("register", Plugin.class, String.class, String.class, int.class, ToIntFunction.class, Consumer.class);
            return Boolean.TRUE.equals(register.invoke(service, owner, id, name, maxLevel, level, reset));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError notThere) {
            return false;
        }
    }
}
