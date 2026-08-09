package com.dwinovo.numen.core.tools;

import static com.dwinovo.numen.task.TaskDispatch.ctx;
import static com.dwinovo.numen.task.TaskDispatch.dispatchAsync;

import com.dwinovo.numen.agent.tool.NumenTool;
import com.dwinovo.numen.core.task.CombatTaskRecord;
import com.dwinovo.numen.core.task.combat.CombatStance;
import com.dwinovo.numen.entity.NumenPlayer;
import com.dwinovo.numen.task.ErrorCode;
import com.dwinovo.numen.task.TaskResult;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Start one bounded, observable tactical combat episode against perceived entity ids. */
public final class CombatTool implements NumenTool {
    private static final Gson GSON = new Gson();
    private final CombatTools impl = new CombatTools();

    private record Args(List<Integer> entity_ids, String stance, Double max_range,
                        Double flee_health, Integer max_seconds) {}

    @Override public String name() { return "combat"; }

    @Override public String description() {
        return "Run deterministic tactical combat against SPECIFIC runtime entity ids from perception. "
                + "The embodied controller maintains a bounded threat blackboard, chooses chase/strafe/attack/"
                + "kite/flee each tick, and applies a final health/hazard safety shield. stance=defensive only "
                + "engages entities attacking this body; aggressive treats every supplied id as authorized; "
                + "kite prioritizes lateral range control. BACKGROUND: acceptance means combat is running.";
    }

    @Override public Map<String, Object> parameterSchema() {
        Map<String, Object> idItem = Map.of("type", "integer");
        Map<String, Object> ids = new LinkedHashMap<>();
        ids.put("type", "array");
        ids.put("description", "Runtime entity ids from scan_nearby_entities (1-20 distinct targets).");
        ids.put("items", idItem);
        ids.put("minItems", 1);
        ids.put("maxItems", 20);

        Map<String, Object> stance = new LinkedHashMap<>();
        stance.put("type", "string");
        stance.put("enum", List.of("defensive", "aggressive", "kite"));
        stance.put("default", "defensive");

        Map<String, Object> maxRange = boundedNumber(4, 32, 12,
                "Combat observation/leash radius in blocks.");
        Map<String, Object> fleeHealth = boundedNumber(4, 16, 8,
                "Stop attacking and retreat at or below this health.");
        Map<String, Object> maxSeconds = new LinkedHashMap<>();
        maxSeconds.put("type", "integer");
        maxSeconds.put("minimum", 30);
        maxSeconds.put("maximum", 600);
        maxSeconds.put("default", 300);
        maxSeconds.put("description", "Maximum episode duration in seconds.");

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("entity_ids", ids);
        props.put("stance", stance);
        props.put("max_range", maxRange);
        props.put("flee_health", fleeHealth);
        props.put("max_seconds", maxSeconds);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "object");
        root.put("properties", props);
        root.put("required", List.of("entity_ids"));
        root.put("additionalProperties", false);
        return root;
    }

    private static Map<String, Object> boundedNumber(double min, double max, double defaultValue,
                                                      String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "number");
        schema.put("minimum", min);
        schema.put("maximum", max);
        schema.put("default", defaultValue);
        schema.put("description", description);
        return schema;
    }

    @Override public void onServerCall(String toolCallId, JsonObject args, NumenPlayer companion,
                                       Consumer<String> reply) {
        CombatTaskRecord record;
        try {
            Args a = GSON.fromJson(args, Args.class);
            CombatStance stance = CombatStance.parse(a.stance());
            double maxRange = a.max_range() == null ? 12.0 : a.max_range();
            double fleeHealth = a.flee_health() == null ? 8.0 : a.flee_health();
            int maxSeconds = a.max_seconds() == null ? 300 : a.max_seconds();
            record = (CombatTaskRecord) impl.combat(a.entity_ids(), stance, maxRange, fleeHealth,
                    maxSeconds, ctx(toolCallId, companion));
        } catch (JsonParseException | IllegalArgumentException e) {
            String message = e.getMessage() == null ? "invalid combat arguments" : e.getMessage();
            reply.accept(TaskResult.fail(message)
                    .withObservability(ErrorCode.VALIDATION.code(), false,
                            List.of("Correct the combat arguments using the live tool schema, then retry."), null)
                    .toJson());
            return;
        }
        dispatchAsync(companion, record, reply);
    }
}
