# Adapters

You do **not** need an adapter for NexusLode, NexusSmelting, NexusCuisine, NexusAlchemy, NexusAngler, NexusMomentum, NexusRedstone or NexusMagic: NexusCharacter reads them directly.

An adapter is only for a plugin that keeps its skill somewhere NexusCharacter cannot see (a file, a private registry), or that you want to behave differently from the built-in reading.

## How

1. Copy `CharacterHook.java` into the plugin's source tree. Change its `package` line to the plugin's own package.
2. In that plugin's `plugin.yml` add `NexusCharacter` to `softdepend`.
3. Call `CharacterHook.register(...)` once at the end of `onEnable`. It registers at once if NexusCharacter is running, otherwise it looks again after 1 tick, 5 seconds and 30 seconds, then gives up quietly (one log line).

The level function returns **0** for "not started" and **-1** for "cannot be read right now" (for example an offline player whose data is not loaded). The reset function is called only for an online player, when they prestige; pass `null` if the skill must never be wiped.

## NexusCraftersGuild (Smithing)

In `NexusCraftersGuild.onEnable()`, right after `smiths.load();`:

```java
CharacterHook.register(this, "smithing", "Smithing", SmithLeveling.MAX_LEVEL,
        id -> {
            SmithProfile p = smiths.find(id);
            return p == null ? 0 : p.level();
        },
        id -> {
            SmithProfile p = smiths.find(id);
            if (p != null) {
                p.xp = 0;
            }
        });
```

and in its `plugin.yml`: `softdepend: [Vault, NexusEndeavors, NexusCharacter]`. Note a smith who has never crafted has no profile (reads 0), and a brand-new profile reads level 1 (the guild's own starting level).

Add a perk using it in NexusCharacter's config.yml, for example:

```yaml
  forged-feast:
    name: Forged Feast
    requires:
      smithing: 15
      cuisine: 15
    effects:
      - "attribute:ARMOR:1:add"
```
