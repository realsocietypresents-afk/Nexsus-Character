package com.nexusuniverse.magic;

import java.util.UUID;

/** A copy of NexusMagic's published interface, so the bridge can be tested without NexusMagic. */
public interface WizardLevelAPI {
    boolean isEnabled();

    int maxLevel();

    int level(UUID playerId);
}
