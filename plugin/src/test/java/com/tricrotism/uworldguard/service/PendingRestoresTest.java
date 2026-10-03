package com.tricrotism.uworldguard.service;

import org.bukkit.GameMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The owed-state file is written by hand and read back as YAML, so a write must load as the same states.
 */
class PendingRestoresTest {

    private PluginMock plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("uWorldGuard");
        assertTrue(plugin.getDataFolder().exists() || plugin.getDataFolder().mkdirs());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void writtenStatesLoadBackUnchanged() {
        final UUID full = UUID.fromString("081e0aef-1d8b-3f1c-ba14-893c5ec534a3");
        final UUID partial = UUID.fromString("12345678-0000-4000-8000-000000000001");
        final PendingRestores written = new PendingRestores(plugin);
        written.record(full, GameMode.CREATIVE, 0.25F, 0.1F, Boolean.TRUE);
        written.record(partial, null, null, null, Boolean.FALSE);
        written.flushNow();

        final Map<UUID, PendingRestores.State> loaded = new PendingRestores(plugin).outstanding();

        assertEquals(2, loaded.size());
        assertEquals(new PendingRestores.State(GameMode.CREATIVE, 0.25F, 0.1F, Boolean.TRUE), loaded.get(full));
        assertEquals(new PendingRestores.State(null, null, null, Boolean.FALSE), loaded.get(partial));
    }
}
