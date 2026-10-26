package com.nexuscraft.nexuscharacter;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static com.nexuscraft.nexuscharacter.Kit.expect;

/** Saving and loading what the plugin remembers. */
public class StoreTest {

    public static void main(String[] args) throws Exception {
        File dir = BukkitKit.reset();
        File file = new File(dir, "players.yml");
        Logger log = Logger.getLogger("test");

        CharacterStore a = new CharacterStore(file, log);
        a.load();
        expect(a.all().isEmpty() && !a.dirty(), "no file yet: empty and clean");
        UUID id = UUID.randomUUID();
        expect(!a.known(id), "unknown");
        CharacterStore.Rec r = a.rec(id);
        expect(a.known(id) && a.rec(id) == r, "rec() makes one and then returns the same");
        r.name = "Ann";
        r.prestige = 3;
        r.prestigeAt = 123456789012L;
        r.seen = 987654321098L;
        r.levels.put("lode", 41);
        r.levels.put("my-skill", 7);
        r.perks.add("hearty-digger");
        r.perks.add("grand-artisan");
        a.touch();
        expect(a.dirty(), "touched is dirty");
        a.save();
        expect(!a.dirty() && file.isFile(), "saving cleans it and writes the file");
        expect(!new File(dir, "players.yml.tmp").exists(), "no half-written file is left behind");

        CharacterStore b = new CharacterStore(file, log);
        b.load();
        CharacterStore.Rec back = b.all().get(id);
        expect(back != null && back.name.equals("Ann") && back.prestige == 3 && back.prestigeAt == 123456789012L && back.seen == 987654321098L, "scalars survive");
        expect(back.levels.equals(r.levels) && back.levels.get("my-skill") == 7, "levels survive, with dashes in ids");
        expect(List.copyOf(back.perks).equals(List.of("hearty-digger", "grand-artisan")), "perks survive in order");

        // two players, and a second save replaces the first
        UUID other = UUID.randomUUID();
        b.rec(other).name = "Bo";
        b.rec(id).prestige = 4;
        b.save();
        CharacterStore c = new CharacterStore(file, log);
        c.load();
        expect(c.all().size() == 2 && c.all().get(id).prestige == 4 && c.all().get(other).name.equals("Bo"), "a second save replaces the first");

        // a damaged file: junk keys and junk values are ignored, the rest is kept
        Files.writeString(file.toPath(), String.join("\n",
                id + ".name\ts\tAnn",
                id + ".prestige\ti\t-9",
                id + ".levels\tl\tlode=12\u0001nonsense\u0001broken=x\u0001neg=-4\u0001=5",
                "not-a-uuid.name\ts\tIgnored",
                other + ".prestige\ti\t2",
                "just some junk") + "\n");
        CharacterStore d = new CharacterStore(file, log);
        d.load();
        expect(d.all().size() == 2, "the junk key is skipped: " + d.all().keySet());
        expect(d.all().get(id).prestige == 0, "a negative prestige is held to 0");
        expect(d.all().get(id).levels.equals(java.util.Map.of("lode", 12, "neg", 0)), "bad level entries are dropped, negatives held to 0: " + d.all().get(id).levels);
        expect(d.all().get(other).prestige == 2 && d.all().get(other).name.isEmpty(), "missing fields get defaults");
        expect(!d.dirty(), "loading is not a change");

        // load replaces what was in memory
        d.rec(UUID.randomUUID());
        d.load();
        expect(d.all().size() == 2, "load starts afresh");

        // saving where it cannot: complains, does not throw, stays dirty
        File blocker = new File(dir, "blocker");
        Files.writeString(blocker.toPath(), "I am a file");
        CharacterStore e = new CharacterStore(new File(blocker, "players.yml"), log);
        e.rec(UUID.randomUUID());
        e.touch();
        e.save();
        expect(e.dirty(), "a failed save leaves it dirty so it is tried again");

        // a file in a folder that does not exist yet is made
        CharacterStore f = new CharacterStore(new File(dir, "deeper/players.yml"), log);
        f.rec(UUID.randomUUID()).name = "Cy";
        f.save();
        expect(new File(dir, "deeper/players.yml").isFile(), "missing folders are created");
        Kit.finish();
    }
}
