package com.dwinovo.numen.core.tools;

import static com.dwinovo.numen.task.TaskDispatch.*;

import com.dwinovo.numen.agent.tool.Schema;
import com.dwinovo.numen.agent.tool.NumenTool;
import com.dwinovo.numen.entity.NumenPlayer;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** World-action tool (raw NumenTool): gather blocks by type and quantity. */
public final class AutoMineTool implements NumenTool {

    private static final Gson GSON = new Gson();
    private final BlockActionTools impl = new BlockActionTools();

    private record Args(List<String> block_ids, int count) {}

    @Override
    public String name() {
        return "mine";
    }

    @Override
    public String description() {
        return "Gather blocks by type and count. Give block id(s) and how many ITEMS you want — it finds "
                + "the nearest matches, travels to each with full terrain-traversing navigation (digs to "
                + "buried ores, pillars up cliffs, bridges gaps), mines, and repeats until `count` NEW items "
                + "are gained or none remain nearby. No coordinates or goto needed. count is items, not "
                + "blocks (redstone_ore drops ~4). Include all variants in block_ids (iron_ore AND "
                + "deepslate_iron_ore). Only mines what its tools actually harvest, and stops naming the "
                + "needed tier if nothing qualifies (to destroy a block regardless of drops, first goto it "
                + "then use interact_at with button=left and its x,y,z). BACKGROUND: a successful call is already running; do not call mine/goto "
                + "again while <current_task> exists and do not poll. task_finished status=done means the "
                + "requested count is complete; only timeout permits resending the same arguments. "
                + "SCAFFOLD COST: navigation bridges gaps and pillars up by PLACING blocks from your "
                + "inventory — anything in the throwaway-scaffold tag (cobblestone, stone, dirt, deepslate, "
                + "netherrack, andesite, diorite, granite, tuff, ...) can be consumed climbing back out, so "
                + "a mine task may report gathered=N while your backpack holds fewer than N of a "
                + "scaffold-typed item. That is the pathfinder working, not a counting bug. If you must "
                + "KEEP the material, convert it immediately (smelt ores, craft planks/ingots/glass) or "
                + "store it in a chest before traveling far; iron ingots and diamonds are never consumed. "
                + "For cobblestone/stone/dirt runs, request ~25% extra to cover the climb out.";
    }


    @Override
    public Map<String, Object> parameterSchema() {
        return Schema.object()
                .stringArray("block_ids", "Namespaced block id(s) to gather; include all variants.", 1)
                .integer("count", "How many ITEMS to gather (not blocks) — a block may drop several, and it "
                        + "counts only items gained on top of what you already hold.", 1, 256)
                .build();
    }

    @Override
    public void onServerCall(String toolCallId, JsonObject args, NumenPlayer companion, Consumer<String> reply) {
        Args a = GSON.fromJson(args, Args.class);
        dispatchAsync(companion, impl.autoMine(a.block_ids(), a.count(),
                ctx(toolCallId, companion)), reply);
    }
}
