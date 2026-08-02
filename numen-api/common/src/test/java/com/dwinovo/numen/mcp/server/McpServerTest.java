package com.dwinovo.numen.mcp.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpServerTest {

    private static final String PROTOCOL_VERSION = "2025-06-18";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private McpServer server;
    private URI endpoint;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop();
    }

    @Test
    void initializeNegotiatesProtocolAndAdvertisesTools() throws Exception {
        start("");

        HttpResponse<String> response = post("""
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                  "protocolVersion":"2025-06-18",
                  "capabilities":{},
                  "clientInfo":{"name":"test-client","version":"1.0"}
                }}
                """, null, null);

        assertEquals(200, response.statusCode());
        assertEquals("application/json", response.headers().firstValue("Content-Type").orElseThrow());
        JsonObject result = json(response).getAsJsonObject("result");
        assertEquals(PROTOCOL_VERSION, result.get("protocolVersion").getAsString());
        assertTrue(result.getAsJsonObject("capabilities").has("tools"));
        assertEquals("numen-mcp", result.getAsJsonObject("serverInfo").get("name").getAsString());
        assertFalse(result.get("instructions").getAsString().isBlank());
    }

    @Test
    void pingAndToolsListReturnJsonRpcResults() throws Exception {
        start("");

        JsonObject ping = json(post(request(2, "ping", "{}"), null, null));
        assertTrue(ping.getAsJsonObject("result").isEmpty());

        JsonObject listed = json(post(request(3, "tools/list", "{}"), PROTOCOL_VERSION, null));
        JsonArray tools = listed.getAsJsonObject("result").getAsJsonArray("tools");
        assertTrue(hasTool(tools, "list_companions"));
        assertTrue(hasTool(tools, "create_companion"));
        assertTrue(hasTool(tools, "delete_companion"));
    }

    @Test
    void initializedNotificationIsAcceptedWithoutResponseBody() throws Exception {
        start("");

        HttpResponse<String> response = post("""
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                """, PROTOCOL_VERSION, null);

        assertEquals(202, response.statusCode());
        assertEquals("", response.body());
    }

    @Test
    void bearerTokenProtectsEndpoint() throws Exception {
        start("secret-token");

        HttpResponse<String> missing = post(request(1, "ping", "{}"), null, null);
        assertEquals(401, missing.statusCode());

        HttpResponse<String> accepted = post(request(1, "ping", "{}"), null, "Bearer secret-token");
        assertEquals(200, accepted.statusCode());
    }

    private void start(String token) throws Exception {
        McpConfig config = new McpConfig(false, "127.0.0.1", 0, token, 5, List.of());
        server = new McpServer(config);
        server.start();
        endpoint = URI.create("http://127.0.0.1:" + server.port() + "/mcp");
    }

    private HttpResponse<String> post(String body, String protocolVersion, String authorization)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json, text/event-stream")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (protocolVersion != null) builder.header("MCP-Protocol-Version", protocolVersion);
        if (authorization != null) builder.header("Authorization", authorization);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonObject json(HttpResponse<String> response) {
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static String request(int id, String method, String params) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"" + method
                + "\",\"params\":" + params + "}";
    }

    private static boolean hasTool(JsonArray tools, String name) {
        for (var tool : tools) {
            if (name.equals(tool.getAsJsonObject().get("name").getAsString())) return true;
        }
        return false;
    }
}
