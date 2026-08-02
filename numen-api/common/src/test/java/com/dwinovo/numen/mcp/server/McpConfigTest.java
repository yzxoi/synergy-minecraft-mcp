package com.dwinovo.numen.mcp.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void existingConfigWithoutAllowedOriginsRemainsCompatible() throws Exception {
        Path file = configFile();
        Files.writeString(file, """
                {"enabled":false,"host":"127.0.0.1","port":8765,"token":"",
                 "call_timeout_seconds":300,"hidden_tools":["load_skill"]}
                """);

        McpConfig config = McpConfig.load(file);

        assertEquals(List.of("load_skill"), config.hiddenTools());
        assertTrue(config.allowedOrigins().isEmpty());
    }

    @Test
    void allowedOriginsRoundTripWhenEnabledStateIsSaved() throws Exception {
        Path file = configFile();
        Files.writeString(file, """
                {"enabled":false,"host":"127.0.0.1","port":8765,"token":"",
                 "call_timeout_seconds":300,"hidden_tools":[],
                 "allowed_origins":["https://agent.example.com"]}
                """);

        McpConfig.load(file).withEnabled(true).save(file);

        JsonObject saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertTrue(saved.get("enabled").getAsBoolean());
        assertEquals("https://agent.example.com",
                saved.getAsJsonArray("allowed_origins").get(0).getAsString());
    }

    private Path configFile() throws Exception {
        Path file = tempDir.resolve("config/numen/mcp_server.json");
        Files.createDirectories(file.getParent());
        return file;
    }
}
