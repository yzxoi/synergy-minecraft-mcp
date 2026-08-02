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

        JsonObject ping = json(post(request(2, "ping", "{}"), PROTOCOL_VERSION, null));
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

        HttpResponse<String> accepted = post(request(1, "ping", "{}"), PROTOCOL_VERSION,
                "Bearer secret-token");
        assertEquals(200, accepted.statusCode());
    }

    @Test
    void queryTokenMustBeAnExactDecodedParameter() throws Exception {
        start("a+b");

        assertEquals(401, postTo(endpoint + "?x=token%3Da%2Bb", request(1, "ping", "{}"),
                null, null, null).statusCode());
        assertEquals(401, postTo(endpoint + "?token=a%2Bb-extra", request(1, "ping", "{}"),
                null, null, null).statusCode());
        assertEquals(200, postTo(endpoint + "?token=a%2Bb", request(1, "ping", "{}"),
                PROTOCOL_VERSION, null, null).statusCode());
    }

    @Test
    void presentOriginMustBeLocalOrExplicitlyAllowed() throws Exception {
        start("");
        String localOrigin = "http://localhost:" + endpoint.getPort();

        assertEquals(200, postTo(endpoint.toString(), request(1, "ping", "{}"),
                PROTOCOL_VERSION, null, localOrigin).statusCode());
        assertEquals(403, postTo(endpoint.toString(), request(1, "ping", "{}"),
                PROTOCOL_VERSION, null, "https://evil.example").statusCode());

        server.stop();
        start("", List.of("https://trusted.example"));
        assertEquals(200, postTo(endpoint.toString(), request(1, "ping", "{}"),
                PROTOCOL_VERSION, null, "https://trusted.example").statusCode());
    }

    @Test
    void initializeChoosesSupportedVersionInsteadOfEchoingClientInput() throws Exception {
        start("");

        HttpResponse<String> response = post("""
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                  "protocolVersion":"future-version",
                  "capabilities":{},
                  "clientInfo":{"name":"test-client","version":"1.0"}
                }}
                """, null, null);

        assertEquals(200, response.statusCode());
        assertEquals(PROTOCOL_VERSION,
                json(response).getAsJsonObject("result").get("protocolVersion").getAsString());
    }

    @Test
    void unsupportedProtocolHeaderGetsHttp400() throws Exception {
        start("");

        HttpResponse<String> response = post(request(1, "ping", "{}"), "future-version", null);

        assertEquals(400, response.statusCode());
        assertError(response, -32600, "unsupported MCP protocol version");
    }

    @Test
    void missingProtocolHeaderGetsHttp400AfterInitialization() throws Exception {
        start("");

        HttpResponse<String> response = post(request(1, "ping", "{}"), null, null);

        assertEquals(400, response.statusCode());
        assertError(response, -32600, "unsupported MCP protocol version");
    }

    @Test
    void malformedJsonAndInvalidShapesAreProtocolErrors() throws Exception {
        start("");

        HttpResponse<String> parseError = post("{broken", null, null);
        assertEquals(400, parseError.statusCode());
        assertError(parseError, -32700, "parse error");

        HttpResponse<String> scalar = post("42", null, null);
        assertEquals(400, scalar.statusCode());
        assertError(scalar, -32600, "invalid request");

        HttpResponse<String> batch = post("[" + request(1, "ping", "{}") + "]", null, null);
        assertEquals(400, batch.statusCode());
        assertError(batch, -32600, "invalid request");

        HttpResponse<String> badId = post("""
                {"jsonrpc":"2.0","id":true,"method":"ping","params":{}}
                """, null, null);
        assertEquals(400, badId.statusCode());
        assertError(badId, -32600, "invalid request");
    }

    @Test
    void invalidMcpParamsReturnInvalidParamsWithoutHttp500() throws Exception {
        start("");

        HttpResponse<String> wrongShape = post("""
                {"jsonrpc":"2.0","id":7,"method":"tools/call","params":[]}
                """, PROTOCOL_VERSION, null);
        assertEquals(200, wrongShape.statusCode());
        assertEquals(7, json(wrongShape).get("id").getAsInt());
        assertEquals(-32602, json(wrongShape).getAsJsonObject("error").get("code").getAsInt());

        HttpResponse<String> missingInitializeFields = post("""
                {"jsonrpc":"2.0","id":8,"method":"initialize","params":{}}
                """, null, null);
        assertEquals(200, missingInitializeFields.statusCode());
        assertEquals(-32602,
                json(missingInitializeFields).getAsJsonObject("error").get("code").getAsInt());
    }

    @Test
    void validToolsCallKeepsFailuresInsideToolResultEnvelope() throws Exception {
        start("");

        HttpResponse<String> response = post("""
                {"jsonrpc":"2.0","id":9,"method":"tools/call","params":{
                  "name":"delete_companion","arguments":{}
                }}
                """, PROTOCOL_VERSION, null);

        assertEquals(200, response.statusCode());
        JsonObject frame = json(response);
        assertFalse(frame.has("error"));
        JsonObject result = frame.getAsJsonObject("result");
        assertTrue(result.get("isError").getAsBoolean());
        assertTrue(result.getAsJsonArray("content").get(0).getAsJsonObject()
                .get("text").getAsString().contains("no such companion"));
    }

    @Test
    void oversizedRequestIsRejectedBeforeJsonParsing() throws Exception {
        start("");
        String oversized = "{\"value\":\"" + "a".repeat(McpServer.MAX_REQUEST_BODY_BYTES) + "\"}";

        HttpResponse<String> response = post(oversized, null, null);

        assertEquals(413, response.statusCode());
        assertEquals("", response.body());
    }

    @Test
    void wellFormedJsonRpcResponseIsAcceptedAsTransportInput() throws Exception {
        start("");

        HttpResponse<String> response = post("""
                {"jsonrpc":"2.0","id":"server-request","result":{}}
                """, PROTOCOL_VERSION, null);

        assertEquals(202, response.statusCode());
        assertEquals("", response.body());
    }

    @Test
    void serverCanStopAndRestartOnFreshEphemeralPort() throws Exception {
        start("");
        assertEquals(200, post(request(1, "ping", "{}"), PROTOCOL_VERSION, null).statusCode());

        server.stop();
        server.start();
        endpoint = URI.create("http://127.0.0.1:" + server.port() + "/mcp");

        assertEquals(200, post(request(2, "ping", "{}"), PROTOCOL_VERSION, null).statusCode());
    }

    private void start(String token) throws Exception {
        start(token, List.of());
    }

    private void start(String token, List<String> allowedOrigins) throws Exception {
        McpConfig config = new McpConfig(false, "127.0.0.1", 0, token, 5, List.of(), allowedOrigins);
        server = new McpServer(config);
        server.start();
        endpoint = URI.create("http://127.0.0.1:" + server.port() + "/mcp");
    }

    private HttpResponse<String> post(String body, String protocolVersion, String authorization)
            throws Exception {
        return postTo(endpoint.toString(), body, protocolVersion, authorization, null);
    }

    private HttpResponse<String> postTo(String uri, String body, String protocolVersion,
                                        String authorization, String origin) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri))
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json, text/event-stream")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (protocolVersion != null) builder.header("MCP-Protocol-Version", protocolVersion);
        if (authorization != null) builder.header("Authorization", authorization);
        if (origin != null) builder.header("Origin", origin);
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

    private static void assertError(HttpResponse<String> response, int code, String message) {
        JsonObject error = json(response).getAsJsonObject("error");
        assertEquals(code, error.get("code").getAsInt());
        assertEquals(message, error.get("message").getAsString());
    }
}
