package com.dwinovo.numen.core.tools;

import com.dwinovo.numen.agent.tool.NumenTool;
import com.dwinovo.numen.task.TaskStatusTool;
import com.dwinovo.numen.task.TaskStopTool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract guard for the tool descriptions: every tool the engine registers
 * must present an English description with enough guidance for a model to know
 * when to use it. No CJK in model-visible description strings (internal
 * comments may stay Chinese).
 *
 * <p>Tools are registered during mod init, so this test instantiates the known
 * roster directly instead of depending on {@code ToolRegistry} state.
 */
class DescriptionsTest {

    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");
    private static final Pattern WHEN = Pattern.compile(
            "(?i)(read|get|check|inspect|scan|lookup|list|find|equip|eat|drop|take|"
                    + "transfer|close|build|craft|locate|move|mine|attack|interact|use|"
                    + "call|when|for|to|needs|requires|if|best)");

    /** The exact tool roster registered by NumenCore.registerTools(). */
    private static List<NumenTool> roster() {
        return List.of(
                new MoveToTool(), new MeleeAttackTool(), new RangedAttackTool(),
                new LocateStructureTool(), new LocateBiomeTool(), new CollectItemsTool(),
                new FishTool(), new AutoMineTool(), new EquipItemTool(), new BuildTool(),
                new BlueprintTool(), new BlueprintReadTool(), new InteractAtTool(),
                new InteractEntityTool(), new EatItemTool(), new TaskStatusTool(),
                new TaskStopTool(), new DropItemsTool(), new TakeItemsTool(),
                new InspectGuiTool(), new TransferTool(), new CloseGuiTool(),
                new GetSelfStatusTool(), new GetOwnerStatusTool(), new LookupRecipeTool(),
                new CraftTool(), new ScanNearbyEntitiesTool(), new ScanBlocksTool(),
                new LookAroundTool(), new InspectBlockTool(), new InspectBlockStorageTool(),
                new GetWorldInfoTool(), new TodoWriteTool(), new LoadSkillTool());
    }

    @Test
    void everyToolHasANonBlankDescriptionAndName() {
        List<NumenTool> tools = roster();
        assertFalse(tools.isEmpty());
        for (NumenTool t : tools) {
            assertNotNull(t.name());
            assertFalse(t.name().isBlank(), "tool with blank name: " + t.getClass());
            String desc = t.description();
            assertFalse(desc == null || desc.isBlank(), t.name() + " has a blank description");
        }
    }

    @Test
    void descriptionsContainNoCjkCharacters() {
        for (NumenTool t : roster()) {
            assertFalse(CJK.matcher(t.description()).find(),
                    t.name() + " description contains CJK: " + t.description());
        }
    }

    @Test
    void descriptionsCarryUsageGuidanceNotJustAName() {
        for (NumenTool t : roster()) {
            String desc = t.description();
            assertTrue(desc.length() >= 40,
                    t.name() + " description too short to guide a model: " + desc);
            assertTrue(WHEN.matcher(desc).find(),
                    t.name() + " description lacks usage guidance: " + desc);
        }
    }
}
