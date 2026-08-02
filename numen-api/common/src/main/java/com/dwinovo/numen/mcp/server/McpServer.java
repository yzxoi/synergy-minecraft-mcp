package com.dwinovo.numen.mcp.server;

import com.dwinovo.numen.Constants;
import com.dwinovo.numen.agent.tool.NumenTool;
import com.dwinovo.numen.agent.tool.ToolRegistry;
import com.dwinovo.numen.api.NumenActuator;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A minimal MCP (Model Context Protocol) server, hand-rolled as JSON-RPC 2.0
 * over the JDK's built-in HTTP server — no third-party MCP SDK, no Reactor. It
 * implements exactly the surface an external agent needs to drive companions:
 * {@code initialize}, {@code tools/list}, {@code tools/call}, {@code ping}, and
 * the {@code notifications/initialized} handshake.
 *
 * <h2>Transport</h2>
 * Binds a loopback HTTP endpoint at {@code /mcp}. Claude Desktop can't dial an
 * HTTP MCP server directly, so the user points it at this endpoint through the
 * {@code mcp-remote} stdio bridge; every JSON-RPC frame arrives here as an HTTP
 * POST and its response goes straight back in the HTTP body.
 *
 * <h2>Tool surface</h2>
 * Every engine tool (from {@link ToolRegistry}, minus the config's hidden set)
 * is advertised with an extra {@code companion} argument, and calls route to
 * {@link NumenActuator#invoke}. Three management tools — {@code list_companions},
 * {@code create_companion}, {@code delete_companion} — wrap the actuator's roster
 * methods. There is no take-control handshake: 模式开启期间内置大脑一轮都不开
 * (见 {@link McpMode}), 所以外部驱动者直接派活即可。Since every call is addressed
 * to a companion and each body runs its tasks independently, an agent can drive
 * several companions in parallel.
 */
public final class McpServer {

    private static final String PROTOCOL_VERSION = "2025-06-18";
    private static final String SERVER_VERSION = "0.1.0";
    /** Roster / create / delete are fast; only tool actions use the config timeout. */
    private static final int CONTROL_TIMEOUT_SECONDS = 10;
    /** 活动流里一条参数/结果摘要的截断长度——面板一行放得下即可。 */
    private static final int SUMMARY_LIMIT = 90;
    /** Hard transport boundary: JSON-RPC messages larger than 1 MiB are rejected. */
    static final int MAX_REQUEST_BODY_BYTES = 1024 * 1024;

    /** Long-running tools whose MCP caller receives a task receipt, not an event push. */
    private static final Set<String> EXTERNAL_ASYNC_TOOLS = Set.of(
            "goto", "mine", "build", "blueprint", "collect_items", "fish",
            "melee_attack", "ranged_attack");

    /**
     * MCP is a request/response transport: external callers do not receive the
     * built-in brain's {@code task_finished} event. Keep that distinction next
     * to the tool-list projection so a stale engine description cannot make a
     * remote caller wait forever for an event it can never observe.
     */
    private static final String EXTERNAL_ASYNC_GUIDANCE =
            "MCP external driver: this action returns a task_id immediately. "
                    + "Save it and call task_status with that task_id until data.terminal=true; "
                    + "then inspect data.state and data.result and use perception to verify the world. "
                    + "MCP callers do not receive task_finished events (that event is for the built-in brain only). "
                    + "Do not resend the same action while its task is non-terminal; use task_stop to cancel it. "
                    + "Ignore any task_finished or no-poll wording in the engine details below: that wording "
                    + "applies only to the built-in brain, not to this MCP transport.";

    /**
     * Sent to the connecting agent in the {@code initialize} handshake (MCP's
     * {@code instructions} field) — what Numen is and how to drive it, so any
     * client gets the essentials without a separately-installed skill.
     */
    private static final String INSTRUCTIONS = """
            Numen companions are AI-controlled, player-like characters inside a live Minecraft game. \
            Through this server you take control of a companion's body and play the game as it — perceive, \
            move, mine, build, craft, fight. You are the brain; the companion is your hands and eyes. Its \
            built-in AI stays idle unless its owner speaks to it, so you drive the body directly — there is \
            no 'take control' handshake.

            Loop: (1) list_companions to see who is live — create_companion by name to summon a new one, \
            delete_companion to dismiss one for good; (2) perceive with get_self_status / scan_blocks / \
            scan_nearby_entities; (3) act with goto / mine / build / craft / equip_item / fish / \
            melee_attack / ranged_attack / interact_at / etc. Long actions return a task_id at once — \
            poll task_status with that task_id until \
            its state is terminal, inspect its structured result, then perceive to confirm. Every action \
            tool takes a 'companion' argument (name or id), so each \
            call targets one companion; just drive it, there is no take-control step.

            The canonical action names, parameters, and JSON Schemas are always the live results of \
            tools/list. Treat this text as orientation only: do not hard-code a name or argument from \
            memory, and re-check tools/list whenever the server or mod version changes.

            Rules: survival mode — the tools do only what a real player can (mine to get stone; there is no \
            give or setblock). You are blind between calls, so perceive before and after acting. A running \
            task reports its phase, progress metrics, and whether material progress has gone stale. You can drive several companions in \
            parallel. Modded blocks, items, and GUIs (Create, AE2, Mekanism) work natively.""";

    private final McpConfig config;
    private final Gson gson = new Gson();
    private HttpServer http;
    private ExecutorService executor;

    public McpServer(McpConfig config) {
        this.config = config;
    }

    public synchronized void start() throws IOException {
        if (http != null) throw new IllegalStateException("server is already running");
        http = HttpServer.create(new InetSocketAddress(config.host(), config.port()), 0);
        http.createContext("/mcp", this::handle);
        executor = Executors.newFixedThreadPool(8, r -> {
            Thread t = new Thread(r, "numen-mcp-http");
            t.setDaemon(true);
            return t;
        });
        http.setExecutor(executor);
        http.start();
    }

    public synchronized void stop() {
        if (http != null) {
            http.stop(0);
            http = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    /** Bound TCP port, primarily useful when tests request an ephemeral port ({@code 0}). */
    synchronized int port() {
        if (http == null) throw new IllegalStateException("server is not running");
        return http.getAddress().getPort();
    }

    // ---- HTTP layer ----

    private void handle(HttpExchange ex) throws IOException {
        try {
            if (!originAllowed(ex)) {
                respond(ex, 403, "");
                return;
            }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                respond(ex, 405, "");
                return;
            }
            if (!authorized(ex)) {
                respond(ex, 401, "");
                return;
            }
            // 任何通过鉴权的请求都算"对方还在"——面板的活跃时间靠它,ping 也算数。
            McpMode.instance().touch();
            byte[] bytes = ex.getRequestBody().readNBytes(MAX_REQUEST_BODY_BYTES + 1);
            if (bytes.length > MAX_REQUEST_BODY_BYTES) {
                respond(ex, 413, "");
                return;
            }
            String body = new String(bytes, StandardCharsets.UTF_8);
            JsonElement parsed;
            try {
                parsed = JsonParser.parseString(body);
            } catch (RuntimeException ignored) {
                respondJson(ex, 400, errorResponse(null, -32700, "parse error"));
                return;
            }

            HttpResult result = process(parsed, ex.getRequestHeaders().get("MCP-Protocol-Version"));
            if (result.body() == null) respond(ex, result.status(), "");
            else respondJson(ex, result.status(), result.body());
        } catch (RuntimeException fatal) {
            Constants.LOG.warn("[numen-mcp] request failed: {}", fatal.toString());
            try { respond(ex, 500, ""); } catch (IOException ignored) {}
        } finally {
            ex.close();
        }
    }

    private boolean authorized(HttpExchange ex) {
        if (config.token().isBlank()) return true;
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.equals("Bearer " + config.token())) return true;
        String query = ex.getRequestURI().getRawQuery();
        if (query == null) return false;
        try {
            for (String pair : query.split("&")) {
                int equals = pair.indexOf('=');
                String rawName = equals < 0 ? pair : pair.substring(0, equals);
                String rawValue = equals < 0 ? "" : pair.substring(equals + 1);
                String name = URLDecoder.decode(rawName, StandardCharsets.UTF_8);
                String value = URLDecoder.decode(rawValue, StandardCharsets.UTF_8);
                if ("token".equals(name) && config.token().equals(value)) return true;
            }
        } catch (IllegalArgumentException ignored) {
            // Malformed percent-encoding is not a credential.
        }
        return false;
    }

    private boolean originAllowed(HttpExchange ex) {
        List<String> headers = ex.getRequestHeaders().get("Origin");
        if (headers == null || headers.isEmpty()) return true; // native clients do not send Origin
        if (headers.size() != 1) return false;
        String origin = normalizeOrigin(headers.get(0));
        if (origin == null) return false;

        int boundPort = ex.getLocalAddress().getPort();
        String ownOrigin = normalizeOrigin("http://" + hostForUri(config.host()) + ":" + boundPort);
        if (origin.equals(ownOrigin)) return true;

        URI parsed = URI.create(origin);
        if (isLoopbackHost(config.host()) && isLoopbackHost(parsed.getHost())
                && parsed.getPort() == boundPort && "http".equals(parsed.getScheme())) {
            return true;
        }
        for (String allowed : config.allowedOrigins()) {
            if (origin.equals(normalizeOrigin(allowed))) return true;
        }
        return false;
    }

    private static String normalizeOrigin(String raw) {
        if (raw == null) return null;
        try {
            URI uri = URI.create(raw.trim());
            String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getRawPath();
            if (!("http".equals(scheme) || "https".equals(scheme)) || host == null
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || (path != null && !path.isEmpty() && !"/".equals(path))) {
                return null;
            }
            int port = uri.getPort();
            if (port < 0) port = "https".equals(scheme) ? 443 : 80;
            return scheme + "://" + hostForUri(host) + ":" + port;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean isLoopbackHost(String host) {
        if (host == null) return false;
        String normalized = host.toLowerCase(Locale.ROOT);
        return "127.0.0.1".equals(normalized) || "localhost".equals(normalized)
                || "::1".equals(normalized) || "[::1]".equals(normalized);
    }

    private static String hostForUri(String host) {
        return host.indexOf(':') >= 0 && !(host.startsWith("[") && host.endsWith("]"))
                ? "[" + host + "]" : host;
    }

    private void respondJson(HttpExchange ex, int status, JsonElement json) throws IOException {
        byte[] bytes = gson.toJson(json).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void respond(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // ---- JSON-RPC dispatch ----

    private record HttpResult(int status, JsonElement body) {}

    private HttpResult process(JsonElement message, List<String> protocolHeaders) {
        // MCP Streamable HTTP 2025-06-18 carries exactly one JSON-RPC message per POST.
        if (!message.isJsonObject()) {
            return jsonError(400, null, -32600, "invalid request");
        }
        JsonObject req = message.getAsJsonObject();
        JsonElement id = validId(req.get("id")) ? req.get("id") : null;
        if (!validEnvelope(req)) {
            return jsonError(400, id, -32600, "invalid request");
        }

        // A JSON-RPC response is valid transport input. This server currently sends no
        // client-directed requests, so there is nothing to correlate and it is accepted.
        if (!req.has("method")) {
            if (!validProtocolHeader(protocolHeaders)) {
                return jsonError(400, id, -32600, "unsupported MCP protocol version");
            }
            return new HttpResult(202, null);
        }

        String method = req.get("method").getAsString();
        if (!"initialize".equals(method) && !validProtocolHeader(protocolHeaders)) {
            return jsonError(400, id, -32600, "unsupported MCP protocol version");
        }
        if ("initialize".equals(method) && !req.has("id")) {
            return jsonError(400, null, -32600, "initialize must be a request");
        }

        String paramsError = validateParams(req, method);
        if (paramsError != null) {
            // Notifications have no JSON-RPC response, but Streamable HTTP permits an
            // HTTP error carrying an error object with a null id when they are rejected.
            return jsonError(req.has("id") ? 200 : 400, id, -32602, paramsError);
        }
        if (!req.has("id")) return new HttpResult(202, null);
        return new HttpResult(200, dispatch(req));
    }

    private static boolean validEnvelope(JsonObject req) {
        if (!req.has("jsonrpc") || !req.get("jsonrpc").isJsonPrimitive()
                || !req.getAsJsonPrimitive("jsonrpc").isString()
                || !"2.0".equals(req.get("jsonrpc").getAsString())) {
            return false;
        }

        if (req.has("method")) {
            if (!req.get("method").isJsonPrimitive() || !req.getAsJsonPrimitive("method").isString()
                    || req.get("method").getAsString().isBlank()) {
                return false;
            }
            if (req.has("id") && !validId(req.get("id"))) return false;
            return !req.has("result") && !req.has("error");
        }

        // JSON-RPC response: id plus exactly one of result/error.
        if (!req.has("id") || !validId(req.get("id"))) return false;
        boolean result = req.has("result");
        boolean error = req.has("error");
        return result != error && (!error || req.get("error").isJsonObject());
    }

    private static boolean validId(JsonElement id) {
        if (id == null || id.isJsonNull() || !id.isJsonPrimitive()) return false;
        JsonPrimitive primitive = id.getAsJsonPrimitive();
        return primitive.isString() || primitive.isNumber();
    }

    private static boolean validProtocolHeader(List<String> values) {
        // With no session state, 2025-06-18 says a missing header means 2025-03-26.
        // This server only implements 2025-06-18, so missing, duplicate, and different
        // values are all unsupported and must receive HTTP 400.
        return values != null && values.size() == 1 && PROTOCOL_VERSION.equals(values.get(0));
    }

    private static String validateParams(JsonObject req, String method) {
        if (req.has("params") && !req.get("params").isJsonObject()) {
            return "params must be an object";
        }
        JsonObject params = req.has("params") ? req.getAsJsonObject("params") : null;
        if ("initialize".equals(method)) {
            if (params == null) return "initialize params are required";
            if (!stringField(params, "protocolVersion")
                    || !objectField(params, "capabilities")
                    || !objectField(params, "clientInfo")) {
                return "initialize requires protocolVersion, capabilities, and clientInfo";
            }
            JsonObject info = params.getAsJsonObject("clientInfo");
            if (!stringField(info, "name") || !stringField(info, "version")) {
                return "clientInfo requires string name and version";
            }
        } else if ("tools/call".equals(method)) {
            if (params == null || !stringField(params, "name")
                    || (params.has("arguments") && !params.get("arguments").isJsonObject())) {
                return "tools/call requires a string name and optional object arguments";
            }
        }
        return null;
    }

    private static boolean stringField(JsonObject object, String name) {
        return object.has(name) && object.get(name).isJsonPrimitive()
                && object.getAsJsonPrimitive(name).isString();
    }

    private static boolean objectField(JsonObject object, String name) {
        return object.has(name) && object.get(name).isJsonObject();
    }

    private HttpResult jsonError(int status, JsonElement id, int code, String message) {
        return new HttpResult(status, errorResponse(id, code, message));
    }

    private JsonObject dispatch(JsonObject req) {
        JsonElement id = req.get("id");
        String method = req.get("method").getAsString();

        try {
            return switch (method) {
                case "initialize" -> okResponse(id, initializeResult(req));
                case "ping" -> okResponse(id, new JsonObject());
                case "tools/list" -> okResponse(id, toolsListResult());
                case "tools/call" -> okResponse(id, toolsCallResult(req));
                default -> errorResponse(id, -32601, "method not found: " + method);
            };
        } catch (RuntimeException error) {
            Constants.LOG.warn("[numen-mcp] JSON-RPC method {} failed: {}", method, error.toString());
            return errorResponse(id, -32603, "internal error");
        }
    }

    private JsonObject initializeResult(JsonObject req) {
        JsonObject params = req.getAsJsonObject("params");
        // If the client proposes another revision, lifecycle negotiation requires the
        // server to choose a version it actually supports instead of echoing arbitrary input.
        McpMode.instance().handshake(clientLabel(params));
        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", PROTOCOL_VERSION);
        JsonObject caps = new JsonObject();
        caps.add("tools", new JsonObject());
        result.add("capabilities", caps);
        JsonObject info = new JsonObject();
        info.addProperty("name", "numen-mcp");
        info.addProperty("version", SERVER_VERSION);
        result.add("serverInfo", info);
        result.addProperty("instructions", INSTRUCTIONS);
        return result;
    }

    /** {@code params.clientInfo} → "Claude Desktop 1.2.3";没报名字就叫 unknown client。 */
    private static String clientLabel(JsonObject params) {
        if (!params.has("clientInfo") || !params.get("clientInfo").isJsonObject()) return "unknown client";
        JsonObject info = params.getAsJsonObject("clientInfo");
        String name = info.has("name") ? info.get("name").getAsString() : "unknown client";
        String version = info.has("version") ? info.get("version").getAsString() : "";
        return version.isBlank() ? name : name + " " + version;
    }

    // ---- tools/list ----

    private JsonObject toolsListResult() {
        JsonArray tools = new JsonArray();

        tools.add(toolDef("list_companions",
                "List the owner's live Minecraft companions (name + id). Call this first to see who you can drive.",
                objectSchema(null, false)));
        tools.add(toolDef("create_companion",
                "Summon a new companion into the world by name (3–16 letters, digits, or underscore). It arrives "
                        + "in a moment; call list_companions to confirm, then drive it directly — no 'take control' step.",
                requiredStringSchema("name",
                        "The new companion's name (3–16 letters, digits, or underscore).")));
        tools.add(toolDef("delete_companion",
                "Permanently dismiss a companion — it drops its inventory and is gone for good. Takes its name or id.",
                requiredStringSchema("companion",
                        "Which companion to dismiss — its name or id (see list_companions).")));

        for (NumenTool tool : ToolRegistry.all()) {
            if (config.isHidden(tool.name())) continue;
            tools.add(toolDef(tool.name(), descriptionForMcp(tool), withCompanion(tool.parameterSchema())));
        }

        JsonObject result = new JsonObject();
        result.add("tools", tools);
        return result;
    }

    /** Project an engine description into the semantics visible to an MCP caller. */
    static String descriptionForMcp(NumenTool tool) {
        String description = tool.description();
        if (!EXTERNAL_ASYNC_TOOLS.contains(tool.name())) return description;
        return EXTERNAL_ASYNC_GUIDANCE + "\n\nEngine details:\n" + description;
    }

    private JsonObject toolDef(String name, String description, JsonObject inputSchema) {
        JsonObject t = new JsonObject();
        t.addProperty("name", name);
        t.addProperty("description", description);
        t.add("inputSchema", inputSchema);
        return t;
    }

    /** An object schema with just an optional required {@code companion} string. */
    private JsonObject objectSchema(String companionKey, boolean required) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();
        if (companionKey != null) props.add(companionKey, companionProp());
        schema.add("properties", props);
        if (companionKey != null && required) {
            JsonArray req = new JsonArray();
            req.add(companionKey);
            schema.add("required", req);
        }
        return schema;
    }

    /** An object schema with a single required string property (key + its description). */
    private JsonObject requiredStringSchema(String key, String description) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();
        JsonObject prop = new JsonObject();
        prop.addProperty("type", "string");
        prop.addProperty("description", description);
        props.add(key, prop);
        schema.add("properties", props);
        JsonArray req = new JsonArray();
        req.add(key);
        schema.add("required", req);
        return schema;
    }

    /** An engine tool's own schema, with a required {@code companion} argument injected. */
    private JsonObject withCompanion(java.util.Map<String, Object> parameterSchema) {
        JsonObject schema = parameterSchema == null
                ? new JsonObject()
                : gson.toJsonTree(parameterSchema).getAsJsonObject();
        schema.addProperty("type", "object");
        JsonObject props = schema.has("properties") && schema.get("properties").isJsonObject()
                ? schema.getAsJsonObject("properties") : new JsonObject();
        props.add("companion", companionProp());
        schema.add("properties", props);
        JsonArray required = schema.has("required") && schema.get("required").isJsonArray()
                ? schema.getAsJsonArray("required") : new JsonArray();
        boolean has = false;
        for (JsonElement e : required) if ("companion".equals(e.getAsString())) has = true;
        if (!has) required.add("companion");
        schema.add("required", required);
        return schema;
    }

    private JsonObject companionProp() {
        JsonObject p = new JsonObject();
        p.addProperty("type", "string");
        p.addProperty("description", "Which companion to act with — its name or id (see list_companions).");
        return p;
    }

    // ---- tools/call ----

    /** 派发一次工具调用,并把它记进活动流——面板上那条流就是这里喂的。 */
    private JsonObject toolsCallResult(JsonObject req) {
        JsonObject params = req.has("params") && req.get("params").isJsonObject()
                ? req.getAsJsonObject("params") : new JsonObject();
        String name = params.has("name") ? params.get("name").getAsString() : "";
        JsonObject args = params.has("arguments") && params.get("arguments").isJsonObject()
                ? params.getAsJsonObject("arguments") : new JsonObject();

        JsonObject out = dispatchToolCall(name, args);
        McpMode.instance().record(name, argsSummary(args), textOf(out), isError(out));
        return out;
    }

    private JsonObject dispatchToolCall(String name, JsonObject args) {
        try {
            return switch (name) {
                case "list_companions" -> content(listCompanions(), false);
                case "create_companion" -> handleCreate(args);
                case "delete_companion" -> handleDelete(args);
                default -> handleToolInvoke(name, args);
            };
        } catch (TimeoutException te) {
            return content("timed out waiting for the action to finish", true);
        } catch (Exception ex) {
            Constants.LOG.warn("[numen-mcp] tool {} failed: {}", name, ex.toString());
            return content("call failed; check the game state and try again", true);
        }
    }

    /** 参数压成一行 {@code companion=Alice, count=5};过长截断,面板一行放得下。 */
    private static String argsSummary(JsonObject args) {
        StringBuilder sb = new StringBuilder();
        for (var entry : args.entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            JsonElement v = entry.getValue();
            String text = v.isJsonPrimitive() ? v.getAsString() : v.toString();
            sb.append(entry.getKey()).append('=').append(truncate(text, 30));
            if (sb.length() > SUMMARY_LIMIT) break;
        }
        return truncate(sb.toString(), SUMMARY_LIMIT);
    }

    private static String textOf(JsonObject result) {
        if (!result.has("content") || !result.get("content").isJsonArray()) return "";
        JsonArray arr = result.getAsJsonArray("content");
        if (arr.isEmpty() || !arr.get(0).isJsonObject()) return "";
        JsonObject first = arr.get(0).getAsJsonObject();
        return first.has("text") ? truncate(first.get("text").getAsString().replace('\n', ' '), SUMMARY_LIMIT) : "";
    }

    private static boolean isError(JsonObject result) {
        return result.has("isError") && result.get("isError").getAsBoolean();
    }

    private static String truncate(String s, int limit) {
        return s.length() <= limit ? s : s.substring(0, limit) + "…";
    }

    private String listCompanions() throws Exception {
        List<NumenActuator.Companion> list = NumenActuator.companions()
                .get(CONTROL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (list.isEmpty()) {
            return "No companions are live in the world right now. Summon one in-game first.";
        }
        StringBuilder sb = new StringBuilder("Live companions:\n");
        for (NumenActuator.Companion c : list) {
            sb.append("- ").append(c.name()).append("  (id: ").append(c.uuid()).append(")\n");
        }
        return sb.toString().stripTrailing();
    }

    private JsonObject handleCreate(JsonObject args) throws Exception {
        String name = args.has("name") && !args.get("name").isJsonNull()
                ? args.get("name").getAsString().trim() : "";
        if (name.isEmpty()) {
            return content("create_companion needs a 'name' (3–16 letters, digits, or underscore)", true);
        }
        boolean ok = NumenActuator.create(name).get(CONTROL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!ok) {
            return content("could not summon — is the game in a world?", true);
        }
        return content("summoning '" + name + "' — it arrives in a moment; call list_companions to confirm, "
                + "then drive it directly (no acquire needed)", false);
    }

    private JsonObject handleDelete(JsonObject args) throws Exception {
        UUID target = resolveCompanion(args);
        if (target == null) {
            return content("no such companion — call list_companions to see valid names/ids", true);
        }
        boolean ok = NumenActuator.delete(target).get(CONTROL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return content(ok ? "dismissed " + target + " — it dropped its inventory and is gone for good"
                : "could not dismiss " + target, !ok);
    }

    private JsonObject handleToolInvoke(String toolName, JsonObject args) throws Exception {
        UUID target = resolveCompanion(args);
        if (target == null) {
            return content("this tool needs a 'companion' argument (a name or id from list_companions)", true);
        }
        JsonObject toolArgs = args.deepCopy();
        toolArgs.remove("companion");
        String result = NumenActuator.invoke(target, toolName, toolArgs.toString())
                .get(config.callTimeoutSeconds(), TimeUnit.SECONDS);
        boolean isError = false;
        try {
            JsonObject r = JsonParser.parseString(result).getAsJsonObject();
            isError = r.has("success") && !r.get("success").getAsBoolean();
        } catch (RuntimeException ignored) {
            // non-JSON result — treat as plain text, not an error
        }
        return content(result, isError);
    }

    /** Resolve the {@code companion} argument (name or UUID) to a live companion's UUID, or null. */
    private UUID resolveCompanion(JsonObject args) throws Exception {
        if (!args.has("companion") || args.get("companion").isJsonNull()) return null;
        String raw = args.get("companion").getAsString().trim();
        if (raw.isEmpty()) return null;
        List<NumenActuator.Companion> list = NumenActuator.companions()
                .get(CONTROL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        // exact id match first
        for (NumenActuator.Companion c : list) {
            if (c.uuid().toString().equalsIgnoreCase(raw)) return c.uuid();
        }
        // then exact name, then case-insensitive name
        for (NumenActuator.Companion c : list) {
            if (c.name().equals(raw)) return c.uuid();
        }
        for (NumenActuator.Companion c : list) {
            if (c.name().equalsIgnoreCase(raw)) return c.uuid();
        }
        return null;
    }

    // ---- result envelopes ----

    private JsonObject content(String text, boolean isError) {
        JsonObject item = new JsonObject();
        item.addProperty("type", "text");
        item.addProperty("text", text);
        JsonArray arr = new JsonArray();
        arr.add(item);
        JsonObject result = new JsonObject();
        result.add("content", arr);
        result.addProperty("isError", isError);
        return result;
    }

    private JsonObject okResponse(JsonElement id, JsonObject result) {
        JsonObject r = new JsonObject();
        r.addProperty("jsonrpc", "2.0");
        r.add("id", id);
        r.add("result", result);
        return r;
    }

    private JsonObject errorResponse(JsonElement id, int code, String message) {
        JsonObject err = new JsonObject();
        err.addProperty("code", code);
        err.addProperty("message", message);
        JsonObject r = new JsonObject();
        r.addProperty("jsonrpc", "2.0");
        r.add("id", id == null ? JsonNull() : id);
        r.add("error", err);
        return r;
    }

    private static JsonElement JsonNull() {
        return com.google.gson.JsonNull.INSTANCE;
    }
}
