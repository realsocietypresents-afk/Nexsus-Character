package com.nexuscraft.nexuscharacter;

import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.ToIntFunction;

/** One skill on the sheet. {@code level} gives -1 when it cannot be read now, 0 when not started. */
record SkillDef(String id, String name, IntSupplier maxLevel, ToIntFunction<UUID> level, Consumer<UUID> reset,
                BooleanSupplier available, String source) {

    boolean resettable() {
        return reset != null;
    }
}
