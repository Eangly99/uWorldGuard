package com.tricrotism.uworldguard.text;

import com.tricrotism.uworldguard.flags.Flags;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * How a region's {@code deny-message} interacts with the shared and per-flag entries in messages.yml.
 */
class DenyMessageTest {

    private ServerMock server;
    private PluginMock plugin;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("uWorldGuard");
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private MessageService messages(final String entries) throws IOException {
        final File dataFolder = plugin.getDataFolder();
        assertTrue(dataFolder.exists() || dataFolder.mkdirs());
        Files.writeString(new File(dataFolder, "messages.yml").toPath(),
            "cooldown-seconds: 0\nmessages:\n" + entries, StandardCharsets.UTF_8);
        return new MessageService(plugin);
    }

    private String next() {
        return PlainTextComponentSerializer.plainText().serialize(player.nextComponentMessage());
    }

    @Test
    void regionMessageShowsWhenTheSharedEntryIsBlank() throws IOException {
        messages("  no-permission: \"\"\n").sendDeny(player, Flags.BLOCK_BREAK, "Keep out");

        assertEquals("Keep out", next());
    }

    @Test
    void blankSharedEntryStillSilencesFlagsWithoutARegionMessage() throws IOException {
        messages("  no-permission: \"\"\n").sendDeny(player, Flags.BLOCK_BREAK, null);

        assertNull(player.nextComponentMessage());
    }

    @Test
    void perFlagFalseSilencesTheRegionMessageForThatFlag() throws IOException {
        messages("  no-permission: \"No.\"\n  no-permission-block-break: false\n")
            .sendDeny(player, Flags.BLOCK_BREAK, "Keep out");

        assertNull(player.nextComponentMessage());
    }
}
